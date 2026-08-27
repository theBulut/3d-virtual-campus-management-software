using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Campus.Editor
{
    /// <summary>
    /// Adds the administration bridge to whichever scene is currently open.
    /// </summary>
    /// <remarks>
    /// This is the entire manual step inside a foreign project. Open <c>Assets/Scenes/Web.unity</c>,
    /// call the menu item, done — four objects appear, their fields are wired, and the scene is saved.
    /// <para>
    /// Idempotent on purpose. A scene that is worked on by several people gets opened, changed and
    /// reverted; a menu item that adds a second <c>WebBridge</c> on the second call would produce
    /// exactly the failure <see cref="WebBridge"/> warns about at runtime, and it would be blamed on the
    /// scene rather than on the tool. Existing objects are reused and nothing already set is overwritten.
    /// </para>
    /// <para>
    /// Unlike <see cref="CampusSceneBuilder"/> this creates no ground, no camera and no controller: the
    /// hosting scene has all of that. The two menu items are for the two situations — build a scene from
    /// nothing, or join one that exists.
    /// </para>
    /// </remarks>
    public static class CampusSceneInjector
    {
        [MenuItem("Campus/Anbindung in aktuelle Szene einfügen", false, 1)]
        public static void Inject()
        {
            var scene = EditorSceneManager.GetActiveScene();
            if (!scene.IsValid())
            {
                EditorUtility.DisplayDialog("Keine Szene",
                    "Es ist keine Szene geöffnet.", "Verstanden");
                return;
            }

            var added = new List<string>();
            var kept = new List<string>();

            var bridge = Ensure<WebBridge>("WebBridge", added, kept);
            Ensure<CampusUi>("CampusUI", added, kept);
            var root = Ensure<SceneLoader>("CampusRoot", added, kept);
            var state = Ensure<GameStateClient>("GameState", added, kept);

            var registry = root.gameObject.GetComponent<CampusBuildingRegistry>()
                           ?? Undo.AddComponent<CampusBuildingRegistry>(root.gameObject);
            if (root.gameObject.GetComponent<CampusInteraction>() == null)
            {
                Undo.AddComponent<CampusInteraction>(root.gameObject);
            }

            WireLoader(root, registry);
            WireState(state, registry);

            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveOpenScenes();

            var report = added.Count == 0
                ? "Nichts zu tun — die Anbindung steht bereits in dieser Szene."
                : "Ergänzt: " + string.Join(", ", added);
            if (kept.Count > 0)
            {
                report += "\nUnverändert übernommen: " + string.Join(", ", kept);
            }
            report += $"\n\nSzene '{scene.name}' gespeichert."
                      + "\n\nNächster Schritt: Campus → WebGL-Build erzeugen."
                      + $"\nZum Testen im Editor genügt Play — {bridge.name} meldet sich selbst an.";

            Debug.Log(report);
            EditorUtility.DisplayDialog("Campus-Anbindung", report, "Alles klar");
        }

        /// <summary>
        /// Returns the component of this type that is already in the scene, or creates an object for it.
        /// Searching by type rather than by name, because the object may have been renamed and a second
        /// one would be worse than an oddly named first.
        /// </summary>
        private static T Ensure<T>(string objectName, List<string> added, List<string> kept)
            where T : Component
        {
            var existing = Object.FindObjectsByType<T>(FindObjectsInactive.Include,
                FindObjectsSortMode.None);
            if (existing.Length > 0)
            {
                if (existing.Length > 1)
                {
                    Debug.LogWarning($"Mehr als ein {typeof(T).Name} in der Szene. Verwendet wird "
                                     + $"'{existing[0].gameObject.name}'; die übrigen sollten weg.");
                }
                kept.Add($"{typeof(T).Name} ({existing[0].gameObject.name})");
                return existing[0];
            }

            var host = new GameObject(objectName);
            Undo.RegisterCreatedObjectUndo(host, "Campus-Anbindung einfügen");
            added.Add(objectName);
            return Undo.AddComponent<T>(host);
        }

        private static void WireLoader(SceneLoader loader, CampusBuildingRegistry registry)
        {
            var serialized = new SerializedObject(loader);

            // Bind, not Spawn: in a scene that already has its buildings, a box from a database
            // coordinate would stand next to the real house rather than on it.
            SetEnum(serialized, "buildingMode", (int)SceneLoader.BuildingMode.Bind);
            SetReference(serialized, "registry", registry);
            SetReferenceIfEmpty(serialized, "publishedMaterial", FindMaterial("POI_Freigegeben"));
            SetReferenceIfEmpty(serialized, "draftMaterial", FindMaterial("POI_Entwurf"));

            serialized.ApplyModifiedProperties();
            EditorUtility.SetDirty(loader);
        }

        private static void WireState(GameStateClient state, CampusBuildingRegistry registry)
        {
            var serialized = new SerializedObject(state);
            SetReference(serialized, "registry", registry);
            serialized.ApplyModifiedProperties();
            EditorUtility.SetDirty(state);
        }

        private static void SetEnum(SerializedObject serialized, string field, int value)
        {
            var property = serialized.FindProperty(field);
            if (property != null)
            {
                property.enumValueIndex = value;
            }
        }

        private static void SetReference(SerializedObject serialized, string field, Object value)
        {
            var property = serialized.FindProperty(field);
            if (property != null)
            {
                property.objectReferenceValue = value;
            }
        }

        /// <summary>Fills a reference only if it is empty — a choice made by hand is not overruled.</summary>
        private static void SetReferenceIfEmpty(SerializedObject serialized, string field, Object value)
        {
            var property = serialized.FindProperty(field);
            if (property != null && property.objectReferenceValue == null && value != null)
            {
                property.objectReferenceValue = value;
            }
        }

        /// <summary>
        /// Finds a material of the bridge by name. Searched rather than addressed by path, because
        /// <c>install-bridge.sh</c> may have put the folder anywhere under <c>Assets/</c>.
        /// </summary>
        private static Material FindMaterial(string materialName)
        {
            var guid = AssetDatabase.FindAssets($"{materialName} t:Material").FirstOrDefault();
            return guid == null
                ? null
                : AssetDatabase.LoadAssetAtPath<Material>(AssetDatabase.GUIDToAssetPath(guid));
        }
    }
}
