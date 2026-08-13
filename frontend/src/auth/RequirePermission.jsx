import { Outlet } from 'react-router-dom';
import ForbiddenPage from '../pages/errors/ForbiddenPage';
import { useAuth } from './AuthContext';

/**
 * Route guard: without one of the listed permissions the 403 page appears instead of the route
 * (scenario S-18).
 * <p>
 * This is convenience, not protection. Whoever bypasses the guard reaches an endpoint that refuses the
 * call itself — every controller method carries its own {@code @PreAuthorize}. The guard exists so
 * nobody is led into a screen whose every request would fail.
 */
export default function RequirePermission({ anyOf = [], children }) {
  const { hasAnyPermission } = useAuth();

  if (!hasAnyPermission(anyOf)) {
    return <ForbiddenPage required={anyOf} />;
  }
  return children ?? <Outlet />;
}
