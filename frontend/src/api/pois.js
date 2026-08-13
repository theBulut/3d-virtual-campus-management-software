import { del, get, patch, post, put, query } from './client';

export const fetchPois = (params = {}) => get(`/pois${query(params)}`);

export const fetchPoi = (id) => get(`/pois/${id}`);

export const createPoi = (poi) => post('/pois', poi);

export const updatePoi = (id, poi) => put(`/pois/${id}`, poi);

export const deletePoi = (id) => del(`/pois/${id}`);

// One call per transition, mirroring the state machine of spec section 4.5. A plain save can never
// change the status — that separation is the point (FA-11).
export const submitPoi = (id) => post(`/pois/${id}/submit`);
export const publishPoi = (id) => post(`/pois/${id}/publish`);
export const rejectPoi = (id, reviewNote) => post(`/pois/${id}/reject`, { reviewNote });
export const archivePoi = (id) => post(`/pois/${id}/archive`);

export const assignPoi = (id, userId) => patch(`/pois/${id}/assignee`, { userId });

export const fetchBuildings = () => get('/buildings');
