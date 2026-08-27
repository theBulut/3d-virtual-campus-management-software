using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace Campus.Editor
{
    /// <summary>
    /// Builds the WebGL player straight into the folder the web application serves.
    /// </summary>
    /// <remarks>
    /// Two settings decide whether the result works at all, and both are easy to forget in the dialog:
    /// the compression format has to be <c>Disabled</c> (nginx compresses on the fly instead — otherwise
    /// it would hand out <c>.br</c> files without a matching <c>Content-Encoding</c> and nothing loads),
    /// and the scene has to be in the build list. Doing it here means it is decided once instead of
    /// every time.
    /// <para>
    /// The output folder is read from <c>CampusBuild.json</c> next to <c>Assets/</c>, written by
    /// <c>game/install-bridge.sh</c>. It cannot be a relative path any more: the bridge now lives inside
    /// a checkout of the FEC project, which may sit anywhere on disk, and "two folders up" only ever
    /// happened to be right for the sandbox project. The file sits outside <c>Assets/</c> so Unity does
    /// not import it as a text asset.
    /// </para>
    /// <para>
    /// Afterwards a small <c>build-info.json</c> is written next to the build. Unity derives the file
    /// names from the output folder and has changed that rule between versions, so the page reads the
    /// actual names from the manifest rather than guessing them.
    /// </para>
    /// </remarks>
    public static class CampusBuild
    {
        private const string ConfigFileName = "CampusBuild.json";

        [MenuItem("Campus/WebGL-Build erzeugen", false, 20)]
        public static void BuildWebGl()
        {
            var output = ConfiguredOutput() ?? AskForOutput();
            if (string.IsNullOrEmpty(output))
            {
                return;
            }

            var scenes = EnabledScenes();
            if (scenes.Length == 0)
            {
                EditorUtility.DisplayDialog("Keine Szene",
                    "In den Build Settings ist keine Szene aktiviert. Im FEC-Projekt ist das "
                    + "Assets/Scenes/Web.unity; in der Sandkasten-Szene erst "
                    + "'Campus → Szene erzeugen' ausführen.", "Verstanden");
                return;
            }

            if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.WebGL)
            {
                if (!EditorUtility.DisplayDialog("Plattform wechseln",
                        "Das Projekt steht auf " + EditorUserBuildSettings.activeBuildTarget
                        + ". Auf WebGL wechseln? Das kann einige Minuten dauern.", "Wechseln", "Abbrechen"))
                {
                    return;
                }
                // Explicitly, not as a side effect of BuildPlayer: the switch reimports every asset and
                // that is what the dialog just warned about — it should happen where it was agreed to.
                EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.WebGL, BuildTarget.WebGL);
            }

            var report = Run(scenes, output);
            if (report.summary.result != BuildResult.Succeeded)
            {
                EditorUtility.DisplayDialog("Build fehlgeschlagen",
                    "Details stehen in der Konsole.", "Verstanden");
                return;
            }

            var manifest = WriteBuildInfo(output);
            EditorUtility.DisplayDialog("Build fertig",
                $"Der Build liegt unter\n{output}\n\n{manifest}\n\n"
                + "Der Ordner ist in docker-compose.yml eingehängt: "
                + "'docker compose restart frontend' genügt, ein Neubau des Images ist nicht nötig.",
                "Alles klar");
        }

        /// <summary>
        /// The same build without a single dialog, for
        /// <c>-batchmode -executeMethod Campus.Editor.CampusBuild.BuildFromCommandLine</c>. The output
        /// folder may be given as <c>-campusOutput &lt;pfad&gt;</c>; otherwise the configured one is used.
        /// </summary>
        public static void BuildFromCommandLine()
        {
            var output = Argument("-campusOutput") ?? ConfiguredOutput();
            if (string.IsNullOrEmpty(output))
            {
                Debug.LogError($"Kein Ausgabeordner. Entweder -campusOutput <pfad> übergeben oder "
                               + $"{ConfigFileName} neben Assets/ ablegen (game/install-bridge.sh).");
                EditorApplication.Exit(2);
                return;
            }

            var scenes = EnabledScenes();
            if (scenes.Length == 0)
            {
                Debug.LogError("In den Build Settings ist keine Szene aktiviert.");
                EditorApplication.Exit(2);
                return;
            }

            var report = Run(scenes, output);
            if (report.summary.result != BuildResult.Succeeded)
            {
                Debug.LogError($"Build fehlgeschlagen: {report.summary.result}");
                EditorApplication.Exit(1);
                return;
            }

            Debug.Log(WriteBuildInfo(output));
            EditorApplication.Exit(0);
        }

        private static BuildReport Run(string[] scenes, string output)
        {
            // Without this the server would have to send Content-Encoding headers for every asset.
            PlayerSettings.WebGL.compressionFormat = WebGLCompressionFormat.Disabled;
            // The campus build is large enough that a second start without the cache is as slow as the
            // first — which during a demonstration is the difference between a pause and a wait.
            PlayerSettings.WebGL.dataCaching = true;
            PlayerSettings.runInBackground = true;

            Directory.CreateDirectory(output);

            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = scenes,
                locationPathName = output,
                target = BuildTarget.WebGL,
                options = BuildOptions.None,
            });

            Debug.Log($"WebGL-Build {report.summary.result}: {output} "
                      + $"({report.summary.totalSize / (1024 * 1024)} MB, "
                      + $"{report.summary.totalTime.TotalMinutes:F1} min)");
            return report;
        }

        private static string[] EnabledScenes() =>
            EditorBuildSettings.scenes.Where(scene => scene.enabled)
                .Select(scene => scene.path)
                .ToArray();

        /// <summary>The output folder from <c>CampusBuild.json</c>, or null if there is none.</summary>
        private static string ConfiguredOutput()
        {
            var path = ConfigPath();
            if (!File.Exists(path))
            {
                return null;
            }

            try
            {
                var config = JsonUtility.FromJson<BuildConfig>(File.ReadAllText(path));
                return string.IsNullOrWhiteSpace(config?.outputPath) ? null : config.outputPath.Trim();
            }
            catch (Exception error)
            {
                Debug.LogWarning($"{ConfigFileName} ist nicht lesbar ({error.Message}). "
                                 + "Der Ordner wird erfragt.");
                return null;
            }
        }

        /// <summary>Asks once and remembers the answer, so the next build runs without a dialog.</summary>
        private static string AskForOutput()
        {
            var chosen = EditorUtility.SaveFolderPanel(
                "Ausgabeordner: frontend/public/game des Verwaltungsprojekts", "", "game");
            if (string.IsNullOrEmpty(chosen))
            {
                return null;
            }

            File.WriteAllText(ConfigPath(),
                JsonUtility.ToJson(new BuildConfig { outputPath = chosen }, true));
            Debug.Log($"Ausgabeordner in {ConfigPath()} gemerkt: {chosen}");
            return chosen;
        }

        private static string ConfigPath() =>
            Path.GetFullPath(Path.Combine(Application.dataPath, "..", ConfigFileName));

        private static string Argument(string name)
        {
            var arguments = Environment.GetCommandLineArgs();
            for (var index = 0; index < arguments.Length - 1; index++)
            {
                if (arguments[index] == name)
                {
                    return arguments[index + 1];
                }
            }
            return null;
        }

        /// <summary>
        /// Writes the actual file names into <c>build-info.json</c>. The page reads them from there, so a
        /// renamed output folder or a different Unity version cannot break the embedding.
        /// </summary>
        private static string WriteBuildInfo(string outputFolder)
        {
            var buildFolder = Path.Combine(outputFolder, "Build");
            if (!Directory.Exists(buildFolder))
            {
                return "Achtung: kein Build-Ordner gefunden.";
            }

            var files = Directory.GetFiles(buildFolder).Select(Path.GetFileName).ToArray();
            string Find(string suffix) => files.FirstOrDefault(name => name.EndsWith(suffix)) ?? "";

            var info = "{\n"
                       + $"  \"loaderUrl\": \"/game/Build/{Find(".loader.js")}\",\n"
                       + $"  \"dataUrl\": \"/game/Build/{Find(".data")}\",\n"
                       + $"  \"frameworkUrl\": \"/game/Build/{Find(".framework.js")}\",\n"
                       + $"  \"codeUrl\": \"/game/Build/{Find(".wasm")}\",\n"
                       + "  \"streamingAssetsUrl\": \"/game/StreamingAssets\"\n"
                       + "}\n";

            File.WriteAllText(Path.Combine(outputFolder, "build-info.json"), info);
            return "Geladen wird: " + Find(".loader.js");
        }

        /// <summary>The shape of <c>CampusBuild.json</c>.</summary>
        [Serializable]
        private class BuildConfig
        {
            public string outputPath;
        }
    }
}
