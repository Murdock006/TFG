// Deterministic, namespaced fixture management for the TeamTask local emulators.
//
// Exposes idempotent `reset` (known empty state) and `seed` (deterministic
// users + documents) operations used by reset.sh, seed.sh, and
// emulator-verify.mjs. Node built-ins only; no npm dependencies.

import { pathToFileURL } from 'node:url';

const DEFAULT_PROJECT = 'demo-teamtask-local';

let project = process.env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT;

const authHost = () => process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const firestoreHost = () => process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const storageHost = () => process.env.FIREBASE_STORAGE_EMULATOR_HOST || '127.0.0.1:9199';

export const NAMESPACE = process.env.FIXTURE_NAMESPACE || 'fixture';
export const FIXED_TS = '2023-11-14T22:13:20.000Z';
export const GROUP_ID = `${NAMESPACE}-grupo-0001`;
export const FIXTURE_PASSWORD = 'FixturePass123!';

export const USERS = [
  {
    localId: `${NAMESPACE}-creator-0001`,
    email: `creator@${NAMESPACE}.teamtask.local`,
    password: FIXTURE_PASSWORD,
    displayName: 'Fixture Creator',
    role: 'creador'
  },
  {
    localId: `${NAMESPACE}-executor-0001`,
    email: `executor@${NAMESPACE}.teamtask.local`,
    password: FIXTURE_PASSWORD,
    displayName: 'Fixture Executor',
    role: 'miembro'
  }
];

export function setProject(value) {
  project = value;
}

export function getProject() {
  return project;
}

export function bucketName() {
  return `${project}.appspot.com`;
}

// ─── Firestore typed-value helpers ──────────────────────────────────────────

const str = (v) => ({ stringValue: v });
const int = (v) => ({ integerValue: String(v) });
const ts = (v) => ({ timestampValue: v });
const nil = () => ({ nullValue: null });
const map = (fields) => ({ mapValue: { fields } });

// ─── Shared HTTP helpers ────────────────────────────────────────────────────

function adminJsonHeaders() {
  return { Authorization: 'Bearer owner', 'Content-Type': 'application/json' };
}



// ─── Reset ──────────────────────────────────────────────────────────────────

export async function clearFirestore() {
  const url = `http://${firestoreHost()}/emulator/v1/projects/${project}/databases/(default)/documents`;
  const res = await fetch(url, { method: 'DELETE' });
  if (!res.ok) {
    throw new Error(`Firestore reset failed: ${res.status} ${await res.text()}`);
  }
}

export async function clearAuth() {
  const url = `http://${authHost()}/emulator/v1/projects/${project}/accounts`;
  const res = await fetch(url, { method: 'DELETE' });
  if (!res.ok) {
    throw new Error(`Auth reset failed: ${res.status} ${await res.text()}`);
  }
}

async function listStorageObjects() {
  const url = `http://${storageHost()}/storage/v1/b/${encodeURIComponent(bucketName())}/o`;
  const res = await fetch(url, { headers: { Authorization: 'Bearer owner' } });
  if (res.status === 404) return [];
  if (!res.ok) {
    throw new Error(`Storage list failed: ${res.status} ${await res.text()}`);
  }
  const body = await res.json();
  return body.items || [];
}

async function deleteStorageObject(name) {
  const url = `http://${storageHost()}/storage/v1/b/${encodeURIComponent(bucketName())}/o/${encodeURIComponent(name)}`;
  const res = await fetch(url, { method: 'DELETE', headers: { Authorization: 'Bearer owner' } });
  if (!res.ok && res.status !== 404) {
    throw new Error(`Storage delete failed for ${name}: ${res.status} ${await res.text()}`);
  }
}

export async function clearStorage() {
  const objects = await listStorageObjects();
  for (const object of objects) {
    await deleteStorageObject(object.name);
  }
  return objects.length;
}

export async function reset() {
  await clearFirestore();
  await clearAuth();
  const removed = await clearStorage();
  return { removedStorageObjects: removed };
}

// ─── Seed ───────────────────────────────────────────────────────────────────

async function replaceDocument(path, fields) {
  const base = `http://${firestoreHost()}/v1/projects/${project}/databases/(default)/documents/${path}`;
  const del = await fetch(base, { method: 'DELETE', headers: adminJsonHeaders() });
  if (!del.ok && del.status !== 404) {
    throw new Error(`Firestore delete failed for ${path}: ${del.status} ${await del.text()}`);
  }
  const res = await fetch(base, {
    method: 'PATCH',
    headers: adminJsonHeaders(),
    body: JSON.stringify({ fields })
  });
  if (!res.ok) {
    throw new Error(`Firestore write failed for ${path}: ${res.status} ${await res.text()}`);
  }
}

async function deleteAuthUser(localId) {
  const url = `http://${authHost()}/identitytoolkit.googleapis.com/v1/projects/${project}/accounts:delete`;
  await fetch(url, { method: 'POST', headers: adminJsonHeaders(), body: JSON.stringify({ localId }) });
}

async function createAuthUser(user) {
  const url = `http://${authHost()}/identitytoolkit.googleapis.com/v1/projects/${project}/accounts`;
  const res = await fetch(url, {
    method: 'POST',
    headers: adminJsonHeaders(),
    body: JSON.stringify({
      localId: user.localId,
      email: user.email,
      password: user.password,
      displayName: user.displayName
    })
  });
  if (!res.ok) {
    throw new Error(`Auth create failed for ${user.email}: ${res.status} ${await res.text()}`);
  }
}

export async function seed() {
  for (const user of USERS) {
    // Delete-then-create keeps a repeated seed deterministic and duplicate-free.
    await deleteAuthUser(user.localId);
    await createAuthUser(user);
    await replaceDocument(`usuarios/${user.localId}`, {
      nombre: str(user.displayName),
      email: str(user.email),
      puntos: int(1000),
      puntosReservados: int(0),
      puntosRecompensa: int(0),
      rachaDias: int(0),
      grupoId: str(GROUP_ID),
      avatarUrl: nil(),
      fechaCreacion: ts(FIXED_TS)
    });
  }

  await replaceDocument(`grupos/${GROUP_ID}`, {
    nombre: str('Fixture Group'),
    miembros: map(Object.fromEntries(USERS.map((u) => [u.localId, str(u.role)]))),
    puntos: int(0),
    fechaCreacion: ts(FIXED_TS),
    emoji: str('\u2764\uFE0F')
  });
}

// ─── CLI ────────────────────────────────────────────────────────────────────

function parseArgs(argv) {
  const command = argv[0];
  const projectIndex = argv.indexOf('--project');
  if (projectIndex !== -1 && argv[projectIndex + 1]) {
    project = argv[projectIndex + 1];
  }
  return command;
}

const invokedDirectly =
  process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;

if (invokedDirectly) {
  const command = parseArgs(process.argv.slice(2));
  const run = command === 'reset' ? reset : command === 'seed' ? seed : null;
  if (!run) {
    console.error('Usage: node fixtures.mjs <reset|seed> [--project demo-<id>]');
    process.exit(2);
  }
  run()
    .then((result) => {
      console.log(`${command} OK for project ${project}${result ? ` ${JSON.stringify(result)}` : ''}`);
    })
    .catch((error) => {
      console.error(`ERROR: ${error.message}`);
      process.exit(1);
    });
}
