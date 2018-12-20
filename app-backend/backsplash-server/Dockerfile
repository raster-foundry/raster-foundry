FROM daunnc/openjdk-gdal:2.3.2

ENV ASPECTJ_WEAVER_VERSION 1.8.10

RUN \
  adduser --home /var/lib/rf --shell /sbin/nologin --disabled-password --gecos "" rf \
  && apt-get install apt-transport-https \
  && echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list \
  && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 \
  && apt-get update \
  && apt-get install -yq sbt

COPY ./target/scala-2.11/backsplash-assembly.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-jar", "backsplash-assembly.jar"]
