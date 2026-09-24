// Automated verification for the TeamTask local Firebase tooling (WU2).
//
// Runs against a live emulator suite, for example:
//   firebase emulators:exec --only auth,firestore,storage \
//     --project demo-teamtask-local "node tools/firebase/emulator-verify.mjs"
//
// Checks: declared ports, rules denial, indexes, reset/seed determinism, the
// namespace guard, and the prerequisite guard. Node built-ins only.

import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  GROUP_ID,
  USERS,
  bucketName,
  getProject,
  reset,
  seed,
  setProject
} from './fixtures.mjs';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(SCRIPT_DIR, '..', '..');

const authHost = () => process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const firestoreHost = () => process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const storageHost = () => process.env.FIREBASE_STORAGE_EMULATOR_HOST || '127.0.0.1:9199';

const NAMESPACE_OTHER = `${USERS[0].localId.split('-')[0]}-otro-9999`;

const results = [];

function record(name, status, detail) {
  results.push({ name, status, detail });
  console.log(`[${status}] ${name}: ${detail}`);
}

async function check(name, fn) {
  try {
    const detail = await fn();
    record(name, 'PASS', detail);
  } catch (error) {
    record(name, 'FAIL', error.message);
  }
}

// ─── Individual checks ──────────────────────────────────────────────────────

async function checkPorts() {
  const targets = [
    ['Auth', authHost()],
    ['Firestore', firestoreHost()],
    ['Storage', storageHost()]
  ];
  const observed = [];
  for (const [label, host] of targets) {
    let status = 'no response';
    try {
      const res = await fetch(`http://${host}/`);
      status = String(res.status);
    } catch (error) {
      throw new Error(`${label} (${host}) did not respond: ${error.message}`);
    }
    observed.push(`${label} ${host}=${status}`);
  }
  return observed.join(', ');
}

async function checkIndexes() {
  const file = join(REPO_ROOT, 'firestore.indexes.json');
  if (!existsSync(file)) throw new Error('firestore.indexes.json not found');
  const parsed = JSON.parse(await readFile(file, 'utf8'));
  const indexes = parsed.indexes || [];
  if (indexes.length === 0) throw new Error('no composite indexes declared');

  const res = await fetch(
    `http://${firestoreHost()}/v1/projects/${getProject()}/databases/(default)/documents:runQuery`,
    {
      method: 'POST',
      headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: 'canjes' }],
          where: {
            fieldFilter: {
              field: { fieldPath: 'grupoId' },
              op: 'EQUAL',
              value: { stringValue: GROUP_ID }
            }
          },
          orderBy: [{ field: { fieldPath: 'fecha' }, direction: 'DESCENDING' }]
        }
      })
    }
  );
  if (!res.ok) throw new Error(`composite query rejected: ${res.status} ${await res.text()}`);
  return `${indexes.length} indexes declared; canjes(grupoId+fecha DESC) query accepted (${res.status})`;
}

