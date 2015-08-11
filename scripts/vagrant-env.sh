#!/bin/bash

VAGRANT_RETRY_ATTEMPTS=3
VAGRANT_TIMEOUT=5

# Shout out to Phil: http://stackoverflow.com/a/8351489
#
function with_backoff {
  local timeout=${VAGRANT_TIMEOUT}
  local max_attempts=${VAGRANT_RETRY_ATTEMPTS}
  local attempt=0
  local exitCode=0

  while [[ $attempt < $max_attempts ]]
  do
    set +e
    "$@"
    exitCode=$?
    set -e

    if [[ $exitCode == 0 ]]
    then
      break
    fi

    echo ""
    echo "Failure detected; waiting ${timeout} seconds..."
    echo ""

    sleep $timeout

    attempt=$(( attempt + 1 ))
    timeout=$(( timeout * 2 ))
  done

  if [[ $exitCode != 0 ]]
  then
    echo "Vagrant has failed the provisioning process ${max_attempts} times."
    echo "Please inspect the failures manually and retry the build."
  fi

  return $exitCode
}
