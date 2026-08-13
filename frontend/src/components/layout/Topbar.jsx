import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import RoleChip from '../rbac/RoleChip';

export default function Topbar() {
  const { user, roles, permissions, logout } = useAuth();
  const navigate = useNavigate();

  const signOut = async () => {
    await logout();
    navigate('/', { replace: true });
  };

  return (
    <header className="topbar">
      <div className="topbar__identity">
        <Link to="/profile" className="topbar__name">
          {user.firstName} {user.lastName}
        </Link>
        <span className="topbar__username">@{user.username}</span>
        <span className="topbar__roles">
          {roles.map((role) => (
            <RoleChip key={role} role={role} />
          ))}
        </span>
        {/* The number makes the difference between the roles tangible during a demo. */}
        <span className="topbar__permissions" title="Anzahl der Berechtigungen dieses Kontos">
          {permissions.length} Berechtigungen
        </span>
      </div>
      <button type="button" className="button button--ghost" onClick={signOut}>
        Abmelden
      </button>
    </header>
  );
}
