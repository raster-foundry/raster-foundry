FROM openjdk:8-jre-alpine

ENV ASPECTJ_WEAVER_VERSION 1.8.10

RUN \
      addgroup -S rf \
      && adduser -D -S -h /var/lib/rf -s /sbin/nologin -G rf rf \
      && apk add --no-cache --virtual .build-deps \
         ca-certificates \
         tar \
         wget \
      && wget -qO /var/lib/rf/aspectjweaver.jar https://s3.amazonaws.com/rasterfoundry-global-artifacts-us-east-1/aspectjweaver/aspectjweaver-$ASPECTJ_WEAVER_VERSION.jar \
      && apk del .build-deps

COPY ./target/scala-2.11/rf-server.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-jar", "rf-server.jar"]
