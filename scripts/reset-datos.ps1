param(
    [switch]$SkipWildflyReload
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resetSql = Join-Path $PSScriptRoot 'reset-datos.sql'
$seedSql = Join-Path $PSScriptRoot 'seed-datos-prueba.sql'
$envFile = Join-Path $projectRoot '.env'

if (-not (Test-Path $resetSql) -or -not (Test-Path $seedSql)) {
    throw "No se encontraron reset-datos.sql o seed-datos-prueba.sql en $PSScriptRoot."
}

function Get-RunningContainer {
    param([string]$Pattern, [string]$Fallback)
    $match = (docker ps --format "{{.Names}}" | Where-Object { $_ -match $Pattern } | Select-Object -First 1)
    if ($match) { return $match.Trim() }
    return $null
}

$postgresContainer = Get-RunningContainer -Pattern 'postgres' -Fallback 'cargauy-p1-postgres-1'
$mongoContainer = Get-RunningContainer -Pattern 'mongodb|mongo' -Fallback 'cargauy-p1-mongodb-1'
$wildflyContainer = Get-RunningContainer -Pattern 'wildfly' -Fallback 'cargauy-p1-wildfly-1'

if (-not $postgresContainer) {
    throw "No se encontró ningún contenedor de PostgreSQL en ejecución. Levantá Docker con 'docker compose up -d'."
}
if (-not $mongoContainer) {
    throw "No se encontró ningún contenedor de MongoDB en ejecución. Levantá Docker con 'docker compose up -d'."
}

function Read-DotEnvValue {
    param([string]$Key)
    if (-not (Test-Path $envFile)) { return $null }
    $line = Get-Content $envFile | Where-Object { $_ -match "^$Key=" } | Select-Object -First 1
    if ($line) { return ($line -split '=', 2)[1].Trim().Trim('"').Trim("'") }
    return $null
}

$trackingEndpoint = $null
$mongoDb = $null
$mongoCollection = $null

if ($wildflyContainer) {
    $trackingEndpoint = (docker exec $wildflyContainer sh -c 'printf "%s" "$TRACKING_ENDPOINT"' 2>$null)
    $mongoDb = (docker exec $wildflyContainer sh -c 'printf "%s" "$MONGO_DB"' 2>$null)
    $mongoCollection = (docker exec $wildflyContainer sh -c 'printf "%s" "$MONGO_COLLECTION"' 2>$null)
}

if ([string]::IsNullOrWhiteSpace($trackingEndpoint)) { $trackingEndpoint = Read-DotEnvValue -Key 'TRACKING_ENDPOINT' }
if ([string]::IsNullOrWhiteSpace($trackingEndpoint)) { $trackingEndpoint = 'https://cargauy-mock-tracking-latest.onrender.com' }
$trackingEndpoint = $trackingEndpoint.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($mongoDb)) { $mongoDb = Read-DotEnvValue -Key 'MONGO_DB' }
if ([string]::IsNullOrWhiteSpace($mongoDb)) { $mongoDb = 'cargauy_tracking' }

if ([string]::IsNullOrWhiteSpace($mongoCollection)) { $mongoCollection = Read-DotEnvValue -Key 'MONGO_COLLECTION' }
if ([string]::IsNullOrWhiteSpace($mongoCollection)) { $mongoCollection = 'eventos_posicion' }

$pgUser = (docker exec $postgresContainer sh -c 'printf "%s" "$POSTGRES_USER"' 2>$null)
$pgDb = (docker exec $postgresContainer sh -c 'printf "%s" "$POSTGRES_DB"' 2>$null)
if ([string]::IsNullOrWhiteSpace($pgUser)) { $pgUser = 'cargauy' }
if ([string]::IsNullOrWhiteSpace($pgDb)) { $pgDb = 'cargauy' }

Write-Host "[1/4] Deteniendo y reiniciando nodo periférico de Tracking ($trackingEndpoint)..." -ForegroundColor Cyan
foreach ($accion in @('detener', 'reiniciar')) {
    try {
        $null = Invoke-RestMethod -Method Post -Uri "$trackingEndpoint/$accion" -Body '{}' -ContentType 'application/json' -TimeoutSec 15
        Write-Host "  - /$accion OK" -ForegroundColor DarkGreen
    } catch {
        Write-Host "  - Aviso: no se pudo invocar /$accion en $trackingEndpoint ($($_.Exception.Message))" -ForegroundColor Yellow
    }
}

Write-Host "[2/4] Limpiando MongoDB ($mongoContainer -> $mongoDb.$mongoCollection)..." -ForegroundColor Cyan
$mongoScript = @"
const colName = '$mongoCollection';
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
"@
docker exec $mongoContainer mongosh "mongodb://127.0.0.1:27017/$mongoDb" --quiet --eval $mongoScript
if ($LASTEXITCODE -ne 0) {
    throw "Falló la limpieza de MongoDB en $mongoContainer."
}

Write-Host "[3/4] Reseteando PostgreSQL ($postgresContainer -> $pgDb) y recargando seed..." -ForegroundColor Cyan
$sqlPayload = (Get-Content $resetSql -Raw) + "`n" + (Get-Content $seedSql -Raw)
$sqlPayload | docker exec -i $postgresContainer psql -U $pgUser -d $pgDb -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
    throw "Falló la ejecución de reset-datos.sql / seed-datos-prueba.sql en PostgreSQL."
}

if (-not $SkipWildflyReload -and $wildflyContainer) {
    Write-Host "[4/4] Recargando WildFly ($wildflyContainer) para limpiar estado en memoria..." -ForegroundColor Cyan
    docker exec $wildflyContainer /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command=":reload" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Falló la recarga de WildFly en $wildflyContainer."
    }
    $deadline = (Get-Date).AddSeconds(60)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $stateOut = (docker exec $wildflyContainer /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command=":read-attribute(name=server-state)" 2>$null)
        if ($stateOut -match '"result"\s*=>\s*"running"') {
            $ready = $true
            break
        }
    }
    if (-not $ready) {
        Write-Host "  - Aviso: WildFly sigue recargándose; verificá su estado en unos segundos." -ForegroundColor Yellow
    } else {
        Write-Host "  - WildFly operativo y con memoria limpia." -ForegroundColor DarkGreen
    }
} else {
    Write-Host "[4/4] Recarga de WildFly omitida." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "¡Datos limpiados y semilla cargada correctamente (PostgreSQL + MongoDB + Tracking)!" -ForegroundColor Green
