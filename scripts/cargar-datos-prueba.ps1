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

Write-Host "Cargando datos de prueba en la base de datos de Docker (cargauy-p1-postgres-1)..." -ForegroundColor Cyan
docker cp $sqlFile cargauy-p1-postgres-1:/tmp/seed-datos-prueba.sql
docker exec -i cargauy-p1-postgres-1 psql -U cargauy -d cargauy -v ON_ERROR_STOP=1 -f /tmp/seed-datos-prueba.sql

if ($LASTEXITCODE -eq 0) {
    Write-Host "¡Datos de prueba cargados con éxito con codificación UTF-8!" -ForegroundColor Green
} else {
    Write-Error "Hubo un error al cargar los datos de prueba. Verificá que el contenedor postgres esté corriendo."
}
