#!/usr/bin/env bash
set -Eeuo pipefail

readonly DEPLOY_PATH="${1:-}"
readonly TARGET_IMAGE="${2:-}"
readonly DOCKER_CONFIG_PATH="${3:-}"
readonly COMPOSE_FILE="$DEPLOY_PATH/docker-compose.prod.yml"
readonly NEXT_COMPOSE_FILE="$COMPOSE_FILE.next"
readonly ROLLBACK_COMPOSE_FILE="$COMPOSE_FILE.rollback"
readonly ENV_FILE="$DEPLOY_PATH/.env.prod"

fail() {
  printf 'deploy error: %s\n' "$*" >&2
  exit 1
}

run_compose() {
  local image="$1"
  shift
  BACKEND_IMAGE="$image" docker compose \
    --project-directory "$DEPLOY_PATH" \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "$@"
}

wait_for_backend() {
  local image="$1"
  local container_id
  local state

  for _ in {1..48}; do
    container_id="$(run_compose "$image" ps -q backend 2>/dev/null || true)"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$container_id" 2>/dev/null || true)"
      case "$state" in
        healthy | running)
          return 0
          ;;
        unhealthy | exited | dead)
          return 1
          ;;
      esac
    fi
    sleep 5
  done
  return 1
}

[[ "$DEPLOY_PATH" =~ ^/[A-Za-z0-9._/-]+$ ]] || \
  fail "deploy path must be an absolute path without spaces"
[[ "$TARGET_IMAGE" =~ ^ghcr\.io/[a-z0-9._/-]+:[0-9a-f]{40}$ ]] || \
  fail "target image must use a full commit SHA tag"
[[ "$DOCKER_CONFIG_PATH" =~ ^/tmp/meaning-log-ghcr-[0-9a-f]{40}$ ]] || \
  fail "docker config path must belong to this deployment"
command -v docker >/dev/null 2>&1 || fail "docker is not installed"
docker compose version >/dev/null 2>&1 || fail "docker compose is not available"
[[ -d "$DEPLOY_PATH" ]] || fail "deploy path does not exist: $DEPLOY_PATH"
[[ -f "$ENV_FILE" ]] || fail "production environment file does not exist: $ENV_FILE"
[[ -f "$NEXT_COMPOSE_FILE" ]] || fail "staged compose file is missing"
[[ -d "$DOCKER_CONFIG_PATH" ]] || fail "temporary docker config does not exist"

export DOCKER_CONFIG="$DOCKER_CONFIG_PATH"

BACKEND_IMAGE="$TARGET_IMAGE" docker compose \
  --project-directory "$DEPLOY_PATH" \
  --env-file "$ENV_FILE" \
  -f "$NEXT_COMPOSE_FILE" \
  config --quiet

previous_container=""
previous_image=""
if [[ -f "$COMPOSE_FILE" ]]; then
  previous_container="$(run_compose "$TARGET_IMAGE" ps -q backend 2>/dev/null || true)"
  if [[ -n "$previous_container" ]]; then
    previous_image="$(docker inspect --format '{{.Config.Image}}' "$previous_container")"
  fi
fi

docker pull "$TARGET_IMAGE"

if [[ -f "$COMPOSE_FILE" ]]; then
  cp "$COMPOSE_FILE" "$ROLLBACK_COMPOSE_FILE"
else
  rm -f "$ROLLBACK_COMPOSE_FILE"
fi
mv "$NEXT_COMPOSE_FILE" "$COMPOSE_FILE"

if run_compose "$TARGET_IMAGE" up -d --no-build --no-deps backend && \
  wait_for_backend "$TARGET_IMAGE"; then
  printf 'deployed backend image: %s\n' "$TARGET_IMAGE"
  exit 0
fi

run_compose "$TARGET_IMAGE" logs --tail=120 --no-color backend || true

if [[ -n "$previous_image" && -f "$ROLLBACK_COMPOSE_FILE" ]]; then
  printf 'deployment failed; restoring previous image: %s\n' "$previous_image" >&2
  cp "$ROLLBACK_COMPOSE_FILE" "$COMPOSE_FILE"
  if run_compose "$previous_image" up -d --no-build --no-deps backend && \
    wait_for_backend "$previous_image"; then
    fail "new image was unhealthy; previous image has been restored"
  fi
  fail "new image was unhealthy and automatic application rollback failed"
fi

fail "new image was unhealthy and no previous image was available"
