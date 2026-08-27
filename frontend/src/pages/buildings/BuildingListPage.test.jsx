import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../auth/AuthContext';
import BuildingListPage from './BuildingListPage';

const BUILDINGS = [
  {
    id: 1,
    code: 'S1|03',
    nameDe: 'Altes Hauptgebäude',
    street: 'Hochschulstraße 1',
    city: 'Darmstadt',
    published: true,
  },
  {
    id: 2,
    code: 'S3|06',
    nameDe: 'Hans-Busch-Institut (ETIT)',
    street: 'Merckstraße 25',
    city: 'Darmstadt',
    published: false,
  },
];

/**
 * The list against a stubbed API. Routing the mock by URL rather than by call order keeps the test
 * readable when the page later fetches something else as well.
 */
function renderList(permissions) {
  localStorage.setItem('campus.accessToken', 'test-token');
  localStorage.setItem('campus.refreshToken', 'test-refresh');
  global.fetch = jest.fn((url) => {
    const body = url.endsWith('/auth/me')
      ? { id: 1, username: 'tester', roles: ['PROJEKTLEITER'], permissions }
      : BUILDINGS;
    return Promise.resolve({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: () => Promise.resolve(body),
    });
  });
  return render(
    <MemoryRouter>
      <AuthProvider>
        <BuildingListPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  localStorage.clear();
  jest.resetAllMocks();
});

test('zeigt jedes Gebäude mit Schlüssel und Sichtbarkeit', async () => {
  renderList(['BUILDING_READ_ALL']);

  // Not the code: S1|03 also appears in the explanatory lead above the table.
  expect(await screen.findByText('Altes Hauptgebäude')).toBeInTheDocument();
  expect(screen.getByRole('cell', { name: 'S1|03' })).toBeInTheDocument();
  expect(screen.getByText('Hans-Busch-Institut (ETIT)')).toBeInTheDocument();
  expect(screen.getByText('Veröffentlicht')).toBeInTheDocument();
  expect(screen.getByText('Unveröffentlicht')).toBeInTheDocument();
});

test('ohne BUILDING_CREATE fehlt der Anlegen-Knopf', async () => {
  renderList(['BUILDING_READ_ALL']);

  await screen.findByText('Altes Hauptgebäude');
  expect(screen.queryByRole('link', { name: 'Gebäude anlegen' })).not.toBeInTheDocument();
});

test('mit BUILDING_CREATE führt der Knopf auf das leere Formular', async () => {
  renderList(['BUILDING_READ_ALL', 'BUILDING_CREATE']);

  const link = await screen.findByRole('link', { name: 'Gebäude anlegen' });
  expect(link).toHaveAttribute('href', '/admin/buildings/new');
});
