using UnityEngine;
#if ENABLE_INPUT_SYSTEM
using UnityEngine.InputSystem;
#endif

namespace Campus
{
    /// <summary>
    /// Opens the info panel of a point. The only input the bridge claims in a foreign scene.
    /// </summary>
    /// <remarks>
    /// The hosting project brings its own player controller — in the FEC campus that is
    /// <c>WorldCharacterController</c> on the object tagged <c>Player</c>. This component therefore moves
    /// nothing and rotates nothing; it reads one click and one key. <see cref="CampusInput"/> with its
    /// free-flying camera stays behind for the standalone sandbox scene, where there is no player at all.
    /// <para>
    /// When the cursor is locked — which is what a first-person controller does — the mouse position is
    /// meaningless and the ray is cast through the centre of the screen instead. Getting that wrong looks
    /// like a broken raycast: the panel opens for whatever happens to sit under the invisible pointer.
    /// </para>
    /// <para>
    /// Written against the new Input System with a fallback to the old one, so the same file compiles in
    /// a project set to either — or, like FEC, to both.
    /// </para>
    /// </remarks>
    public class CampusInteraction : MonoBehaviour
    {
        [Tooltip("Wie weit ein Klick reicht. Ohne Grenze öffnet ein Klick quer über den Campus ein "
                 + "Infofeld, das man gar nicht meinte.")]
        [SerializeField] private float maxDistance = 250f;

        [Tooltip("Optional: das Panel der Sandkasten-Szene. Leer: das zur Laufzeit gebaute CampusUi.")]
        [SerializeField] private InfoPanel fallbackPanel;

        private void Update()
        {
            if (ResetPressed())
            {
                ResetToSpawn();
                return;
            }
            if (EscapePressed())
            {
                Close();
                return;
            }
            if (!SelectPressed(out var screenPosition))
            {
                return;
            }

            var camera = Camera.main;
            if (camera == null)
            {
                return;
            }

            var ray = camera.ScreenPointToRay(Cursor.lockState == CursorLockMode.Locked
                ? new Vector2(Screen.width / 2f, Screen.height / 2f)
                : screenPosition);

            if (Physics.Raycast(ray, out var hit, maxDistance)
                && hit.collider.GetComponentInParent<PoiMarker>() is { } marker)
            {
                Open(marker.Describe());
                return;
            }
            Close();
        }

        private void Open(string text)
        {
            if (CampusUi.Instance != null)
            {
                CampusUi.Instance.Show(text);
                return;
            }
            if (fallbackPanel != null)
            {
                fallbackPanel.Show(text);
                return;
            }
            Debug.Log(text);
        }

        private void Close()
        {
            if (CampusUi.Instance != null)
            {
                CampusUi.Instance.Hide();
            }
            if (fallbackPanel != null)
            {
                fallbackPanel.Hide();
            }
        }

        private static bool SelectPressed(out Vector2 screenPosition)
        {
#if ENABLE_INPUT_SYSTEM
            var mouse = Mouse.current;
            if (mouse != null && mouse.leftButton.wasReleasedThisFrame)
            {
                screenPosition = mouse.position.ReadValue();
                return true;
            }
            var keyboard = Keyboard.current;
            if (keyboard != null && keyboard.eKey.wasPressedThisFrame)
            {
                screenPosition = new Vector2(Screen.width / 2f, Screen.height / 2f);
                return true;
            }
            screenPosition = Vector2.zero;
            return false;
#else
            if (Input.GetMouseButtonUp(0))
            {
                screenPosition = Input.mousePosition;
                return true;
            }
            if (Input.GetKeyDown(KeyCode.E))
            {
                screenPosition = new Vector2(Screen.width / 2f, Screen.height / 2f);
                return true;
            }
            screenPosition = Vector2.zero;
            return false;
#endif
        }

        private static bool EscapePressed()
        {
#if ENABLE_INPUT_SYSTEM
            return Keyboard.current != null && Keyboard.current.escapeKey.wasPressedThisFrame;
#else
            return Input.GetKeyDown(KeyCode.Escape);
#endif
        }

        /// <summary>
        /// <c>Pos1</c> — <c>Home</c> on an English layout. Chosen over the obvious <c>R</c> because the
        /// hosting project may bind that, and because a key nobody presses by accident matters here: an
        /// unintended reset during a study task would destroy the measurement for that task.
        /// </summary>
        private static bool ResetPressed()
        {
#if ENABLE_INPUT_SYSTEM
            return Keyboard.current != null && Keyboard.current.homeKey.wasPressedThisFrame;
#else
            return Input.GetKeyDown(KeyCode.Home);
#endif
        }

        /// <summary>
        /// Hands the request to <see cref="GameStateClient"/>, which owns the player and the spawn point.
        /// Looked up per press rather than cached: the object is created by the scene injector and may
        /// appear after this component.
        /// </summary>
        private void ResetToSpawn()
        {
            var state = FindAnyObjectByType<GameStateClient>();
            if (state == null)
            {
                Debug.LogWarning("Kein GameState in der Szene — Zurücksetzen nicht möglich.");
                return;
            }
            Open(state.ResetToSpawn()
                ? "Zurück am Startpunkt."
                : "Kein Startpunkt bekannt — diese Szene hat keinen Spieler.");
        }
    }
}
