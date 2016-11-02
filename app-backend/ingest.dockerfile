FROM quay.io/azavea/spark:2.0.1

COPY ./ingest/target/scala-2.11/rf-ingest.jar /opt/rf-ingest.jar
COPY ./ingest/src/test/resources/ /opt/resources/

