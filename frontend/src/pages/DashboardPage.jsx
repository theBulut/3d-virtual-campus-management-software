import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import RoleChip from '../components/rbac/RoleChip';

/**
 * The one page every account reaches. It shows what this account may do — which makes the difference
 * between the roles visible immediately after signing in (scenario S-17).
 */
export default function DashboardPage() {
  const { user, roles, permissions } = useAuth();

  return (
    <div className="page">
      <h1 className="page__title">Willkommen, {user.firstName}</h1>

      <section className="card">
        <h2 className="card__title">Dieses Konto</h2>
        <dl className="definition">
          <dt>Benutzername</dt>
          <dd>{user.username}</dd>
          <dt>E-Mail</dt>
          <dd>{user.email}</dd>
          <dt>Einrichtung</dt>
          <dd>{user.organisation ?? '—'}</dd>
          <dt>Rollen</dt>
          <dd>
            {roles.map((role) => (
              <RoleChip key={role} role={role} />
            ))}
          </dd>
        </dl>
      </section>

      <section className="card">
        <h2 className="card__title">Berechtigungen ({permissions.length})</h2>
        <p className="card__hint">
          Diese Liste stammt aus dem Token und entscheidet, welche Menüpunkte und Schaltflächen sichtbar
          sind. Sie wird beim Anmelden und bei jeder Token-Erneuerung neu vom Server geliefert.
        </p>
        <ul className="permission-list">
          {[...permissions].sort().map((permission) => (
            <li key={permission}>
              <code>{permission}</code>
            </li>
          ))}
        </ul>
        <p className="card__hint">
          Die vollständige Matrix aller Rollen steht unter <Link to="/admin/roles/matrix">Rollen &amp; Rechte</Link>.
        </p>
      </section>
    </div>
  );
}
