import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { tokenStore } from '../api/client';
import { fetchScene } from '../api/game';
import { useAuth } from '../auth/AuthContext';
import ScenePreview from '../components/game/ScenePreview';
import UnityCanvas from '../components/game/UnityCanvas';

/**
 * The page that hosts the campus.
 * <p>
 * The web app keeps the token: it already knows how to refresh and rotate one, and duplicating that in
 * C# would mean two implementations of the same rule (docs/DECISIONS.md D-44). Unity receives the
 * current token after loading and asks for a fresh one through the bridge whenever the API answers 401.
 * <p>
 * Until a WebGL build exists under {@code /game}, the page falls back to a readable listing of the same
 * payload — the data path can then be checked end to end without Unity being installed.
 */
export default function PlayPage() {
  const { user, canAdminister, permissions } = useAuth();
  const [scene, setScene] = useState(null);
  const [error, setError] = useState(null);
  const unityRef = useRef(null);

  useEffect(() => {
    fetchScene().then(setScene).catch(setError);
  }, []);

  /** Handed to Unity so it can ask for the current token instead of keeping its own copy. */
  const provideToken = useCallback(() => tokenStore.access(), []);

  const seesUnpublished = permissions.includes('POI_READ_ALL');

  return (
    <div className="play">
      <header className="play__bar">
        <span className="play__title">3D Campus</span>
        <span className="play__player">
          {user.firstName} {user.lastName}
        </span>
        {seesUnpublished && (
          <span className="badge badge--in-review" title="Diese Ansicht zeigt auch Entwürfe">
            Redaktionsansicht
          </span>
        )}
        <span className="play__spacer" />
        {canAdminister && (
          <Link className="button button--ghost" to="/admin">
            Zum Verwaltungstool
          </Link>
        )}
        <Link className="button button--ghost" to="/profile">
          Profil
        </Link>
      </header>

      {error && (
        <p className="page__error">
          Die Szene konnte nicht geladen werden: {error.message}
        </p>
      )}

      <UnityCanvas ref={unityRef} provideToken={provideToken}>
        {/* Shown while no build is present. Same data, without the 3D. */}
        {scene ? <ScenePreview scene={scene} /> : <p className="page__loading">Szene wird geladen …</p>}
      </UnityCanvas>
    </div>
  );
}
