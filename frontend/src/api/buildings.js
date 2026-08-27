import { del, get, post, put } from './client';

/**
 * Buildings have no review workflow — only POIs do. {@code published} is therefore a plain field of the
 * request and not a transition of its own, which is why there is no counterpart to submitPoi/publishPoi
 * here (spec section 4.5).
 */
export const fetchBuildings = () => get('/buildings');

export const fetchBuilding = (id) => get(`/buildings/${id}`);

export const createBuilding = (building) => post('/buildings', building);

export const updateBuilding = (id, building) => put(`/buildings/${id}`, building);

// Refused with 409 while POIs still point at the building — the foreign key deliberately has no
// ON DELETE clause (docs/DECISIONS.md D-16).
export const deleteBuilding = (id) => del(`/buildings/${id}`);
