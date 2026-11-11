#!/bin/sh
set -e

export PORT="${PORT:-80}"

# Auto-detect system DNS resolver from /etc/resolv.conf, fallback to Docker/public DNS
DETECTED_RESOLVER=$(awk 'BEGIN{ORS=" "} /^nameserver/{print $2}' /etc/resolv.conf 2>/dev/null)
export RESOLVER="${DETECTED_RESOLVER:-127.0.0.11 1.1.1.1 8.8.8.8}"

if [ -z "$BACKEND_URL" ]; then
    export BACKEND_URL="http://backend:8080"
elif echo "$BACKEND_URL" | grep -qvE '^https?://'; then
    # If it's a bare Render private service name without port, append :8080
    if echo "$BACKEND_URL" | grep -qvE '(:[0-9]+|\.onrender\.com)'; then
        export BACKEND_URL="http://${BACKEND_URL}:8080"
    elif echo "$BACKEND_URL" | grep -qE '\.onrender\.com'; then
        export BACKEND_URL="https://${BACKEND_URL}"
    else
        export BACKEND_URL="http://${BACKEND_URL}"
    fi
fi

envsubst '$PORT $BACKEND_URL $RESOLVER' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
