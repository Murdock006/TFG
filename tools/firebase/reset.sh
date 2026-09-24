#!/usr/bin/env bash
#
# Reset the local Firebase emulators to a known empty state.
#
# Clears Auth accounts, Firestore documents, and Storage objects for the
# selected demo project. Run it before and after an isolated verification.
#
# Usage:
#   bash tools/firebase/reset.sh
#
# Requires the emulators to already be running (for example via
# 'firebase emulators:exec --only auth,firestore,storage ...').

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=tools/firebase/_guard.sh
source "$SCRIPT_DIR/_guard.sh"

require_prerequisites
PROJECT="$(require_local_namespace)"

exec "$NODE_BIN" "$SCRIPT_DIR/fixtures.mjs" reset --project "$PROJECT"
