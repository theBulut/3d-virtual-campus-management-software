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

/**
 * The weekday select has three states, and they have to stay apart: nothing chosen yet is not the same as
 * a one-off appointment. The server cannot tell them apart — for it a null weekday *is* a one-off — so the
 * form carries this sentinel and turns it into null on the way out.
 */
const ONE_OFF = 'ONCE';

/** Deliberately without values: an invented Monday 10:00 reads like a slot somebody already entered. */
const EMPTY_SLOT = {
  dayOfWeek: '',
  startTime: '',
  endTime: '',
  validFrom: '',
  validTo: '',
  roomOverride: '',
  note: '',
};

/** "10:00:00" from the server, "10:00" for an <input type="time">. Both parse back as LocalTime. */
const asTimeInput = (value) => (value ?? '').slice(0, 5);

/**
 * One slot as the form holds it. {@code dirty} marks an edit that has not reached the server: the offer's
 * own save button does not carry the slots — they have their own endpoints — so without the flag a
 * changed row and a saved one look the same, and the change is lost without anybody noticing.
 */
const toSlotRow = (event) => ({
  id: event.id,
  // The API omits null fields (spring.jackson.default-property-inclusion: non_null), so a one-off arrives
  // with no dayOfWeek key at all — undefined, not null. Hence the loose comparison: checking for null
  // alone left a saved appointment showing the placeholder and hid its two date fields.
  dayOfWeek: event.dayOfWeek == null ? ONE_OFF : String(event.dayOfWeek),
  startTime: asTimeInput(event.startTime),
  endTime: asTimeInput(event.endTime),
  validFrom: event.validFrom ?? '',
  validTo: event.validTo ?? '',
  roomOverride: event.roomOverride ?? '',
  note: event.note ?? '',
  dirty: false,
});

/**
 * The fields of one slot, shared by the stored rows and the entry row so the two cannot drift apart.
 * <p>
 * The two dates belong to a one-off appointment only and appear with it: a weekly slot has no date, and
 * two empty date inputs next to every weekday would invite somebody to fill them in.
 *
 * @param onChange a field name yields the handler for that field, matching {@code updateSlot}
 */