async function signIn(email, password) {
  const res = await fetch(
    `http://${authHost()}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true })
    }
  );
  if (!res.ok) throw new Error(`sign-in failed for ${email}: ${res.status} ${await res.text()}`);
  return (await res.json()).idToken;
}

async function firestoreWrite(docPath, token) {
  const url = `http://${firestoreHost()}/v1/projects/${getProject()}/databases/(default)/documents/${docPath}`;
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(url, {
    method: 'PATCH',
    headers,
    body: JSON.stringify({ fields: { nombre: { stringValue: 'probe' } } })
  });
}

async function checkFirestoreRules() {
  const creator = USERS[0];
  const executor = USERS[1];

  const unauthenticated = await firestoreWrite(`usuarios/${creator.localId}`, null);
  if (unauthenticated.status !== 403) {
    throw new Error(`expected 403 for unauthenticated write, got ${unauthenticated.status}`);
  }

  const token = await signIn(creator.email, creator.password);

  const own = await firestoreWrite(`usuarios/${creator.localId}`, token);
  if (own.status !== 200) {
    throw new Error(`expected 200 for own usuarios write, got ${own.status} ${await own.text()}`);
  }

  const otherCreate = await firestoreWrite(`usuarios/${NAMESPACE_OTHER}`, token);
  if (otherCreate.status !== 403) {
    throw new Error(`expected 403 for creating another user's doc, got ${otherCreate.status}`);
  }

  const outside = await firestoreWrite('fuera_contrato/doc-1', token);
  if (outside.status !== 403) {
    throw new Error(`expected 403 for outside-contract write, got ${outside.status}`);
  }

  return `unauth=403, own=200, create-other=403, outside-contract=403 (user ${executor.localId} signed-in token enforced)`;
}

async function storageUpload(objectPath, token, contentType) {
  const url = `http://${storageHost()}/v0/b/${encodeURIComponent(bucketName())}/o?name=${encodeURIComponent(objectPath)}&uploadType=media`;
  const headers = { 'Content-Type': contentType };
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(url, { method: 'POST', headers, body: new Uint8Array([1, 2, 3, 4]) });
}

async function checkStorageRules() {
  const creator = USERS[0];
  const executor = USERS[1];
  const token = await signIn(creator.email, creator.password);

  const unauth = await storageUpload(`avatares/${creator.localId}/probe.png`, null, 'image/png');
  if (unauth.status !== 403) {
    throw new Error(`expected 403 for unauthenticated upload, got ${unauth.status}`);
  }

  const own = await storageUpload(`avatares/${creator.localId}/probe.png`, token, 'image/png');
  if (own.status !== 200) {
    throw new Error(`expected 200 for own avatar upload, got ${own.status} ${await own.text()}`);
  }

  const other = await storageUpload(`avatares/${executor.localId}/probe.png`, token, 'image/png');
  if (other.status !== 403) {
    throw new Error(`expected 403 for another user's avatar upload, got ${other.status}`);
  }

  return `unauth=403, own=200, other=403`;
}

async function firestoreCollection(collectionId) {
  const res = await fetch(
    `http://${firestoreHost()}/v1/projects/${getProject()}/databases/(default)/documents/${collectionId}`,
    { headers: { Authorization: 'Bearer owner' } }
  );
  if (!res.ok) throw new Error(`list ${collectionId} failed: ${res.status} ${await res.text()}`);
  const body = await res.json();
  const docs = (body.documents || []).map((doc) => ({ name: doc.name, fields: doc.fields }));
  docs.sort((a, b) => a.name.localeCompare(b.name));
  return docs;
}

async function authLookupByEmail(emails) {
  const res = await fetch(
    `http://${authHost()}/identitytoolkit.googleapis.com/v1/projects/${getProject()}/accounts:lookup`,
    {
      method: 'POST',
      headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: emails })
    }
  );
  if (!res.ok) throw new Error(`auth lookup failed: ${res.status} ${await res.text()}`);
  const body = await res.json();
  return (body.users || []).map((user) => user.localId).sort();
}

async function snapshot() {
  return {
    usuarios: await firestoreCollection('usuarios'),
    grupos: await firestoreCollection('grupos'),
    auth: await authLookupByEmail(USERS.map((u) => u.email))
  };
}

async function checkResetSeedDeterminism() {
  await reset();
  const empty = await snapshot();
  if (empty.usuarios.length !== 0 || empty.grupos.length !== 0 || empty.auth.length !== 0) {
    throw new Error('reset did not leave a known empty state');
  }

  await seed();
  const first = await snapshot();
  await seed();
  const second = await snapshot();

  const expectedUids = USERS.map((u) => u.localId).sort().join(',');
  if (first.auth.join(',') !== expectedUids) {
    throw new Error(`auth fixture set mismatch: ${first.auth.join(',')} vs ${expectedUids}`);
  }
  if (first.usuarios.length !== USERS.length || first.grupos.length !== 1) {
    throw new Error(`unexpected fixture counts: usuarios=${first.usuarios.length}, grupos=${first.grupos.length}`);
  }
  if (JSON.stringify(first) !== JSON.stringify(second)) {
    throw new Error('seed; seed did not converge to identical state');
  }
  return `reset empty; seed;seed converged (usuarios=${first.usuarios.length}, grupos=${first.grupos.length}, auth=${first.auth.length})`;
}

