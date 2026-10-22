#!/bin/sh
# Run from the prepared backend deploy directory containing .env and compose.prod.yml.
set -eu

rollback_image=studyfactory-backend:rollback
if ! docker image inspect "$rollback_image" > /dev/null 2>&1; then
  echo "No previous backend image is available for rollback."
  exit 1
fi

if ! beverage_state=$(docker image inspect \
  --format '{{ index .Config.Labels "io.studyfactory.beverage-state" }}' \
  "$rollback_image"); then
  echo "Could not verify the previous backend's beverage-state safety. Refusing rollback."
  exit 1
fi
if [ "$beverage_state" != canonical-v1 ]; then
  echo "Refusing rollback: the previous backend may restore deleted beverage preferences."
  echo "Deploy a verified fixed image instead; do not restart an older backend image."
  exit 1
fi

echo "Restoring previous beverage-safe backend image."
docker tag "$rollback_image" studyfactory-backend:latest
if ! docker compose --env-file .env -f compose.prod.yml up -d --no-build --force-recreate backend; then
  echo "Previous backend image could not be recreated."
  exit 1
fi

attempt=1
while [ "$attempt" -le 30 ]; do
  if curl --fail --silent --connect-timeout 2 --max-time 5 http://127.0.0.1:8080/api/branches > /dev/null; then
    sleep 5
    if curl --fail --silent --connect-timeout 2 --max-time 5 http://127.0.0.1:8080/api/branches > /dev/null; then
      echo "Previous backend image restored successfully."
      exit 0
    fi
  fi
  sleep 2
  attempt=$((attempt + 1))
done

echo "Previous backend image also failed its health check."
docker compose --env-file .env -f compose.prod.yml logs --tail=100 backend
exit 1