function SlotFields({ slot, errors, onChange, name }) {
  return (
    <>
      <FormField label="Wochentag" name={`day-${name}`} error={errors.dayOfWeek}>
        <select
          id={`field-day-${name}`}
          className="field__input"
          value={slot.dayOfWeek}
          onChange={(event) => onChange('dayOfWeek')(event.target.value)}
        >
          <option value="">Wählen …</option>
          {WEEKDAYS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
          <option value={ONE_OFF}>Einzeltermin</option>
        </select>
      </FormField>
      <FormField
        label="von"
        name={`start-${name}`}
        type="time"
        value={slot.startTime}
        onChange={onChange('startTime')}
        error={errors.startTime}
      />
      <FormField
        label="bis"
        name={`end-${name}`}
        type="time"
        value={slot.endTime}
        onChange={onChange('endTime')}
        error={errors.endTime}
      />
      <FormField
        label="Hinweis"
        name={`note-${name}`}
        value={slot.note}
        onChange={onChange('note')}
        error={errors.note}
      />
      {slot.dayOfWeek === ONE_OFF && (
        <div className="slots__dates">
          <FormField
            label="gültig von"
            name={`from-${name}`}
            type="date"
            value={slot.validFrom}
            onChange={onChange('validFrom')}
            error={errors.validFrom}
          />
          <FormField
            label="gültig bis"
            name={`to-${name}`}
            type="date"
            value={slot.validTo}
            onChange={onChange('validTo')}
            error={errors.validTo}
          />
        </div>
      )}
    </>
  );
}

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
  // The entry row exists only while somebody is filling it in, and a new slot reaches the list only once
  // the server has stored it — a row on screen always stands for a row in the database.
  const [adding, setAdding] = useState(false);
  const [newSlotErrors, setNewSlotErrors] = useState({});
  const [rowErrors, setRowErrors] = useState({});
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
        setSlots(loaded.events.map(toSlotRow));
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

  // Empty fields go out as null, not as "": an empty string is no LocalTime, and the request would fail
  // as an unreadable body instead of producing the field error that belongs under the input.
  const slotPayload = (slot) => ({
    dayOfWeek: slot.dayOfWeek === ONE_OFF ? null : Number(slot.dayOfWeek),
    startTime: slot.startTime === '' ? null : slot.startTime,
    endTime: slot.endTime === '' ? null : slot.endTime,
    validFrom: slot.validFrom === '' ? null : slot.validFrom,
    validTo: slot.validTo === '' ? null : slot.validTo,
    roomOverride: slot.roomOverride === '' ? null : slot.roomOverride,
    note: slot.note === '' ? null : slot.note,
  });

  /**
   * The one rule the client owns. Everything else is Bean Validation on the server, which the form only
   * renders — but "nothing chosen yet" is invisible there, because a null weekday is how a legitimate
   * one-off appointment is expressed.
   */
  const missingWeekday = (slot) =>
    slot.dayOfWeek === '' ? { dayOfWeek: 'Wochentag oder Einzeltermin wählen' } : null;

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
        // This button saves the master data only. Saying so beats letting somebody walk away believing a
        // changed opening hour went with it.
        const pending = slots.filter((slot) => slot.dirty).length;
        if (pending > 0) {
          toast.info(
            `${pending === 1 ? 'Eine Sprechzeit hat' : `${pending} Sprechzeiten haben`} noch `
              + 'ungespeicherte Änderungen. Sie werden über „Speichern“ in der jeweiligen '
              + 'Zeile übernommen.',
          );
        }
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
      current.map((slot, position) =>
        position === index ? { ...slot, [field]: value, dirty: true } : slot,
      ),
    );

  const saveSlot = async (index) => {
    const slot = slots[index];
    const missing = missingWeekday(slot);
    if (missing) {
      setRowErrors((current) => ({ ...current, [slot.id]: missing }));
      return;
    }
    setBusy(true);
    try {
      const saved = await updateConsultationEvent(slot.id, slotPayload(slot));
      // The whole row comes from the response, which also clears the dirty flag. Writing back only the
      // start time would leave a row that still claims to be unsaved.
      setSlots((current) =>
        current.map((row, position) => (position === index ? toSlotRow(saved) : row)),
      );
      setRowErrors((current) => ({ ...current, [slot.id]: {} }));
      toast.success('Sprechzeit gespeichert.');
    } catch (apiError) {
      setRowErrors((current) => ({ ...current, [slot.id]: apiError.fieldErrors ?? {} }));
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

  const updateNewSlot = (field) => (value) =>
    setNewSlot((current) => ({ ...current, [field]: value }));

  const startAdding = () => {
    setNewSlot(EMPTY_SLOT);
    setNewSlotErrors({});
    setAdding(true);
  };

  const cancelAdding = () => {
    setNewSlot(EMPTY_SLOT);
    setNewSlotErrors({});
    setAdding(false);
  };

  const addSlot = async () => {
    const missing = missingWeekday(newSlot);
    if (missing) {
      setNewSlotErrors(missing);
      return;
    }
    setBusy(true);
    try {
      const created = await addConsultationEvent(id, slotPayload(newSlot));
      // Only now does the row appear — what is on screen matches what the database holds.
      setSlots((current) => [...current, toSlotRow(created)]);
      cancelAdding();
      toast.success('Sprechzeit hinzugefügt.');
    } catch (apiError) {
      // The entry row stays open with its values, so nothing has to be typed again.
      setNewSlotErrors(apiError.fieldErrors ?? {});
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
              <div
                className={slot.dirty ? 'slots__row slots__row--dirty' : 'slots__row'}
                key={slot.id}
              >
                <SlotFields
                  slot={slot}
                  name={String(slot.id)}
                  errors={rowErrors[slot.id] ?? {}}
                  onChange={(field) => updateSlot(index, field)}
                />
                <div className="slots__actions">
                  {/* Primary exactly while there is something to save — the clearest answer to which
                      button belongs to which row. */}
                  <button
                    type="button"
                    className={slot.dirty ? 'button button--primary' : 'button'}
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

            {adding ? (
              <div className="slots__row">
                <SlotFields
                  slot={newSlot}
                  name="new"
                  errors={newSlotErrors}
                  onChange={updateNewSlot}
                />
                <div className="slots__actions">
                  <button
                    type="button"
                    className="button button--primary"
                    disabled={busy}
                    onClick={addSlot}
                  >
                    Hinzufügen
                  </button>
                  <button
                    type="button"
                    className="button button--ghost"
                    disabled={busy}
                    onClick={cancelAdding}
                  >
                    Abbrechen
                  </button>
                </div>
              </div>
            ) : (
              <Can anyOf={['CONSULTATION_UPDATE_ANY', 'CONSULTATION_UPDATE_OWN']}>
                <button type="button" className="button slots__add" onClick={startAdding}>
                  {slots.length === 0 ? 'Erste Sprechzeit anlegen' : 'Sprechzeit hinzufügen'}
                </button>
              </Can>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
