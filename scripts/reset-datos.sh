#!/bin/bash
set -euo pipefail

SKIP_RELOAD=false
if [ "${1:-}" = "--no-reload" ]; then
  SKIP_RELOAD=true
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESET_SQL="$SCRIPT_DIR/reset-datos.sql"
SEED_SQL="$SCRIPT_DIR/seed-datos-prueba.sql"
ENV_FILE="$PROJECT_ROOT/.env"

POSTGRES_CONTAINER=$(docker ps --format "{{.Names}}" | grep -i "postgres" | head -n 1 || true)
MONGO_CONTAINER=$(docker ps --format "{{.Names}}" | grep -Ei "mongodb|mongo" | head -n 1 || true)
WILDFLY_CONTAINER=$(docker ps --format "{{.Names}}" | grep -i "wildfly" | head -n 1 || true)

if [ -z "$POSTGRES_CONTAINER" ] || [ -z "$MONGO_CONTAINER" ]; then
  echo "Error: PostgreSQL o MongoDB no están en ejecución. Levantá Docker con 'docker compose up -d'." >&2
  exit 1
fi

read_dotenv() {
  local key="$1"
  if [ -f "$ENV_FILE" ]; then
    grep -E "^${key}=" "$ENV_FILE" | head -n 1 | cut -d '=' -f2- | tr -d "\"'\r" || true
  fi
}

TRACKING_ENDPOINT=""
MONGO_DB=""
MONGO_COLLECTION=""
if [ -n "$WILDFLY_CONTAINER" ]; then
  TRACKING_ENDPOINT=$(docker exec "$WILDFLY_CONTAINER" sh -c 'printf "%s" "$TRACKING_ENDPOINT"' 2>/dev/null || true)
  MONGO_DB=$(docker exec "$WILDFLY_CONTAINER" sh -c 'printf "%s" "$MONGO_DB"' 2>/dev/null || true)
  MONGO_COLLECTION=$(docker exec "$WILDFLY_CONTAINER" sh -c 'printf "%s" "$MONGO_COLLECTION"' 2>/dev/null || true)
fi

[ -z "$TRACKING_ENDPOINT" ] && TRACKING_ENDPOINT="$(read_dotenv TRACKING_ENDPOINT)"
[ -z "$TRACKING_ENDPOINT" ] && TRACKING_ENDPOINT="https://cargauy-mock-tracking-latest.onrender.com"
TRACKING_ENDPOINT="${TRACKING_ENDPOINT%/}"

[ -z "$MONGO_DB" ] && MONGO_DB="$(read_dotenv MONGO_DB)"
[ -z "$MONGO_DB" ] && MONGO_DB="cargauy_tracking"

[ -z "$MONGO_COLLECTION" ] && MONGO_COLLECTION="$(read_dotenv MONGO_COLLECTION)"
[ -z "$MONGO_COLLECTION" ] && MONGO_COLLECTION="eventos_posicion"

PG_USER=$(docker exec "$POSTGRES_CONTAINER" sh -c 'printf "%s" "$POSTGRES_USER"' 2>/dev/null || echo "cargauy")
PG_DB=$(docker exec "$POSTGRES_CONTAINER" sh -c 'printf "%s" "$POSTGRES_DB"' 2>/dev/null || echo "cargauy")
[ -z "$PG_USER" ] && PG_USER="cargauy"
[ -z "$PG_DB" ] && PG_DB="cargauy"

echo "[1/4] Deteniendo y reiniciando nodo periférico de Tracking ($TRACKING_ENDPOINT)..."
curl -fsS --max-time 15 -X POST -H "Content-Type: application/json" -d '{}' "$TRACKING_ENDPOINT/detener" >/dev/null 2>&1 || echo "  - Aviso: /detener no disponible"
curl -fsS --max-time 15 -X POST -H "Content-Type: application/json" -d '{}' "$TRACKING_ENDPOINT/reiniciar" >/dev/null 2>&1 || echo "  - Aviso: /reiniciar no disponible"

echo "[2/4] Limpiando MongoDB ($MONGO_CONTAINER -> $MONGO_DB.$MONGO_COLLECTION)..."
docker exec "$MONGO_CONTAINER" mongosh "mongodb://127.0.0.1:27017/$MONGO_DB" --quiet --eval "
const colName = '$MONGO_COLLECTION';
const res = db.getCollection(colName).deleteMany({});
db.getCollectionNames().forEach(name => {
  if (!name.startsWith('system.') && name !== colName) {
    const extra = db.getCollection(name).deleteMany({});
    print('  - Colección adicional ' + name + ': eliminados=' + extra.deletedCount);
  }
});
const legacyDb = db.getSiblingDB('cargauy');
if (legacyDb.getCollectionNames().includes('posiciones')) {
  const leg = legacyDb.posiciones.deleteMany({});
  print('  - Colección heredada cargauy.posiciones: eliminados=' + leg.deletedCount);
}
const remaining = db.getCollection(colName).countDocuments();
print('  - MongoDB ' + db.getName() + '.' + colName + ': eliminados=' + res.deletedCount + ', restantes=' + remaining);
if (remaining !== 0) { throw new Error('Quedaron documentos residuales en MongoDB: ' + remaining); }
"

echo "[3/4] Reseteando PostgreSQL ($POSTGRES_CONTAINER -> $PG_DB) y recargando seed..."
cat "$RESET_SQL" "$SEED_SQL" | docker exec -i "$POSTGRES_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -v ON_ERROR_STOP=1

if [ "$SKIP_RELOAD" = false ] && [ -n "$WILDFLY_CONTAINER" ]; then
  echo "[4/4] Recargando WildFly ($WILDFLY_CONTAINER) para limpiar estado en memoria..."
  docker exec "$WILDFLY_CONTAINER" /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command=":reload" >/dev/null
  for _ in $(seq 1 30); do
    sleep 2
    if docker exec "$WILDFLY_CONTAINER" /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command=":read-attribute(name=server-state)" 2>/dev/null | grep -q '"running"'; then
      echo "  - WildFly operativo y con memoria limpia."
      break
    fi
  done
fi

echo "¡Datos limpiados y semilla cargada correctamente (PostgreSQL + MongoDB + Tracking)!"
