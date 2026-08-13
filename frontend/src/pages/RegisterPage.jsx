import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import FormField from '../components/ui/FormField';

const EMPTY = {
  username: '',
  email: '',
  firstName: '',
  lastName: '',
  password: '',
};

/**
 * Self-registration (FA-23). Every new account receives the role EXTERNE_PERSON — there is no choice
 * here, and the request carries no roles field at all.
 */
export default function RegisterPage() {
  const { register, isAuthenticated, loading } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (loading) {
    return <p className="page__loading">Sitzung wird geprüft …</p>;
  }
  if (isAuthenticated) {
    return <Navigate to="/play" replace />;
  }

  const update = (field) => (value) => setForm((current) => ({ ...current, [field]: value }));

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await register(form);
      // Registration answers with a session, so the game is one navigation away.
      navigate('/play', { replace: true });
    } catch (apiError) {
      setFieldErrors(apiError.fieldErrors ?? {});
      setError(apiError.message);
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <form className="login__card login__card--wide" onSubmit={submit}>
        <h1 className="login__title">Konto anlegen</h1>
        <p className="login__subtitle">
          Kostenlos und in einer Minute. Danach geht es direkt in den Campus.
        </p>

        <FormField
          label="Benutzername"
          name="username"
          value={form.username}
          onChange={update('username')}
          error={fieldErrors.username}
          hint="Buchstaben, Ziffern, Punkt, Unterstrich, Bindestrich"
          required
        />
        <FormField
          label="E-Mail"
          name="email"
          type="email"
          value={form.email}
          onChange={update('email')}
          error={fieldErrors.email}
          hint="Damit kannst du dich später auch anmelden."
          required
        />
        <div className="form__row">
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
        </div>
        <FormField
          label="Passwort"
          name="password"
          type="password"
          value={form.password}
          onChange={update('password')}
          error={fieldErrors.password}
          hint="Mindestens 12 Zeichen"
          required
        />

        {error && (
          <p className="login__error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="button button--primary button--wide" disabled={busy}>
          {busy ? 'Konto wird angelegt …' : 'Konto anlegen'}
        </button>

        <p className="login__hint">
          Schon ein Konto? <Link to="/login">Anmelden</Link>
        </p>
      </form>
    </div>
  );
}
