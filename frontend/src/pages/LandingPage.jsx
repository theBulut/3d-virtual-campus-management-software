import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * The public entrance. Explains what the campus is and offers the two ways in.
 * <p>
 * Anyone already signed in is sent on immediately: players to the game, everyone with something to do in
 * the administration to the dashboard. The landing page is for visitors, not a stop on the way.
 */
export default function LandingPage() {
  const { isAuthenticated, loading, canAdminister } = useAuth();

  if (loading) {
    return <p className="page__loading">Sitzung wird geprüft …</p>;
  }
  if (isAuthenticated) {
    return <Navigate to={canAdminister ? '/admin' : '/play'} replace />;
  }

  return (
    <div className="landing">
      <header className="landing__hero">
        <span className="landing__mark">3D</span>
        <h1 className="landing__title">Der Campus der TU Darmstadt, begehbar im Browser</h1>
        <p className="landing__lead">
          Hörsäle, Bibliotheken, Mensen und Beratungsangebote — als 3D-Umgebung, die sich ohne
          Installation auf Rechner, Tablet und Telefon erkunden lässt. Gedacht für internationale
          Studierende, die den Campus kennenlernen, bevor sie zum ersten Mal davorstehen.
        </p>
        <div className="landing__actions">
          <Link className="button button--primary" to="/register">
            Konto anlegen und loslegen
          </Link>
          <Link className="button" to="/login">
            Anmelden
          </Link>
        </div>
      </header>

      <section className="landing__columns">
        <article>
          <h2>Was dich erwartet</h2>
          <p>
            Du bewegst dich frei über das Gelände, findest Gebäude und Orientierungspunkte und siehst zu
            jedem Ort die passenden Beratungszeiten. Dein Fortschritt hängt am Konto und ist beim
            nächsten Mal wieder da — auch auf einem anderen Gerät.
          </p>
        </article>
        <article>
          <h2>Inhalte werden gepflegt, nicht programmiert</h2>
          <p>
            Alles, was du im Spiel siehst, wird über ein Verwaltungswerkzeug eingepflegt und durchläuft
            eine Freigabe. Wer daran mitarbeiten möchte, meldet sich zunächst ganz normal an — die
            Projektleitung schaltet die zusätzlichen Rechte danach frei.
          </p>
        </article>
      </section>

      <footer className="landing__footer">
        Ein Projekt der AG Serious Games im Rahmen einer Bachelorarbeit am Fachbereich Informatik.
      </footer>
    </div>
  );
}
