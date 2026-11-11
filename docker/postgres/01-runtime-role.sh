#!/bin/bash
set -eu

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=runtime_password="$LEDGERLINE_RUNTIME_PASSWORD" <<'SQL'
CREATE ROLE ledgerline_runtime LOGIN PASSWORD :'runtime_password';
GRANT CONNECT ON DATABASE ledgerline TO ledgerline_runtime;
GRANT USAGE ON SCHEMA public TO ledgerline_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ledgerline_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ledgerline_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ledgerline_owner IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ledgerline_runtime;
SQL
