import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchPois } from '../../api/pois';
import Can from '../../auth/Can';
import DataTable from '../../components/ui/DataTable';
import StatusBadge, { STATUS_LABELS } from '../../components/ui/StatusBadge';

/**
 * The POI list. With {@code reviewQueue} it becomes the release queue — same table, fixed filter on
 * IN_REVIEW, reachable only for holders of POI_PUBLISH.
 */
export default function PoiListPage({ reviewQueue = false }) {
  const navigate = useNavigate();
  const [page, setPage] = useState(null);
  const [status, setStatus] = useState(reviewQueue ? 'IN_REVIEW' : '');
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setError(null);
    fetchPois({ status: reviewQueue ? 'IN_REVIEW' : status, size: 50 })
      .then(setPage)
      .catch(setError);
  }, [reviewQueue, status]);

  useEffect(load, [load]);

  const columns = [
    { key: 'nameDe', header: 'Name' },
    { key: 'category', header: 'Kategorie' },
    { key: 'buildingCode', header: 'Gebäude', render: (poi) => poi.buildingCode ?? '—' },
    { key: 'status', header: 'Status', render: (poi) => <StatusBadge status={poi.status} /> },
    {
      key: 'assignedToUsername',
      header: 'Bearbeiter',
      render: (poi) => poi.assignedToUsername ?? poi.createdByUsername ?? '—',
    },
  ];

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">{reviewQueue ? 'Freigabe-Warteschlange' : 'Points of Interest'}</h1>
        {!reviewQueue && (
          <Can perm="POI_CREATE">
            <Link className="button button--primary" to="/admin/pois/new">
              POI anlegen
            </Link>
          </Can>
        )}
      </div>

      {reviewQueue ? (
        <p className="page__lead">
          Eingereichte Inhalte warten hier auf eine Entscheidung. Freigeben und Zurückweisen setzen
          <code> POI_PUBLISH</code> voraus — eine Berechtigung, die die beitragende Rolle nicht hat.
        </p>
      ) : (
        <form className="toolbar">
          <label className="toolbar__label" htmlFor="status-filter">
            Status
          </label>
          <select
            id="status-filter"
            className="field__input"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">alle</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </form>
      )}

      {!page ? (
        <p className="page__loading">POIs werden geladen …</p>
      ) : (
        <DataTable
          columns={columns}
          rows={page.content}
          onRowClick={(poi) => navigate(`/admin/pois/${poi.id}`)}
          empty={reviewQueue ? 'Nichts zu prüfen.' : 'Keine POIs gefunden.'}
        />
      )}
    </div>
  );
}
