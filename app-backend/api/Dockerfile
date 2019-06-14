FROM openjdk:11-slim

RUN \
    adduser --system --disabled-password --home /var/lib/rf --shell /sbin/nologin --disabled-password --group rf

COPY ./target/scala-2.12/api-assembly.jar /var/lib/rf/

USER rf
WORKDIR /var/lib/rf

ENTRYPOINT ["java"]
CMD ["-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "api-assembly.jar"]
