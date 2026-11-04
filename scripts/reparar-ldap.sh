#!/bin/bash
set -e

echo "Insertando cuentas LDAP faltantes..."
docker exec -i cargauy-p1-ldap-1 ldapadd -x -c -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" 2>/dev/null << 'EOF' || true
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
EOF

echo "Asegurando contraseñas correctas..."
docker exec -i cargauy-p1-ldap-1 ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" -s "admin123" "uid=martin.admin,ou=people,dc=cargauy,dc=local" 2>/dev/null || true
docker exec -i cargauy-p1-ldap-1 ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" -s "admin123" "uid=admin.central,ou=people,dc=cargauy,dc=local" 2>/dev/null || true
docker exec -i cargauy-p1-ldap-1 ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE" -s "autoridad123" "uid=alberto.autoridad,ou=people,dc=cargauy,dc=local" 2>/dev/null || true

echo "¡Directorio LDAP actualizado con éxito!"
echo "Cuentas configuradas:"
echo "  - admin.central / admin123"
echo "  - martin.admin / admin123"
echo "  - alberto.autoridad / autoridad123"
echo ""
echo "IMPORTANTE: Si no cargaste los datos de prueba en PostgreSQL, ejecutá:"
echo "  docker exec -i cargauy-p1-postgres-1 psql -U cargauy -d cargauy < scripts/seed-datos-prueba.sql"
