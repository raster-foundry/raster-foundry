FROM python:2.7-slim

# Install requirements before copying source to take advantage of caching
COPY ./requirements.txt /tmp/requirements.txt
RUN set -ex \
  && buildDeps=' \
    git \
  ' \
  && apt-get update && apt-get install -y ${buildDeps} --no-install-recommends \
  && rm -rf /var/lib/apt/lists/* \
  && pip install --no-cache-dir -r /tmp/requirements.txt \
  && rm /tmp/requirements.txt \
  && pip install --no-cache-dir -e git+https://github.com/boto/botoflow.git@bdb87b59226e249a1f8bf4f8914e44815dcffcab#egg=botoflow \
  && apt-get purge -y --auto-remove ${buildDeps}

COPY ./ /opt/raster-foundry/app-tasks
WORKDIR /opt/raster-foundry/app-tasks

# Install rf project
RUN python setup.py install
