import { Link, NavLink } from 'react-router-dom';
import Can from '../../auth/Can';
import { useAuth } from '../../auth/AuthContext';

/**
 * The navigation of spec section 6. Every entry names the permissions that make it visible — the table
 * below is the menu rule, in one place, comparable line by line with the specification.
 */
const MENU = [
  { to: '/admin', label: 'Dashboard', end: true, anyOf: null },
  { to: '/admin/users', label: 'Nutzerverwaltung', anyOf: ['USER_READ'] },
  { to: '/admin/roles/matrix', label: 'Rollen & Rechte', anyOf: ['ROLE_READ'] },
  { to: '/admin/pois', label: 'POIs', anyOf: ['POI_READ_ALL'] },
  { to: '/admin/pois/review', label: 'Freigabe-Warteschlange', anyOf: ['POI_PUBLISH'] },
  { to: '/admin/audit', label: 'Audit-Log', anyOf: ['AUDIT_READ', 'AUDIT_READ_CONTENT'] },
];

export default function Sidebar() {
  const { hasAnyPermission } = useAuth();
  const visible = MENU.filter((entry) => !entry.anyOf || hasAnyPermission(entry.anyOf));

  return (
    <nav className="sidebar" aria-label="Hauptnavigation">
      <div className="sidebar__brand">
        <span className="sidebar__brand-mark">3D</span>
        <span>Campus&nbsp;Admin</span>
      </div>
      <ul className="sidebar__list">
        {visible.map((entry) => (
          <li key={entry.to}>
            <NavLink
              to={entry.to}
              end={entry.end}
              className={({ isActive }) =>
                isActive ? 'sidebar__link sidebar__link--active' : 'sidebar__link'
              }
            >
              {entry.label}
            </NavLink>
          </li>
        ))}
      </ul>
      {/* Every account with a content permission may walk the campus it maintains. */}
      <Can perm="POI_READ_PUBLISHED">
        <Link className="sidebar__link sidebar__link--game" to="/play">
          ▸ Zum Campus-Spiel
        </Link>
      </Can>

      <p className="sidebar__note">
        Sichtbar ist, wofür Berechtigungen vorliegen. Durchgesetzt wird serverseitig.
      </p>
    </nav>
  );
}

export { MENU };
