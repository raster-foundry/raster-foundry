FROM openjdk:11-slim

RUN \
    adduser --home /var/lib/rf --shell /sbin/nologin --disabled-password --gecos "" rf

COPY ./target/scala-2.12/api-assembly.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-jar", "api-assembly.jar"]
