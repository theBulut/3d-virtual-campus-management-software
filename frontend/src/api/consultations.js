import { del, get, post, put } from './client';

export const fetchConsultations = () => get('/consultations');

export const fetchConsultation = (id) => get(`/consultations/${id}`);

/**
 * {@code published} is only honoured for callers holding CONSULTATION_UPDATE_ANY; for everyone else the
 * server ignores the field silently (docs/DECISIONS.md D-34). The form therefore does not offer it
 * without that permission — a checkbox whose effect disappears on save is worse than no checkbox.
 */
export const createConsultation = (consultation) => post('/consultations', consultation);

export const updateConsultation = (id, consultation) => put(`/consultations/${id}`, consultation);

export const deleteConsultation = (id) => del(`/consultations/${id}`);

// Slots hang off the offer, but are edited one at a time: the offer's own PUT does not carry them, so a
// changed opening hour never needs the whole record to be sent back.
export const addConsultationEvent = (id, event) => post(`/consultations/${id}/events`, event);

export const updateConsultationEvent = (eventId, event) =>
  put(`/consultations/events/${eventId}`, event);

export const deleteConsultationEvent = (eventId) => del(`/consultations/events/${eventId}`);
