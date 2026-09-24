#!/usr/bin/env bash
#
# Shared preflight guard for TeamTask local Firebase tooling.
#
# This tooling is LOCAL-ONLY. The guard refuses production credentials, any
# non-demo project id, and unnamespaced fixture data so a local run can never
# select or mutate the production Firebase project (teamtask-3a855).
#
# Source it from reset.sh / seed.sh, or run it directly to check prerequisites:
#   bash tools/firebase/_guard.sh prerequisites
#   bash tools/firebase/_guard.sh namespace

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

NODE_BIN="${NODE_BIN:-node}"
FIREBASE_BIN="${FIREBASE_BIN:-firebase}"
JAVA_BIN="${JAVA_BIN:-java}"
FIXTURE_NAMESPACE="${FIXTURE_NAMESPACE:-fixture}"

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  local bin="$1"
  local hint="$2"
  if ! command -v "$bin" >/dev/null 2>&1; then
    fail "Missing prerequisite '$bin'. $hint"
  fi
}

require_prerequisites() {
  require_command "$NODE_BIN" \
    "Install Node.js 18+ (Node 24 verified) and ensure '$NODE_BIN' is on PATH. See tools/firebase/README.md."
  require_command "$FIREBASE_BIN" \
    "Install the Firebase CLI with 'npm install -g firebase-tools' (15.31 verified). See tools/firebase/README.md."
  require_command "$JAVA_BIN" \
    "Install a JDK (Java 11+; Java 25 verified). The Auth/Firestore/Storage emulators are Java processes. See tools/firebase/README.md."
}

resolve_project() {
  if [ -n "${FIREBASE_PROJECT_ID:-}" ]; then
    printf '%s' "$FIREBASE_PROJECT_ID"
    return 0
  fi
  "$NODE_BIN" -e \
    "const fs=require('fs');const p=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));process.stdout.write((p.projects&&p.projects.default)||'')" \
    "$REPO_ROOT/.firebaserc"
}

require_local_namespace() {
  if [ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]; then
    fail "GOOGLE_APPLICATION_CREDENTIALS is set. Local tooling must not use production service-account credentials. Unset it and retry."
  fi
  if [ -n "${FIREBASE_TOKEN:-}" ]; then
    fail "FIREBASE_TOKEN is set. Local tooling must not use production CI credentials. Unset it and retry."
  fi

  local project
  project="$(resolve_project)"
  case "$project" in
    demo-*) ;;
    *) fail "Refusing to run against project '$project'. Local tooling requires a 'demo-*' project id so production can never be selected." ;;
  esac

  if ! printf '%s' "$FIXTURE_NAMESPACE" | grep -Eq '^[a-z0-9][a-z0-9-]*$'; then
    fail "FIXTURE_NAMESPACE='$FIXTURE_NAMESPACE' is not a valid namespace. Use lowercase letters, digits, and dashes (for example 'fixture')."
  fi

  printf '%s' "$project"
}

# Allow direct invocation for prerequisite/namespace checks and simulations.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  case "${1:-all}" in
    prerequisites) require_prerequisites ;;
    namespace) require_local_namespace >/dev/null ;;
    all) require_prerequisites; require_local_namespace >/dev/null ;;
    *) fail "Unknown guard target '${1:-}'. Use: prerequisites | namespace | all." ;;
  esac
fi
