#!/bin/bash

# Pass arguments to npm the project root

set -e
set -x

ARGS=$*

vagrant ssh app -c "cd /opt/app && envdir /etc/rf.d/env npm $ARGS"
