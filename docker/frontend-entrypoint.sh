#!/bin/sh
set -e

export PORT="${PORT:-80}"

if [ -z "$BACKEND_URL" ]; then
    export BACKEND_URL="http://backend:8080"
elif ! echo "$BACKEND_URL" | grep -qE '^https?://'; then
    export BACKEND_URL="https://$BACKEND_URL"
fi

envsubst '$PORT $BACKEND_URL' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
