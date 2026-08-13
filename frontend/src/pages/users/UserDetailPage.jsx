import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchUser, setUserActive } from '../../api/users';
import Can from '../../auth/Can';
import { useAuth } from '../../auth/AuthContext';
import RoleAssignPanel from '../../components/rbac/RoleAssignPanel';
import { useToast } from '../../components/ui/Toast';

export default function UserDetailPage() {
  const { id } = useParams();
  const { user: currentUser } = useAuth();
  const toast = useToast();
  const [user, setUser] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    fetchUser(id).then(setUser).catch(setError);
  }, [id]);

  useEffect(load, [load]);

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }
  if (!user) {
    return <p className="page__loading">Konto wird geladen …</p>;
  }

  const isSelf = currentUser.id === user.id;

  const toggleActive = async () => {
    setBusy(true);
    try {
      const updated = await setUserActive(user.id, !user.active);
      setUser(updated);
      toast.success(
        updated.active
          ? 'Konto entsperrt.'
          : 'Konto gesperrt — alle laufenden Sitzungen dieses Kontos sind damit beendet.',
      );
    } catch (apiError) {
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">
          {user.firstName} {user.lastName}
        </h1>
        <Link className="button button--ghost" to="/admin/users">
          Zurück zur Liste
        </Link>
      </div>

      <section className="card">
        <h2 className="card__title">Stammdaten</h2>
        <dl className="definition">
          <dt>Benutzername</dt>
          <dd>
            <code>{user.username}</code>
          </dd>
          <dt>E-Mail</dt>
          <dd>{user.email}</dd>
          <dt>Einrichtung</dt>
          <dd>{user.organisation ?? '—'}</dd>
          <dt>Status</dt>
          <dd>
            <span className={user.active ? 'badge badge--published' : 'badge badge--archived'}>
              {user.active ? 'aktiv' : 'gesperrt'}
            </span>
            {user.mustChangePassword && (
              <span className="badge badge--draft">Passwortwechsel offen</span>
            )}
          </dd>
          <dt>Letzte Anmeldung</dt>
          <dd>{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('de-DE') : 'nie'}</dd>
        </dl>

        <Can perm="USER_ACTIVATE">
          {!isSelf && (
            <button type="button" className="button" onClick={toggleActive} disabled={busy}>
              {user.active ? 'Konto sperren' : 'Konto entsperren'}
            </button>
          )}
        </Can>
      </section>

      <RoleAssignPanel user={user} onChange={setUser} />
    </div>
  );
}
