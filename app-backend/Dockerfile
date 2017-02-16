FROM openjdk:8-jre

COPY ./ /opt/raster-foundry
WORKDIR /opt/raster-foundry

RUN ./sbt update && \
    ./sbt compile

CMD ["./sbt"]
