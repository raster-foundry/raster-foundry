#!/bin/bash

# Run flake8 against the Django codebase

set -e
set -x

vagrant ssh app -c "flake8 /opt/app/apps --exclude migrations || echo flake8 check failed"
