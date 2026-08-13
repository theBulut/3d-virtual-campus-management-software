import { useCallback, useEffect, useState } from 'react';
import { fetchAuditLog } from '../../api/audit';
import { useAuth } from '../../auth/AuthContext';
import DataTable from '../../components/ui/DataTable';

/**
 * The audit trail (FA-15). Holders of AUDIT_READ_CONTENT only ever receive entries about content — the
 * restriction is applied by the server as a filter, so this page needs no special case for it.
 */
export default function AuditLogPage() {
  const { hasPermission } = useAuth();
  const [page, setPage] = useState(null);
  const [action, setAction] = useState('');
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setError(null);
    fetchAuditLog({ action, size: 50 }).then(setPage).catch(setError);
  }, [action]);

  useEffect(load, [load]);

  const columns = [
    {
      key: 'createdAt',
      header: 'Zeitpunkt',
      render: (entry) => new Date(entry.createdAt).toLocaleString('de-DE'),
    },
    { key: 'actorUsername', header: 'Konto', render: (entry) => entry.actorUsername ?? 'anonym' },
    { key: 'action', header: 'Aktion', render: (entry) => <code>{entry.action}</code> },
    {
      key: 'resource',
      header: 'Ressource',
      render: (entry) => `${entry.resourceType}${entry.resourceId ? ` #${entry.resourceId}` : ''}`,
    },
    {
      key: 'success',
      header: 'Ergebnis',
      render: (entry) =>
        entry.success ? (
          <span className="badge badge--published">erfolgreich</span>
        ) : (
          <span className="badge badge--archived" title={entry.errorCode}>
            abgewiesen: {entry.errorCode}
          </span>
        ),
    },
  ];

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }

  return (
    <div className="page">
      <h1 className="page__title">Audit-Log</h1>
      <p className="page__lead">
        Jede schreibende Operation und jeder abgewiesene Zugriff werden protokolliert. Passwörter,
        Hashes und Tokens werden beim Schreiben maskiert und stehen nie im Log.
        {!hasPermission('AUDIT_READ') && (
          <> Dieses Konto sieht ausschließlich Einträge zu Inhalten (<code>AUDIT_READ_CONTENT</code>).</>
        )}
      </p>

      <form className="toolbar">
        <label className="toolbar__label" htmlFor="action-filter">
          Aktion
        </label>
        <input
          id="action-filter"
          className="field__input"
          value={action}
          onChange={(event) => setAction(event.target.value.toUpperCase())}
          placeholder="z. B. ROLE_ASSIGNED"
        />
      </form>

      {!page ? (
        <p className="page__loading">Einträge werden geladen …</p>
      ) : (
        <>
          <DataTable columns={columns} rows={page.content} empty="Keine Einträge." />
          <p className="page__hint">{page.totalElements} Einträge insgesamt.</p>
        </>
      )}
    </div>
  );
}
