FROM quay.io/azavea/openjdk-gdal:2.3.2-jdk8-slim

RUN \
    adduser --home /var/lib/rf --shell /sbin/nologin --disabled-password --gecos "" rf

COPY ./target/scala-2.12/backsplash-assembly.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-jar", "backsplash-assembly.jar"]
