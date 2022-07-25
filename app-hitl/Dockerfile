FROM aaronxsu/raster-vision-pytorch:latest

COPY hitl/requirements.txt /tmp/

RUN pip3 install --no-cache-dir -r /tmp/requirements.txt

COPY hitl/ /tmp/hitl
COPY completion.bash /tmp/hitl/completion.bash

RUN (cat /tmp/hitl/completion.bash | tee -a /root/.bashrc && cd /tmp/hitl && python3 setup.py install)

ENV CURL_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt