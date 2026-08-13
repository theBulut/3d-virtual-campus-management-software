import { ApiError, request, setSessionLostHandler, tokenStore } from './client';

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: () => Promise.resolve(body),
  };
}

beforeEach(() => {
  localStorage.clear();
  setSessionLostHandler(() => {});
});

afterEach(() => {
  jest.resetAllMocks();
});

test('hängt den Bearer-Header an, sobald ein Token vorliegt', async () => {
  tokenStore.set('access-1', 'refresh-1');
  global.fetch = jest.fn(() => Promise.resolve(jsonResponse(200, { ok: true })));

  await request('/users');

  expect(global.fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer access-1');
});

test('erneuert nach 401 genau einmal und wiederholt die Anfrage', async () => {
  tokenStore.set('stale', 'refresh-1');
  global.fetch = jest
    .fn()
    .mockResolvedValueOnce(jsonResponse(401, { code: 'TOKEN_STALE' }))
    .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'fresh', refreshToken: 'refresh-2' }))
    .mockResolvedValueOnce(jsonResponse(200, { id: 7 }));

  const result = await request('/users/7');

  expect(result).toEqual({ id: 7 });
  expect(global.fetch).toHaveBeenCalledTimes(3);
  expect(global.fetch.mock.calls[1][0]).toBe('/api/auth/refresh');
  // Rotation: the client keeps the new pair, the old refresh token is dead.
  expect(tokenStore.access()).toBe('fresh');
  expect(tokenStore.refresh()).toBe('refresh-2');
  expect(global.fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer fresh');
});

test('gibt nach einem zweiten 401 auf und meldet den Sitzungsverlust', async () => {
  tokenStore.set('stale', 'refresh-1');
  const sessionLost = jest.fn();
  setSessionLostHandler(sessionLost);
  global.fetch = jest
    .fn()
    .mockResolvedValueOnce(jsonResponse(401, {}))
    .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'fresh', refreshToken: 'refresh-2' }))
    .mockResolvedValueOnce(jsonResponse(401, { code: 'TOKEN_STALE', message: 'Sitzung abgelaufen.' }));

  await expect(request('/users')).rejects.toThrow('Sitzung abgelaufen.');

  // Exactly one retry — not a loop against a token the server has already refused.
  expect(global.fetch).toHaveBeenCalledTimes(3);
  expect(sessionLost).toHaveBeenCalled();
  expect(tokenStore.access()).toBeNull();
});

test('reicht Fehlercode und Feldfehler des Backends durch', async () => {
  tokenStore.set('access-1', 'refresh-1');
  global.fetch = jest.fn(() =>
    Promise.resolve(
      jsonResponse(403, {
        code: 'ROLE_NOT_GRANTABLE',
        message: 'Diese Rolle dürfen Sie nicht vergeben.',
        fieldErrors: { roleName: 'unzulässig' },
      }),
    ),
  );

  const error = await request('/users/1/roles', { method: 'POST', body: {} }).catch((e) => e);

  expect(error).toBeInstanceOf(ApiError);
  expect(error.status).toBe(403);
  expect(error.code).toBe('ROLE_NOT_GRANTABLE');
  expect(error.message).toBe('Diese Rolle dürfen Sie nicht vergeben.');
  expect(error.fieldErrors.roleName).toBe('unzulässig');
});

test('erneuert nicht, wenn gar kein Refresh-Token vorliegt', async () => {
  global.fetch = jest.fn(() => Promise.resolve(jsonResponse(401, {})));

  await expect(request('/users')).rejects.toBeInstanceOf(ApiError);

  expect(global.fetch).toHaveBeenCalledTimes(1);
});
