param()
$ErrorActionPreference = 'Stop'

Write-Host "Verificando que el contenedor de OpenLDAP esté activo..." -ForegroundColor Cyan

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.local-p1\servicios.env'

# Leer LDAP_ROOT_PASSWORD desde .local-p1/servicios.env si existe; si no, consultar la variable de entorno dentro del contenedor
$ldapContainer = (docker ps --format "{{.Names}}" | Where-Object { $_ -match "ldap" } | Select-Object -First 1)
if (-not $ldapContainer) {
    $ldapContainer = "cargauy-p1-ldap-1"
}

$rootPassword = $null
if (Test-Path $envFile) {
    $line = Get-Content $envFile | Where-Object { $_ -match '^LDAP_ROOT_PASSWORD=' } | Select-Object -First 1
    if ($line) {
        $rootPassword = ($line -split '=', 2)[1].Trim()
    }
}
if (-not $rootPassword) {
    $rootPassword = (docker exec $ldapContainer sh -c 'printf "%s" "$LDAP_ROOT_PASSWORD"' 2>$null)
}

$ldif = @"
dn: uid=martin.admin,ou=people,dc=cargauy,dc=local
objectClass: inetOrgPerson
uid: martin.admin
cn: Martin Admin
sn: Admin
userPassword: admin123

dn: uid=admin.central,ou=people,dc=cargauy,dc=local
objectClass: inetOrgPerson
uid: admin.central
cn: Admin Central
sn: Central
userPassword: admin123

dn: uid=alberto.autoridad,ou=people,dc=cargauy,dc=local
objectClass: inetOrgPerson
uid: alberto.autoridad
cn: Alberto Director
sn: Autoridad
userPassword: autoridad123
"@

Write-Host "Insertando cuentas LDAP faltantes en $ldapContainer..." -ForegroundColor Cyan
$ldif | docker exec -i $ldapContainer ldapadd -x -c -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$rootPassword" 2>$null

Write-Host "Asegurando contraseñas correctas (admin123 / autoridad123)..." -ForegroundColor Cyan
docker exec -i $ldapContainer ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$rootPassword" -s "admin123" "uid=martin.admin,ou=people,dc=cargauy,dc=local" 2>$null
docker exec -i $ldapContainer ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$rootPassword" -s "admin123" "uid=admin.central,ou=people,dc=cargauy,dc=local" 2>$null
docker exec -i $ldapContainer ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$rootPassword" -s "autoridad123" "uid=alberto.autoridad,ou=people,dc=cargauy,dc=local" 2>$null

Write-Host "¡Directorio LDAP actualizado con éxito!" -ForegroundColor Green
Write-Host "Cuentas configuradas:" -ForegroundColor White
Write-Host "  - admin.central / admin123" -ForegroundColor Yellow
Write-Host "  - martin.admin / admin123" -ForegroundColor Yellow
Write-Host "  - alberto.autoridad / autoridad123" -ForegroundColor Yellow
Write-Host ""
Write-Host "IMPORTANTE: Si todavía no corriste los datos de prueba en la base de datos PostgreSQL, ejecutá:" -ForegroundColor Cyan
Write-Host "  ./scripts/cargar-datos-prueba.ps1" -ForegroundColor White
