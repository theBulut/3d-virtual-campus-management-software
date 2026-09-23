#!/usr/bin/env bash
# Shared settings and the target check for the study scripts. Sourced, not executed.
#
# The check exists because the reset deletes every account and all content in its target database. Run
# against the development database that would destroy work in progress, so nothing destructive happens
# before the target has identified itself as the study instance.

EVAL_PROJECT="campus-eval"
EVAL_DB="campus_eval"
EVAL_DB_USER="postgres"
EVAL_API="http://localhost:8090/api"
EVAL_APP="http://localhost:3100"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EVAL_COMPOSE=(docker compose -f "$REPO_ROOT/docker-compose.yml" -f "$REPO_ROOT/docker-compose.eval.yml")
EVAL_BASELINE_SQL="$REPO_ROOT/backend/src/main/resources/db/eval/R__seed_eval_baseline.sql"

# Where research data is written. Outside the repository on purpose: session snapshots and reports are
# research data and must not be committed.
EVAL_DATA_DIR="${EVAL_DATA_DIR:-/Users/mehmetbulut/Studium/Bachelorthesis/eval/erhebungsdaten}"

eval_die() {
    echo "Abbruch: $*" >&2
    exit 1
}

# Name of the running database container of the study project, or empty.
eval_db_container() {
    "${EVAL_COMPOSE[@]}" ps -q db 2>/dev/null | head -1
}

# Refuses unless the target really is the study database. Three independent checks, because any one of
# them alone can be satisfied by accident:
#   1. a db container exists in the campus-eval project,
#   2. it carries that project label,
#   3. the database campus_eval exists inside it.
eval_assert_target() {
    local container
    container="$(eval_db_container)"
    [ -n "$container" ] || eval_die "Kein Datenbank-Container im Projekt '$EVAL_PROJECT'. Zuerst ./scripts/eval/eval-up.sh"

    local project
    project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$container" 2>/dev/null)"
    [ "$project" = "$EVAL_PROJECT" ] \
        || eval_die "Container gehört zum Projekt '$project', erwartet '$EVAL_PROJECT'. Es wird nichts verändert."

    docker exec "$container" psql -U "$EVAL_DB_USER" -lqt 2>/dev/null | cut -d'|' -f1 | grep -qw "$EVAL_DB" \
        || eval_die "Datenbank '$EVAL_DB' im Zielcontainer nicht gefunden. Es wird nichts verändert."

    EVAL_DB_CONTAINER="$container"
}

# psql against the study database. Only ever called after eval_assert_target.
eval_psql() {
    docker exec -i "$EVAL_DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$EVAL_DB_USER" -d "$EVAL_DB" "$@"
}

eval_token_for() {
    local user="$1" password="$2"
    curl -sf -X POST "$EVAL_API/auth/login" -H 'Content-Type: application/json' \
        -d "{\"username\":\"$user\",\"password\":\"$password\"}" |
        python3 -c 'import sys, json; print(json.load(sys.stdin)["accessToken"])'
}

eval_wait_for_api() {
    local seconds="${1:-120}"
    curl -sf --retry "$((seconds / 3))" --retry-delay 3 --retry-all-errors --retry-connrefused \
        --max-time "$seconds" "$EVAL_API/health" >/dev/null 2>&1
}
