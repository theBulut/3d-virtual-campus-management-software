import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createUser, fetchGrantableRoles } from '../../api/users';
import FormField from '../../components/ui/FormField';
import { useToast } from '../../components/ui/Toast';

const EMPTY = {
  username: '',
  email: '',
  firstName: '',
  lastName: '',
  organisation: '',
  roles: [],
};

/**
 * Creating an account (FA-07, scenario S-05).
 * <p>
 * No password field: the server generates a temporary one, returns it exactly once and sets
 * {@code must_change_password}. The role checkboxes come from the caller's grant set, so an account can
 * never be created with a role the caller could not assign afterwards either.
 */
export default function UserFormPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState(EMPTY);
  const [grantable, setGrantable] = useState([]);
  const [fieldErrors, setFieldErrors] = useState({});
  const [created, setCreated] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchGrantableRoles().then(setGrantable).catch(() => setGrantable([]));
  }, []);

  const update = (field) => (value) => setForm((current) => ({ ...current, [field]: value }));

  const toggleRole = (role) =>
    setForm((current) => ({
      ...current,
      roles: current.roles.includes(role)
        ? current.roles.filter((entry) => entry !== role)
        : [...current.roles, role],
    }));

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFieldErrors({});
    try {
      const result = await createUser(form);
      setCreated(result);
      toast.success(`Konto ${result.user.username} wurde angelegt.`);
    } catch (error) {
      setFieldErrors(error.fieldErrors ?? {});
      toast.fromError(error);
    } finally {
      setBusy(false);
    }
  };

  // The temporary password is shown once and never again — it is not stored anywhere in plain text.
  if (created) {
    return (
      <div className="page">
        <h1 className="page__title">Konto angelegt</h1>
        <section className="card card--highlight">
          <h2 className="card__title">Initialpasswort</h2>
          <p className="card__hint">
            Dieses Passwort wird genau einmal angezeigt. Beim ersten Anmelden enthält das Token
            ausschließlich <code>PROFILE_UPDATE_OWN</code> — das Konto kann sich anmelden und sein
            Passwort setzen, mehr nicht.
          </p>
          <p className="secret">{created.temporaryPassword}</p>
          <dl className="definition">
            <dt>Benutzername</dt>
            <dd>
              <code>{created.user.username}</code>
            </dd>
            <dt>Rollen</dt>
            <dd>{created.user.roles.join(', ')}</dd>
          </dl>
          <div className="page__actions">
            <Link className="button button--primary" to={`/admin/users/${created.user.id}`}>
              Zum Konto
            </Link>
            <button
              type="button"
              className="button"
              onClick={() => {
                setCreated(null);
                setForm(EMPTY);
              }}
            >
              Weiteres Konto anlegen
            </button>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="page">
      <h1 className="page__title">Konto anlegen</h1>

      <form className="form" onSubmit={submit}>
        <FormField
          label="Benutzername"
          name="username"
          value={form.username}
          onChange={update('username')}
          error={fieldErrors.username}
          required
          hint="Buchstaben, Ziffern, Punkt, Unterstrich, Bindestrich"
        />
        <FormField
          label="E-Mail"
          name="email"
          type="email"
          value={form.email}
          onChange={update('email')}
          error={fieldErrors.email}
          required
        />
        <FormField
          label="Vorname"
          name="firstName"
          value={form.firstName}
          onChange={update('firstName')}
          error={fieldErrors.firstName}
          required
        />
        <FormField
          label="Nachname"
          name="lastName"
          value={form.lastName}
          onChange={update('lastName')}
          error={fieldErrors.lastName}
          required
        />
        <FormField
          label="Einrichtung"
          name="organisation"
          value={form.organisation}
          onChange={update('organisation')}
          error={fieldErrors.organisation}
        />

        <FormField label="Rollen" name="roles" error={fieldErrors.roles} required>
          <div className="checkbox-group">
            {grantable.map((role) => (
              <label key={role} className="checkbox">
                <input
                  type="checkbox"
                  checked={form.roles.includes(role)}
                  onChange={() => toggleRole(role)}
                />
                {role}
              </label>
            ))}
            {!grantable.length && (
              <p className="field__hint">Dieses Konto darf keine Rollen vergeben.</p>
            )}
          </div>
        </FormField>

        <div className="page__actions">
          <button type="submit" className="button button--primary" disabled={busy}>
            {busy ? 'Wird angelegt …' : 'Konto anlegen'}
          </button>
          <button type="button" className="button button--ghost" onClick={() => navigate('/admin/users')}>
            Abbrechen
          </button>
        </div>
      </form>

      <p className="page__hint">
        Zur Auswahl stehen nur Rollen aus der eigenen Vergabemenge. Mindestens eine Rolle ist Pflicht
        (Invariante INV-3).
      </p>
    </div>
  );
}
