import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  archivePoi,
  createPoi,
  fetchBuildings,
  fetchPoi,
  publishPoi,
  rejectPoi,
  submitPoi,
  updatePoi,
} from '../../api/pois';
import Can from '../../auth/Can';
import { useAuth } from '../../auth/AuthContext';
import FormField from '../../components/ui/FormField';
import StatusBadge from '../../components/ui/StatusBadge';
import { useToast } from '../../components/ui/Toast';

const CATEGORIES = ['LECTURE_HALL', 'LIBRARY', 'CAFETERIA', 'SERVICE', 'LAB', 'OTHER'];

const EMPTY = {
  nameDe: '',
  nameEn: '',
  descriptionDe: '',
  descriptionEn: '',
  category: 'LECTURE_HALL',
  buildingId: '',
  positionX: 0,
  positionY: 0,
  positionZ: 0,
};

/**
 * The content editor of spec section 6, with the context sensitive action bar that makes the separation
 * of duties visible: <em>Zur Prüfung einreichen</em> appears for the author of a draft,
 * <em>Freigeben</em> and <em>Zurückweisen</em> only for holders of POI_PUBLISH, and only while the POI
 * is under review.
 * <p>
 * Which button is shown follows the same two rules the server enforces (permission plus state), so the
 * interface never offers an action that would come back as 403 or 422.
 */
export default function PoiEditorPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { user, hasPermission } = useAuth();

  const isNew = id === undefined;
  const [poi, setPoi] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [buildings, setBuildings] = useState([]);
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    if (isNew) {
      return;
    }
    fetchPoi(id)
      .then((loaded) => {
        setPoi(loaded);
        setForm({
          nameDe: loaded.nameDe,
          nameEn: loaded.nameEn ?? '',
          descriptionDe: loaded.descriptionDe ?? '',
          descriptionEn: loaded.descriptionEn ?? '',
          category: loaded.category,
          buildingId: loaded.buildingId ?? '',
          positionX: loaded.positionX,
          positionY: loaded.positionY,
          positionZ: loaded.positionZ,
        });
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
  if (!isNew && !poi) {
    return <p className="page__loading">POI wird geladen …</p>;
  }

  const update = (field) => (value) => setForm((current) => ({ ...current, [field]: value }));

  const payload = () => ({
    ...form,
    buildingId: form.buildingId === '' ? null : Number(form.buildingId),
    positionX: Number(form.positionX),
    positionY: Number(form.positionY),
    positionZ: Number(form.positionZ),
  });

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFieldErrors({});
    try {
      if (isNew) {
        const created = await createPoi(payload());
        toast.success('POI angelegt — im Status Entwurf.');
        navigate(`/admin/pois/${created.id}`, { replace: true });
      } else {
        setPoi(await updatePoi(id, payload()));
        toast.success('Änderungen gespeichert.');
      }
    } catch (apiError) {
      setFieldErrors(apiError.fieldErrors ?? {});
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  const transition = async (action, message) => {
    setBusy(true);
    try {
      setPoi(await action());
      toast.success(message);
    } catch (apiError) {
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  const reject = () => {
    const note = window.prompt('Begründung für die Zurückweisung (Pflichtfeld):');
    if (note === null) {
      return;
    }
    transition(() => rejectPoi(id, note), 'POI zurückgewiesen und an die Bearbeitung zurückgegeben.');
  };

  // Ownership as the server sees it: creator or assignee, and only before publication. Whoever holds
  // POI_UPDATE_ANY is not bound by either half — the same two rules as in the @PreAuthorize expression.
  const isOwner = poi && (poi.createdById === user.id || poi.assignedToId === user.id);
  const editable =
    isNew ||
    hasPermission('POI_UPDATE_ANY') ||
    (poi && isOwner && ['DRAFT', 'IN_REVIEW'].includes(poi.status));

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">{isNew ? 'POI anlegen' : poi.nameDe}</h1>
        {poi && <StatusBadge status={poi.status} />}
      </div>

      {poi?.reviewNote && (
        <div className="banner banner--warning">
          <strong>Zurückgewiesen:</strong> {poi.reviewNote}
        </div>
      )}

      <form className="form" onSubmit={save}>
        <FormField
          label="Name (deutsch)"
          name="nameDe"
          value={form.nameDe}
          onChange={update('nameDe')}
          error={fieldErrors.nameDe}
          required
        />
        <FormField
          label="Name (englisch)"
          name="nameEn"
          value={form.nameEn}
          onChange={update('nameEn')}
          error={fieldErrors.nameEn}
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

        <FormField label="Kategorie" name="category" error={fieldErrors.category} required>
          <select
            id="field-category"
            className="field__input"
            value={form.category}
            onChange={(event) => update('category')(event.target.value)}
          >
            {CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </select>
        </FormField>

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
          <FormField label="X" name="positionX" type="number" value={form.positionX} onChange={update('positionX')} error={fieldErrors.positionX} />
          <FormField label="Y" name="positionY" type="number" value={form.positionY} onChange={update('positionY')} error={fieldErrors.positionY} />
          <FormField label="Z" name="positionZ" type="number" value={form.positionZ} onChange={update('positionZ')} error={fieldErrors.positionZ} />
        </div>

        <div className="page__actions">
          <Can anyOf={['POI_UPDATE_ANY', 'POI_UPDATE_OWN', 'POI_CREATE']}>
            <button type="submit" className="button button--primary" disabled={busy || !editable}>
              Speichern
            </button>
          </Can>

          {!isNew && (
            <>
              {poi.status === 'DRAFT' && isOwner && (
                <Can perm="POI_SUBMIT_REVIEW">
                  <button
                    type="button"
                    className="button"
                    disabled={busy}
                    onClick={() => transition(() => submitPoi(id), 'Zur Prüfung eingereicht.')}
                  >
                    Zur Prüfung einreichen
                  </button>
                </Can>
              )}

              {poi.status === 'IN_REVIEW' && (
                <Can perm="POI_PUBLISH">
                  <button
                    type="button"
                    className="button button--primary"
                    disabled={busy}
                    onClick={() => transition(() => publishPoi(id), 'POI veröffentlicht.')}
                  >
                    Freigeben
                  </button>
                  <button type="button" className="button" disabled={busy} onClick={reject}>
                    Zurückweisen
                  </button>
                </Can>
              )}

              {poi.status === 'PUBLISHED' && (
                <Can perm="POI_PUBLISH">
                  <button
                    type="button"
                    className="button"
                    disabled={busy}
                    onClick={() => transition(() => archivePoi(id), 'POI archiviert.')}
                  >
                    Archivieren
                  </button>
                </Can>
              )}
            </>
          )}

          <button type="button" className="button button--ghost" onClick={() => navigate('/admin/pois')}>
            Zurück
          </button>
        </div>
      </form>

      {!isNew && !editable && (
        <p className="page__hint">
          Dieser Eintrag ist für Ihr Konto nicht bearbeitbar — entweder gehört er einem anderen Konto
          oder er ist bereits veröffentlicht. Die Prüfung findet serverseitig in
          <code> @poiSecurity.canEdit</code> statt.
        </p>
      )}
    </div>
  );
}
