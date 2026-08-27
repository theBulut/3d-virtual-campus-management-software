using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using UnityEngine.Networking;

namespace Campus
{
    /// <summary>
    /// Loads and stores the progress of the signed-in account (FA-25).
    /// </summary>
    /// <remarks>
    /// The endpoint always works on the caller's own state — the account comes from the token, never
    /// from a parameter, so there is nothing here that could point at somebody else.
    /// <para>
    /// A 204 means this account has never played: the game then starts from the beginning, which is
    /// exactly the case of a freshly registered student.
    /// </para>
    /// <para>
    /// <b>When is it saved?</b> Every <see cref="autoSaveSeconds"/> seconds, and whenever the window
    /// loses focus — in WebGL that is what a tab switch triggers. Deliberately <em>not</em> in
    /// <c>OnApplicationQuit</c>: a coroutine started there never gets another frame to run in, and a
    /// browser closing a tab does not reliably call it at all. A save that only appears to happen is
    /// worse than none, because nobody looks for the lost minutes.
    /// </para>
    /// </remarks>
    public class GameStateClient : MonoBehaviour
    {
        [Tooltip("Sekunden zwischen zwei automatischen Speicherungen")]
        [SerializeField] private float autoSaveSeconds = 30f;

        [Tooltip("Leer: das Objekt mit dem Tag 'Player' wird gesucht.")]
        [SerializeField] private Transform player;

        [Header("Besuchte Gebäude")]
        [Tooltip("Leer: die Komponente am CampusRoot wird gesucht.")]
        [SerializeField] private CampusBuildingRegistry registry;

        [Tooltip("Ab welcher Entfernung ein Gebäude als besucht gilt (Meter, waagerecht).")]
        [SerializeField] private float visitRadius = 40f;

        public GameStateData State { get; private set; } = new();

        /// <summary>Nothing is written before the first load answered; otherwise it would overwrite.</summary>
        private bool loaded;

        private IEnumerator Start()
        {
            ResolvePlayer();

            yield return Load();
            loaded = true;

            if (autoSaveSeconds > 0f)
            {
                InvokeRepeating(nameof(SaveNow), autoSaveSeconds, autoSaveSeconds);
            }
        }

        /// <summary>
        /// The hosting project owns the player object, so it is found by its tag rather than wired into
        /// a field that a scene rebuild would clear.
        /// </summary>
        private void ResolvePlayer()
        {
            if (player != null)
            {
                return;
            }
            try
            {
                var tagged = GameObject.FindGameObjectWithTag("Player");
                if (tagged != null)
                {
                    player = tagged.transform;
                }
            }
            catch (UnityException)
            {
                // No 'Player' tag in this project. The sandbox scene has none, and there the game state
                // is about the minutes played, not about a position.
            }
        }

        public IEnumerator Load()
        {
            var bridge = WebBridge.Instance;
            yield return bridge.EnsureToken();

            using var request = UnityWebRequest.Get($"{bridge.ApiBase()}/game/state");
            request.SetRequestHeader("Authorization", $"Bearer {bridge.Token()}");
            yield return request.SendWebRequest();

            if (request.responseCode == 204)
            {
                Debug.Log("Kein Spielstand vorhanden — neues Spiel");
                State = new GameStateData();
                yield break;
            }
            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogWarning($"Spielstand nicht ladbar: {request.responseCode} "
                                 + request.downloadHandler.text);
                yield break;
            }

            State = JsonUtility.FromJson<GameStateData>(request.downloadHandler.text)
                    ?? new GameStateData();
            if (player != null && State.position != null)
            {
                Teleport(State.position.ToVector3());
            }
            Debug.Log($"Spielstand geladen: {State.minutesPlayed} Minuten gespielt, zuletzt "
                      + $"{State.savedAt}");
        }

        /// <summary>
        /// Puts the player back where they stopped. A <c>CharacterController</c> writes its own position
        /// every frame from its internal state and would undo a plain assignment on the next one; it has
        /// to be switched off for the moment of the move. Same for a kinematic rigidbody.
        /// </summary>
        private void Teleport(Vector3 position)
        {
            var controller = player.GetComponent<CharacterController>();
            var wasEnabled = controller != null && controller.enabled;
            if (wasEnabled)
            {
                controller.enabled = false;
            }

            player.position = position;

            if (wasEnabled)
            {
                controller.enabled = true;
            }
        }

        /// <summary>Saves right now — used by the timer and callable from anywhere.</summary>
        public void SaveNow()
        {
            if (loaded)
            {
                StartCoroutine(Save());
            }
        }

        public IEnumerator Save()
        {
            if (player != null)
            {
                State.position = new Vector3Data
                {
                    x = player.position.x,
                    y = player.position.y,
                    z = player.position.z,
                };
                State.visitedBuildings = VisitedBuildings();
            }
            State.minutesPlayed = Mathf.RoundToInt(Time.realtimeSinceStartup / 60f);
            State.savedAt = DateTime.UtcNow.ToString("o");

            var bridge = WebBridge.Instance;
            var body = JsonUtility.ToJson(State);
            using var request = UnityWebRequest.Put($"{bridge.ApiBase()}/game/state", body);
            request.SetRequestHeader("Content-Type", "application/json");
            request.SetRequestHeader("Authorization", $"Bearer {bridge.Token()}");
            yield return request.SendWebRequest();

            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogWarning($"Spielstand nicht gespeichert: {request.responseCode} "
                                 + request.downloadHandler.text);
                yield break;
            }
            Debug.Log($"Spielstand gespeichert ({State.minutesPlayed} Minuten, "
                      + $"{State.visitedBuildings.Length} Gebäude besucht)");
        }

        /// <summary>
        /// Everything already visited plus whatever the player is standing next to now. Height is left
        /// out of the distance: standing on the third floor of a building still counts as being there.
        /// </summary>
        private string[] VisitedBuildings()
        {
            var visited = new HashSet<string>(State.visitedBuildings ?? Array.Empty<string>());

            var lookup = registry != null
                ? registry
                : registry = FindAnyObjectByType<CampusBuildingRegistry>();
            var scene = FindAnyObjectByType<SceneLoader>()?.Scene;
            if (lookup == null || scene == null)
            {
                return visited.ToArray();
            }

            foreach (var building in scene.buildings)
            {
                if (!lookup.TryAnchor(building.code, out var anchor))
                {
                    continue;
                }
                var distance = Vector2.Distance(new Vector2(anchor.x, anchor.z),
                    new Vector2(player.position.x, player.position.z));
                if (distance <= visitRadius)
                {
                    visited.Add(building.code);
                }
            }

            return visited.OrderBy(code => code).ToArray();
        }

        /// <summary>
        /// A tab switch in the browser lands here. It is the last moment at which a coroutine still gets
        /// frames, so it is the closest thing to "save on leaving" that WebGL actually offers.
        /// </summary>
        private void OnApplicationFocus(bool hasFocus)
        {
            if (!hasFocus)
            {
                SaveNow();
            }
        }

        /// <summary>On mobile browsers the pause callback fires instead of the focus one.</summary>
        private void OnApplicationPause(bool paused)
        {
            if (paused)
            {
                SaveNow();
            }
        }

        /// <summary>
        /// The document the server stores untouched. Extending it needs no migration — that is the point
        /// of keeping it opaque on the other side (docs/DECISIONS.md D-41).
        /// </summary>
        [Serializable]
        public class GameStateData
        {
            public Vector3Data position;
            public string[] visitedBuildings = Array.Empty<string>();
            public int minutesPlayed;
            public string savedAt;
        }
    }
}
