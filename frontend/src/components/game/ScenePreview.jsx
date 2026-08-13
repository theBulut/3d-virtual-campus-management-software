import StatusBadge from '../ui/StatusBadge';

/**
 * The scene payload as a readable listing.
 * <p>
 * Stands in for the 3D view while no WebGL build is deployed, and stays useful afterwards: it shows
 * exactly what this account is allowed to see, which is the part the permission model decides. Objects
 * that are not published only ever appear here for accounts that hold the wider read permission — the
 * server does not send them to anyone else.
 */
export default function ScenePreview({ scene }) {
  return (
    <div className="preview">
      <section className="card">
        <h2 className="card__title">Gebäude ({scene.buildings.length})</h2>
        <ul className="preview__list">
          {scene.buildings.map((building) => (
            <li key={building.id}>
              <code>{building.code}</code> {building.nameDe}
              <span className="preview__meta">
                Position {format(building.position)} · Drehung {building.rotationY}°
                {building.modelRef ? ` · ${building.modelRef}` : ''}
              </span>
              {building.published === false && <span className="badge badge--draft">unveröffentlicht</span>}
            </li>
          ))}
          {!scene.buildings.length && <li className="preview__empty">Keine Gebäude freigegeben.</li>}
        </ul>
      </section>

      <section className="card">
        <h2 className="card__title">Orientierungspunkte ({scene.pois.length})</h2>
        <ul className="preview__list">
          {scene.pois.map((poi) => (
            <li key={poi.id}>
              {poi.nameDe}
              {poi.status && <StatusBadge status={poi.status} />}
              <span className="preview__meta">
                {poi.category}
                {poi.buildingCode ? ` · ${poi.buildingCode}` : ''} · Position {format(poi.position)}
              </span>
            </li>
          ))}
          {!scene.pois.length && <li className="preview__empty">Keine Punkte freigegeben.</li>}
        </ul>
      </section>

      <section className="card">
        <h2 className="card__title">Beratungszeiten ({scene.consultations.length})</h2>
        <ul className="preview__list">
          {scene.consultations.map((consultation) => (
            <li key={consultation.id}>
              {consultation.titleDe}
              {consultation.published === false && (
                <span className="badge badge--draft">unveröffentlicht</span>
              )}
              <span className="preview__meta">
                {consultation.organisation}
                {consultation.buildingCode ? ` · ${consultation.buildingCode}` : ''}
                {consultation.room ? ` · Raum ${consultation.room}` : ''}
              </span>
              <span className="preview__meta">
                {consultation.slots.map((slot, index) => (
                  <span key={index}>
                    {WEEKDAYS[slot.dayOfWeek] ?? '?'} {short(slot.startTime)}–{short(slot.endTime)}
                    {index < consultation.slots.length - 1 ? ' · ' : ''}
                  </span>
                ))}
              </span>
            </li>
          ))}
          {!scene.consultations.length && (
            <li className="preview__empty">Keine Beratungsangebote freigegeben.</li>
          )}
        </ul>
      </section>
    </div>
  );
}

const WEEKDAYS = { 1: 'Mo', 2: 'Di', 3: 'Mi', 4: 'Do', 5: 'Fr', 6: 'Sa', 7: 'So' };

const format = (position) => `${position.x} / ${position.y} / ${position.z}`;

/** The API sends HH:mm:ss; seconds are noise in a schedule. */
const short = (time) => (time ?? '').slice(0, 5);
