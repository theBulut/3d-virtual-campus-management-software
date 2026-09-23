#!/usr/bin/env bash
# Starts the isolated study stack and waits until it answers.
#
#   ./scripts/eval/eval-up.sh            start, build only what changed
#   ./scripts/eval/eval-up.sh --build    force a rebuild of the images
#
# Runs alongside the development stack: different Compose project, different host ports. Nothing here
# touches the development database.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/eval-env.sh"

# Plain string rather than an array: bash 3.2, which macOS still ships, treats an empty array as an
# unbound variable under `set -u`.
BUILD_ARG=""
[ "${1:-}" = "--build" ] && BUILD_ARG="--build"

echo "Projekt:   $EVAL_PROJECT"
echo "Anwendung: $EVAL_APP"
echo "API:       $EVAL_API"
echo

if [ -n "$BUILD_ARG" ]; then
    "${EVAL_COMPOSE[@]}" up -d --build
else
    "${EVAL_COMPOSE[@]}" up -d
fi

echo
echo -n "Warte auf die API … "
if eval_wait_for_api 150; then
    echo "bereit."
else
    echo "keine Antwort."
    echo "Log ansehen mit: ${EVAL_COMPOSE[*]} logs backend --tail=40" >&2
    exit 1
fi

eval_assert_target
echo
echo "Datenbank: $EVAL_DB in $EVAL_DB_CONTAINER"
echo "Stand:     $(cd "$REPO_ROOT" && git rev-parse --short HEAD)"
echo
echo "Vor jeder Sitzung den Ausgangszustand herstellen: ./scripts/eval/eval-reset.sh"
