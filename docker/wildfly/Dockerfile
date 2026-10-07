# =====================================================================
# Etapa 1: compila el EAR con Maven (así alcanza con "docker compose up --build")
# =====================================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Primero solo los POM, para cachear las dependencias entre builds
COPY pom.xml .
COPY ejb/pom.xml ejb/
COPY web/pom.xml web/
COPY ear/pom.xml ear/
RUN mvn -B -q dependency:go-offline -DskipTests || true

# Después el código fuente
COPY ejb/src ejb/src
COPY web/src web/src
COPY ear/src ear/src
RUN mvn -B package -DskipTests

# =====================================================================
# Etapa 2: WildFly configurado con PostgreSQL + JMS
# =====================================================================
# (WildFly 41 no publica imagen -jdk17; el bytecode Java 17 corre sin problema en JDK 21)
FROM quay.io/wildfly/wildfly:41.0.0.Final-jdk21

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

# Copiar el EAR compilado en la etapa 1
COPY --from=build --chown=jboss:jboss /build/ear/target/CargaUY.ear /opt/jboss/wildfly/standalone/deployments/

EXPOSE 8080 9990

# Iniciar WildFly con perfil full (JMS / ActiveMQ Artemis habilitado)
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
