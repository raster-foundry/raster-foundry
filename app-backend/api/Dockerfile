FROM openjdk:11-slim

RUN \
      addgroup -S rf \
      && adduser -D -S -h /var/lib/rf -s /sbin/nologin -G rf rf

COPY ./target/scala-2.12/api-assembly.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-jar", "api-assembly.jar"]