function findBash() {
  if (process.env.BASH_BIN) return process.env.BASH_BIN;
  const candidates = [
    'C:\\Program Files\\Git\\bin\\bash.exe',
    'C:\\Program Files\\Git\\usr\\bin\\bash.exe'
  ];
  for (const candidate of candidates) {
    if (existsSync(candidate)) return candidate;
  }
  return null;
}

function runBash(script, args, extraEnv) {
  const bash = findBash();
  if (!bash) throw new Error('bash not found; set BASH_BIN to run guard checks');
  return spawnSync(bash, [script, ...args], {
    cwd: REPO_ROOT,
    encoding: 'utf8',
    env: { ...process.env, ...extraEnv }
  });
}

async function checkNamespaceGuard() {
  const script = join(SCRIPT_DIR, 'reset.sh');

  const production = runBash(script, [], { FIREBASE_PROJECT_ID: 'teamtask-3a855' });
  if (production.status === 0 || !`${production.stderr}`.includes('Refusing to run against project')) {
    throw new Error(`production project was not rejected (exit=${production.status})`);
  }

  const credentials = runBash(script, [], {
    FIREBASE_PROJECT_ID: 'demo-teamtask-local',
    GOOGLE_APPLICATION_CREDENTIALS: join(REPO_ROOT, 'fake-service-account.json')
  });
  if (credentials.status === 0 || !`${credentials.stderr}`.includes('GOOGLE_APPLICATION_CREDENTIALS')) {
    throw new Error(`production credentials were not rejected (exit=${credentials.status})`);
  }

  return 'production project id and GOOGLE_APPLICATION_CREDENTIALS both rejected with actionable errors';
}

async function checkPrerequisiteGuard() {
  const script = join(SCRIPT_DIR, '_guard.sh');
  const missing = runBash(script, ['prerequisites'], { NODE_BIN: 'node-does-not-exist' });
  if (missing.status === 0 || !`${missing.stderr}`.includes('Missing prerequisite')) {
    throw new Error(`missing prerequisite was not reported (exit=${missing.status})`);
  }
  return 'missing Node.js binary reported as an actionable prerequisite error';
}

// ─── Runner ─────────────────────────────────────────────────────────────────

async function main() {
  const projectIndex = process.argv.indexOf('--project');
  const project =
    projectIndex !== -1 && process.argv[projectIndex + 1]
      ? process.argv[projectIndex + 1]
      : process.env.FIREBASE_PROJECT_ID || process.env.GCLOUD_PROJECT || 'demo-teamtask-local';
  setProject(project);

  console.log(`emulator-verify: project=${project}`);

  await check('ports', checkPorts);
  await check('indexes', checkIndexes);
  // Seed before the rules checks: the rules scenarios need the fixture users.
  await check('reset-seed-determinism', checkResetSeedDeterminism);
  await check('firestore-rules', checkFirestoreRules);
  await check('storage-rules', checkStorageRules);
  await check('namespace-guard', checkNamespaceGuard);
  await check('prerequisite-guard', checkPrerequisiteGuard);

  // Leave a known empty state after the isolated verification.
  try {
    await reset();
  } catch (error) {
    record('final-reset', 'FAIL', error.message);
  }

  const failed = results.filter((r) => r.status !== 'PASS');
  console.log(`\nemulator-verify: ${results.length - failed.length}/${results.length} checks passed`);
  if (failed.length > 0) {
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(`emulator-verify fatal: ${error.message}`);
  process.exit(1);
});
