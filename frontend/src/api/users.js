const API_BASE = '/api';

export class ApiError extends Error {
  constructor(message, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.fieldErrors = fieldErrors;
  }
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(
      body?.message || `Anfrage fehlgeschlagen (HTTP ${response.status})`,
      body?.fieldErrors ?? {}
    );
  }

  return response.status === 204 ? null : response.json();
}

export const fetchUsers = () => request('/users');

export const createUser = (user) =>
  request('/users', { method: 'POST', body: JSON.stringify(user) });

export const updateUser = (id, user) =>
  request(`/users/${id}`, { method: 'PUT', body: JSON.stringify(user) });

export const deleteUser = (id) => request(`/users/${id}`, { method: 'DELETE' });

export const fetchAdmin = (username = 'admin') => request(`/admins/${username}`);
