#!/bin/sh
set -eu

TRACKING_BASE="${TRACKING_ENDPOINT:-https://cargauy-mock-tracking-latest.onrender.com}"
TRACKING_BASE="${TRACKING_BASE%/}"

echo "[reset] Deteniendo y reiniciando nodo periferico de Tracking ($TRACKING_BASE)..."
wget -qO- --timeout=12 --post-data='{}' --header='Content-Type: application/json' \
  "$TRACKING_BASE/detener" >/dev/null 2>&1 || echo "[reset] Aviso: no se pudo contactar /detener en $TRACKING_BASE (se continua)."
wget -qO- --timeout=12 --post-data='{}' --header='Content-Type: application/json' \
  "$TRACKING_BASE/reiniciar" >/dev/null 2>&1 || echo "[reset] Aviso: no se pudo contactar /reiniciar en $TRACKING_BASE (se continua)."

echo "[reset] Reseteando PostgreSQL y recargando datos de prueba..."
exec psql \
  -h "${PGHOST:-postgres}" \
  -U "${PGUSER:-cargauy}" \
  -d "${PGDATABASE:-cargauy}" \
  -v ON_ERROR_STOP=1 \
  -f /scripts/reset-datos.sql \
  -f /scripts/seed-datos-prueba.sql
