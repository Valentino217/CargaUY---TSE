FROM alpine:3.19
RUN apk add --no-cache openldap openldap-clients openldap-back-mdb && \
    mkdir -p /etc/ldap && ln -s /etc/openldap/schema /etc/ldap/schema && \
    mkdir -p /usr/lib/ldap && ln -s /usr/lib/openldap/* /usr/lib/ldap/
COPY iniciar.sh /iniciar.sh
RUN chmod 755 /iniciar.sh
EXPOSE 1389
ENTRYPOINT ["/bin/sh", "/iniciar.sh"]
