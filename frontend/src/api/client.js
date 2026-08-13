const API_BASE = '/api';

const ACCESS_TOKEN_KEY = 'campus.accessToken';
const REFRESH_TOKEN_KEY = 'campus.refreshToken';

/**
 * A failed request, carrying what the backend's ApiError says (spec section 4.7): the machine readable
 * code for logic, the German message for the user, and the field errors for forms.
 */
export class ApiError extends Error {
  constructor(status, code, message, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_TOKEN_KEY),
  refresh: () => localStorage.getItem(REFRESH_TOKEN_KEY),
  set(accessToken, refreshToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

/** Called when even the refresh fails — set by AuthContext so the client stays free of React. */
let onSessionLost = () => {};

export function setSessionLostHandler(handler) {
  onSessionLost = handler;
}

/**
 * Runs while a refresh is in flight. Without it, three parallel 401s would trigger three refreshes, and
 * the rotation would invalidate the tokens of the other two.
 */
let refreshInFlight = null;

async function send(path, { method = 'GET', body, headers = {}, auth = true } = {}) {
  const requestHeaders = { ...headers };
  if (body !== undefined && !(body instanceof FormData)) {
    requestHeaders['Content-Type'] = 'application/json';
  }
  const token = tokenStore.access();
  if (auth && token) {
    requestHeaders.Authorization = `Bearer ${token}`;
  }

  // A string body is passed through unchanged: the game state is JSON that belongs to the client, and
  // running it through JSON.stringify again would wrap it in quotes.
  const payload = body === undefined || body instanceof FormData || typeof body === 'string'
    ? body
    : JSON.stringify(body);

  return fetch(`${API_BASE}${path}`, {
    method,
    headers: requestHeaders,
    body: payload,
  });
}

async function toError(response) {
  const body = await response.json().catch(() => null);
  return new ApiError(
    response.status,
    body?.code ?? 'UNKNOWN',
    body?.message ?? `Die Anfrage ist fehlgeschlagen (HTTP ${response.status}).`,
    body?.fieldErrors ?? {},
  );
}

async function refreshTokens() {
  const refreshToken = tokenStore.refresh();
  if (!refreshToken) {
    return false;
  }
  const response = await send('/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
    auth: false,
  });
  if (!response.ok) {
    return false;
  }
  const tokens = await response.json();
  // The backend rotates on every refresh: the old refresh token is dead from here on (FA-02).
  tokenStore.set(tokens.accessToken, tokens.refreshToken);
  return true;
}

/**
 * One request, and on 401 exactly one retry after a refresh.
 * <p>
 * Only once: if the second attempt is refused as well, the session is really over — retrying in a loop
 * would hammer the server with a token it has already rejected.
 */
export async function request(path, options = {}) {
  let response = await send(path, options);

  if (response.status === 401 && options.auth !== false && tokenStore.refresh()) {
    refreshInFlight = refreshInFlight ?? refreshTokens().finally(() => {
      refreshInFlight = null;
    });
    const refreshed = await refreshInFlight;
    if (refreshed) {
      response = await send(path, options);
    }
    if (!refreshed || response.status === 401) {
      tokenStore.clear();
      onSessionLost();
      throw await toError(response);
    }
  }

  if (!response.ok) {
    throw await toError(response);
  }
  if (response.status === 204) {
    return null;
  }
  const contentType = response.headers.get('Content-Type') ?? '';
  return contentType.includes('application/json') ? response.json() : response.text();
}

export const get = (path) => request(path);
export const post = (path, body) => request(path, { method: 'POST', body });
export const put = (path, body) => request(path, { method: 'PUT', body });
export const patch = (path, body) => request(path, { method: 'PATCH', body });
export const del = (path) => request(path, { method: 'DELETE' });

/** Builds a query string, leaving out everything the caller did not set. */
export function query(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, value);
    }
  });
  const rendered = search.toString();
  return rendered ? `?${rendered}` : '';
}
