import { useEffect, useState } from 'react';
import { fetchMatrix } from '../../api/roles';
import { useAuth } from '../../auth/AuthContext';

import PermissionMatrix from '../../components/rbac/PermissionMatrix';

export default function PermissionMatrixPage() {
  const { roles } = useAuth();
  const [matrix, setMatrix] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchMatrix().then(setMatrix).catch(setError);
  }, []);

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }
  if (!matrix) {
    return <p className="page__loading">Matrix wird geladen …</p>;
  }

  return (
    <div className="page">
      <h1 className="page__title">Rollen &amp; Rechte</h1>
      <p className="page__lead">
        {matrix.roles.length} Rollen, {matrix.permissions.length} Berechtigungen. Diese Darstellung wird
        aus <code>GET /api/roles/matrix</code> erzeugt — derselben Quelle, gegen die die Autorisierung
        prüft.
      </p>

      <PermissionMatrix matrix={matrix} highlightRole={roles[0]} />

      <section className="card">
        <h2 className="card__title">Vergaberegeln</h2>
        <p className="card__hint">
          Wer welche Rolle vergeben darf, ist eine eigene Beziehung und folgt nicht aus den
          Berechtigungen. Eine Projektleitung darf Rollen vergeben — aber nur diese hier.
        </p>
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Rolle</th>
              <th scope="col">darf vergeben</th>
            </tr>
          </thead>
          <tbody>
            {matrix.roles.map((role) => (
              <tr key={role.name}>
                <th scope="row">{role.name}</th>
                <td>
                  {matrix.grants[role.name]?.length
                    ? matrix.grants[role.name].join(', ')
                    : '— keine —'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
