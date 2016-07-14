#!/bin/bash

# Lint Python and JavaScript.

set -e
set -x

vagrant ssh app -c "flake8 /opt/app/apps --exclude migrations || echo flake8 check failed"

vagrant ssh app -c "cd /opt/app && npm run lint"
