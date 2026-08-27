using System.Collections;
using System.Runtime.InteropServices;
using UnityEngine;
using UnityEngine.Networking;

namespace Campus
{
    /// <summary>
    /// The Unity side of the bridge to the hosting page.
    /// </summary>
    /// <remarks>
    /// Belongs on a GameObject named exactly <c>WebBridge</c>: the page addresses it by that name in
    /// <c>SendMessage("WebBridge", "SetToken", …)</c>.
    /// <para>
    /// In the browser the page owns the session and this class only asks for the current token. In the
    /// editor there is no page, so it signs in by itself with the demo credentials below — otherwise
    /// every test run would begin by pasting a token that expires after fifteen minutes.
    /// </para>
    /// The credentials never reach a build: everything editor related sits behind
    /// <c>#if UNITY_EDITOR</c> and is compiled out for WebGL.
    /// </remarks>
    public class WebBridge : MonoBehaviour
    {
        public static WebBridge Instance { get; private set; }

        [Header("Nur im Editor benutzt")]
        [Tooltip("API-Adresse für Testläufe außerhalb des Browsers")]
        [SerializeField] private string editorApiBase = "http://localhost:8080/api";

        [Tooltip("Konto, mit dem sich der Editor anmeldet. demo_leitung sieht auch Entwürfe, "
                 + "demo_studi nur Freigegebenes.")]
        [SerializeField] private string editorUsername = "demo_leitung";

        [SerializeField] private string editorPassword = "demo-passwort";

        [Tooltip("Optional: festes Token statt Anmeldung. Leer lassen für die automatische Anmeldung.")]
        [SerializeField] private string editorToken = "";

        private string token = "";
        private bool signingIn;

#if UNITY_WEBGL && !UNITY_EDITOR
        [DllImport("__Internal")] private static extern string CampusRequestToken();
        [DllImport("__Internal")] private static extern void CampusSceneReady();
        [DllImport("__Internal")] private static extern string CampusApiBase();
#endif

        private void Awake()
        {
            // A second bridge would decide at random which settings apply — the last Awake wins. Better
            // to say so and remove it than to debug a scene that answers with the wrong account.
            if (Instance != null && Instance != this)
            {
                Debug.LogWarning($"Zweite WebBridge auf '{name}' gefunden und entfernt. "
                                 + $"Aktiv bleibt '{Instance.name}'.");
                Destroy(gameObject);
                return;
            }

            Instance = this;
            DontDestroyOnLoad(gameObject);
#if UNITY_EDITOR
            // What this object really carries — not what an Inspector somewhere else shows.
            Debug.Log($"WebBridge '{name}' bereit · API {editorApiBase} · Konto '{editorUsername}'"
                      + (string.IsNullOrWhiteSpace(editorToken) ? "" : " · festes Token gesetzt"));
#endif
        }

        /// <summary>Called by the page right after the Unity instance is created.</summary>
        public void SetToken(string value)
        {
            token = (value ?? "").Trim();
            Debug.Log(string.IsNullOrEmpty(token)
                ? "Sitzung ohne Token erhalten"
                : "Sitzung erhalten");
        }

        /// <summary>
        /// Makes sure a token is available before the first API call. In the browser this returns
        /// immediately — the page has already delivered one. In the editor it signs in.
        /// </summary>
        public IEnumerator EnsureToken()
        {
#if UNITY_EDITOR
            if (!string.IsNullOrWhiteSpace(editorToken))
            {
                token = editorToken.Trim();
                yield break;
            }
            if (!string.IsNullOrEmpty(token))
            {
                yield break;
            }
            // SceneLoader and GameStateClient both ask on Start. Without this guard both would sign in,
            // which is one pointless request and, with the rate limit in mind, one too many.
            if (signingIn)
            {
                while (signingIn)
                {
                    yield return null;
                }
                yield break;
            }
            signingIn = true;

            var body = $"{{\"username\":\"{editorUsername}\",\"password\":\"{editorPassword}\"}}";
            using var request = new UnityWebRequest($"{editorApiBase}/auth/login", "POST")
            {
                uploadHandler = new UploadHandlerRaw(System.Text.Encoding.UTF8.GetBytes(body)),
                downloadHandler = new DownloadHandlerBuffer(),
            };
            request.SetRequestHeader("Content-Type", "application/json");
            yield return request.SendWebRequest();
            signingIn = false;

            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogError($"Anmeldung im Editor fehlgeschlagen ({request.responseCode}). "
                               + $"Läuft das Backend unter {editorApiBase}? "
                               + $"Antwort: {request.downloadHandler.text}");
                yield break;
            }

            var response = JsonUtility.FromJson<LoginResponse>(request.downloadHandler.text);
            token = response.accessToken;
            // The name comes out of the answer, not out of the input field: it proves which account the
            // token actually belongs to.
            Debug.Log($"Im Editor angemeldet — Server bestätigt Konto '{response.user.username}' "
                      + $"mit {response.user.permissions.Length} Berechtigungen");
#else
            yield break;
#endif
        }

        /// <summary>
        /// The token for the next request. In the browser the page is asked every time rather than
        /// trusting an older copy: after a rotation it holds a newer one.
        /// </summary>
        public string Token()
        {
#if UNITY_WEBGL && !UNITY_EDITOR
            var fresh = CampusRequestToken();
            if (!string.IsNullOrEmpty(fresh))
            {
                token = fresh.Trim();
            }
#endif
            return token;
        }

        public string ApiBase()
        {
#if UNITY_WEBGL && !UNITY_EDITOR
            return CampusApiBase();
#else
            return editorApiBase;
#endif
        }

        public void NotifySceneReady()
        {
#if UNITY_WEBGL && !UNITY_EDITOR
            CampusSceneReady();
#else
            Debug.Log("Szene aufgebaut");
#endif
        }

        /// <summary>Only the parts of the login response that are needed here.</summary>
        [System.Serializable]
        private class LoginResponse
        {
            public string accessToken;
            public UserInfo user;
        }

        [System.Serializable]
        private class UserInfo
        {
            public string username;
            public string[] permissions = System.Array.Empty<string>();
        }
    }
}
