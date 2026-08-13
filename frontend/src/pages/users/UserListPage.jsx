import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchUsers } from '../../api/users';
import Can from '../../auth/Can';
import RoleChip from '../../components/rbac/RoleChip';
import DataTable from '../../components/ui/DataTable';

export default function UserListPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(null);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);

  const load = (q = '') => {
    fetchUsers({ q, size: 50 }).then(setPage).catch(setError);
  };

  useEffect(() => {
    load();
  }, []);

  const columns = [
    { key: 'username', header: 'Benutzername', render: (user) => <code>{user.username}</code> },
    { key: 'name', header: 'Name', render: (user) => `${user.firstName} ${user.lastName}` },
    { key: 'email', header: 'E-Mail' },
    {
      key: 'roles',
      header: 'Rollen',
      render: (user) => user.roles.map((role) => <RoleChip key={role} role={role} />),
    },
    {
      key: 'active',
      header: 'Status',
      render: (user) => (
        <span className={user.active ? 'badge badge--published' : 'badge badge--archived'}>
          {user.active ? 'aktiv' : 'gesperrt'}
        </span>
      ),
    },
  ];

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">Nutzerverwaltung</h1>
        <Can perm="USER_CREATE">
          <Link className="button button--primary" to="/admin/users/new">
            Konto anlegen
          </Link>
        </Can>
      </div>

      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault();
          load(search);
        }}
      >
        <input
          className="field__input"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Name, Benutzername oder E-Mail"
          aria-label="Konten durchsuchen"
        />
        <button type="submit" className="button">
          Suchen
        </button>
      </form>

      {!page ? (
        <p className="page__loading">Konten werden geladen …</p>
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={page.content}
            onRowClick={(user) => navigate(`/admin/users/${user.id}`)}
            empty="Keine Konten gefunden."
          />
          <p className="page__hint">
            {page.totalElements} Konten insgesamt. Ein Klick auf eine Zeile öffnet die Rollenvergabe.
          </p>
        </>
      )}
    </div>
  );
}
