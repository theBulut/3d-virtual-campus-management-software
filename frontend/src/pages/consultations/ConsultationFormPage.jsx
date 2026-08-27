import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchBuildings } from '../../api/buildings';
import {
  addConsultationEvent,
  createConsultation,
  deleteConsultation,
  deleteConsultationEvent,
  fetchConsultation,
  updateConsultation,
  updateConsultationEvent,
} from '../../api/consultations';
import Can from '../../auth/Can';
import { useAuth } from '../../auth/AuthContext';
import FormField from '../../components/ui/FormField';
import PublishedBadge from '../../components/ui/PublishedBadge';
import { useToast } from '../../components/ui/Toast';

const WEEKDAYS = [
  ['1', 'Montag'],
  ['2', 'Dienstag'],
  ['3', 'Mittwoch'],
  ['4', 'Donnerstag'],
  ['5', 'Freitag'],
  ['6', 'Samstag'],
  ['7', 'Sonntag'],
];

const EMPTY = {
  titleDe: '',
  titleEn: '',
  descriptionDe: '',
  descriptionEn: '',
  organisation: '',
  buildingId: '',
  room: '',
  contactEmail: '',
  published: false,
};

const EMPTY_SLOT = { dayOfWeek: '1', startTime: '10:00', endTime: '12:00', roomOverride: '', note: '' };

/** "10:00:00" from the server, "10:00" for an <input type="time">. Both parse back as LocalTime. */
const asTimeInput = (value) => (value ?? '').slice(0, 5);

/**
 * Create and edit a consultation offer together with its weekly slots.
 * <p>
 * Two things are deliberately not symmetrical with the POI editor. Releasing is a checkbox rather than a
 * transition, because offers have no review workflow; and the checkbox only appears for holders of
 * CONSULTATION_UPDATE_ANY, since for everybody else the server ignores the field without saying so
 * (docs/DECISIONS.md D-34). A control whose effect vanishes on save would look like a bug.
 * <p>
 * Slots are saved one at a time against their own endpoints, so they only exist once the offer does.
 */
