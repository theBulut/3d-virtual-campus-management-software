import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** Everything behind this needs a session; without one the user goes to the login page. */
export default function ProtectedRoute() {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="page__loading">Sitzung wird geprüft …</p>;
  }
  if (!isAuthenticated) {
    // The target is remembered, so a deep link survives the detour through the login form.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
