FROM quay.io/azavea/raster-vision:pytorch-0.13

COPY requirements.txt /tmp/

RUN pip3 install --no-cache-dir -r /tmp/requirements.txt

WORKDIR /opt/src