using System.Collections.Generic;
using System.Linq;
using System.Text;
using UnityEngine;

namespace Campus
{
    /// <summary>
    /// Finds the buildings of the hosting scene by the code the administration knows them under.
    /// </summary>
    /// <remarks>
    /// This is the seam between the two projects. The FEC campus already contains every building as a
    /// finished model, named after its official TU code with the separator dropped —
    /// <c>S103</c>, <c>S202</c>, <c>S120</c>. The administration writes the same code as <c>S1|03</c>.
    /// Both sides are reduced to letters and digits, and then they match.
    /// <para>
    /// That is why nothing is spawned for a building that is already there: a model placed by hand in
    /// the real scene beats a box placed by a coordinate from the database, and the database coordinate
    /// would only ever be an approximation of where the model already stands.
    /// </para>
    /// <para>
    /// Only the codes actually asked for are indexed. A full dictionary of the FEC scene would be
    /// thousands of transforms of which five are interesting.
    /// </para>
    /// </remarks>
    public class CampusBuildingRegistry : MonoBehaviour
    {
        [Header("Suche")]
        [Tooltip("Tags, unter denen Gebäude in der Szene stehen. Der erste Treffer gewinnt; ohne "
                 + "Treffer wird die ganze Szene einmal durchsucht.")]
        [SerializeField] private string[] buildingTags = { "EnergyGameBuilding", "Building" };

        [Header("Ankerpunkt")]
        [Tooltip("Abstand über der Dachkante, in dem Marker gesetzt werden.")]
        [SerializeField] private float markerHeight = 8f;

        private readonly Dictionary<string, Transform> byCode = new();
        private readonly List<string> missing = new();

        /// <summary>
        /// Reduces a building identifier to what both sides agree on: letters and digits, upper case.
        /// <c>S1|03</c>, <c>s1-03</c> and <c>S103</c> all become <c>S103</c>.
        /// </summary>
        public static string Normalise(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return "";
            }

            var result = new StringBuilder(value.Length);
            foreach (var character in value)
            {
                if (char.IsLetterOrDigit(character))
                {
                    result.Append(char.ToUpperInvariant(character));
                }
            }
            return result.ToString();
        }

        /// <summary>Looks up the given codes in the scene. Safe to call again after a reload.</summary>
        public void Bind(IEnumerable<string> codes)
        {
            byCode.Clear();
            missing.Clear();

            var wanted = new HashSet<string>(codes.Select(Normalise).Where(code => code.Length > 0));
            if (wanted.Count == 0)
            {
                return;
            }

            foreach (var candidate in Candidates())
            {
                var code = Normalise(candidate.name);
                if (wanted.Contains(code) && !byCode.ContainsKey(code))
                {
                    byCode[code] = candidate;
                }
            }

            missing.AddRange(wanted.Where(code => !byCode.ContainsKey(code)).OrderBy(code => code));
        }

        /// <summary>
        /// The objects worth looking at: everything under one of the configured tags, and if that yields
        /// nothing, every transform in the scene.
        /// </summary>
        private IEnumerable<Transform> Candidates()
        {
            foreach (var tag in buildingTags)
            {
                GameObject[] tagged;
                try
                {
                    tagged = GameObject.FindGameObjectsWithTag(tag);
                }
                catch (UnityException)
                {
                    // A tag the hosting project does not define. Not worth a warning — the tag list is a
                    // guess about a foreign scene, and the scan below covers the case.
                    continue;
                }

                if (tagged.Length > 0)
                {
                    foreach (var candidate in tagged)
                    {
                        yield return candidate.transform;
                    }
                    yield break;
                }
            }

            foreach (var root in gameObject.scene.GetRootGameObjects())
            {
                foreach (var candidate in root.GetComponentsInChildren<Transform>(true))
                {
                    yield return candidate;
                }
            }
        }

        public bool TryGet(string code, out Transform building) =>
            byCode.TryGetValue(Normalise(code), out building);

        /// <summary>
        /// Where a marker for this building belongs: over the middle of its footprint, just above the
        /// roof. Taken from the renderers rather than the transform, because the origin of an imported
        /// model sits wherever the modeller left it.
        /// </summary>
        public bool TryAnchor(string code, out Vector3 anchor)
        {
            anchor = Vector3.zero;
            if (!TryGet(code, out var building))
            {
                return false;
            }

            var renderers = building.GetComponentsInChildren<Renderer>();
            if (renderers.Length == 0)
            {
                anchor = building.position + Vector3.up * markerHeight;
                return true;
            }

            var bounds = renderers[0].bounds;
            for (var index = 1; index < renderers.Length; index++)
            {
                bounds.Encapsulate(renderers[index].bounds);
            }

            anchor = new Vector3(bounds.center.x, bounds.max.y + markerHeight, bounds.center.z);
            return true;
        }

        /// <summary>
        /// One line for the console saying which codes found their model and which did not. During the
        /// demonstration this is the proof that administration and scene meet — and when a code is
        /// mistyped it is the only place that says so.
        /// </summary>
        public string Report()
        {
            var bound = byCode.Count == 0 ? "—" : string.Join(", ", byCode.Keys.OrderBy(code => code));
            var absent = missing.Count == 0 ? "—" : string.Join(", ", missing);
            return $"Gebäude gebunden: {bound} · nicht in der Szene gefunden: {absent}";
        }
    }
}
