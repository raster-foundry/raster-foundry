FROM openjdk:8-jre-alpine

RUN \
      addgroup -S rf \
      && adduser -D -S -h /var/lib/rf -s /sbin/nologin -G rf rf

COPY ./target/scala-2.11/rf-backend.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-jar", "rf-backend.jar"]
