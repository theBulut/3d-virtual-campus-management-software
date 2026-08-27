import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchBuildings } from '../../api/buildings';
import Can from '../../auth/Can';
import DataTable from '../../components/ui/DataTable';
import PublishedBadge from '../../components/ui/PublishedBadge';

/**
 * The buildings of the campus. Unpaged, because the list is the set of houses of one university and
 * grows by a handful a decade — the endpoint returns it in one go.
 */
export default function BuildingListPage() {
  const navigate = useNavigate();
  const [buildings, setBuildings] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setError(null);
    fetchBuildings().then(setBuildings).catch(setError);
  }, []);

  useEffect(load, [load]);

  const columns = [
    { key: 'code', header: 'Schlüssel' },
    { key: 'nameDe', header: 'Name' },
    {
      key: 'address',
      header: 'Adresse',
      render: (building) =>
        [building.street, building.city].filter(Boolean).join(', ') || '—',
    },
    {
      key: 'published',
      header: 'Sichtbarkeit',
      render: (building) => <PublishedBadge published={building.published} />,
    },
  ];

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">Gebäude</h1>
        <Can perm="BUILDING_CREATE">
          <Link className="button button--primary" to="/admin/buildings/new">
            Gebäude anlegen
          </Link>
        </Can>
      </div>

      <p className="page__lead">
        Der Gebäudeschlüssel verbindet Verwaltung und 3D-Szene: <code>S1|03</code> hier findet dort das
        Modell <code>S103</code>. Ein Schlüssel, den die Szene nicht kennt, ist kein Fehler — das Gebäude
        wird dann aus seinen Szenenkoordinaten gestellt.
      </p>

      {!buildings ? (
        <p className="page__loading">Gebäude werden geladen …</p>
      ) : (
        <DataTable
          columns={columns}
          rows={buildings}
          onRowClick={(building) => navigate(`/admin/buildings/${building.id}`)}
          empty="Keine Gebäude vorhanden."
        />
      )}
    </div>
  );
}
