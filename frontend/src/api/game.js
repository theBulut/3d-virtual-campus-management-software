import { get, request } from './client';

/**
 * The scene as this account may see it. What comes back depends on the permissions in the token: a
 * player receives published content, an editor additionally receives drafts and submissions, each with
 * its status.
 */
export const fetchScene = () => get('/game/scene');

/** The own progress; null when this account has never played. */
export const fetchGameState = () => get('/game/state');

/**
 * Saves the progress. The document is passed through as text — its format belongs to the Unity client,
 * and the web app has no business parsing it.
 */
export const saveGameState = (state) =>
  request('/game/state', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: typeof state === 'string' ? state : JSON.stringify(state),
  });
