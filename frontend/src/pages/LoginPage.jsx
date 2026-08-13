import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import FormField from '../components/ui/FormField';

/** Same list as in AuthContext; whoever holds none of these has nothing to do in the administration. */
const ADMIN_ENTRY_PERMISSIONS = [
  'USER_READ',
  'ROLE_READ',
  'POI_READ_ALL',
  'POI_PUBLISH',
  'AUDIT_READ',
  'AUDIT_READ_CONTENT',
];

export default function LoginPage() {
  const { login, isAuthenticated, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (loading) {
    return <p className="page__loading">Sitzung wird geprüft …</p>;
  }
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  /**
   * Where an account belongs after signing in. A registered student holds only the reading permissions
   * of EXTERNE_PERSON and would face an empty administration, so they go straight into the game;
   * everyone else lands on the dashboard and reaches the campus from the menu.
   */
  const destinationFor = (account) => {
    if (account.mustChangePassword) {
      return '/profile';
    }
    return ADMIN_ENTRY_PERMISSIONS.some((p) => account.permissions.includes(p)) ? '/admin' : '/play';
  };

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const account = await login(username, password);
      navigate(location.state?.from ?? destinationFor(account), { replace: true });
    } catch (apiError) {
      // Wrong password and unknown account are indistinguishable by design (spec section 4.2).
      setError(apiError.message);
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <form className="login__card" onSubmit={submit}>
        <h1 className="login__title">3D Campus Explorer</h1>
        <p className="login__subtitle">Anmelden mit Benutzername oder E-Mail-Adresse</p>

        <FormField
          label="Benutzername oder E-Mail"
          name="username"
          value={username}
          onChange={setUsername}
          required
        />
        <FormField
          label="Passwort"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          required
        />

        {error && (
          <p className="login__error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="button button--primary button--wide" disabled={busy}>
          {busy ? 'Anmeldung läuft …' : 'Anmelden'}
        </button>

        <p className="login__hint">
          Noch kein Konto? <Link to="/register">Jetzt registrieren</Link>
        </p>
      </form>
    </div>
  );
}
