FROM python:2.7

ENV AIRFLOW_HOME /usr/local/airflow

COPY requirements.txt /tmp/
RUN set -ex \
    addgroup --system airflow \
    && adduser --disabled-password --system --group \
               --uid 1000 --home /var/lib/airflow \
               --shell /usr/sbin/nologin \
               airflow \
    && mkdir -p ${AIRFLOW_HOME} \
    && chown airflow:airflow -R ${AIRFLOW_HOME} \
    && buildDeps=' \
       python-dev/sid \
    ' \
    && gdalDeps=' \
       gdal-bin/sid \
       python-gdal/sid \
    ' \
    && javaDeps=' \
       openjdk-8-jre \
       ca-certificates-java \
    ' \
    && echo 'deb http://deb.debian.org/debian jessie-backports main' > /etc/apt/sources.list.d/jessie-backports.list \
    && echo 'deb http://http.us.debian.org/debian sid main non-free contrib' > /etc/apt/sources.list.d/sid.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends ${buildDeps} \
    && apt-get install -y --no-install-recommends ${gdalDeps} \
    && apt-get install -y --no-install-recommends ${javaDeps} \
    && pip install --no-cache-dir \
           numpy==$(grep "numpy" /tmp/requirements.txt | cut -d= -f3) \
    && pip install --no-cache-dir -r /tmp/requirements.txt \
    && apt-get purge -y --auto-remove ${buildDeps} \
    && rm -rf /var/lib/apt/lists/*

COPY rf/ /tmp/rf
RUN (cd /tmp/rf && python setup.py install)

USER airflow
WORKDIR ${AIRFLOW_HOME}

COPY usr/local/airflow/airflow.cfg ${AIRFLOW_HOME}/airflow.cfg
COPY usr/local/airflow/plugins/ ${AIRFLOW_HOME}/plugins/
COPY dags/ /opt/raster-foundry/app-tasks/dags/
COPY jars/ /opt/raster-foundry/jars/
