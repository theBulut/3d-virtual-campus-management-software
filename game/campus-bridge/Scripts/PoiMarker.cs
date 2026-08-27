using System.Linq;
using System.Text;
using UnityEngine;

namespace Campus
{
    /// <summary>
    /// Sits on every spawned point and knows what the administration says about it — including the
    /// building it belongs to and the consultation hours held there.
    /// </summary>
    /// <remarks>
    /// Deliberately without input handling. Clicks are raycast centrally in
    /// <see cref="CampusInteraction"/>, which keeps the input path in one place — the hosting project
    /// brings its own player controller, and two components reading the mouse would fight over it.
    /// <para>
    /// The link to a consultation offer runs over the building code — the same identifier an editor
    /// picks in the web interface. That is what makes the milestone visible: a point, a building and an
    /// offer, entered separately, meet again in the game.
    /// </para>
    /// </remarks>
    public class PoiMarker : MonoBehaviour
    {
        public ScenePoi Poi { get; set; }

        /// <summary>The building this point belongs to, if the payload contained one.</summary>
        public SceneBuilding Building { get; set; }

        /// <summary>The text shown when this point is clicked.</summary>
        public string Describe()
        {
            var text = new StringBuilder();
            text.AppendLine($"<b>{Poi.nameDe}</b>");
            if (Building != null)
            {
                text.AppendLine($"{Building.nameDe} ({Building.code})");
            }
            if (!string.IsNullOrEmpty(Poi.descriptionDe))
            {
                text.AppendLine(Poi.descriptionDe);
            }
            if (Poi.IsUnpublished)
            {
                text.AppendLine($"[{Poi.status} — für Besucher nicht sichtbar]");
            }

            var loader = FindAnyObjectByType<SceneLoader>();
            var consultations = loader?.Scene?.consultations
                .Where(entry => !string.IsNullOrEmpty(Poi.buildingCode)
                                && entry.buildingCode == Poi.buildingCode)
                .ToArray();

            if (consultations is { Length: > 0 })
            {
                text.AppendLine();
                text.AppendLine("Beratung in diesem Gebäude:");
                foreach (var consultation in consultations)
                {
                    var slots = string.Join(", ", consultation.slots.Select(slot => slot.ToString()));
                    text.AppendLine($"· {consultation.titleDe} ({consultation.room}) {slots}");
                }
            }

            return text.ToString();
        }
    }
}
