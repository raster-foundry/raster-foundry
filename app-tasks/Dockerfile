FROM python:2.7

ENV AIRFLOW_HOME /usr/local/airflow

COPY requirements.txt /tmp/

RUN set -ex \
  && pip install --no-cache-dir -r /tmp/requirements.txt \
  && rm /tmp/requirements.txt

COPY usr/local/airflow/airflow.cfg ${AIRFLOW_HOME}/airflow.cfg

EXPOSE 8080 5555 8793

COPY rf/ /tmp/rf
RUN set -ex \
    && cd /tmp/rf \
    && python setup.py install \
    && rm -rf /tmp/rf

WORKDIR ${AIRFLOW_HOME}
