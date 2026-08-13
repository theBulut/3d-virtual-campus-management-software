import { Link } from 'react-router-dom';

/**
 * Shown when a route is entered without the required permission (scenario S-18). It names the missing
 * permission on purpose: during the evaluation it should be visible <em>why</em> access was refused,
 * and the same call against the API is answered with 403 as well.
 */
export default function ForbiddenPage({ required = [] }) {
  return (
    <div className="page page--centered">
      <h1 className="page__title">403 — Kein Zugriff</h1>
      <p>Für diesen Bereich fehlt Ihrem Konto die erforderliche Berechtigung.</p>
      {required.length > 0 && (
        <p className="page__hint">
          Benötigt wird: {required.map((permission) => <code key={permission}>{permission}</code>)}
        </p>
      )}
      <p className="page__hint">
        Die Prüfung erfolgt zusätzlich serverseitig — der zugehörige API-Aufruf wird ebenfalls mit 403
        abgewiesen und im Audit-Log vermerkt.
      </p>
      <Link className="button" to="/admin">
        Zum Dashboard
      </Link>
    </div>
  );
}
