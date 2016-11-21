FROM nginx:1.10

RUN mkdir -p /etc/nginx/includes && \
    mkdir -p /srv/dist/docs/swagger

COPY srv/dist /srv/dist/
RUN chown nginx:nginx -R /srv/dist/

COPY etc/nginx/nginx.conf /etc/nginx/nginx.conf
COPY etc/nginx/includes/*.conf /etc/nginx/includes/
COPY etc/nginx/conf.d/*.conf /etc/nginx/conf.d/
