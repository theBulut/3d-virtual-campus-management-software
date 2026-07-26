import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';

const ADMIN = { id: 1, username: 'admin', displayName: 'Campus Administrator' };
const USERS = [
  { id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@campus.example' },
];

function jsonResponse(body, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  });
}

beforeEach(() => {
  global.fetch = jest.fn((url, options = {}) => {
    const method = options.method ?? 'GET';
    if (url === '/api/admins/admin') return jsonResponse(ADMIN);
    if (url === '/api/users' && method === 'POST') {
      return jsonResponse({ id: 2, ...JSON.parse(options.body) }, 201);
    }
    if (url === '/api/users' && method === 'GET') return jsonResponse(USERS);
    return jsonResponse({ message: `unexpected ${method} ${url}` }, 500);
  });
});

/** The admin badge only appears once the mount effect settled. */
async function renderApp() {
  render(<App />);
  await screen.findByText('Campus Administrator');
}

test('renders heading and the signed-in admin', async () => {
  await renderApp();
  expect(
    screen.getByRole('heading', { name: /3D Virtual Campus Management Software/i })
  ).toBeInTheDocument();
});

test('lists all users on click', async () => {
  await renderApp();
  fireEvent.click(screen.getByRole('button', { name: 'Alle User anzeigen' }));

  expect(await screen.findByText('ada@campus.example')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'User (1)' })).toBeInTheDocument();
});

test('creates a user through the editor', async () => {
  await renderApp();
  fireEvent.click(screen.getByRole('button', { name: '+ Neuer User' }));

  fireEvent.change(screen.getByLabelText('Vorname'), { target: { value: 'Grace' } });
  fireEvent.change(screen.getByLabelText('Nachname'), { target: { value: 'Hopper' } });
  fireEvent.change(screen.getByLabelText('E-Mail'), {
    target: { value: 'grace@campus.example' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }));

  await waitFor(() =>
    expect(global.fetch).toHaveBeenCalledWith(
      '/api/users',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          firstName: 'Grace',
          lastName: 'Hopper',
          email: 'grace@campus.example',
        }),
      })
    )
  );
  // Saving closes the editor and reloads the table.
  expect(await screen.findByText('ada@campus.example')).toBeInTheDocument();
});

test('prefills the editor when editing an existing user', async () => {
  await renderApp();
  fireEvent.click(screen.getByRole('button', { name: 'Alle User anzeigen' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }));

  expect(screen.getByRole('heading', { name: 'User #1 bearbeiten' })).toBeInTheDocument();
  expect(screen.getByLabelText('Vorname')).toHaveValue('Ada');
  expect(screen.getByLabelText('E-Mail')).toHaveValue('ada@campus.example');
});

test('shows field errors returned by the backend', async () => {
  global.fetch = jest.fn((url, options = {}) => {
    if (url === '/api/admins/admin') return jsonResponse(ADMIN);
    if ((options.method ?? 'GET') === 'POST') {
      return jsonResponse(
        {
          message: 'Validation failed',
          fieldErrors: { email: 'Email must be a valid address' },
        },
        400
      );
    }
    return jsonResponse(USERS);
  });

  await renderApp();
  fireEvent.click(screen.getByRole('button', { name: '+ Neuer User' }));
  fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }));

  expect(await screen.findByText('Email must be a valid address')).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('Validation failed');
});
