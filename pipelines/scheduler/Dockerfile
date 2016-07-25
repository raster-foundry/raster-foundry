FROM python:2.7-slim

MAINTAINER Azavea <systems@azavea.com>

ENV LUIGI_CONFIG_DIR /etc/luigi/
ENV LUIGI_CONFIG_PATH $LUIGI_CONFIG_DIR/scheduler.cfg

COPY requirements.txt /tmp/
RUN set -ex \
  && pip install --no-cache-dir -r /tmp/requirements.txt \
  && rm /tmp/requirements.txt

RUN mkdir /etc/luigi && mkdir /var/lib/luigi-scheduler
COPY luigi-scheduler.cfg $LUIGI_CONFIG_PATH
COPY logging.cfg $LUIGI_CONFIG_DIR

EXPOSE 8082
ENTRYPOINT /usr/local/bin/luigid
