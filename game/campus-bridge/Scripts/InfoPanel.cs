using UnityEngine;
using UnityEngine.UI;

namespace Campus
{
    /// <summary>The text panel that appears when a point is clicked.</summary>
    public class InfoPanel : MonoBehaviour
    {
        [SerializeField] private GameObject panel;
        [SerializeField] private Text label;

        private void Start() => Hide();

        public void Show(string text)
        {
            if (label != null)
            {
                label.text = text;
            }
            panel?.SetActive(true);
        }

        public void Hide() => panel?.SetActive(false);
    }
}
