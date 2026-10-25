#!/bin/sh
# Starts the log formatter, then nginx. nginx writes its access + error logs into a pipe in /tmp
# (the only writable place; the rest of the filesystem is read-only) and logfmt.awk prints
# readable, coloured lines to stdout → `docker logs confess-web`.
set -eu

PIPE=/tmp/nginx-log.pipe
rm -f "$PIPE"
mkfifo -m 600 "$PIPE"

# Keep a reader on the pipe for the container's whole life (restarted if it ever exits),
# so nginx never blocks writing a log line.
(
  while :; do
    awk -v colors="${LOG_COLORS:-1}" -f /etc/nginx/logfmt.awk < "$PIPE" || true
  done
) &

exec /docker-entrypoint.sh nginx -g 'daemon off;'
