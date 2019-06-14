FROM quay.io/azavea/openjdk-gdal:2.4-jdk8-slim

RUN set -ex && \
	apt-get update && \
	apt-get install -y --no-install-recommends \
		git \
	&& \
	rm -rf /var/lib/apt/lists/*

ENV GIT_DISCOVERY_ACROSS_FILESYSTEM 1
