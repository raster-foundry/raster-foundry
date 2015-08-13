#!/bin/bash

# Run a gunicorn webserver with expanded logging and auto-restart

set -e
set -x

vagrant ssh app -c "sudo service rf-app stop || /bin/true"
vagrant ssh app -c "cd /opt/app/ && envdir /etc/rf.d/env gunicorn --config /etc/rf.d/gunicorn.py rf.wsgi"
