FROM nginx:1.14

RUN mkdir -p /etc/nginx/includes

COPY etc/nginx/nginx.conf /etc/nginx/nginx.conf
COPY etc/nginx/includes/*.conf /etc/nginx/includes/
COPY etc/nginx/conf.d/backsplash.conf /etc/nginx/conf.d/default.conf
