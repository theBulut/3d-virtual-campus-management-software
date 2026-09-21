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

        [Header("Absturzsicherung")]
        [Tooltip("Unterhalb dieser Höhe gilt der Spieler als aus der Welt gefallen und wird an den "
                 + "Startpunkt zurückgesetzt.")]
        [SerializeField] private float minValidY = -50f;

        [Tooltip("Oberhalb dieser Höhe gilt eine gespeicherte Position als unbrauchbar und wird beim "
                 + "Laden verworfen.")]
        [SerializeField] private float maxValidY = 1000f;

        [Tooltip("Aus: der Spieler fällt weiter, statt zurückgesetzt zu werden. Nur zum Nachstellen "
                 + "eines Fehlers sinnvoll.")]
        [SerializeField] private bool catchFalling = true;

        public GameStateData State { get; private set; } = new();

        /// <summary>Nothing is written before the first load answered; otherwise it would overwrite.</summary>
        private bool loaded;

        /// <summary>
        /// Where the scene put the player, read before the stored position is applied. The hosting
        /// project owns the spawn point and does not expose it, so the only reliable way to learn it is
        /// to look at the player before anything has moved them.
        /// </summary>
        private Vector3 spawnPosition;
        private bool spawnKnown;

        /// <summary>Seconds the fall watchdog waits before it may fire again.</summary>
        private const float RescueCooldownSeconds = 2f;

        private float nextRescueAllowed;

        private IEnumerator Start()
        {
            ResolvePlayer();
            RememberSpawn();

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

        /// <summary>
        /// Takes the spawn point from the untouched player. Must run before <see cref="Load"/>, or the
        /// remembered point would be the stored position — and resetting to it would put the player back
        /// into whatever hole they are trying to escape.
        /// </summary>
        private void RememberSpawn()
        {
            if (player == null)
            {
                return;
            }
            spawnPosition = player.position;
            spawnKnown = true;
        }

        /// <summary>
        /// A position worth applying. Rejects the two shapes that break a session: values that are not
        /// finite, which a damaged payload can carry and which poison every later calculation, and
        /// heights far outside the world, which are what a fall leaves behind.
        /// </summary>
        private bool IsUsable(Vector3 position) =>
            float.IsFinite(position.x) && float.IsFinite(position.y) && float.IsFinite(position.z)
            && position.y >= minValidY && position.y <= maxValidY;

        /// <summary>
        /// Puts the player back where the scene started them. Returns false when there is no player or no
        /// known spawn — the sandbox scene, for instance — so the caller can say so instead of pretending
        /// the key did something.
        /// </summary>
        public bool ResetToSpawn()
        {
            if (player == null || !spawnKnown)
            {
                Debug.LogWarning("Kein Startpunkt bekannt — es gibt keinen Spieler in dieser Szene.");
                return false;
            }

            Teleport(spawnPosition);
            // The position is the one thing the stored state must not keep from a failed run. Writing it
            // straight away means a reload cannot drop the player back into the void, even if the tab is
            // closed before the next autosave.
            State.position = new Vector3Data
            {
                x = spawnPosition.x,
                y = spawnPosition.y,
                z = spawnPosition.z,
            };
            SaveNow();

            Debug.Log($"Zurück am Startpunkt {spawnPosition}.");
            return true;
        }

        /// <summary>
        /// Catches the fall that no stored position can predict. The player walks off an edge, keeps
        /// falling and never lands; nothing in the hosting project stops them, and the autosave would
        /// write that position every 30 seconds.
        /// </summary>
        private void Update()
        {
            if (!catchFalling || player == null || !spawnKnown)
            {
                return;
            }
            if (player.position.y >= minValidY || Time.time < nextRescueAllowed)
            {
                return;
            }

            // A spawn point below the threshold cannot cure the condition, so the rescue would fire
            // again on the very next frame — and each one writes to the API. Refuse once and stop
            // watching rather than flooding the network with a misconfiguration.
            if (spawnPosition.y < minValidY)
            {
                Debug.LogError($"Der Startpunkt liegt selbst unter {minValidY} m. Absturzsicherung "
                               + "abgeschaltet — bitte 'Min Valid Y' anpassen.");
                catchFalling = false;
                return;
            }

            // The player needs a moment to land before the next check; without this a bounce off the
            // spawn collider could trigger a second rescue.
            nextRescueAllowed = Time.time + RescueCooldownSeconds;
            Debug.LogWarning($"Spieler unter {minValidY} m — aus der Welt gefallen, "
                             + "zurück an den Startpunkt.");
            ResetToSpawn();
            // Not the ?. operator: Unity overrides == for destroyed objects, and ?. bypasses that
            // override, so a destroyed canvas would be treated as alive.
            if (CampusUi.Instance != null)
            {
                CampusUi.Instance.Show("Du bist aus der Welt gefallen und stehst wieder am Start.");
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
                var stored = State.position.ToVector3();
                if (IsUsable(stored))
                {
                    Teleport(stored);
                }
                else
                {
                    // Dropping the stored position rather than applying it: a session that ended in a
                    // fall must not hand that fall to the next one. The rest of the state survives.
                    Debug.LogWarning($"Gespeicherte Position {stored} liegt außerhalb der Welt und "
                                     + "wird verworfen — Start am Spawn-Punkt.");
                    State.position = null;
                }
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

            // Without this the fall continues at the spawn point: a rigidbody keeps the speed it picked
            // up on the way down, and the player drops through the ground a second time.
            if (player.TryGetComponent<Rigidbody>(out var body) && !body.isKinematic)
            {
#if UNITY_6000_0_OR_NEWER
                body.linearVelocity = Vector3.zero;
#else
                body.velocity = Vector3.zero;
#endif
                body.angularVelocity = Vector3.zero;
            }

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
                // A position from mid-fall is worse than no position: it survives the session and drops
                // the next one straight back into the void. The previous good value is kept instead.
                if (IsUsable(player.position))
                {
                    State.position = new Vector3Data
                    {
                        x = player.position.x,
                        y = player.position.y,
                        z = player.position.z,
                    };
                }
                else
                {
                    Debug.LogWarning($"Position {player.position} wird nicht gespeichert — "
                                     + "außerhalb der Welt.");
                }
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
