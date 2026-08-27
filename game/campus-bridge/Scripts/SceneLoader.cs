using System.Collections;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using UnityEngine.Networking;

namespace Campus
{
    /// <summary>
    /// Fetches the scene from the administration and puts it into the world (FA-24).
    /// </summary>
    /// <remarks>
    /// This is the whole point of the proof of concept: what an editor releases in the web interface
    /// stands on the campus after the next load. Nothing here knows about roles — the server decides
    /// what is in the payload, and an account that may not see a draft never receives one.
    /// <para>
    /// Two ways to place a building, chosen by <see cref="buildingMode"/>:
    /// <list type="bullet">
    /// <item><b>Bind</b> — the building already exists in the hosting scene and is looked up by its code
    /// (<see cref="CampusBuildingRegistry"/>). Nothing is instantiated. This is the mode for the FEC
    /// campus, where every house is a finished model.</item>
    /// <item><b>Spawn</b> — a prefab or a placeholder box is instantiated at the coordinates from the
    /// database. This is the mode for the standalone sandbox scene, which has no buildings of its
    /// own.</item>
    /// </list>
    /// </para>
    /// <para>
    /// A point of interest is placed relative to its building whenever that building was found:
    /// <c>position</c> is then an offset in metres from the anchor over the roof, which is what lets an
    /// editor place a point correctly without knowing a single world coordinate of a foreign scene. With
    /// no building, or with a building that is not in the scene, the same three numbers are read as
    /// absolute world coordinates (docs/DECISIONS.md D-47).
    /// </para>
    /// </remarks>
    public class SceneLoader : MonoBehaviour
    {
        public enum BuildingMode
        {
            /// <summary>Look the building up in the hosting scene by its code.</summary>
            Bind,

            /// <summary>Instantiate a prefab or a placeholder box from the database coordinates.</summary>
            Spawn,
        }

        [Header("Gebäude")]
        [SerializeField] private BuildingMode buildingMode = BuildingMode.Bind;

        [Tooltip("Wird für 'Bind' gebraucht. Leer: die Komponente auf demselben Objekt.")]
        [SerializeField] private CampusBuildingRegistry registry;

        [Tooltip("Nur für 'Spawn': Prefab je model_ref. Ohne Treffer entsteht ein Platzhalterquader.")]
        [SerializeField] private List<BuildingPrefab> buildingPrefabs = new();

        [Header("Darstellung")]
        [SerializeField] private Material publishedMaterial;
        [SerializeField] private Material draftMaterial;

        [Tooltip("Optional: Prefab für einen Orientierungspunkt. Leer: ein Würfel.")]
        [SerializeField] private GameObject poiMarkerPrefab;

        [SerializeField] private float poiScale = 2f;

        private readonly List<GameObject> spawned = new();

        public ScenePayload Scene { get; private set; }

        private IEnumerator Start()
        {
            yield return Reload();
        }

        /// <summary>Loads the scene again — after a change in the administration, for instance.</summary>
        public IEnumerator Reload()
        {
            var bridge = WebBridge.Instance;
            if (bridge == null)
            {
                Debug.LogError("WebBridge fehlt in der Szene; ohne ihn gibt es kein Token.");
                yield break;
            }

            // In the browser the token is already there; in the editor this signs in first.
            yield return bridge.EnsureToken();

            var token = bridge.Token();
            using var request = UnityWebRequest.Get($"{bridge.ApiBase()}/game/scene");
            if (!string.IsNullOrEmpty(token))
            {
                request.SetRequestHeader("Authorization", $"Bearer {token}");
            }

            yield return request.SendWebRequest();

            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogError(Explain(request, token));
                yield break;
            }

            Scene = JsonUtility.FromJson<ScenePayload>(request.downloadHandler.text);
            Build(Scene);
            bridge.NotifySceneReady();
        }

        /// <summary>
        /// Turns a failed request into a sentence that names the cause. A bare 401 is ambiguous — no
        /// token and a rejected token look identical from the outside — and that ambiguity cost an
        /// afternoon once.
        /// </summary>
        private static string Explain(UnityWebRequest request, string token)
        {
            var body = request.downloadHandler?.text ?? "";
            var reason = request.responseCode switch
            {
                401 when string.IsNullOrEmpty(token) =>
                    "Es wurde gar kein Token mitgeschickt. Im Editor meldet sich WebBridge selbst an — "
                    + "prüfe, ob das Backend läuft und ob auf dem Objekt 'WebBridge' Benutzername und "
                    + "Passwort stimmen.",
                401 => "Das Token wurde abgelehnt: abgelaufen (15 Minuten), von einem anderen "
                       + "Backend-Start ausgestellt, oder das Konto wurde geändert.",
                403 => "Das Konto darf die Szene nicht laden. Nötig ist POI_READ_PUBLISHED — "
                       + "MAINTENANCE_DEV hat sie nicht, und ein Konto mit erzwungenem Passwortwechsel "
                       + "bekommt ein eingeschränktes Token.",
                0 => "Keine Verbindung zum Backend. Läuft es? "
                     + "docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d db redis backend",
                _ => request.error,
            };
            return $"Szene konnte nicht geladen werden ({request.responseCode}): {reason}"
                   + (string.IsNullOrEmpty(body) ? "" : $"\nAntwort des Servers: {body}");
        }

