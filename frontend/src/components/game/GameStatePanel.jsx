import { useCallback, useEffect, useState } from 'react';
import { fetchGameState, saveGameState } from '../../api/game';
import { useToast } from '../ui/Toast';

/**
 * The stored progress of the signed-in account, and a way to change it.
 * <p>
 * Shown next to the scene listing while no WebGL build is deployed. Without it the two halves of
 * {@code /api/game/state} — a fresh account answering 204, an existing one answering its document —
 * would only be observable through Unity or curl, and the browser is where the behaviour has to be
 * demonstrated.
 * <p>
 * The document is deliberately built here the same way the Unity client builds it, so that a state
 * written in the browser and one written in the game are interchangeable.
 */
export default function GameStatePanel() {
  const toast = useToast();
  const [state, setState] = useState(undefined);
  const [busy, setBusy] = useState(false);

  const load = useCallback(
    (announce = false) => {
      fetchGameState()
        .then((loaded) => {
          setState(loaded);
          if (announce) {
            toast.success(loaded ? 'Spielstand geladen.' : 'Kein Spielstand — neues Spiel.');
          }
        })
        .catch((error) => toast.fromError(error));
    },
    [toast],
  );

  useEffect(() => load(), [load]);

  /** Moves the player a step and saves — enough to show that the value survives a reload. */
  const save = async () => {
    setBusy(true);
    const next = {
      position: {
        x: Math.round((state?.position?.x ?? 0) + 5),
        y: 0,
        z: Math.round((state?.position?.z ?? 0) + 3),
      },
      visitedBuildings: state?.visitedBuildings ?? [],
      minutesPlayed: (state?.minutesPlayed ?? 0) + 1,
      savedAt: new Date().toISOString(),
    };
    try {
      await saveGameState(next);
      setState(next);
      toast.success('Spielstand gespeichert.');
    } catch (error) {
      toast.fromError(error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card">
      <h2 className="card__title">Spielstand</h2>

      {state === undefined && <p className="card__hint">wird geladen …</p>}

      {state === null && (
        <p className="card__hint">
          Für dieses Konto ist nichts gespeichert — der Server antwortet mit <code>204</code>, und das
          Spiel würde von vorn beginnen. Genau der Fall eines frisch registrierten Kontos.
        </p>
      )}

      {state && (
        <dl className="definition">
          <dt>Position</dt>
          <dd>
            {state.position?.x ?? 0} / {state.position?.y ?? 0} / {state.position?.z ?? 0}
          </dd>
          <dt>Spielzeit</dt>
          <dd>{state.minutesPlayed ?? 0} Minuten</dd>
          <dt>Besuchte Gebäude</dt>
          <dd>{state.visitedBuildings?.length ? state.visitedBuildings.join(', ') : '—'}</dd>
          <dt>Zuletzt gespeichert</dt>
          <dd>{state.savedAt ? new Date(state.savedAt).toLocaleString('de-DE') : '—'}</dd>
        </dl>
      )}

      <div className="page__actions">
        <button type="button" className="button button--primary" onClick={save} disabled={busy}>
          Schritt gehen und speichern
        </button>
        <button type="button" className="button" onClick={() => load(true)} disabled={busy}>
          Vom Server neu laden
        </button>
      </div>

      <p className="card__hint">
        Der Spielstand hängt am Konto, nicht am Gerät: Nach dem Abmelden und erneuten Anmelden — auch in
        einem anderen Browser — steht derselbe Wert hier.
      </p>
    </section>
  );
}
