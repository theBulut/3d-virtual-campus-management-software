import { Outlet } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import MustChangePasswordBanner from './MustChangePasswordBanner';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

export default function AppShell() {
  const { user } = useAuth();

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell__main">
        <Topbar />
        <main className="shell__content">
          {user.mustChangePassword && <MustChangePasswordBanner />}
          <Outlet />
        </main>
      </div>
    </div>
  );
}
