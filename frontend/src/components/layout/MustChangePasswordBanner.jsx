import { Link } from 'react-router-dom';

/**
 * Explains the empty menu of a freshly created account: as long as the temporary password is in place,
 * the token carries nothing but PROFILE_UPDATE_OWN (docs/DECISIONS.md D-21). Without this note the
 * restriction would look like a defect.
 */
export default function MustChangePasswordBanner() {
  return (
    <div className="banner banner--warning" role="status">
      <strong>Passwort ändern erforderlich.</strong> Dieses Konto arbeitet noch mit einem
      Initialpasswort. Bis zur Änderung enthält das Token ausschließlich die Berechtigung, das eigene
      Passwort zu setzen — alle anderen Bereiche bleiben deshalb verborgen.{' '}
      <Link to="/profile">Jetzt Passwort ändern</Link>
    </div>
  );
}
