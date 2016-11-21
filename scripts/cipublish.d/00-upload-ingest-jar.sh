#!/bin/bash

set -e

if [[ -n "${RF_DEBUG}" ]]; then
    set -x
fi

DIR="$(dirname "$0")"

function upload_ingest_jar {

    if [[ -z "${1}" ]]; then
        echo "This function requires a hash to uniquely identify the upload jar"
        exit 1
    else
        echo "Using upload hash: ${1}"
        UPLOAD_HASH="${1}"
    fi

    pushd "${DIR}/.."
    echo "Uploading Spark Ingest Jar to S3"
    aws s3 cp \
        "app-backend/ingest/target/scala-2.11/rf-ingest.jar" \
        "s3://rasterfoundry-global-artifacts-us-east-1/ingest/rf-ingest-${UPLOAD_HASH}.jar"
    popd
}
