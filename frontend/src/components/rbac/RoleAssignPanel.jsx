import { useEffect, useState } from 'react';
import { assignRole, fetchGrantableRoles, revokeRole } from '../../api/users';
import { useAuth } from '../../auth/AuthContext';
import { useToast } from '../ui/Toast';
import RoleChip from './RoleChip';

/**
 * Assigning and revoking roles on a foreign account (spec section 6, scenarios S-05 and S-06).
 * <p>
 * The dropdown is filled exclusively from {@code GET /api/users/me/grantable-roles}. The client never
 * computes which role may be handed out — the grant set lives in {@code role_grant} and is enforced by
 * {@code RoleAssignmentService}. A project lead therefore does not even see ADMIN here, and if the call
 * is made by hand anyway, the server answers 403 ROLE_NOT_GRANTABLE.
 */
export default function RoleAssignPanel({ user, onChange }) {
  const { user: currentUser } = useAuth();
  const toast = useToast();
  const [grantable, setGrantable] = useState([]);
  const [selected, setSelected] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchGrantableRoles()
      .then(setGrantable)
      .catch(() => setGrantable([]));
  }, []);

  // Nobody changes their own roles — the server refuses it with SELF_MODIFICATION_FORBIDDEN (INV-2),
  // so offering the buttons would only produce an error message.
  const isSelf = currentUser.id === user.id;
  const addable = grantable.filter((role) => !user.roles.includes(role));

  const run = async (action, successMessage) => {
    setBusy(true);
    try {
      const updated = await action();
      toast.success(successMessage);
      onChange(updated);
    } catch (error) {
      toast.fromError(error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel">
      <h2 className="panel__title">Rollen</h2>

      <div className="panel__chips">
        {user.roles.map((role) => (
          <RoleChip
            key={role}
            role={role}
            disabled={busy || isSelf}
            onRemove={
              isSelf
                ? undefined
                : (name) =>
                    run(
                      () => revokeRole(user.id, name),
                      `Rolle ${name} wurde entzogen.`,
                    )
            }
          />
        ))}
      </div>

      {isSelf ? (
        <p className="panel__note">
          Eigene Rollen lassen sich nicht ändern (Invariante INV-2). Das gilt auch dann, wenn die
          Berechtigung zur Rollenvergabe vorliegt.
        </p>
      ) : (
        <form
          className="panel__form"
          onSubmit={(event) => {
            event.preventDefault();
            if (!selected) return;
            run(() => assignRole(user.id, selected), `Rolle ${selected} wurde vergeben.`).then(() =>
              setSelected(''),
            );
          }}
        >
          <select
            className="field__input"
            value={selected}
            onChange={(event) => setSelected(event.target.value)}
            aria-label="Zu vergebende Rolle"
            disabled={busy || !addable.length}
          >
            <option value="">Rolle wählen …</option>
            {addable.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </select>
          <button type="submit" className="button" disabled={busy || !selected}>
            Rolle vergeben
          </button>
        </form>
      )}

      <p className="panel__note">
        Zur Auswahl stehen ausschließlich Rollen aus der eigenen Vergabemenge
        (<code>GET /api/users/me/grantable-roles</code>). Diese Einschränkung wird serverseitig
        durchgesetzt, nicht hier.
      </p>
    </section>
  );
}
