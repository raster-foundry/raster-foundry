#!/bin/bash
set -e

if [[ -n "${RF_DEBUG}" ]]; then
    set -x
fi

function build_ingest_jar {
    echo "Building ingest JAR"
    docker-compose run --no-deps --rm --entrypoint ./sbt app-server ingest/assembly
}

function build_application_jar {
    echo "Building application JAR"
    docker-compose run --no-deps --entrypoint ./sbt --rm app-server app/assembly
}

function build_tile_server_jar {
    echo "Building tile server JAR"
    docker-compose run --no-deps --entrypoint ./sbt --rm tile-server tile/assembly
}
