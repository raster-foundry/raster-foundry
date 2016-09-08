FROM nginx:1.10

RUN mkdir -p /srv/dist/angular && \
    mkdir -p /srv/dist/docs/swagger

COPY srv/dist /srv/dist/
RUN chown nginx:nginx -R /srv/dist/

COPY etc/nginx/nginx.conf /etc/nginx/nginx.conf
COPY etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
