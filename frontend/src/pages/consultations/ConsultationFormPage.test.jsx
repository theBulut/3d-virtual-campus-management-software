import { fireEvent, render, screen } from '@testing-library/react';
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

function renderForm(permissions, offer = OFFER) {
  localStorage.setItem('campus.accessToken', 'test-token');
  localStorage.setItem('campus.refreshToken', 'test-refresh');
  global.fetch = jest.fn((url) => {
    let body = offer;
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

/**
 * The offer's own save button does not carry the slots — they have their own endpoints. A changed row
 * therefore has to look different from a saved one, or the edit is lost without anybody noticing.
 */
test('eine geänderte Sprechzeit wird als ungespeichert markiert', async () => {
  const { container } = renderForm(['CONSULTATION_READ_ALL', 'CONSULTATION_UPDATE_OWN']);

  await screen.findByDisplayValue('Studienberatung Informatik');
  expect(container.querySelector('.slots__row--dirty')).not.toBeInTheDocument();

  fireEvent.change(container.querySelector('#field-start-11'), { target: { value: '11:00' } });

  const row = container.querySelector('.slots__row--dirty');
  expect(row).toBeInTheDocument();
  expect(row.querySelector('.button--primary')).toHaveTextContent('Speichern');
});

test('die Sprechzeit einer frisch geladenen Maske gilt als gespeichert', async () => {
  const { container } = renderForm(['CONSULTATION_READ_ALL', 'CONSULTATION_UPDATE_OWN']);

  await screen.findByDisplayValue('Ohne Anmeldung');
  expect(container.querySelector('.slots__row--dirty')).not.toBeInTheDocument();
  // Nothing pending, nothing being entered — so no primary button anywhere in the slot list.
  expect(container.querySelectorAll('.slots .button--primary')).toHaveLength(0);
});

const WRITE = ['CONSULTATION_READ_ALL', 'CONSULTATION_UPDATE_OWN'];

/**
 * Exactly as the API returns a one-off appointment: no {@code dayOfWeek} key at all. The backend runs with
 * {@code default-property-inclusion: non_null}, so a null field is omitted rather than sent as null —
 * which is why a loaded one-off used to fall back to the placeholder and lose its date fields.
 */
const ONE_OFF_EVENT = {
  id: 12,
  startTime: '12:14:00',
  endTime: '14:14:00',
  validFrom: '2026-09-22',
  validTo: '2026-09-25',
};

test('ein gespeicherter Einzeltermin wird als solcher geladen, samt seiner Daten', async () => {
  const { container } = renderForm(WRITE, { ...OFFER, events: [ONE_OFF_EVENT] });

  await screen.findByDisplayValue('Studienberatung Informatik');
  expect(container.querySelector('#field-day-12')).toHaveValue('ONCE');
  expect(container.querySelector('#field-from-12')).toHaveValue('2026-09-22');
  expect(container.querySelector('#field-to-12')).toHaveValue('2026-09-25');
});

/**
 * A row on screen stands for a row in the database. The form used to keep a pre-filled entry row visible
 * at all times, which read as a slot somebody had already entered — and could not be removed, because the
 * button beside it said "Hinzufügen".
 */
test('ohne Sprechzeiten steht dort ein Knopf und keine erfundene Zeile', async () => {
  const { container } = renderForm(WRITE, { ...OFFER, events: [] });

  expect(await screen.findByText('Noch keine Sprechzeit hinterlegt.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Erste Sprechzeit anlegen' })).toBeInTheDocument();
  expect(container.querySelectorAll('.slots__row')).toHaveLength(0);
});

test('der Knopf klappt eine Zeile ohne vorbelegte Werte auf', async () => {
  const { container } = renderForm(WRITE, { ...OFFER, events: [] });

  fireEvent.click(await screen.findByRole('button', { name: 'Erste Sprechzeit anlegen' }));

  expect(container.querySelectorAll('.slots__row')).toHaveLength(1);
  expect(container.querySelector('#field-start-new')).toHaveValue('');
  expect(container.querySelector('#field-end-new')).toHaveValue('');
  expect(container.querySelector('#field-day-new')).toHaveValue('');
});

/**
 * The one rule the client owns: the server cannot tell "nothing chosen" from a one-off appointment, since
 * both arrive as a null weekday.
 */
test('ohne Wochentag wird nicht abgeschickt, sondern das Feld markiert', async () => {
  const { container } = renderForm(WRITE, { ...OFFER, events: [] });

  fireEvent.click(await screen.findByRole('button', { name: 'Erste Sprechzeit anlegen' }));
  fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

  expect(screen.getByText('Wochentag oder Einzeltermin wählen')).toBeInTheDocument();
  expect(container.querySelector('#field-day-new').closest('.field')).toHaveClass('field--invalid');
  expect(global.fetch.mock.calls.some(([url]) => String(url).includes('/events'))).toBe(false);
});

test('die Datumsfelder gehören zum Einzeltermin und nur zu ihm', async () => {
  const { container } = renderForm(WRITE, { ...OFFER, events: [] });

  fireEvent.click(await screen.findByRole('button', { name: 'Erste Sprechzeit anlegen' }));
  expect(container.querySelector('#field-from-new')).not.toBeInTheDocument();

  fireEvent.change(container.querySelector('#field-day-new'), { target: { value: 'ONCE' } });
  expect(container.querySelector('#field-from-new')).toBeInTheDocument();
  expect(container.querySelector('#field-to-new')).toBeInTheDocument();

  fireEvent.change(container.querySelector('#field-day-new'), { target: { value: '3' } });
  expect(container.querySelector('#field-from-new')).not.toBeInTheDocument();
});

test('mit CONSULTATION_UPDATE_ANY erscheint das Kästchen', async () => {
  renderForm(['CONSULTATION_READ_ALL', 'CONSULTATION_UPDATE_ANY']);

  await screen.findByDisplayValue('Studienberatung Informatik');
  const checkbox = screen.getByRole('checkbox');
  expect(checkbox).toBeChecked();
  expect(screen.queryByText(/Freigeben setzt/)).not.toBeInTheDocument();
});
