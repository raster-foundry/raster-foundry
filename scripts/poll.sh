#!/bin/bash

# Run poll service interactively.

set -e
set -x

ARGS=$*

vagrant ssh worker -c "sudo service rf-worker stop || /bin/true"
vagrant ssh worker -c "cd /opt/app && envdir /etc/rf.d/env ./manage.py poll $ARGS"
