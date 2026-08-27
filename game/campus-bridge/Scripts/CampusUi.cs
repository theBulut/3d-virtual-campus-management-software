using UnityEngine;
using UnityEngine.UI;

namespace Campus
{
    /// <summary>
    /// The overlay the bridge draws for itself: the info panel of a point and the editorial badge.
    /// </summary>
    /// <remarks>
    /// Built at runtime rather than assembled in the scene, and deliberately not hooked into the UI of
    /// the hosting project. The FEC campus brings its own canvases for dialogue, inventory and the energy
    /// game; borrowing one of them would tie this bridge to their layout and break it the next time
    /// somebody there rearranges a panel. A canvas of its own costs a few lines and survives that.
    /// <para>
    /// <b>No button and no EventSystem.</b> The panel closes on the next click into the world or on
    /// Escape, handled in <see cref="CampusInteraction"/>. A close button would need an EventSystem with
    /// an input module, and which module is correct depends on whether the hosting project uses the old
    /// input handling, the new one, or both — three cases to get right for one button. Every graphic is
    /// therefore <c>raycastTarget = false</c>, so the overlay never swallows a click meant for the game.
    /// </para>
    /// </remarks>
    public class CampusUi : MonoBehaviour
    {
        public static CampusUi Instance { get; private set; }

        [SerializeField] private int sortingOrder = 5000;

        private GameObject panel;
        private Text label;
        private Text badge;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            BuildCanvas();
            Hide();
        }

        private void OnDestroy()
        {
            if (Instance == this)
            {
                Instance = null;
            }
        }

        public void Show(string text)
        {
            if (label != null)
            {
                label.text = text;
            }
            if (panel != null)
            {
                panel.SetActive(true);
            }
        }

        public void Hide()
        {
            if (panel != null)
            {
                panel.SetActive(false);
            }
        }

        public bool IsVisible => panel != null && panel.activeSelf;

        /// <summary>
        /// Shows or hides the note that this account sees more than a visitor does. Whether that is the
        /// case is not asked anywhere — it follows from the payload, because the API only fills
        /// <c>status</c> for accounts that may see unpublished content (docs/DECISIONS.md D-42).
        /// </summary>
        public void SetEditorialView(bool editorial)
        {
            if (badge != null)
            {
                badge.gameObject.SetActive(editorial);
            }
        }

        private void BuildCanvas()
        {
            var canvasObject = new GameObject("CampusOverlay",
                typeof(Canvas), typeof(CanvasScaler));
            canvasObject.transform.SetParent(transform, false);

            var canvas = canvasObject.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            // Above whatever the hosting project draws; its canvases sit at the default order 0.
            canvas.sortingOrder = sortingOrder;

            var scaler = canvasObject.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920f, 1080f);

            panel = NewPanel(canvasObject.transform);
            label = NewLabel(panel.transform);
            badge = NewBadge(canvasObject.transform);
        }

        private static GameObject NewPanel(Transform parent)
        {
            var panelObject = new GameObject("Infotafel", typeof(Image));
            panelObject.transform.SetParent(parent, false);

            var image = panelObject.GetComponent<Image>();
            image.color = new Color(0f, 0f, 0f, 0.78f);
            image.raycastTarget = false;

            var rect = panelObject.GetComponent<RectTransform>();
            rect.anchorMin = new Vector2(0f, 0f);
            rect.anchorMax = new Vector2(0f, 0f);
            rect.pivot = new Vector2(0f, 0f);
            rect.anchoredPosition = new Vector2(32f, 32f);
            rect.sizeDelta = new Vector2(640f, 300f);

            return panelObject;
        }

        private static Text NewLabel(Transform parent)
        {
            var labelObject = new GameObject("Text", typeof(Text));
            labelObject.transform.SetParent(parent, false);

            var text = labelObject.GetComponent<Text>();
            text.font = LegacyFont();
            text.fontSize = 22;
            text.color = Color.white;
            text.supportRichText = true;
            text.alignment = TextAnchor.UpperLeft;
            text.horizontalOverflow = HorizontalWrapMode.Wrap;
            text.verticalOverflow = VerticalWrapMode.Overflow;
            text.raycastTarget = false;

            var rect = labelObject.GetComponent<RectTransform>();
            rect.anchorMin = Vector2.zero;
            rect.anchorMax = Vector2.one;
            rect.offsetMin = new Vector2(20f, 20f);
            rect.offsetMax = new Vector2(-20f, -20f);

            return text;
        }

        private static Text NewBadge(Transform parent)
        {
            var badgeObject = new GameObject("Redaktionsansicht", typeof(Text));
            badgeObject.transform.SetParent(parent, false);

            var text = badgeObject.GetComponent<Text>();
            text.font = LegacyFont();
            text.text = "Redaktionsansicht — Entwürfe sichtbar";
            text.fontSize = 20;
            text.color = new Color(1f, 0.72f, 0.2f);
            text.alignment = TextAnchor.UpperRight;
            text.horizontalOverflow = HorizontalWrapMode.Overflow;
            text.raycastTarget = false;

            var rect = badgeObject.GetComponent<RectTransform>();
            rect.anchorMin = new Vector2(1f, 1f);
            rect.anchorMax = new Vector2(1f, 1f);
            rect.pivot = new Vector2(1f, 1f);
            rect.anchoredPosition = new Vector2(-32f, -32f);
            rect.sizeDelta = new Vector2(560f, 32f);

            badgeObject.SetActive(false);
            return text;
        }

        /// <summary>
        /// Unity's built-in font. Called <c>Arial.ttf</c> until 2022 and <c>LegacyRuntime.ttf</c> since;
        /// both names are tried so the bridge also compiles into an older hosting project.
        /// </summary>
        private static Font LegacyFont() =>
            Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf")
            ?? Resources.GetBuiltinResource<Font>("Arial.ttf");
    }
}
