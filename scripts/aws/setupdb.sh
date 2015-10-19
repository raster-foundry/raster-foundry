#!/bin/bash

# Export settings required to run psql non-interactively
export PGHOST=$(cat /etc/rf.d/env/RF_DB_HOST)
export PGDATABASE=$(cat /etc/rf.d/env/RF_DB_NAME)
export PGUSER=$(cat /etc/rf.d/env/RF_DB_USER)
export PGPASSWORD=$(cat /etc/mmw.d/env/RF_DB_PASSWORD)

# Ensure that the PostGIS extension exists
psql -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# Run migrations
envdir /etc/rf.d/env /opt/app/manage.py migrate

# Enable DB Cache
envdir /etc/rf.d/env /opt/app/manage.py createcachetable
