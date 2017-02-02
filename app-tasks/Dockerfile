FROM python:2.7

ENV AIRFLOW_HOME /usr/local/airflow

COPY requirements.txt /tmp/
COPY rf/ /tmp/rf

RUN set -ex \
    addgroup --system airflow \
    && adduser --disabled-password --system --group \
               --uid 1000 --home ${AIRFLOW_HOME} \
               --shell /usr/sbin/nologin \
               airflow \
    && buildDeps=' \
       python-dev \
    ' \
    && gdal=' \
       gdal-bin \
       libgdal1h \
       libgdal-dev \
    ' \
    && echo "deb http://ftp.debian.org/debian jessie-backports main" > /etc/apt/sources.list.d/backports.list \
    && apt-get update && apt-get install -y --no-install-recommends ${buildDeps} ${gdal} \
    && pip install --no-cache-dir \
           numpy==$(grep "numpy" /tmp/requirements.txt | cut -d= -f3) \
    && pip install --no-cache-dir -r /tmp/requirements.txt \
    && (cd /tmp/rf && python setup.py install) \
    && apt-get purge -y --auto-remove ${buildDeps} \
    && rm -rf /var/lib/apt/lists/*

USER airflow
WORKDIR ${AIRFLOW_HOME}

COPY usr/local/airflow/airflow.cfg ${AIRFLOW_HOME}/airflow.cfg
COPY dags/ /opt/raster-foundry/app-tasks/dags/
