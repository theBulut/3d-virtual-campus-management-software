import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import RequirePermission from './auth/RequirePermission';
import AppShell from './components/layout/AppShell';
import { ToastProvider } from './components/ui/Toast';
import DashboardPage from './pages/DashboardPage';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import PlayPage from './pages/PlayPage';
import ProfilePage from './pages/ProfilePage';
import RegisterPage from './pages/RegisterPage';
import AuditLogPage from './pages/audit/AuditLogPage';
import ForbiddenPage from './pages/errors/ForbiddenPage';
import NotFoundPage from './pages/errors/NotFoundPage';
import PoiEditorPage from './pages/pois/PoiEditorPage';
import PoiListPage from './pages/pois/PoiListPage';
import PermissionMatrixPage from './pages/roles/PermissionMatrixPage';
import UserDetailPage from './pages/users/UserDetailPage';
import UserFormPage from './pages/users/UserFormPage';
import UserListPage from './pages/users/UserListPage';
import './styles/main.scss';

/**
 * Three areas, and the route table says which is which.
 * <p>
 * Public: landing, login, registration. Signed in: the game under {@code /play} and the own profile,
 * both reachable for every account. The administration lives under {@code /admin} and every route there
 * names the permission it needs — the same code the backend checks in its {@code @PreAuthorize}
 * expression, so the two can be compared line by line.
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            <Route element={<ProtectedRoute />}>
              {/* The game needs no shell: it fills the window. */}
              <Route element={<RequirePermission anyOf={['POI_READ_PUBLISHED']} />}>
                <Route path="play" element={<PlayPage />} />
              </Route>

              <Route element={<AppShell />}>
                <Route path="profile" element={<ProfilePage />} />

                <Route path="admin" element={<DashboardPage />} />

                <Route element={<RequirePermission anyOf={['USER_READ']} />}>
                  <Route path="admin/users" element={<UserListPage />} />
                  <Route path="admin/users/:id" element={<UserDetailPage />} />
                </Route>
                <Route element={<RequirePermission anyOf={['USER_CREATE']} />}>
                  <Route path="admin/users/new" element={<UserFormPage />} />
                </Route>

                <Route element={<RequirePermission anyOf={['ROLE_READ']} />}>
                  <Route path="admin/roles/matrix" element={<PermissionMatrixPage />} />
                </Route>

                <Route element={<RequirePermission anyOf={['POI_READ_ALL']} />}>
                  <Route path="admin/pois" element={<PoiListPage />} />
                  <Route path="admin/pois/:id" element={<PoiEditorPage />} />
                </Route>
                <Route element={<RequirePermission anyOf={['POI_CREATE']} />}>
                  <Route path="admin/pois/new" element={<PoiEditorPage />} />
                </Route>
                <Route element={<RequirePermission anyOf={['POI_PUBLISH']} />}>
                  <Route path="admin/pois/review" element={<PoiListPage reviewQueue />} />
                </Route>

                <Route element={<RequirePermission anyOf={['AUDIT_READ', 'AUDIT_READ_CONTENT']} />}>
                  <Route path="admin/audit" element={<AuditLogPage />} />
                </Route>

                <Route path="403" element={<ForbiddenPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
