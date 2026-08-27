using System;
using UnityEngine;

namespace Campus
{
    /// <summary>
    /// The payload of <c>GET /api/game/scene</c>, shaped for <see cref="JsonUtility"/>.
    /// </summary>
    /// <remarks>
    /// Plain fields and arrays, no properties and no dictionaries — that is all JsonUtility understands,
    /// and it saves pulling a JSON library into the WebGL build. The names match the API exactly.
    /// <para>
    /// <c>status</c> and <c>published</c> are only filled for accounts that may see unpublished content.
    /// An empty status therefore means "published", which is why <see cref="ScenePoi.IsDraft"/> asks the
    /// question rather than comparing strings all over the place.
    /// </para>
    /// </remarks>
    [Serializable]
    public class ScenePayload
    {
        public ScenePoi[] pois = Array.Empty<ScenePoi>();
        public SceneBuilding[] buildings = Array.Empty<SceneBuilding>();
        public SceneConsultation[] consultations = Array.Empty<SceneConsultation>();
    }

    [Serializable]
    public class ScenePoi
    {
        public long id;
        public string nameDe;
        public string nameEn;
        public string descriptionDe;
        public string category;
        public string buildingCode;
        public Vector3Data position;
        public string status;

        /// <summary>True while the point has not been released yet.</summary>
        public bool IsUnpublished => !string.IsNullOrEmpty(status) && status != "PUBLISHED";
    }

    [Serializable]
    public class SceneBuilding
    {
        public long id;
        public string code;
        public string nameDe;
        public string modelRef;
        public Vector3Data position;
        public float rotationY;
        public string published;

        /// <summary>
        /// A string rather than a bool: JsonUtility maps a missing field to false, which would make every
        /// building of a player look unpublished. The API omits the field entirely in that case.
        /// </summary>
        public bool IsUnpublished => published == "false" || published == "False";
    }

    [Serializable]
    public class SceneConsultation
    {
        public long id;
        public string titleDe;
        public string descriptionDe;
        public string organisation;
        public string buildingCode;
        public string room;
        public string contactEmail;
        public SceneSlot[] slots = Array.Empty<SceneSlot>();
    }

    [Serializable]
    public class SceneSlot
    {
        public int dayOfWeek;
        public string startTime;
        public string endTime;
        public string room;

        private static readonly string[] Weekdays =
            { "?", "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So" };

        public override string ToString()
        {
            var day = dayOfWeek >= 1 && dayOfWeek <= 7 ? Weekdays[dayOfWeek] : "?";
            return $"{day} {Short(startTime)}–{Short(endTime)}";
        }

        private static string Short(string time) =>
            string.IsNullOrEmpty(time) || time.Length < 5 ? time : time.Substring(0, 5);
    }

    /// <summary>The API sends x, y and z; Unity's own Vector3 does not deserialise from that directly.</summary>
    [Serializable]
    public class Vector3Data
    {
        public float x;
        public float y;
        public float z;

        public Vector3 ToVector3() => new Vector3(x, y, z);
    }
}
