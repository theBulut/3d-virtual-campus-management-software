import { get } from './client';

export const fetchRoles = () => get('/roles');

export const fetchRole = (name) => get(`/roles/${name}`);

/** The full permission matrix, exactly as the backend enforces it (FA-05). */
export const fetchMatrix = () => get('/roles/matrix');

export const fetchPermissions = () => get('/permissions');
