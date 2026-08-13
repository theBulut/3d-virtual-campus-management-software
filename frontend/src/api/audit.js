import { get, query } from './client';

export const fetchAuditLog = (params = {}) => get(`/audit${query(params)}`);

export const fetchAuditEntry = (id) => get(`/audit/${id}`);
