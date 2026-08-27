using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Campus.Editor
{
    /// <summary>
    /// Switches the account the editor signs in with, from the menu instead of a text field.
    /// </summary>
    /// <remarks>
    /// Typing into the Inspector has three ways to go wrong that all look identical afterwards: the edit
    /// happens during Play Mode and is discarded on stop, the field keeps focus so the value is never
    /// committed, or a second <c>WebBridge</c> in the scene is the one that actually runs. This writes
    /// the value through the serialization API, saves the scene and reports what it did — so the state
    /// on disk and the state in the Inspector cannot disagree.
    /// </remarks>
    public static class CampusAccountMenu
    {
        [MenuItem("Campus/Editor-Konto/demo_studi (nur Freigegebenes)", false, 40)]
        public static void UseStudent() => Apply("demo_studi");

        [MenuItem("Campus/Editor-Konto/demo_leitung (auch Entwürfe)", false, 41)]
        public static void UseLead() => Apply("demo_leitung");

        [MenuItem("Campus/Editor-Konto/demo_admin", false, 42)]
        public static void UseAdmin() => Apply("demo_admin");

        [MenuItem("Campus/Editor-Konto/Aktuelles Konto anzeigen", false, 60)]
        public static void ShowCurrent()
        {
            var bridges = Object.FindObjectsByType<WebBridge>(FindObjectsInactive.Include,
                FindObjectsSortMode.None);
            if (bridges.Length == 0)
            {
                EditorUtility.DisplayDialog("Keine WebBridge",
                    "In der offenen Szene gibt es kein Objekt mit der Komponente WebBridge. "
                    + "Erst 'Campus → Szene erzeugen' ausführen.", "Verstanden");
                return;
            }

            var report = "";
            foreach (var bridge in bridges)
            {
                var serialized = new SerializedObject(bridge);
                report += $"• {bridge.gameObject.name}: "
                          + $"{serialized.FindProperty("editorUsername").stringValue}"
                          + $" @ {serialized.FindProperty("editorApiBase").stringValue}";
                var token = serialized.FindProperty("editorToken").stringValue;
                if (!string.IsNullOrWhiteSpace(token))
                {
                    report += "  ⚠ festes Token gesetzt — es gewinnt über die Anmeldung";
                }
                report += "\n";
            }

            if (bridges.Length > 1)
            {
                report += "\n⚠ Mehr als eine WebBridge in der Szene. Zur Laufzeit gewinnt eine davon, "
                          + "welche ist nicht vorhersagbar.";
            }

            EditorUtility.DisplayDialog("Editor-Konto", report, "Alles klar");
        }

        private static void Apply(string username)
        {
            var bridges = Object.FindObjectsByType<WebBridge>(FindObjectsInactive.Include,
                FindObjectsSortMode.None);
            if (bridges.Length == 0)
            {
                EditorUtility.DisplayDialog("Keine WebBridge",
                    "In der offenen Szene gibt es kein Objekt mit der Komponente WebBridge.",
                    "Verstanden");
                return;
            }

            foreach (var bridge in bridges)
            {
                var serialized = new SerializedObject(bridge);
                serialized.FindProperty("editorUsername").stringValue = username;
                serialized.FindProperty("editorPassword").stringValue = "demo-passwort";
                // A leftover fixed token would win over the login and quietly keep the old account.
                serialized.FindProperty("editorToken").stringValue = "";
                serialized.ApplyModifiedProperties();
                EditorUtility.SetDirty(bridge);
            }

            EditorSceneManager.MarkSceneDirty(EditorSceneManager.GetActiveScene());
            EditorSceneManager.SaveOpenScenes();

            Debug.Log($"Editor-Konto auf '{username}' gesetzt und Szene gespeichert "
                      + $"({bridges.Length} WebBridge). Jetzt Play drücken.");
        }
    }
}
