param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sqlFile = Join-Path $projectRoot 'scripts\seed-datos-prueba.sql'

if (-not (Test-Path $sqlFile)) {
    Write-Error "No se encontró el archivo $sqlFile"
    exit 1
}

Write-Host "Cargando datos de prueba en la base de datos de Docker (cargauy-p1-postgres-1)..." -ForegroundColor Cyan
Get-Content $sqlFile -Raw -Encoding UTF8 | docker exec -i cargauy-p1-postgres-1 psql -U cargauy -d cargauy

if ($LASTEXITCODE -eq 0) {
    Write-Host "¡Datos de prueba cargados con éxito!" -ForegroundColor Green
} else {
    Write-Error "Hubo un error al cargar los datos de prueba. Verificá que el contenedor postgres esté corriendo."
}
