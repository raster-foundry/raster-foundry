FROM openjdk:8-jre-alpine

COPY rf/requirements.txt /tmp/

RUN set -ex \
        && echo "@edge http://dl-cdn.alpinelinux.org/alpine/edge/testing" >> /etc/apk/repositories \
        && apk add --update --no-cache --virtual .build-deps \
               build-base \
               python-dev \
        && apk add --no-cache \
               bash \
               gdal@edge \
               gdal-dev@edge \
               python \
               imagemagick \
               py-gdal@edge \
               py-pip \
               py-setuptools \
               proj4-dev@edge \
        && ln -s /usr/include/locale.h /usr/include/xlocale.h \
        && pip install --no-cache-dir numpy==$(grep "numpy" /tmp/requirements.txt | cut -d= -f3) \
        && pip install --no-cache-dir -r /tmp/requirements.txt \
        && apk del .build-deps \
        && rm -rf /var/cache/apk/*

COPY jars/ /opt/raster-foundry/jars/

COPY rf/ /tmp/rf
RUN (cd /tmp/rf && python setup.py install)
