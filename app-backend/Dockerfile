FROM quay.io/azavea/scala:2.11.8

COPY ./ /opt/raster-foundry
WORKDIR /opt/raster-foundry

RUN ./sbt update && \
    ./sbt compile

CMD ["./sbt"]
