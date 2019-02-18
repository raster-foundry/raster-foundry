FROM quay.io/azavea/openjdk-gdal:2.3.2-jdk8-slim

ENV ASPECTJ_WEAVER_VERSION 1.8.10

COPY ./target/scala-2.11/backsplash-assembly.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-jar", "backsplash-assembly.jar"]