        private void Build(ScenePayload scene)
        {
            Clear();

            var buildingsByCode = scene.buildings
                .Where(building => !string.IsNullOrEmpty(building.code))
                .GroupBy(building => CampusBuildingRegistry.Normalise(building.code))
                .ToDictionary(group => group.Key, group => group.First());

            if (buildingMode == BuildingMode.Bind)
            {
                BindBuildings(scene);
            }
            else
            {
                foreach (var building in scene.buildings)
                {
                    var instance = Spawn(building);
                    instance.transform.SetParent(transform, false);
                    spawned.Add(instance);
                }
            }

            foreach (var poi in scene.pois)
            {
                Place(poi, buildingsByCode);
            }

            if (CampusUi.Instance != null)
            {
                CampusUi.Instance.SetEditorialView(IsEditorialView(scene));
            }

            Debug.Log($"Szene aufgebaut: {scene.buildings.Length} Gebäude, {scene.pois.Length} POIs, "
                      + $"{scene.consultations.Length} Beratungsangebote");
        }

        /// <summary>
        /// Whether this account sees more than a visitor does. Nobody is asked — the answer is in the
        /// payload: the API fills <c>status</c> and <c>published</c> only for accounts that may see
        /// unpublished content, and omits the fields entirely for everyone else (D-42).
        /// </summary>
        private static bool IsEditorialView(ScenePayload scene) =>
            scene.pois.Any(poi => !string.IsNullOrEmpty(poi.status))
            || scene.buildings.Any(building => !string.IsNullOrEmpty(building.published));

        /// <summary>
        /// Looks the buildings up instead of creating them. Nothing in the hosting scene is moved or
        /// recoloured — an unpublished building gets a badge above it rather than a changed material,
        /// so the foreign scene stays exactly as its authors left it.
        /// </summary>
        private void BindBuildings(ScenePayload scene)
        {
            var lookup = Registry();
            if (lookup == null)
            {
                Debug.LogWarning("Kein CampusBuildingRegistry gefunden — Gebäude bleiben ungebunden. "
                                 + "Entweder die Komponente ergänzen oder auf 'Spawn' stellen.");
                return;
            }

            lookup.Bind(scene.buildings.Select(building => building.code));
            Debug.Log(lookup.Report());

            foreach (var building in scene.buildings)
            {
                if (!building.IsUnpublished || !lookup.TryAnchor(building.code, out var anchor))
                {
                    continue;
                }

                var badge = NewMarker($"Gebäude {building.code} (unveröffentlicht)", anchor);
                Paint(badge, true);
                spawned.Add(badge);
            }
        }

        /// <summary>Puts one point into the world and hands it its data.</summary>
        private void Place(ScenePoi poi, IReadOnlyDictionary<string, SceneBuilding> buildingsByCode)
        {
            var offset = poi.position.ToVector3();
            var lookup = buildingMode == BuildingMode.Bind ? Registry() : null;

            var position = lookup != null && lookup.TryAnchor(poi.buildingCode, out var anchor)
                ? anchor + offset
                : offset;

            var marker = NewMarker($"POI {poi.id} {poi.nameDe}", position);
            Paint(marker, poi.IsUnpublished);

            // Carries the data into the click handler, so the label needs no lookup table.
            var component = marker.AddComponent<PoiMarker>();
            component.Poi = poi;
            if (buildingsByCode.TryGetValue(CampusBuildingRegistry.Normalise(poi.buildingCode),
                    out var building))
            {
                component.Building = building;
            }

            spawned.Add(marker);
        }

        /// <summary>A marker object — from the prefab if there is one, otherwise a cube.</summary>
        private GameObject NewMarker(string markerName, Vector3 position)
        {
            var marker = poiMarkerPrefab != null
                ? Instantiate(poiMarkerPrefab)
                : GameObject.CreatePrimitive(PrimitiveType.Cube);

            marker.name = markerName;
            marker.transform.SetParent(transform, false);
            marker.transform.position = position;

            if (poiMarkerPrefab == null)
            {
                marker.transform.localScale = Vector3.one * poiScale;
            }

            // Without a collider the raycast in CampusInteraction never reaches the marker, and a click
            // silently does nothing — the kind of failure that looks like a broken API call.
            if (marker.GetComponentInChildren<Collider>() == null)
            {
                marker.AddComponent<BoxCollider>();
            }

            return marker;
        }

        private CampusBuildingRegistry Registry() =>
            registry != null ? registry : registry = GetComponent<CampusBuildingRegistry>();

        private GameObject Spawn(SceneBuilding building)
        {
            var prefab = buildingPrefabs.Find(entry => entry.modelRef == building.modelRef)?.prefab;
            var instance = prefab != null
                ? Instantiate(prefab)
                : GameObject.CreatePrimitive(PrimitiveType.Cube);

            instance.name = $"Gebäude {building.code}";
            instance.transform.position = building.position.ToVector3();
            instance.transform.rotation = Quaternion.Euler(0f, building.rotationY, 0f);

            if (prefab == null)
            {
                // A placeholder: wide, deep and low enough to read as a building rather than a box.
                instance.transform.localScale = new Vector3(12f, 6f, 12f);
                Paint(instance, building.IsUnpublished);
            }

            return instance;
        }

        /// <summary>Unpublished objects stay visible but are marked, so an editor can tell them apart.</summary>
        private void Paint(GameObject target, bool unpublished)
        {
            var material = unpublished ? draftMaterial : publishedMaterial;
            if (material == null)
            {
                return;
            }
            foreach (var renderer in target.GetComponentsInChildren<Renderer>())
            {
                renderer.material = material;
            }
        }

        private void Clear()
        {
            foreach (var instance in spawned)
            {
                Destroy(instance);
            }
            spawned.Clear();
        }

        /// <summary>Maps a <c>model_ref</c> from the database to a prefab of the project.</summary>
        [System.Serializable]
        public class BuildingPrefab
        {
            public string modelRef;
            public GameObject prefab;
        }
    }
}
