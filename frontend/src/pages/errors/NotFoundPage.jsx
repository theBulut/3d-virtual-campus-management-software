import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="page page--centered">
      <h1 className="page__title">404 — Nicht gefunden</h1>
      <p>Diese Seite gibt es nicht.</p>
      <Link className="button" to="/admin">
        Zum Dashboard
      </Link>
    </div>
  );
}
