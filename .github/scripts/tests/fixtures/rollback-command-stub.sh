#!/bin/sh
set -eu

command_name=${0##*/}
printf '%s %s\n' "$command_name" "$*" >> "${ROLLBACK_TEST_LOG:?}"

case "$command_name" in
  docker)
    case "$*" in
      'image inspect studyfactory-backend:rollback')
        [ "${ROLLBACK_TEST_SCENARIO:?}" != missing ]
        ;;
      'image inspect --format '* )
        case "$ROLLBACK_TEST_SCENARIO" in
          unlabelled) printf '\n' ;;
          wrong-label) printf 'legacy-v0\n' ;;
          inspect-error) exit 1 ;;
          *) printf 'canonical-v1\n' ;;
        esac
        ;;
      'tag studyfactory-backend:rollback studyfactory-backend:latest')
        [ "$ROLLBACK_TEST_SCENARIO" != tag-error ]
        ;;
      'compose --env-file .env -f compose.prod.yml up -d --no-build --force-recreate backend')
        [ "$ROLLBACK_TEST_SCENARIO" != recreate-error ]
        ;;
      'compose --env-file .env -f compose.prod.yml logs --tail=100 backend') ;;
      *) echo "Unexpected docker command: $*" >&2; exit 99 ;;
    esac
    ;;
  curl)
    [ "$ROLLBACK_TEST_SCENARIO" != unhealthy ]
    ;;
  sleep) ;;
  *) echo "Unexpected stub command: $command_name" >&2; exit 99 ;;
esac
