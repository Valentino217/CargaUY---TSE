#!/bin/sh
set -eu
: "${LDAP_ROOT_PASSWORD:?Falta LDAP_ROOT_PASSWORD}"
: "${LDAP_APP_PASSWORD:?Falta LDAP_APP_PASSWORD}"
: "${LDAP_APP_UID:?Falta LDAP_APP_UID}"
case "$LDAP_APP_UID" in *[!a-zA-Z0-9._-]*|'') echo "UID inválido"; exit 1;; esac
mkdir -p /data
ROOT_HASH=$(slappasswd -s "$LDAP_ROOT_PASSWORD")
APP_HASH=$(slappasswd -s "$LDAP_APP_PASSWORD")
LISTEN="ldap://0.0.0.0:1389/"
TLS_CONFIG=""
if [ -n "${LDAP_TLS_CERT:-}" ]; then
 : "${LDAP_TLS_KEY:?Falta LDAP_TLS_KEY}"
 : "${LDAP_TLS_CA:?Falta LDAP_TLS_CA}"
 TLS_CONFIG="TLSCertificateFile $LDAP_TLS_CERT
TLSCertificateKeyFile $LDAP_TLS_KEY
TLSCACertificateFile $LDAP_TLS_CA"
 LISTEN="$LISTEN ldaps://0.0.0.0:1636/"
fi
cat >/tmp/slapd.conf <<EOF
include /etc/ldap/schema/core.schema
include /etc/ldap/schema/cosine.schema
include /etc/ldap/schema/inetorgperson.schema
$TLS_CONFIG
pidfile /tmp/slapd.pid
argsfile /tmp/slapd.args
modulepath /usr/lib/ldap
moduleload back_mdb
database mdb
maxsize 1073741824
suffix "dc=cargauy,dc=local"
rootdn "cn=manager,dc=cargauy,dc=local"
rootpw $ROOT_HASH
directory /data
index objectClass eq
access to attrs=userPassword by self write by anonymous auth by * none
access to * by self read by * none
EOF
if [ ! -f /data/data.mdb ]; then
 cat >/tmp/seed.ldif <<EOF
dn: dc=cargauy,dc=local
objectClass: top
objectClass: dcObject
objectClass: organization
dc: cargauy
o: CargaUY local

dn: ou=people,dc=cargauy,dc=local
objectClass: organizationalUnit
ou: people

dn: uid=$LDAP_APP_UID,ou=people,dc=cargauy,dc=local
objectClass: inetOrgPerson
uid: $LDAP_APP_UID
cn: Administrador de pruebas
sn: Pruebas
userPassword: $APP_HASH
EOF
 slapadd -f /tmp/slapd.conf -l /tmp/seed.ldif
 rm -f /tmp/seed.ldif
fi
exec slapd -f /tmp/slapd.conf -h "$LISTEN" -d 0
