import { del, get, patch, post, put, query } from './client';

export const fetchUsers = (params = {}) => get(`/users${query(params)}`);

export const fetchUser = (id) => get(`/users/${id}`);

export const createUser = (user) => post('/users', user);

export const updateUser = (id, user) => put(`/users/${id}`, user);

export const setUserActive = (id, active) => patch(`/users/${id}/status`, { active });

export const deleteUser = (id) => del(`/users/${id}`);

export const assignRole = (id, roleName) => post(`/users/${id}/roles`, { roleName });

export const revokeRole = (id, roleName) => del(`/users/${id}/roles/${roleName}`);

/**
 * The roles the signed-in account may hand out. The dropdown in RoleAssignPanel is filled from here and
 * from nowhere else — the grant set lives on the server (spec section 1.4).
 */
export const fetchGrantableRoles = () => get('/users/me/grantable-roles');
