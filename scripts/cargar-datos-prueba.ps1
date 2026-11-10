param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sqlFile = Join-Path $projectRoot 'scripts\seed-datos-prueba.sql'

if (-not (Test-Path $sqlFile)) {
    Write-Error "No se encontró el archivo $sqlFile"
    exit 1
}

# Detectar automáticamente el contenedor de PostgreSQL (p. ej. cargauy-p1-postgres-1 o postgres)
$pgContainer = (docker ps --format "{{.Names}}" | Where-Object { $_ -match "postgres" } | Select-Object -First 1)
if (-not $pgContainer) {
    $pgContainer = "cargauy-p1-postgres-1"
}

Write-Host "Cargando datos de prueba en PostgreSQL ($pgContainer)..." -ForegroundColor Cyan
docker cp $sqlFile "${pgContainer}:/tmp/seed-datos-prueba.sql"
docker exec -i $pgContainer psql -U cargauy -d cargauy -v ON_ERROR_STOP=1 -f /tmp/seed-datos-prueba.sql

if ($LASTEXITCODE -eq 0) {
    Write-Host "¡Datos de prueba cargados con éxito con codificación UTF-8!" -ForegroundColor Green
} else {
    Write-Error "Hubo un error al cargar los datos de prueba. Verificá que el contenedor postgres ($pgContainer) esté corriendo."
}
