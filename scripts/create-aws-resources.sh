#!/bin/bash

# Convenience script for manually running create-aws-resources.py on app VM

set -e
set -x

vagrant ssh app -c "cd /opt/app && envdir /etc/rf.d/env ./create-aws-resources.py"
