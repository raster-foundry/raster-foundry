FROM python:2.7

ENV AIRFLOW_HOME /usr/local/airflow

COPY requirements.txt /tmp/

RUN set -ex \
  && buildDeps=' \
    python-dev \
  ' \
  && gdal=' \
    gdal-bin \
    libgdal1h \
    libgdal-dev \
  ' \
  && apt-get update && apt-get install -y --no-install-recommends ${buildDeps} ${gdal} \
  && pip install --no-cache-dir \
         numpy==$(grep "numpy" /tmp/requirements.txt | cut -d= -f3) \
  && pip install --no-cache-dir -r /tmp/requirements.txt \
  && apt-get purge -y --auto-remove ${buildDeps} \
  && rm -rf /var/lib/apt/lists/*

COPY usr/local/airflow/airflow.cfg ${AIRFLOW_HOME}/airflow.cfg
COPY rf/ /tmp/rf
COPY dags/ /opt/raster-foundry/app-tasks/dags/

RUN set -ex \
    && cd /tmp/rf \
    && python setup.py install \
    && rm -rf /tmp/rf

EXPOSE 8080 5555 8793

WORKDIR ${AIRFLOW_HOME}
