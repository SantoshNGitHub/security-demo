FROM nginx:1.25-alpine
RUN apk add --no-cache openssl
COPY nginx.conf /etc/nginx/nginx.conf
COPY ssl/ /etc/nginx/ssl/
RUN chmod 600 /etc/nginx/ssl/key.pem
