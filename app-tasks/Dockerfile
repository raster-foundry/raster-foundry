FROM quay.io/azavea/openjdk-gdal:2.4-jdk11-slim
# did you change the image here, possibly to upgrade gdal?
# if so, make sure that you verify that tifs that have trouble
# becoming COGs due to the tiling scheme can still be processed
# successfully.
# https://github.com/raster-foundry/raster-foundry/pull/5573

COPY rf/requirements.txt /tmp/
RUN set -ex \
    && gdalDeps=' \
       python3-pip \
       python3-setuptools \
       python3-dev \
       python3-requests \
       build-essential \
       imagemagick \
    ' \
    && apt-get update \
    && apt-get install -y --no-install-recommends ${gdalDeps} wget \
    && pip3 install --no-cache-dir -r /tmp/requirements.txt \
    && apt-get purge -y build-essential python-dev \
    && apt-get -y autoremove \
    && rm -rf /var/lib/apt/lists/*

COPY jars/ /opt/raster-foundry/jars/

COPY rf/ /tmp/rf
COPY completion.bash /tmp/rf/completion.bash

RUN (cat /tmp/rf/completion.bash | tee -a /root/.bashrc && cd /tmp/rf && python3 setup.py install)

ENV CURL_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt
