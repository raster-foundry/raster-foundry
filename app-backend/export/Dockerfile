FROM quay.io/azavea/spark:2.0.1

USER root

RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823

ENV SBT_VERSION 0.13.12

RUN echo 'deb http://dl.bintray.com/sbt/debian /' > /etc/apt/sources.list.d/sbt.list

RUN \
      mkdir -p /spark-state \
      && chown spark:spark -R /spark-state \
      && apt-get update && apt-get install -y --no-install-recommends \
              sbt=$SBT_VERSION \
      && rm -rf /var/lib/apt/lists/*

COPY ./target/scala-2.11/rf-export.jar /opt/rf/jars/

USER spark
