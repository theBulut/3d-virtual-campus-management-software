using UnityEngine;
#if ENABLE_INPUT_SYSTEM
using UnityEngine.InputSystem;
#endif

namespace Campus
{
    /// <summary>
    /// Moving the camera and clicking a point — the whole input of the proof of concept.
    /// </summary>
    /// <remarks>
    /// Written against the new Input System, because that is what the project is configured for, with a
    /// fallback to the old one so the scripts also compile in a project that still uses it (the FEC
    /// project may). Everything is behind <c>#if</c>, nothing else in the code has to know.
    /// <para>
    /// WASD moves, holding the right mouse button looks around, the scroll wheel changes height. Enough
    /// to walk a campus; a real controller from the target project replaces this file and nothing else.
    /// </para>
    /// </remarks>
    [RequireComponent(typeof(Camera))]
    public class CampusInput : MonoBehaviour
    {
        [SerializeField] private float moveSpeed = 20f;
        [SerializeField] private float lookSensitivity = 0.12f;
        [SerializeField] private InfoPanel infoPanel;

        private float yaw;
        private float pitch;

        private void Start()
        {
            var angles = transform.eulerAngles;
            yaw = angles.y;
            pitch = angles.x;
        }

        private void Update()
        {
            Look();
            Move();
            Select();
        }

        private void Look()
        {
#if ENABLE_INPUT_SYSTEM
            var mouse = Mouse.current;
            if (mouse == null || !mouse.rightButton.isPressed)
            {
                return;
            }
            var delta = mouse.delta.ReadValue();
#else
            if (!Input.GetMouseButton(1))
            {
                return;
            }
            var delta = new Vector2(Input.GetAxis("Mouse X"), Input.GetAxis("Mouse Y")) * 10f;
#endif
            yaw += delta.x * lookSensitivity;
            pitch = Mathf.Clamp(pitch - delta.y * lookSensitivity, -85f, 85f);
            transform.rotation = Quaternion.Euler(pitch, yaw, 0f);
        }

        private void Move()
        {
            var direction = Vector3.zero;
            var lift = 0f;

#if ENABLE_INPUT_SYSTEM
            var keyboard = Keyboard.current;
            if (keyboard == null)
            {
                return;
            }
            if (keyboard.wKey.isPressed || keyboard.upArrowKey.isPressed) direction += Vector3.forward;
            if (keyboard.sKey.isPressed || keyboard.downArrowKey.isPressed) direction += Vector3.back;
            if (keyboard.aKey.isPressed || keyboard.leftArrowKey.isPressed) direction += Vector3.left;
            if (keyboard.dKey.isPressed || keyboard.rightArrowKey.isPressed) direction += Vector3.right;
            if (keyboard.eKey.isPressed) lift += 1f;
            if (keyboard.qKey.isPressed) lift -= 1f;
            var fast = keyboard.leftShiftKey.isPressed;
#else
            direction = new Vector3(Input.GetAxisRaw("Horizontal"), 0f, Input.GetAxisRaw("Vertical"));
            var fast = Input.GetKey(KeyCode.LeftShift);
#endif

            var speed = moveSpeed * (fast ? 3f : 1f) * Time.deltaTime;
            transform.position += transform.TransformDirection(direction.normalized) * speed
                                  + Vector3.up * (lift * speed);
        }

        /// <summary>A click on a point opens its panel; a click into the void closes it again.</summary>
        private void Select()
        {
#if ENABLE_INPUT_SYSTEM
            var mouse = Mouse.current;
            if (mouse == null || !mouse.leftButton.wasReleasedThisFrame)
            {
                return;
            }
            var screenPosition = mouse.position.ReadValue();
#else
            if (!Input.GetMouseButtonUp(0))
            {
                return;
            }
            var screenPosition = (Vector2)Input.mousePosition;
#endif
            var ray = GetComponent<Camera>().ScreenPointToRay(screenPosition);
            if (Physics.Raycast(ray, out var hit)
                && hit.collider.TryGetComponent<PoiMarker>(out var marker))
            {
                if (infoPanel != null)
                {
                    infoPanel.Show(marker.Describe());
                }
                else
                {
                    Debug.Log(marker.Describe());
                }
                return;
            }
            infoPanel?.Hide();
        }
    }
}
