function UserTable({ users, selectedId, onEdit, onDelete }) {
  if (users.length === 0) {
    return <p className="hint">Noch keine User angelegt.</p>;
  }

  return (
    <table className="user-table">
      <thead>
        <tr>
          <th>Vorname</th>
          <th>Nachname</th>
          <th>E-Mail</th>
          <th>Aktionen</th>
        </tr>
      </thead>
      <tbody>
        {users.map((user) => (
          <tr key={user.id} className={user.id === selectedId ? 'selected' : undefined}>
            <td>{user.firstName}</td>
            <td>{user.lastName}</td>
            <td>{user.email}</td>
            <td className="actions">
              <button type="button" onClick={() => onEdit(user)}>
                Bearbeiten
              </button>
              <button type="button" className="danger" onClick={() => onDelete(user)}>
                Löschen
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default UserTable;
