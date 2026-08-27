using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Campus.Editor
{
    /// <summary>
    /// Builds the campus scene from a menu entry instead of a dozen manual steps.
    /// </summary>
    /// <remarks>
    /// Clicking a scene together by hand is the part that goes wrong: one missing component, one field
    /// left unassigned, and the game starts without a token or renders magenta. Here every object and
    /// every reference is written down and reproducible — running it twice yields the same scene.
    /// <para>
    /// The materials use the URP shader, because this project runs the Universal Render Pipeline. With a
    /// Built-in shader every object would come out magenta, which is the classic first surprise when
    /// example code meets a URP project.
    /// </para>
    /// </remarks>
    public static class CampusSceneBuilder
    {
        private const string SceneFolder = "Assets/Campus/Scenes";
        private const string ScenePath = SceneFolder + "/CampusScene.unity";
        private const string MaterialFolder = "Assets/Campus/Materials";

        /// <summary>TU Darmstadt corporate blue for released content.</summary>
        private static readonly Color Published = new(0f, 0.41f, 0.62f);

        /// <summary>Amber and see-through for everything not released yet.</summary>
        private static readonly Color Draft = new(0.95f, 0.65f, 0.15f, 0.45f);

        [MenuItem("Campus/Szene erzeugen", false, 10)]
        public static void BuildScene()
        {
            if (!EditorUtility.DisplayDialog("Campus-Szene erzeugen",
                    "Legt " + ScenePath + " neu an. Eine vorhandene Datei wird überschrieben.",
                    "Erzeugen", "Abbrechen"))
            {
                return;
            }

            var scene = EditorSceneManager.NewScene(NewSceneSetup.DefaultGameObjects,
                NewSceneMode.Single);

            var infoPanel = CreateUi();
            CreateGround();
            CreateBridge();
            var loader = CreateLoader();
            CreateCamera(infoPanel);
            CreateGameState();

            Directory.CreateDirectory(SceneFolder);
            EditorSceneManager.SaveScene(scene, ScenePath);
            AddToBuildSettings();

            Selection.activeObject = loader;
            EditorUtility.DisplayDialog("Fertig",
                "Die Szene liegt unter " + ScenePath + ".\n\n"
                + "Zum Testen im Editor auf dem Objekt 'WebBridge' ein Access-Token in das Feld "
                + "'Editor Token' eintragen — dann lädt Play dieselben Daten wie der Browser.",
                "Alles klar");
            Debug.Log("Campus-Szene erzeugt: " + ScenePath);
        }

        /// <summary>A plain floor, so the buildings do not float in the void.</summary>
        private static void CreateGround()
        {
            var ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
            ground.name = "Boden";
            ground.transform.localScale = new Vector3(40f, 1f, 40f);
            ground.GetComponent<Renderer>().sharedMaterial =
                CreateMaterial("Boden", new Color(0.82f, 0.84f, 0.85f));
        }

        private static void CreateBridge()
        {
            var bridge = new GameObject("WebBridge");
            bridge.AddComponent<WebBridge>();
            // The name matters: the page addresses this object in SendMessage("WebBridge", …).
        }

        private static SceneLoader CreateLoader()
        {
            var campus = new GameObject("Campus");
            var loader = campus.AddComponent<SceneLoader>();

            var serialized = new SerializedObject(loader);
            // Spawn, not Bind: this scene is an empty plane, so there is nothing here to look a
            // building up in. In the FEC campus it is the other way round — see CampusSceneInjector.
            serialized.FindProperty("buildingMode").enumValueIndex = (int)SceneLoader.BuildingMode.Spawn;
            serialized.FindProperty("publishedMaterial").objectReferenceValue =
                CreateMaterial("POI_Freigegeben", Published);
            serialized.FindProperty("draftMaterial").objectReferenceValue =
                CreateMaterial("POI_Entwurf", Draft, transparent: true);
            serialized.ApplyModifiedPropertiesWithoutUndo();
            return loader;
        }

        private static void CreateCamera(InfoPanel infoPanel)
        {
            var camera = Camera.main != null ? Camera.main.gameObject : new GameObject("Main Camera");
            camera.name = "Spieler";
            camera.tag = "MainCamera";
            camera.transform.position = new Vector3(0f, 12f, -45f);
            camera.transform.rotation = Quaternion.Euler(12f, 0f, 0f);

            if (!camera.TryGetComponent<Camera>(out _))
            {
                camera.AddComponent<Camera>();
            }

            var input = camera.AddComponent<CampusInput>();
            var serialized = new SerializedObject(input);
            serialized.FindProperty("infoPanel").objectReferenceValue = infoPanel;
            serialized.ApplyModifiedPropertiesWithoutUndo();
        }

        private static void CreateGameState()
        {
            var state = new GameObject("GameState");
            var client = state.AddComponent<GameStateClient>();

            var serialized = new SerializedObject(client);
            // The camera is the player here: its position is what gets saved and restored.
            serialized.FindProperty("player").objectReferenceValue =
                Camera.main != null ? Camera.main.transform : null;
            serialized.ApplyModifiedPropertiesWithoutUndo();
        }

        /// <summary>
        /// Canvas, panel and label for the click info. No EventSystem on purpose: the panel only shows
        /// text, and an EventSystem without the Input System UI module would log a warning every frame.
        /// </summary>
        private static InfoPanel CreateUi()
        {
            var canvasObject = new GameObject("UI",
                typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            var canvas = canvasObject.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            var scaler = canvasObject.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920f, 1080f);

            var panelObject = new GameObject("InfoPanel", typeof(Image));
            panelObject.transform.SetParent(canvasObject.transform, false);
            var panelImage = panelObject.GetComponent<Image>();
            panelImage.color = new Color(0.05f, 0.07f, 0.09f, 0.88f);
            var panelRect = panelObject.GetComponent<RectTransform>();
            panelRect.anchorMin = new Vector2(0f, 0f);
            panelRect.anchorMax = new Vector2(0f, 0f);
            panelRect.pivot = new Vector2(0f, 0f);
            panelRect.anchoredPosition = new Vector2(24f, 24f);
            panelRect.sizeDelta = new Vector2(620f, 260f);

            var labelObject = new GameObject("Text", typeof(Text));
            labelObject.transform.SetParent(panelObject.transform, false);
            var label = labelObject.GetComponent<Text>();
            label.font = LegacyFont();
            label.fontSize = 22;
            label.color = Color.white;
            label.supportRichText = true;
            label.alignment = TextAnchor.UpperLeft;
            var labelRect = labelObject.GetComponent<RectTransform>();
            labelRect.anchorMin = Vector2.zero;
            labelRect.anchorMax = Vector2.one;
            labelRect.offsetMin = new Vector2(18f, 18f);
            labelRect.offsetMax = new Vector2(-18f, -18f);

            var infoPanel = canvasObject.AddComponent<InfoPanel>();
            var serialized = new SerializedObject(infoPanel);
            serialized.FindProperty("panel").objectReferenceValue = panelObject;
            serialized.FindProperty("label").objectReferenceValue = label;
            serialized.ApplyModifiedPropertiesWithoutUndo();

            var hint = new GameObject("Bedienhinweis", typeof(Text));
            hint.transform.SetParent(canvasObject.transform, false);
            var hintText = hint.GetComponent<Text>();
            hintText.font = LegacyFont();
            hintText.text = "WASD bewegen · rechte Maustaste umsehen · Q/E Höhe · Klick auf einen Würfel";
            hintText.fontSize = 20;
            hintText.color = new Color(1f, 1f, 1f, 0.65f);
            hintText.alignment = TextAnchor.UpperLeft;
            var hintRect = hint.GetComponent<RectTransform>();
            hintRect.anchorMin = new Vector2(0f, 1f);
            hintRect.anchorMax = new Vector2(0f, 1f);
            hintRect.pivot = new Vector2(0f, 1f);
            hintRect.anchoredPosition = new Vector2(24f, -24f);
            hintRect.sizeDelta = new Vector2(900f, 40f);

            return infoPanel;
        }

        /// <summary>
        /// Unity 6 no longer ships the built-in Arial as a default font asset; LegacyRuntime.ttf is the
        /// documented replacement for <c>UnityEngine.UI.Text</c>.
        /// </summary>
        private static Font LegacyFont() =>
            AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf")
            ?? Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");

        private static Material CreateMaterial(string name, Color color, bool transparent = false)
        {
            Directory.CreateDirectory(MaterialFolder);
            var path = $"{MaterialFolder}/{name}.mat";

            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            var material = new Material(shader) { color = color };

            if (transparent)
            {
                // The URP recipe for a transparent Lit material. Setting only the alpha would leave the
                // object opaque, because the surface type is a shader keyword, not a colour.
                material.SetFloat("_Surface", 1f);
                material.SetFloat("_Blend", 0f);
                material.SetOverrideTag("RenderType", "Transparent");
                material.SetInt("_SrcBlend", (int)BlendMode.SrcAlpha);
                material.SetInt("_DstBlend", (int)BlendMode.OneMinusSrcAlpha);
                material.SetInt("_ZWrite", 0);
                material.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
                material.renderQueue = (int)RenderQueue.Transparent;
            }

            AssetDatabase.CreateAsset(material, path);
            return material;
        }

        private static void AddToBuildSettings()
        {
            var scenes = new EditorBuildSettingsScene[]
            {
                new(ScenePath, true),
            };
            EditorBuildSettings.scenes = scenes;
        }
    }
}
