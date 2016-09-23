#!/bin/bash

S3URL="${1}"

$SPARK_HOME/bin/spark-submit \
  --class com.azavea.rf.Ingest \
  --master local[*] \
  target/scala-2.11/rf-ingest-assembly-0.1-SNAPSHOT.jar \
  "${S3URL}"
