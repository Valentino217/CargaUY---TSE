param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resetSql = Join-Path $projectRoot 'scripts\reset-datos.sql'
$seedSql = Join-Path $projectRoot 'scripts\seed-datos-prueba.sql'

if (-not (Test-Path $resetSql) -or -not (Test-Path $seedSql)) {
    Write-Error "No se encontraron los archivos SQL necesarios en la carpeta scripts."
    exit 1
}

Write-Host "Vaciando tablas de la base de datos PostgreSQL..." -ForegroundColor Yellow
docker cp $resetSql cargauy-p1-postgres-1:/tmp/reset-datos.sql
docker exec -i cargauy-p1-postgres-1 psql -U cargauy -d cargauy -v ON_ERROR_STOP=1 -f /tmp/reset-datos.sql

Write-Host "Limpiando colección de telemetría en MongoDB..." -ForegroundColor Yellow
docker exec -i cargauy-p1-mongodb-1 mongosh cargauy_tracking --eval "db.eventos_posicion.deleteMany({})" 2>$null

Write-Host "Cargando datos de prueba iniciales limpios..." -ForegroundColor Cyan
docker cp $seedSql cargauy-p1-postgres-1:/tmp/seed-datos-prueba.sql
docker exec -i cargauy-p1-postgres-1 psql -U cargauy -d cargauy -v ON_ERROR_STOP=1 -f /tmp/seed-datos-prueba.sql

if ($LASTEXITCODE -eq 0) {
    Write-Host "¡Base de datos reseteada con éxito a su estado inicial de prueba con codificación UTF-8!" -ForegroundColor Green
} else {
    Write-Error "Ocurrió un error al recargar los datos."
}
