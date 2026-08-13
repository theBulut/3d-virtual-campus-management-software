import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { tokenStore } from '../../api/client';

/**
 * Written by the Unity build script. Unity derives the file names from the output folder and has changed
 * that rule between versions, so the names are read rather than guessed.
 */
const MANIFEST_URL = '/game/build-info.json';

/**
 * Loads the Unity WebGL build and keeps the bridge to it.
 * <p>
 * Two directions of traffic. Into Unity through {@code SendMessage}, which calls a method on a named
 * GameObject — that is how the session reaches the game. Out of Unity through {@code window.campusBridge},
 * which the {@code .jslib} plugin calls; the game uses it to ask for a token rather than storing one.
 * <p>
 * If no build is deployed the component renders its children instead. That is the normal state until
 * somebody has run Unity, and an empty page would look like a defect.
 */
const UnityCanvas = forwardRef(function UnityCanvas({ provideToken, children }, ref) {
  const canvasRef = useRef(null);
  const instanceRef = useRef(null);
  const [status, setStatus] = useState('loading');
  const [progress, setProgress] = useState(0);

  useImperativeHandle(ref, () => ({
    /** Sends a message into the running game; no-op while no build is loaded. */
    send(gameObject, method, payload) {
      instanceRef.current?.SendMessage(gameObject, method, payload ?? '');
    },
  }));

  useEffect(() => {
    let cancelled = false;
    let script = null;

    // Unity calls this from its plugin; it is the only global the page publishes.
    window.campusBridge = {
      requestToken: () => provideToken?.() ?? tokenStore.access() ?? '',
      onSceneReady: () => setStatus('ready'),
    };

    /**
     * The manifest decides whether a build exists. Checking for it beats requesting the loader
     * directly: a development server answers unknown paths with index.html, so a missing script would
     * arrive as HTML and fail with a syntax error instead of a clean 404.
     */
    async function loadManifest() {
      const response = await fetch(MANIFEST_URL, { cache: 'no-store' });
      if (!response.ok) {
        return null;
      }
      const contentType = response.headers.get('Content-Type') ?? '';
      if (!contentType.includes('application/json')) {
        return null;
      }
      return response.json();
    }

    loadManifest()
      .then((manifest) => {
        if (cancelled || !manifest?.loaderUrl) {
          setStatus('unavailable');
          return;
        }

        script = document.createElement('script');
        script.src = manifest.loaderUrl;
        script.async = true;
        script.onerror = () => setStatus('unavailable');
        script.onload = () => {
          if (cancelled || typeof window.createUnityInstance !== 'function') {
            setStatus('unavailable');
            return;
          }
          window
            .createUnityInstance(
              canvasRef.current,
              {
                dataUrl: manifest.dataUrl,
                frameworkUrl: manifest.frameworkUrl,
                codeUrl: manifest.codeUrl,
                streamingAssetsUrl: manifest.streamingAssetsUrl ?? '/game/StreamingAssets',
                companyName: 'TU Darmstadt',
                productName: '3D Campus',
                productVersion: '0.1',
              },
              (value) => setProgress(Math.round(value * 100)),
            )
            .then((instance) => {
              if (cancelled) {
                return;
              }
              instanceRef.current = instance;
              setStatus('ready');
              // The game starts without a session and receives it here, once.
              instance.SendMessage('WebBridge', 'SetToken', tokenStore.access() ?? '');
            })
            .catch(() => setStatus('unavailable'));
        };
        document.body.appendChild(script);
      })
      .catch(() => setStatus('unavailable'));

    return () => {
      cancelled = true;
      script?.remove();
      delete window.campusBridge;
      instanceRef.current?.Quit?.();
      instanceRef.current = null;
    };
  }, [provideToken]);

  if (status === 'unavailable') {
    return (
      <div className="play__fallback">
        <div className="banner banner--warning">
          <strong>Kein Unity-Build vorhanden.</strong> Unter <code>/game</code> liegt noch kein
          WebGL-Build. Angezeigt werden stattdessen die Daten, die das Spiel laden würde — dieselbe
          Antwort von <code>GET /api/game/scene</code>.
        </div>
        {children}
      </div>
    );
  }

  return (
    <div className="play__stage">
      <canvas ref={canvasRef} className="play__canvas" id="unity-canvas" />
      {status === 'loading' && <p className="play__progress">Campus wird geladen … {progress}%</p>}
    </div>
  );
});

export default UnityCanvas;
