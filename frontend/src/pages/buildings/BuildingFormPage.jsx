import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  createBuilding,
  deleteBuilding,
  fetchBuilding,
  updateBuilding,
} from '../../api/buildings';
import Can from '../../auth/Can';
import { useAuth } from '../../auth/AuthContext';
import FormField from '../../components/ui/FormField';
import PublishedBadge from '../../components/ui/PublishedBadge';
import { useToast } from '../../components/ui/Toast';

const EMPTY = {
  code: '',
  nameDe: '',
  nameEn: '',
  street: '',
  postalCode: '',
  city: 'Darmstadt',
  latitude: '',
  longitude: '',
  modelRef: '',
  positionX: 0,
  positionY: 0,
  positionZ: 0,
  rotationY: 0,
  published: false,
};

/**
 * Create and edit a building.
 * <p>
 * No review workflow and therefore no action bar: a building is published by a checkbox, because only
 * POIs pass through the four states of spec section 4.5. Which is also why {@code BUILDING_UPDATE} is a
 * single permission with no <em>own</em> variant — buildings have no author.
 */
export default function BuildingFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { hasPermission } = useAuth();

  const isNew = id === undefined;
  const [building, setBuilding] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    if (isNew) {
      return;
    }
    fetchBuilding(id)
      .then((loaded) => {
        setBuilding(loaded);
        setForm({
          code: loaded.code,
          nameDe: loaded.nameDe,
          nameEn: loaded.nameEn ?? '',
          street: loaded.street ?? '',
          postalCode: loaded.postalCode ?? '',
          city: loaded.city ?? '',
          latitude: loaded.latitude ?? '',
          longitude: loaded.longitude ?? '',
          modelRef: loaded.modelRef ?? '',
          positionX: loaded.positionX,
          positionY: loaded.positionY,
          positionZ: loaded.positionZ,
          rotationY: loaded.rotationY,
          published: loaded.published,
        });
      })
      .catch(setError);
  }, [id, isNew]);

  useEffect(load, [load]);

  if (error) {
    return <p className="page__error">{error.message}</p>;
  }
  if (!isNew && !building) {
    return <p className="page__loading">Gebäude wird geladen …</p>;
  }

  const update = (field) => (value) => setForm((current) => ({ ...current, [field]: value }));

  const number = (value) => (value === '' || value === null ? null : Number(value));

  const payload = () => ({
    ...form,
    latitude: number(form.latitude),
    longitude: number(form.longitude),
    positionX: Number(form.positionX),
    positionY: Number(form.positionY),
    positionZ: Number(form.positionZ),
    rotationY: Number(form.rotationY),
  });

  const editable = isNew ? hasPermission('BUILDING_CREATE') : hasPermission('BUILDING_UPDATE');

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setFieldErrors({});
    try {
      if (isNew) {
        const created = await createBuilding(payload());
        toast.success('Gebäude angelegt.');
        navigate(`/admin/buildings/${created.id}`, { replace: true });
      } else {
        setBuilding(await updateBuilding(id, payload()));
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
    if (!window.confirm(`Gebäude ${form.code} wirklich löschen?`)) {
      return;
    }
    setBusy(true);
    try {
      await deleteBuilding(id);
      toast.success('Gebäude gelöscht.');
      navigate('/admin/buildings');
    } catch (apiError) {
      // 409 while POIs still reference it — the message from the server names the reason.
      toast.fromError(apiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">{isNew ? 'Gebäude anlegen' : building.nameDe}</h1>
        {building && <PublishedBadge published={building.published} />}
      </div>

      <form className="form" onSubmit={save}>
        <FormField
          label="Gebäudeschlüssel"
          name="code"
          value={form.code}
          onChange={update('code')}
          error={fieldErrors.code}
          hint="Amtliche Schreibweise, etwa S1|03. Die 3D-Szene findet das Modell darüber (S103)."
          required
        />
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

        <FormField
          label="Straße"
          name="street"
          value={form.street}
          onChange={update('street')}
          error={fieldErrors.street}
        />
        <div className="coordinates">
          <FormField
            label="PLZ"
            name="postalCode"
            value={form.postalCode}
            onChange={update('postalCode')}
            error={fieldErrors.postalCode}
          />
          <FormField
            label="Ort"
            name="city"
            value={form.city}
            onChange={update('city')}
            error={fieldErrors.city}
          />
        </div>

        <div className="coordinates">
          <FormField
            label="Breitengrad"
            name="latitude"
            type="number"
            value={form.latitude}
            onChange={update('latitude')}
            error={fieldErrors.latitude}
          />
          <FormField
            label="Längengrad"
            name="longitude"
            type="number"
            value={form.longitude}
            onChange={update('longitude')}
            error={fieldErrors.longitude}
          />
        </div>

        <FormField
          label="Modellreferenz"
          name="modelRef"
          value={form.modelRef}
          onChange={update('modelRef')}
          error={fieldErrors.modelRef}
          hint="Nur für Szenen, die das Gebäude selbst erzeugen. Im FEC-Campus steht das Modell bereits."
        />

        <fieldset className="form__group">
          <legend className="form__legend">Szenenkoordinaten</legend>
          <p className="page__hint">
            Nur wirksam, solange in der 3D-Szene kein Objekt mit diesem Schlüssel steht. Im FEC-Campus
            ist das Gebäude vorhanden und bleibt, wo sein Modell steht — die Werte hier bleiben dann
            ungenutzt. Sie sind nicht aus Breiten- und Längengrad ableitbar: dafür fehlten Ursprung und
            Maßstab der Szene.
          </p>
          <div className="coordinates">
            <FormField label="X" name="positionX" type="number" value={form.positionX} onChange={update('positionX')} error={fieldErrors.positionX} />
            <FormField label="Y" name="positionY" type="number" value={form.positionY} onChange={update('positionY')} error={fieldErrors.positionY} />
            <FormField label="Z" name="positionZ" type="number" value={form.positionZ} onChange={update('positionZ')} error={fieldErrors.positionZ} />
            <FormField label="Drehung Y (Grad)" name="rotationY" type="number" value={form.rotationY} onChange={update('rotationY')} error={fieldErrors.rotationY} />
          </div>
        </fieldset>

        <FormField label="Sichtbarkeit" name="published" error={fieldErrors.published}>
          <label className="checkbox">
            <input
              id="field-published"
              type="checkbox"
              checked={form.published}
              onChange={(event) => update('published')(event.target.checked)}
            />
            Veröffentlicht — auch für externe Konten sichtbar
          </label>
        </FormField>

        <div className="page__actions">
          <Can anyOf={['BUILDING_CREATE', 'BUILDING_UPDATE']}>
            <button type="submit" className="button button--primary" disabled={busy || !editable}>
              Speichern
            </button>
          </Can>

          {!isNew && (
            <Can perm="BUILDING_DELETE">
              <button type="button" className="button" disabled={busy} onClick={remove}>
                Löschen
              </button>
            </Can>
          )}

          <button
            type="button"
            className="button button--ghost"
            onClick={() => navigate('/admin/buildings')}
          >
            Zurück
          </button>
        </div>
      </form>
    </div>
  );
}
