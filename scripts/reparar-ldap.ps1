param()
$ErrorActionPreference = 'Stop'

Write-Host "Verificando que el contenedor de OpenLDAP esté activo..." -ForegroundColor Cyan

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

Write-Host "Insertando cuentas LDAP faltantes..." -ForegroundColor Cyan
$ldif | docker exec -i cargauy-p1-ldap-1 ldapadd -x -c -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" 2>$null

Write-Host "Asegurando contraseñas correctas (admin123 / autoridad123)..." -ForegroundColor Cyan
docker exec -i cargauy-p1-ldap-1 ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" -s "admin123" "uid=martin.admin,ou=people,dc=cargauy,dc=local" 2>$null
docker exec -i cargauy-p1-ldap-1 ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" -s "admin123" "uid=admin.central,ou=people,dc=cargauy,dc=local" 2>$null
docker exec -i cargauy-p1-ldap-1 ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" -s "autoridad123" "uid=alberto.autoridad,ou=people,dc=cargauy,dc=local" 2>$null

Write-Host "¡Directorio LDAP actualizado con éxito!" -ForegroundColor Green
Write-Host "Cuentas configuradas:" -ForegroundColor White
Write-Host "  - admin.central / admin123" -ForegroundColor Yellow
Write-Host "  - martin.admin / admin123" -ForegroundColor Yellow
Write-Host "  - alberto.autoridad / autoridad123" -ForegroundColor Yellow
Write-Host ""
Write-Host "IMPORTANTE: Si todavía no corriste los datos de prueba en la base de datos PostgreSQL, ejecutá:" -ForegroundColor Cyan
Write-Host "  ./scripts/cargar-datos-prueba.ps1" -ForegroundColor White
