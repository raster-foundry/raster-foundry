FROM openjdk:8-jre

COPY ./ /opt/raster-foundry
WORKDIR /opt/raster-foundry

RUN ./sbt migrations/update && \
    ./sbt migrations/compile

CMD ["./sbt"]
