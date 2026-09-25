FROM quay.io/wildfly/wildfly:41.0.0.Final-jdk17

USER root

# Crear directorio para el módulo JDBC de PostgreSQL
RUN mkdir -p /opt/jboss/wildfly/modules/org/postgresql/main && \
    chown -R jboss:jboss /opt/jboss/wildfly/modules/org

USER jboss

# Copiar el driver JDBC y el descriptor de módulo
COPY --chown=jboss:jboss docker/wildfly/postgresql-42.7.4.jar /opt/jboss/wildfly/modules/org/postgresql/main/
COPY --chown=jboss:jboss docker/wildfly/module.xml /opt/jboss/wildfly/modules/org/postgresql/main/

# Copiar y ejecutar script de configuración CLI contra standalone-full.xml
COPY --chown=jboss:jboss docker/wildfly/configure-wildfly.cli /opt/jboss/
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/opt/jboss/configure-wildfly.cli && \
    rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history

# Copiar EAR compilado a la carpeta de despliegues
COPY --chown=jboss:jboss ear/target/CargaUY.ear /opt/jboss/wildfly/standalone/deployments/

EXPOSE 8080 9990

# Iniciar WildFly con perfil full (JMS / ActiveMQ Artemis habilitado)
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
