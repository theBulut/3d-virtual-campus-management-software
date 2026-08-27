import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../../auth/AuthContext';
import { ToastProvider } from '../../components/ui/Toast';
import ConsultationFormPage from './ConsultationFormPage';

const OFFER = {
  id: 7,
  titleDe: 'Studienberatung Informatik',
  titleEn: 'Computer Science Study Advice',
  descriptionDe: 'Fragen zu Studienverlauf und Prüfungsordnung.',
  descriptionEn: '',
  organisation: 'Fachgebiet Informatik',
  buildingId: 2,
  buildingCode: 'S2|02',
  room: 'B302',
  contactEmail: 'studienberatung.inf@tu-darmstadt.de',
  responsibleUserId: 5,
  responsibleUsername: 'demo_personal',
  published: true,
  events: [
    {
      id: 11,
      dayOfWeek: 2,
      startTime: '10:00:00',
      endTime: '12:00:00',
      validFrom: null,
      validTo: null,
      roomOverride: null,
      note: 'Ohne Anmeldung',
    },
  ],
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

const BUILDINGS = [{ id: 2, code: 'S2|02', nameDe: 'Robert-Piloty-Gebäude' }];

function renderForm(permissions) {
  localStorage.setItem('campus.accessToken', 'test-token');
  localStorage.setItem('campus.refreshToken', 'test-refresh');
  global.fetch = jest.fn((url) => {
    let body = OFFER;
    if (url.endsWith('/auth/me')) {
      body = { id: 5, username: 'demo_personal', roles: ['PERSONAL'], permissions };
    } else if (url.endsWith('/buildings')) {
      body = BUILDINGS;
    }
    return Promise.resolve({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: () => Promise.resolve(body),
    });
  });
  return render(
    <MemoryRouter initialEntries={['/admin/consultations/7']}>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/admin/consultations/:id" element={<ConsultationFormPage />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  localStorage.clear();
  jest.resetAllMocks();
});

test('lädt Stammdaten und Sprechzeiten des Angebots', async () => {
  const { container } = renderForm(['CONSULTATION_READ_ALL', 'CONSULTATION_UPDATE_OWN']);

  expect(await screen.findByDisplayValue('Studienberatung Informatik')).toBeInTheDocument();
  expect(screen.getByDisplayValue('Fachgebiet Informatik')).toBeInTheDocument();
  expect(screen.getByDisplayValue('Ohne Anmeldung')).toBeInTheDocument();

  // "10:00:00" from the server has to reach the time input as "10:00", or the browser shows it empty.
  // Addressed by the id of this slot: the empty row underneath carries the same default time.
  expect(container.querySelector('#field-start-11')).toHaveValue('10:00');
  expect(container.querySelector('#field-end-11')).toHaveValue('12:00');
  expect(container.querySelector('#field-day-11')).toHaveValue('2');
});

test('ohne CONSULTATION_UPDATE_ANY gibt es kein Veröffentlichen-Kästchen, sondern den Grund', async () => {
  renderForm(['CONSULTATION_READ_ALL', 'CONSULTATION_UPDATE_OWN']);

  await screen.findByDisplayValue('Studienberatung Informatik');
  expect(screen.queryByLabelText(/Veröffentlicht/)).not.toBeInTheDocument();
  expect(screen.getByText(/Freigeben setzt/)).toBeInTheDocument();
});

test('mit CONSULTATION_UPDATE_ANY erscheint das Kästchen', async () => {
  renderForm(['CONSULTATION_READ_ALL', 'CONSULTATION_UPDATE_ANY']);

  await screen.findByDisplayValue('Studienberatung Informatik');
  const checkbox = screen.getByRole('checkbox');
  expect(checkbox).toBeChecked();
  expect(screen.queryByText(/Freigeben setzt/)).not.toBeInTheDocument();
});
