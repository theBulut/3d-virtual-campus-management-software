import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword, updateProfile } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import FormField from '../components/ui/FormField';
import { useToast } from '../components/ui/Toast';

/**
 * The one page every role can reach — PROFILE_UPDATE_OWN is the only permission all six roles hold.
 * <p>
 * Changing the password does two things at once: it clears {@code must_change_password}, so the next
 * token carries the full permissions of the role again (D-21), and it raises {@code refresh_version},
 * which kills every token issued so far (FA-19). The session therefore has to be renewed here.
 */
export default function ProfilePage() {
  const { user, setUser, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [profile, setProfile] = useState({
    firstName: user.firstName,
    lastName: user.lastName,
    email: user.email,
    organisation: user.organisation ?? '',
  });
  const [profileErrors, setProfileErrors] = useState({});
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '' });
  const [passwordErrors, setPasswordErrors] = useState({});
  const [busy, setBusy] = useState(false);

  const saveProfile = async (event) => {
    event.preventDefault();
    setBusy(true);
    setProfileErrors({});
    try {
      const updated = await updateProfile(profile);
      setUser(updated);
      toast.success('Profil gespeichert.');
    } catch (error) {
      setProfileErrors(error.fieldErrors ?? {});
      toast.fromError(error);
    } finally {
      setBusy(false);
    }
  };

  const savePassword = async (event) => {
    event.preventDefault();
    setBusy(true);
    setPasswordErrors({});
    try {
      await changePassword(passwords.currentPassword, passwords.newPassword);
      setPasswords({ currentPassword: '', newPassword: '' });
      // A password change raises both counters (D-3): access and refresh token are dead from this
      // moment, so there is nothing left to refresh. Signing in again is the only correct reaction —
      // and it is what makes the lifted restriction visible, because the new token carries the full
      // permissions of the role.
      toast.info('Passwort geändert. Bitte melden Sie sich mit dem neuen Passwort erneut an.');
      await logout();
      navigate('/login', { replace: true });
    } catch (error) {
      setPasswordErrors(error.fieldErrors ?? {});
      toast.fromError(error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <h1 className="page__title">Mein Profil</h1>

      <section className="card">
        <h2 className="card__title">Stammdaten</h2>
        <form className="form" onSubmit={saveProfile}>
          <FormField
            label="Vorname"
            name="firstName"
            value={profile.firstName}
            onChange={(value) => setProfile({ ...profile, firstName: value })}
            error={profileErrors.firstName}
            required
          />
          <FormField
            label="Nachname"
            name="lastName"
            value={profile.lastName}
            onChange={(value) => setProfile({ ...profile, lastName: value })}
            error={profileErrors.lastName}
            required
          />
          <FormField
            label="E-Mail"
            name="email"
            type="email"
            value={profile.email}
            onChange={(value) => setProfile({ ...profile, email: value })}
            error={profileErrors.email}
            required
          />
          <FormField
            label="Einrichtung"
            name="organisation"
            value={profile.organisation}
            onChange={(value) => setProfile({ ...profile, organisation: value })}
            error={profileErrors.organisation}
          />
          <button type="submit" className="button button--primary" disabled={busy}>
            Speichern
          </button>
        </form>
      </section>

      <section className={user.mustChangePassword ? 'card card--highlight' : 'card'}>
        <h2 className="card__title">Passwort ändern</h2>
        <p className="card__hint">
          Mindestens 12 Zeichen. Mit der Änderung werden alle bestehenden Anmeldungen dieses Kontos
          ungültig — auch auf anderen Geräten.
        </p>
        <form className="form" onSubmit={savePassword}>
          <FormField
            label="Aktuelles Passwort"
            name="currentPassword"
            type="password"
            value={passwords.currentPassword}
            onChange={(value) => setPasswords({ ...passwords, currentPassword: value })}
            error={passwordErrors.currentPassword}
            required
          />
          <FormField
            label="Neues Passwort"
            name="newPassword"
            type="password"
            value={passwords.newPassword}
            onChange={(value) => setPasswords({ ...passwords, newPassword: value })}
            error={passwordErrors.newPassword}
            required
          />
          <button type="submit" className="button button--primary" disabled={busy}>
            Passwort ändern
          </button>
        </form>
      </section>
    </div>
  );
}
