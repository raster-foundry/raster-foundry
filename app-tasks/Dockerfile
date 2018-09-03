FROM openjdk:8-jdk-slim

ENV SPARK_VERSION 2.1.0
ENV HADOOP_VERSION 2.7
ENV PATH=${PATH}:/usr/lib/spark/sbin:/usr/lib/spark/bin

COPY rf/requirements.txt /tmp/
RUN set -ex \
    && gdalDeps=' \
       gdal-bin/sid \
       python-gdal/sid \
       python-pip \
       python-setuptools \
       python-dev \
       python-requests \
       build-essential \
       imagemagick \
    ' \
    && echo 'deb http://http.us.debian.org/debian sid main non-free contrib' > /etc/apt/sources.list.d/sid.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends ${gdalDeps} wget \
    && pip install --no-cache-dir \
           numpy==$(grep "numpy" /tmp/requirements.txt | cut -d= -f3) \
    && pip install --no-cache-dir -r /tmp/requirements.txt \
    && apt-get purge -y build-essential python-dev \
    && apt-get -y autoremove \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /usr/lib/spark \
    && wget -qO- http://d3kbcqa49mib13.cloudfront.net/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
      | tar -xzC /usr/lib/spark --strip-components=1

COPY jars/ /opt/raster-foundry/jars/

COPY rf/ /tmp/rf
COPY completion.bash /tmp/rf/completion.bash
RUN (cat /tmp/rf/completion.bash | tee -a /root/.bashrc && cd /tmp/rf && python setup.py install)
