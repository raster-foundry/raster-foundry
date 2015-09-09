#!/bin/bash

# Run unit tests

set -e
set -x

# Run flake8 against the Django codebase and output a known string so that
# the Jenkins text finder plugin can detect a failed check and mark the build
# unstable. This command should only fail the build if the `vagrant ssh`
# command itself fails.
vagrant ssh app -c "flake8 /opt/app/apps --exclude migrations || echo flake8 check failed"

# Run the Django test suite with --noinput flag.
vagrant ssh app -c "cd /opt/app && envdir /etc/rf.d/env ./manage.py test --noinput"

# Check for client-side JS lint.
vagrant ssh app -c "cd /opt/app && npm run lint"
