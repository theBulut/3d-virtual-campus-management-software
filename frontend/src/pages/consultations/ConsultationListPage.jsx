import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchConsultations } from '../../api/consultations';
import Can from '../../auth/Can';
import DataTable from '../../components/ui/DataTable';
import PublishedBadge from '../../components/ui/PublishedBadge';

/** Consultation and support offers with their weekly slots. */
export default function ConsultationListPage() {
  const navigate = useNavigate();
  const [consultations, setConsultations] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setError(null);
    fetchConsultations().then(setConsultations).catch(setError);
  }, []);

  useEffect(load, [load]);

  const columns = [
    { key: 'titleDe', header: 'Angebot' },
    { key: 'organisation', header: 'Einrichtung' },
    {
      key: 'buildingCode',
      header: 'Gebäude',
      render: (offer) => [offer.buildingCode, offer.room].filter(Boolean).join(' · ') || '—',
    },
    {
      key: 'events',
      header: 'Sprechzeiten',
      render: (offer) => offer.events.length,
    },
    {
      key: 'published',
      header: 'Sichtbarkeit',
      render: (offer) => <PublishedBadge published={offer.published} />,
    },
  ];

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">Beratungsangebote</h1>
        <Can perm="CONSULTATION_CREATE">
          <Link className="button button--primary" to="/admin/consultations/new">
            Angebot anlegen
          </Link>
        </Can>
      </div>

      <p className="page__lead">
        Veröffentlichte Angebote erscheinen im Campus am Gebäude, das hier eingetragen ist — im Infofeld
        jedes Punktes in diesem Haus, mit den Sprechzeiten darunter.
      </p>

      {!consultations ? (
        <p className="page__loading">Angebote werden geladen …</p>
      ) : (
        <DataTable
          columns={columns}
          rows={consultations}
          onRowClick={(offer) => navigate(`/admin/consultations/${offer.id}`)}
          empty="Keine Beratungsangebote vorhanden."
        />
      )}
    </div>
  );
}
