#!/bin/bash

set -x

DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

source "${DIR}/vagrant-env.sh"

vagrant up --no-provision

for vm in services app worker;
do
  with_backoff vagrant reload --provision ${vm}
done
