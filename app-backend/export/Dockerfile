FROM quay.io/azavea/spark:2.1-hadoop2.7

USER root

RUN \
      mkdir -p /spark-state \
      && chown spark:spark -R /spark-state

COPY ./target/scala-2.11/rf-export.jar /opt/rf/jars/

USER spark
