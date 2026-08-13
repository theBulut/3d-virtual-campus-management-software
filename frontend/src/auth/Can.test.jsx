import { render, screen } from '@testing-library/react';
import Can from './Can';
import { AuthProvider } from './AuthContext';

/**
 * Renders Can inside a real AuthProvider whose session was primed through the /auth/me call — the same
 * path the application takes after a reload.
 */
function renderWithPermissions(permissions, ui) {
  localStorage.setItem('campus.accessToken', 'test-token');
  localStorage.setItem('campus.refreshToken', 'test-refresh');
  global.fetch = jest.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: () => Promise.resolve({ id: 1, username: 'tester', roles: ['PERSONAL'], permissions }),
    }),
  );
  return render(<AuthProvider>{ui}</AuthProvider>);
}

afterEach(() => {
  localStorage.clear();
  jest.resetAllMocks();
});

test('zeigt den Inhalt, wenn die Berechtigung vorliegt', async () => {
  renderWithPermissions(['POI_PUBLISH'], <Can perm="POI_PUBLISH">Freigeben</Can>);

  expect(await screen.findByText('Freigeben')).toBeInTheDocument();
});

test('verbirgt den Inhalt ohne die Berechtigung', async () => {
  renderWithPermissions(
    ['POI_CREATE'],
    <>
      <span>immer da</span>
      <Can perm="POI_PUBLISH">Freigeben</Can>
    </>,
  );

  await screen.findByText('immer da');
  expect(screen.queryByText('Freigeben')).not.toBeInTheDocument();
});

test('anyOf genügt eine der genannten Berechtigungen', async () => {
  renderWithPermissions(
    ['AUDIT_READ_CONTENT'],
    <Can anyOf={['AUDIT_READ', 'AUDIT_READ_CONTENT']}>Audit-Log</Can>,
  );

  expect(await screen.findByText('Audit-Log')).toBeInTheDocument();
});
