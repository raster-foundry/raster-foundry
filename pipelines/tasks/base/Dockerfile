FROM python:2.7-slim

MAINTAINER Azavea <systems@azavea.com>

ENV LUIGI_CONFIG_DIR /etc/luigi/
ENV LUIGI_CONFIG_PATH $LUIGI_CONFIG_DIR/worker.cfg

COPY requirements.txt /tmp/
RUN set -ex \
  && pip install --no-cache-dir -r /tmp/requirements.txt \
  && rm /tmp/requirements.txt

RUN mkdir /etc/luigi
COPY luigi-worker.cfg $LUIGI_CONFIG_PATH
COPY logging.cfg $LUIGI_CONFIG_DIR
