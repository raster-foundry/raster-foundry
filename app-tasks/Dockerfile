FROM openjdk:8-jre-slim

COPY rf/requirements.txt /tmp/
RUN set -ex \
    && gdalDeps=' \
       gdal-bin/sid \
       python-gdal/sid \
       python-pip \
       python-setuptools \
       python-dev \
       build-essential \
    ' \
    && echo 'deb http://http.us.debian.org/debian sid main non-free contrib' > /etc/apt/sources.list.d/sid.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends ${gdalDeps} \
    && pip install --no-cache-dir \
           numpy==$(grep "numpy" /tmp/requirements.txt | cut -d= -f3) \
    && pip install --no-cache-dir -r /tmp/requirements.txt \
    && apt-get purge -y build-essential python-dev \
    && apt-get -y autoremove \
    && rm -rf /var/lib/apt/lists/*

COPY jars/ /opt/raster-foundry/jars/

COPY rf/ /tmp/rf
RUN (cd /tmp/rf && python setup.py install)