export default function ConsultationFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { hasPermission } = useAuth();

  const isNew = id === undefined;
  const [offer, setOffer] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [buildings, setBuildings] = useState([]);
  const [slots, setSlots] = useState([]);
  const [newSlot, setNewSlot] = useState(EMPTY_SLOT);
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const mayPublish = hasPermission('CONSULTATION_UPDATE_ANY');

  const load = useCallback(() => {
    if (isNew) {
      return;
    }
    fetchConsultation(id)
      .then((loaded) => {
        setOffer(loaded);
        setForm({
          titleDe: loaded.titleDe,
          titleEn: loaded.titleEn ?? '',
          descriptionDe: loaded.descriptionDe ?? '',
          descriptionEn: loaded.descriptionEn ?? '',
          organisation: loaded.organisation,
          buildingId: loaded.buildingId ?? '',
          room: loaded.room ?? '',
          contactEmail: loaded.contactEmail ?? '',
          published: loaded.published,
        });
        setSlots(
          loaded.events.map((event) => ({
            id: event.id,
            dayOfWeek: event.dayOfWeek === null ? '' : String(event.dayOfWeek),
            startTime: asTimeInput(event.startTime),
            endTime: asTimeInput(event.endTime),
            roomOverride: event.roomOverride ?? '',
            note: event.note ?? '',
          })),
        );
      })
      .catch(setError);
  }, [id, isNew]);

  useEffect(load, [load]);

  useEffect(() => {
    fetchBuildings()
      .then(setBuildings)
      .catch(() => setBuildings([]));
  }, []);

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }
  if (!isNew && !offer) {
    return <p className="page__loading">Angebot wird geladen …</p>;
  }

  const update = (field) => (value) => setForm((current) => ({ ...current, [field]: value }));

  const payload = () => ({
    ...form,
    buildingId: form.buildingId === '' ? null : Number(form.buildingId),
    // Sent regardless; the server drops it without CONSULTATION_UPDATE_ANY, and repeating that rule
    // here would be a second place to keep in step.
    published: form.published,
  });

  const slotPayload = (slot) => ({
    dayOfWeek: slot.dayOfWeek === '' ? null : Number(slot.dayOfWeek),
    startTime: slot.startTime,
    endTime: slot.endTime,
    roomOverride: slot.roomOverride === '' ? null : slot.roomOverride,
    note: slot.note === '' ? null : slot.note,
  });

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFieldErrors({});
    try {
      if (isNew) {
        const created = await createConsultation(payload());
        toast.success('Angebot angelegt. Sprechzeiten lassen sich jetzt eintragen.');
        navigate(`/admin/consultations/${created.id}`, { replace: true });
      } else {
        setOffer(await updateConsultation(id, payload()));
        toast.success('Änderungen gespeichert.');
      }
    } catch (apiError) {
      setFieldErrors(apiError.fieldErrors ?? {});
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!window.confirm(`Angebot „${form.titleDe}" wirklich löschen?`)) {
      return;
    }
    setBusy(true);
    try {
      await deleteConsultation(id);
      toast.success('Angebot gelöscht.');
      navigate('/admin/consultations');
    } catch (apiError) {
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  const updateSlot = (index, field) => (value) =>
    setSlots((current) =>
      current.map((slot, position) => (position === index ? { ...slot, [field]: value } : slot)),
    );

  const saveSlot = async (index) => {
    setBusy(true);
    try {
      const saved = await updateConsultationEvent(slots[index].id, slotPayload(slots[index]));
      updateSlot(index, 'startTime')(asTimeInput(saved.startTime));
      toast.success('Sprechzeit gespeichert.');
    } catch (apiError) {
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  const removeSlot = async (index) => {
    setBusy(true);
    try {
      await deleteConsultationEvent(slots[index].id);
      setSlots((current) => current.filter((_, position) => position !== index));
      toast.success('Sprechzeit entfernt.');
    } catch (apiError) {
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  const addSlot = async () => {
    setBusy(true);
    try {
      const created = await addConsultationEvent(id, slotPayload(newSlot));
      setSlots((current) => [
        ...current,
        {
          id: created.id,
          dayOfWeek: created.dayOfWeek === null ? '' : String(created.dayOfWeek),
          startTime: asTimeInput(created.startTime),
          endTime: asTimeInput(created.endTime),
          roomOverride: created.roomOverride ?? '',
          note: created.note ?? '',
        },
      ]);
      setNewSlot(EMPTY_SLOT);
      toast.success('Sprechzeit hinzugefügt.');
    } catch (apiError) {
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">{isNew ? 'Beratungsangebot anlegen' : offer.titleDe}</h1>
        {offer && <PublishedBadge published={offer.published} />}
      </div>

      <form className="form form--wide" onSubmit={save}>
        <FormField
          label="Titel (deutsch)"
          name="titleDe"
          value={form.titleDe}
          onChange={update('titleDe')}
          error={fieldErrors.titleDe}
          required
        />
        <FormField
          label="Titel (englisch)"
          name="titleEn"
          value={form.titleEn}
          onChange={update('titleEn')}
          error={fieldErrors.titleEn}
        />
        <FormField label="Beschreibung (deutsch)" name="descriptionDe" error={fieldErrors.descriptionDe}>
          <textarea
            id="field-descriptionDe"
            className="field__input"
            rows={3}
            value={form.descriptionDe}
            onChange={(event) => update('descriptionDe')(event.target.value)}
          />
        </FormField>
        <FormField label="Beschreibung (englisch)" name="descriptionEn" error={fieldErrors.descriptionEn}>
          <textarea
            id="field-descriptionEn"
            className="field__input"
            rows={3}
            value={form.descriptionEn}
            onChange={(event) => update('descriptionEn')(event.target.value)}
          />
        </FormField>

        <FormField
          label="Einrichtung"
          name="organisation"
          value={form.organisation}
          onChange={update('organisation')}
          error={fieldErrors.organisation}
          required
        />

        <FormField label="Gebäude" name="buildingId" error={fieldErrors.buildingId}>
          <select
            id="field-buildingId"
            className="field__input"
            value={form.buildingId}
            onChange={(event) => update('buildingId')(event.target.value)}
          >
            <option value="">— kein Gebäude —</option>
            {buildings.map((building) => (
              <option key={building.id} value={building.id}>
                {building.code} · {building.nameDe}
              </option>
            ))}
          </select>
        </FormField>

        <div className="coordinates">
          <FormField
            label="Raum"
            name="room"
            value={form.room}
            onChange={update('room')}
            error={fieldErrors.room}
          />
          <FormField
            label="Kontakt-E-Mail"
            name="contactEmail"
            type="email"
            value={form.contactEmail}
            onChange={update('contactEmail')}
            error={fieldErrors.contactEmail}
          />
        </div>

        {mayPublish ? (
          <FormField label="Sichtbarkeit" name="published" error={fieldErrors.published}>
            <label className="checkbox">
              <input
                id="field-published"
                type="checkbox"
                checked={form.published}
                onChange={(event) => update('published')(event.target.checked)}
              />
              Veröffentlicht — im Campus und über die öffentliche Schnittstelle sichtbar
            </label>
          </FormField>
        ) : (
          <p className="page__hint">
            Freigeben setzt <code>CONSULTATION_UPDATE_ANY</code> voraus. Inhalte pflegen darf dieses
            Konto, veröffentlichen nicht — dieselbe Trennung wie bei den POIs.
          </p>
        )}

        <div className="page__actions">
          <Can anyOf={['CONSULTATION_CREATE', 'CONSULTATION_UPDATE_ANY', 'CONSULTATION_UPDATE_OWN']}>
            <button type="submit" className="button button--primary" disabled={busy}>
              Speichern
            </button>
          </Can>

          {!isNew && (
            <Can perm="CONSULTATION_DELETE">
              <button type="button" className="button" disabled={busy} onClick={remove}>
                Löschen
              </button>
            </Can>
          )}

          <button
            type="button"
            className="button button--ghost"
            onClick={() => navigate('/admin/consultations')}
          >
            Zurück
          </button>
        </div>
      </form>

      <section className="panel">
        <h2 className="panel__title">Sprechzeiten</h2>

        {isNew ? (
          <p className="slots__empty">
            Sprechzeiten lassen sich eintragen, sobald das Angebot angelegt ist — sie hängen an seiner
            Kennung.
          </p>
        ) : (
          <div className="slots">
            {slots.length === 0 && (
              <p className="slots__empty">Noch keine Sprechzeit hinterlegt.</p>
            )}

            {slots.map((slot, index) => (
              <div className="slots__row" key={slot.id}>
                <FormField label="Wochentag" name={`day-${slot.id}`}>
                  <select
                    id={`field-day-${slot.id}`}
                    className="field__input"
                    value={slot.dayOfWeek}
                    onChange={(event) => updateSlot(index, 'dayOfWeek')(event.target.value)}
                  >
                    <option value="">Einzeltermin</option>
                    {WEEKDAYS.map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField
                  label="von"
                  name={`start-${slot.id}`}
                  type="time"
                  value={slot.startTime}
                  onChange={updateSlot(index, 'startTime')}
                />
                <FormField
                  label="bis"
                  name={`end-${slot.id}`}
                  type="time"
                  value={slot.endTime}
                  onChange={updateSlot(index, 'endTime')}
                />
                <FormField
                  label="Hinweis"
                  name={`note-${slot.id}`}
                  value={slot.note}
                  onChange={updateSlot(index, 'note')}
                />
                <div className="page__actions">
                  <button
                    type="button"
                    className="button"
                    disabled={busy}
                    onClick={() => saveSlot(index)}
                  >
                    Speichern
                  </button>
                  <button
                    type="button"
                    className="button button--ghost"
                    disabled={busy}
                    onClick={() => removeSlot(index)}
                  >
                    Entfernen
                  </button>
                </div>
              </div>
            ))}

            <div className="slots__row">
              <FormField label="Wochentag" name="day-new">
                <select
                  id="field-day-new"
                  className="field__input"
                  value={newSlot.dayOfWeek}
                  onChange={(event) =>
                    setNewSlot((current) => ({ ...current, dayOfWeek: event.target.value }))
                  }
                >
                  <option value="">Einzeltermin</option>
                  {WEEKDAYS.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField
                label="von"
                name="start-new"
                type="time"
                value={newSlot.startTime}
                onChange={(value) => setNewSlot((current) => ({ ...current, startTime: value }))}
              />
              <FormField
                label="bis"
                name="end-new"
                type="time"
                value={newSlot.endTime}
                onChange={(value) => setNewSlot((current) => ({ ...current, endTime: value }))}
              />
              <FormField
                label="Hinweis"
                name="note-new"
                value={newSlot.note}
                onChange={(value) => setNewSlot((current) => ({ ...current, note: value }))}
              />
              <div className="page__actions">
                <button type="button" className="button button--primary" disabled={busy} onClick={addSlot}>
                  Hinzufügen
                </button>
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
