import { useEffect, useState } from 'react';
import './App.scss';
import {
  ApiError,
  createUser,
  deleteUser,
  fetchAdmin,
  fetchUsers,
  updateUser,
} from './api/users';
import UserForm from './components/UserForm';
import UserTable from './components/UserTable';

function App() {
  const [admin, setAdmin] = useState(null);
  // null until the admin loads the list for the first time.
  const [users, setUsers] = useState(null);
  // null when the editor is closed, otherwise the user being edited ({} = new).
  const [editing, setEditing] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  useEffect(() => {
    fetchAdmin()
      .then(setAdmin)
      .catch(() => setAdmin(null));
  }, []);

  const loadUsers = async () => {
    setLoading(true);
    setError('');
    try {
      setUsers(await fetchUsers());
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const openEditor = (user) => {
    setFieldErrors({});
    setError('');
    setEditing(user);
  };

  const closeEditor = () => {
    setFieldErrors({});
    setEditing(null);
  };

  const handleSubmit = async (values) => {
    setSubmitting(true);
    setError('');
    setFieldErrors({});
    try {
      if (editing.id === undefined) {
        await createUser(values);
      } else {
        await updateUser(editing.id, values);
      }
      setEditing(null);
      await loadUsers();
    } catch (err) {
      setError(err.message);
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (user) => {
    if (!window.confirm(`${user.firstName} ${user.lastName} wirklich löschen?`)) {
      return;
    }
    setError('');
    try {
      await deleteUser(user.id);
      if (editing?.id === user.id) {
        closeEditor();
      }
      await loadUsers();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <main className="app">
      <header className="app-header">
        <h1>3D Virtual Campus Management Software</h1>
        <p className="admin-badge">
          Angemeldet als: <strong>{admin ? admin.displayName : '—'}</strong>
        </p>
      </header>

      <div className="toolbar">
        <button type="button" className="primary" onClick={loadUsers} disabled={loading}>
          {loading ? 'Lädt…' : 'Alle User anzeigen'}
        </button>
        <button type="button" onClick={() => openEditor({})}>
          + Neuer User
        </button>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <div className="layout">
        <section className="panel">
          <h2>User{users ? ` (${users.length})` : ''}</h2>
          {users === null ? (
            <p className="hint">Klick auf „Alle User anzeigen“, um die Liste zu laden.</p>
          ) : (
            <UserTable
              users={users}
              selectedId={editing?.id}
              onEdit={openEditor}
              onDelete={handleDelete}
            />
          )}
        </section>

        {editing && (
          <section className="panel">
            <UserForm
              key={editing.id ?? 'new'}
              user={editing}
              fieldErrors={fieldErrors}
              submitting={submitting}
              onSubmit={handleSubmit}
              onCancel={closeEditor}
            />
          </section>
        )}
      </div>
    </main>
  );
}

export default App;
