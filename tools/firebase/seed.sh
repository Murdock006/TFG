#!/usr/bin/env bash
#
# Seed deterministic, namespaced fixtures into the local Firebase emulators.
#
# Creates two email/password fixture users (creator/executor) and their
# Firestore documents. The operation is idempotent: running it twice converges
# to the same fixture state without duplicates.
#
# Usage:
#   bash tools/firebase/seed.sh
#
# Requires the emulators to already be running.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=tools/firebase/_guard.sh
source "$SCRIPT_DIR/_guard.sh"

require_prerequisites
PROJECT="$(require_local_namespace)"

exec "$NODE_BIN" "$SCRIPT_DIR/fixtures.mjs" seed --project "$PROJECT"
