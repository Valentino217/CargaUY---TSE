#!/bin/bash
set -e

LDAP_CONTAINER=$(docker ps --format "{{.Names}}" | grep -i "ldap" | head -n 1)
LDAP_CONTAINER=${LDAP_CONTAINER:-cargauy-p1-ldap-1}

ROOT_PW=$(docker exec "$LDAP_CONTAINER" sh -c 'printf "%s" "$LDAP_ROOT_PASSWORD"')

echo "Insertando cuentas LDAP faltantes en $LDAP_CONTAINER..."
docker exec -i "$LDAP_CONTAINER" ldapadd -x -c -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$ROOT_PW" 2>/dev/null << 'EOF' || true
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
docker exec -i "$LDAP_CONTAINER" ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$ROOT_PW" -s "admin123" "uid=martin.admin,ou=people,dc=cargauy,dc=local" 2>/dev/null || true
docker exec -i "$LDAP_CONTAINER" ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$ROOT_PW" -s "admin123" "uid=admin.central,ou=people,dc=cargauy,dc=local" 2>/dev/null || true
docker exec -i "$LDAP_CONTAINER" ldappasswd -x -H ldap://127.0.0.1:1389 -D "cn=manager,dc=cargauy,dc=local" -w "$ROOT_PW" -s "autoridad123" "uid=alberto.autoridad,ou=people,dc=cargauy,dc=local" 2>/dev/null || true

echo "¡Directorio LDAP actualizado con éxito!"
echo "Cuentas configuradas:"
echo "  - admin.central / admin123"
echo "  - martin.admin / admin123"
echo "  - alberto.autoridad / autoridad123"
