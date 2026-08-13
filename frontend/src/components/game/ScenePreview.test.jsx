import { render, screen } from '@testing-library/react';
import ScenePreview from './ScenePreview';

/**
 * The scene as a player receives it: no status field on any object, because the server does not send one
 * to accounts that may only see published content.
 */
const PLAYER_SCENE = {
  pois: [
    {
      id: 1,
      nameDe: 'Audimax',
      category: 'LECTURE_HALL',
      buildingCode: 'S1|03',
      position: { x: 12.5, y: 0, z: 34.2 },
      status: null,
    },
  ],
  buildings: [
    {
      id: 1,
      code: 'S1|03',
      nameDe: 'Altes Hauptgebäude',
      modelRef: 'models/s1_03.glb',
      position: { x: 0, y: 0, z: 0 },
      rotationY: 0,
      published: null,
    },
  ],
  consultations: [
    {
      id: 1,
      titleDe: 'Studienberatung',
      organisation: 'Fachgebiet Informatik',
      buildingCode: 'S2|02',
      room: 'B302',
      slots: [{ dayOfWeek: 2, startTime: '10:00:00', endTime: '12:00:00' }],
      published: null,
    },
  ],
};

/** The same scene for an editor: two more objects, each carrying its state. */
const EDITOR_SCENE = {
  ...PLAYER_SCENE,
  pois: [
    { ...PLAYER_SCENE.pois[0], status: 'PUBLISHED' },
    {
      id: 2,
      nameDe: 'Würfel im Entwurf',
      category: 'OTHER',
      buildingCode: null,
      position: { x: 1, y: 0, z: 2 },
      status: 'DRAFT',
    },
  ],
  buildings: [
    { ...PLAYER_SCENE.buildings[0], published: true },
    {
      id: 2,
      code: 'L4|01',
      nameDe: 'Noch nicht freigegeben',
      modelRef: null,
      position: { x: 5, y: 0, z: 5 },
      rotationY: 90,
      published: false,
    },
  ],
};

test('zeigt die Szene eines Spielers ohne Statusangaben', () => {
  render(<ScenePreview scene={PLAYER_SCENE} />);

  expect(screen.getByText('Audimax')).toBeInTheDocument();
  expect(screen.getByText(/Altes Hauptgebäude/)).toBeInTheDocument();
  expect(screen.getByText('Studienberatung')).toBeInTheDocument();
  // Nothing about drafts: the payload of a player carries no status at all.
  expect(screen.queryByText('Entwurf')).not.toBeInTheDocument();
  expect(screen.queryByText('unveröffentlicht')).not.toBeInTheDocument();
});

test('kennzeichnet Entwürfe in der Redaktionsansicht', () => {
  render(<ScenePreview scene={EDITOR_SCENE} />);

  expect(screen.getByText('Würfel im Entwurf')).toBeInTheDocument();
  expect(screen.getByText('Entwurf')).toBeInTheDocument();
  expect(screen.getByText('Veröffentlicht')).toBeInTheDocument();
  expect(screen.getByText('unveröffentlicht')).toBeInTheDocument();
});

test('zeigt Position und Termine so an, wie Unity sie bekommt', () => {
  render(<ScenePreview scene={PLAYER_SCENE} />);

  expect(screen.getByText(/12\.5 \/ 0 \/ 34\.2/)).toBeInTheDocument();
  // Seconds are noise in a schedule.
  expect(screen.getByText(/Di 10:00–12:00/)).toBeInTheDocument();
});

test('bleibt lesbar, wenn nichts freigegeben ist', () => {
  render(<ScenePreview scene={{ pois: [], buildings: [], consultations: [] }} />);

  expect(screen.getByText('Keine Gebäude freigegeben.')).toBeInTheDocument();
  expect(screen.getByText('Keine Punkte freigegeben.')).toBeInTheDocument();
  expect(screen.getByText('Keine Beratungsangebote freigegeben.')).toBeInTheDocument();
});
