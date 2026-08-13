import { get, post, put, request, tokenStore } from './client';

/** The identifier may be a username or a mail address; the backend accepts both. */
export const login = (username, password) =>
  request('/auth/login', { method: 'POST', body: { username, password }, auth: false });

/**
 * Self-registration. Answers with the same token pair as a login, so the new account goes straight into
 * the game — it receives the role EXTERNE_PERSON and nothing else.
 */
export const register = (account) =>
  request('/auth/register', { method: 'POST', body: account, auth: false });

export const me = () => get('/auth/me');

export const updateProfile = (profile) => put('/auth/me', profile);

export const changePassword = (currentPassword, newPassword) =>
  post('/auth/me/password', { currentPassword, newPassword });

/** Ends this session; the refresh token goes along so both ids reach the blacklist (D-19). */
export const logout = () => post('/auth/logout', { refreshToken: tokenStore.refresh() });

/** Ends every session of the account, on every device (D-24). */
export const logoutEverywhere = () => post('/auth/logout-all');
