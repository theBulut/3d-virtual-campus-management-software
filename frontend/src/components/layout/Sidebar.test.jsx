import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../auth/AuthContext';
import Sidebar from './Sidebar';

/**
 * Scenario S-17: the menu of each role matches the permission matrix. The expectation is written per
 * role and derived from the permissions the backend hands out — a menu entry without its permission
 * would be a promise the API does not keep.
 */
function renderSidebar(permissions) {
  localStorage.setItem('campus.accessToken', 'test-token');
  localStorage.setItem('campus.refreshToken', 'test-refresh');
  global.fetch = jest.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: () => Promise.resolve({ id: 1, username: 'tester', roles: ['X'], permissions }),
    }),
  );
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Sidebar />
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  localStorage.clear();
  jest.resetAllMocks();
});

test('PROJEKTMITARBEITER sieht POIs, aber keine Freigabe-Warteschlange', async () => {
  renderSidebar(['POI_READ_ALL', 'POI_CREATE', 'POI_SUBMIT_REVIEW', 'PROFILE_UPDATE_OWN']);

  expect(await screen.findByText('POIs')).toBeInTheDocument();
  expect(screen.queryByText('Freigabe-Warteschlange')).not.toBeInTheDocument();
  expect(screen.queryByText('Nutzerverwaltung')).not.toBeInTheDocument();
  expect(screen.queryByText('Audit-Log')).not.toBeInTheDocument();
});

test('PROJEKTLEITER sieht zusätzlich Warteschlange und Nutzerverwaltung', async () => {
  renderSidebar(['POI_READ_ALL', 'POI_PUBLISH', 'USER_READ', 'ROLE_READ', 'AUDIT_READ_CONTENT']);

  expect(await screen.findByText('Freigabe-Warteschlange')).toBeInTheDocument();
  expect(screen.getByText('Nutzerverwaltung')).toBeInTheDocument();
  expect(screen.getByText('Rollen & Rechte')).toBeInTheDocument();
  expect(screen.getByText('Audit-Log')).toBeInTheDocument();
});

test('MAINTENANCE_DEV sieht ausschließlich Audit-Log und Rollen, keine Inhalte', async () => {
  renderSidebar(['ROLE_READ', 'AUDIT_READ', 'AUDIT_READ_CONTENT', 'SYSTEM_HEALTH_READ', 'PROFILE_UPDATE_OWN']);

  expect(await screen.findByText('Audit-Log')).toBeInTheDocument();
  expect(screen.getByText('Rollen & Rechte')).toBeInTheDocument();
  expect(screen.queryByText('POIs')).not.toBeInTheDocument();
  expect(screen.queryByText('Nutzerverwaltung')).not.toBeInTheDocument();
});

test('PERSONAL sieht nur das Dashboard', async () => {
  renderSidebar(['PROFILE_UPDATE_OWN', 'CONSULTATION_READ_ALL', 'POI_READ_PUBLISHED']);

  expect(await screen.findByText('Dashboard')).toBeInTheDocument();
  expect(screen.queryByText('POIs')).not.toBeInTheDocument();
  expect(screen.queryByText('Audit-Log')).not.toBeInTheDocument();
});

test('ein frisch angelegtes Konto sieht ausschließlich das Dashboard', async () => {
  renderSidebar(['PROFILE_UPDATE_OWN']);

  const links = await screen.findAllByRole('link');
  expect(links).toHaveLength(1);
  expect(links[0]).toHaveTextContent('Dashboard');
});
