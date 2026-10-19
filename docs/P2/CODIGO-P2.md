# Código completo de la segunda guía P1

Actualizado para ID Uruguay de testing. Leer GUIA-P1-PARTE-2-LDAP.md y USUARIO-GUB-UY-REAL.md antes de copiar. Cada ruta es un destino relativo a la raíz del repositorio; no copiar las marcas de Markdown. Las credenciales reales se completan únicamente en configuración privada.

## .gitignore.fragment

Una línea para incorporar al .gitignore real antes de generar secretos.

```text
# Agregar a .gitignore antes de ejecutar preparar-local.ps1
/.local-p1/
```

## docker-compose.p1.tls.yml

Overlay TLS con certificados provistos por el equipo; no genera ni desactiva validación de certificados.

```yaml
# Overlay para pruebas TLS con certificados y claves suministrados por el equipo.
services:
  ldap:
    environment:
      LDAP_TLS_CERT: /tls/ldap.crt
      LDAP_TLS_KEY: /tls/ldap.key
      LDAP_TLS_CA: /tls/ca.crt
    ports: ["127.0.0.1:1636:1636"]
    volumes: ["./.local-p1/tls:/tls:ro"]
  mock-pdi:
    environment:
      PDI_PUBLIC_ENDPOINT: https://localhost:8081/pdi/ws
      PDI_TLS_CERT: /tls/pdi.crt
      PDI_TLS_KEY: /tls/pdi.key
    volumes: ["./.local-p1/tls:/tls:ro"]
  gub:
    command: ["start-dev", "--import-realm", "--http-enabled=false",
              "--https-port=8443", "--https-certificate-file=/tls/gub.crt",
              "--https-certificate-key-file=/tls/gub.key"]
    ports: ["127.0.0.1:8084:8443"]
    volumes: ["./.local-p1/tls:/tls:ro"]
```

## docker-compose.p1.yml

Levanta solo LDAP, Keycloak y PDI en puertos de loopback, sin reemplazar WildFly/PostgreSQL.

```yaml
services:
  ldap:
    build: ./docker/ldap
    environment:
      LDAP_ROOT_PASSWORD: ${LDAP_ROOT_PASSWORD:?Ejecutar preparar-local.ps1}
      LDAP_APP_PASSWORD: ${LDAP_APP_PASSWORD:?Ejecutar preparar-local.ps1}
      LDAP_APP_UID: ${LDAP_APP_UID:?Ejecutar preparar-local.ps1}
    ports: ["127.0.0.1:1389:1389"]
    volumes: ["ldap_p1:/data"]
  gub:
    image: quay.io/keycloak/keycloak:26.4.7
    command: ["start-dev", "--import-realm"]
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: ${KC_BOOTSTRAP_ADMIN_USERNAME}
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KC_BOOTSTRAP_ADMIN_PASSWORD}
    ports: ["127.0.0.1:8083:8080"]
    volumes:
      - ./.local-p1/gub-realm.json:/opt/keycloak/data/import/cargauy-test-realm.json:ro
      - keycloak_p1:/opt/keycloak/data
  mock-pdi:
    build: ./docker/mock-pdi
    environment:
      PDI_PUBLIC_ENDPOINT: http://localhost:8081/pdi/ws
      PDI_DELAY_SECONDS: "0"
    ports: ["127.0.0.1:8081:8080"]
volumes:
  ldap_p1:
  keycloak_p1:
```

## docker/ldap/Dockerfile

Archivo de referencia de la segunda guía; ver instrucciones en la guía principal.

```text
FROM debian:bookworm-slim
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends slapd ldap-utils ca-certificates && rm -rf /var/lib/apt/lists/*
COPY iniciar.sh /iniciar.sh
RUN chmod 755 /iniciar.sh
EXPOSE 1389
ENTRYPOINT ["/bin/sh", "/iniciar.sh"]
```

## docker/ldap/iniciar.sh

Inicializa el directorio LDAP, almacena hashes y arranca slapd; LDAPS opcional con certificados suministrados.

```text
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
```

## docker/mock-pdi/Dockerfile

Archivo de referencia de la segunda guía; ver instrucciones en la guía principal.

```text
FROM python:3.12-slim
WORKDIR /app
COPY mock_pdi.py pdi.wsdl ./
EXPOSE 8080
CMD ["python", "-u", "mock_pdi.py"]
```

## docker/mock-pdi/mock_pdi.py

Simulador con catálogo, Fault de ciudadano inexistente, validación de Addressing, logs y TLS opcional.

```python
import json, os, time, uuid, ssl
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

S = "http://schemas.xmlsoap.org/soap/envelope/"
A = "http://www.w3.org/2005/08/addressing"
P = "http://pdi.dnic.gub.uy/"
ACTION = "urn:cargauy:pdi:consultarCiudadano"
for prefix, ns in [("soap", S), ("wsa", A), ("pdi", P)]:
    ET.register_namespace(prefix, ns)
CATALOGO = {
    12345678: ("Juan", "Perez", "1985-05-15"),
    80000001: ("Ana", "Prueba", "1990-01-01"),
    80000002: ("Diego", "Chofer", "1988-01-01"),
}
def child(parent, tag, text=None):
    e = ET.SubElement(parent, tag)
    if text is not None: e.text = str(text)
    return e
def respuesta(mid, ci=None, datos=None, error=None):
    e = ET.Element("{" + S + "}Envelope")
    h = child(e, "{" + S + "}Header")
    child(h, "{" + A + "}Action", A + "/fault" if error else ACTION + "Response")
    child(h, "{" + A + "}RelatesTo", mid)
    b = child(e, "{" + S + "}Body")
    if error:
        fault = child(b, "{" + S + "}Fault")
        child(fault, "faultcode", "soap:Client")
        child(fault, "faultstring", error)
        detail = child(fault, "detail")
        d = child(detail, "{" + P + "}" + error)
        child(d, "ci" if error == "CiudadanoNoEncontrado" else "mensaje",
              ci if error == "CiudadanoNoEncontrado" else "Cabeceras o cuerpo inválidos")
    else:
        r = child(b, "{" + P + "}consultarCiudadanoResponse")
        for tag, value in [("ci", ci), ("nombre", datos[0]), ("apellido", datos[1]),
                           ("fechaNacimiento", datos[2]), ("vigente", "true")]:
            child(r, tag, value)
    return ET.tostring(e, encoding="utf-8", xml_declaration=True)

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        if self.path != "/pdi/ws?wsdl":
            self.send_error(404); return
        wsdl = Path(__file__).with_name("pdi.wsdl").read_text(encoding="utf-8")
        wsdl = wsdl.replace("@ENDPOINT@", os.getenv("PDI_PUBLIC_ENDPOINT", "http://localhost:8081/pdi/ws"))
        data = wsdl.encode("utf-8")
        self.send_response(200); self.send_header("Content-Type", "text/xml;charset=utf-8")
        self.end_headers(); self.wfile.write(data)
    def do_POST(self):
        inicio = time.monotonic()
        mid, ci, status = "", None, 500
        corr = self.headers.get("X-Correlation-ID", "")
        try: corr = str(uuid.UUID(corr))
        except ValueError: corr = str(uuid.uuid4())
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if self.path != "/pdi/ws" or length < 1 or length > 65536: raise ValueError()
            raw = self.rfile.read(length)
            if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper(): raise ValueError()
            doc = ET.fromstring(raw)
            if doc.tag != "{" + S + "}Envelope": raise ValueError()
            h, b = doc.find("{" + S + "}Header"), doc.find("{" + S + "}Body")
            def one(parent, tag):
                nodes = parent.findall(tag)
                if len(nodes) != 1: raise ValueError()
                return nodes[0]
            mid = one(h, "{" + A + "}MessageID").text or ""
            uuid.UUID(mid.removeprefix("urn:uuid:"))
            if not mid.startswith("urn:uuid:"): raise ValueError()
            expected = os.getenv("PDI_PUBLIC_ENDPOINT", "http://localhost:8081/pdi/ws")
            if one(h, "{" + A + "}Action").text != ACTION: raise ValueError()
            if one(h, "{" + A + "}To").text != expected: raise ValueError()
            if self.headers.get("SOAPAction", "").strip('"') != ACTION: raise ValueError()
            reply = one(h, "{" + A + "}ReplyTo")
            if one(reply, "{" + A + "}Address").text != A + "/anonymous": raise ValueError()
            operation = one(b, "{" + P + "}consultarCiudadano")
            ci = int(one(operation, "ci").text)
            if ci <= 0: raise ValueError()
            delay = float(os.getenv("PDI_DELAY_SECONDS", "0"))
            if delay > 0: time.sleep(min(delay, 10))
            datos = CATALOGO.get(ci)
            data = respuesta(mid, ci, datos, None if datos else "CiudadanoNoEncontrado")
            status = 200 if datos else 500
        except Exception:
            data = respuesta(mid, error="ContratoInvalido")
        self.send_response(status); self.send_header("Content-Type", "text/xml;charset=utf-8")
        self.send_header("X-Correlation-ID", corr); self.end_headers()
        try: self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError): pass
        print(json.dumps({"evento": "pdi.respuesta", "correlacion": corr,
              "status": status, "duracionMs": round((time.monotonic()-inicio)*1000)}), flush=True)

if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8080), Handler)
    cert, key = os.getenv("PDI_TLS_CERT"), os.getenv("PDI_TLS_KEY")
    if cert:
        if not key: raise RuntimeError("Falta PDI_TLS_KEY")
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert, key)
        server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever()
```

## docker/mock-pdi/pdi.wsdl

Contrato SOAP y cabeceras WS-Addressing; declara respuestas y fallas.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
 xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
 xmlns:wsaw="http://www.w3.org/2006/05/addressing/wsdl"
 xmlns:tns="http://pdi.dnic.gub.uy/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"
 targetNamespace="http://pdi.dnic.gub.uy/" name="PdiService">
 <types><xsd:schema targetNamespace="http://pdi.dnic.gub.uy/">
  <xsd:element name="consultarCiudadano"><xsd:complexType><xsd:sequence>
   <xsd:element name="ci" type="xsd:int"/>
  </xsd:sequence></xsd:complexType></xsd:element>
  <xsd:element name="consultarCiudadanoResponse"><xsd:complexType><xsd:sequence>
   <xsd:element name="ci" type="xsd:int"/><xsd:element name="nombre" type="xsd:string"/>
   <xsd:element name="apellido" type="xsd:string"/><xsd:element name="fechaNacimiento" type="xsd:string"/>
   <xsd:element name="vigente" type="xsd:boolean"/>
  </xsd:sequence></xsd:complexType></xsd:element>
  <xsd:element name="CiudadanoNoEncontrado"><xsd:complexType><xsd:sequence>
   <xsd:element name="ci" type="xsd:int"/>
  </xsd:sequence></xsd:complexType></xsd:element>
  <xsd:element name="ContratoInvalido"><xsd:complexType><xsd:sequence>
   <xsd:element name="mensaje" type="xsd:string"/>
  </xsd:sequence></xsd:complexType></xsd:element>
 </xsd:schema></types>
 <message name="Entrada"><part name="parameters" element="tns:consultarCiudadano"/></message>
 <message name="Salida"><part name="parameters" element="tns:consultarCiudadanoResponse"/></message>
 <message name="NoEncontrado"><part name="fault" element="tns:CiudadanoNoEncontrado"/></message>
 <message name="Invalido"><part name="fault" element="tns:ContratoInvalido"/></message>
 <portType name="PdiPortType"><operation name="consultarCiudadano">
  <input message="tns:Entrada" wsaw:Action="urn:cargauy:pdi:consultarCiudadano"/>
  <output message="tns:Salida" wsaw:Action="urn:cargauy:pdi:consultarCiudadanoResponse"/>
  <fault name="NoEncontrado" message="tns:NoEncontrado"
   wsaw:Action="http://www.w3.org/2005/08/addressing/fault"/>
  <fault name="Invalido" message="tns:Invalido"
   wsaw:Action="http://www.w3.org/2005/08/addressing/fault"/>
 </operation></portType>
 <binding name="PdiBinding" type="tns:PdiPortType">
  <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
  <wsaw:UsingAddressing wsdl:required="true"/>
  <operation name="consultarCiudadano"><soap:operation soapAction="urn:cargauy:pdi:consultarCiudadano"/>
   <input><soap:body use="literal"/></input><output><soap:body use="literal"/></output>
   <fault name="NoEncontrado"><soap:fault name="NoEncontrado" use="literal"/></fault>
   <fault name="Invalido"><soap:fault name="Invalido" use="literal"/></fault>
  </operation>
 </binding>
 <service name="PdiService"><port name="PdiPort" binding="tns:PdiBinding">
  <soap:address location="@ENDPOINT@"/>
 </port></service>
</definitions>
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/Chofer.java

Conserva su subtipo por compatibilidad; hereda la relación de guías desde Usuario.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Chofer encargada de operar los vehículos y reportar eventos en ruta.
 */
@Entity
@Table(name = "choferes")
@DiscriminatorValue("CHOFER")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Chofer extends Usuario {

    private static final long serialVersionUID = 1L;


    public Chofer() {
        super();
    }

    public Chofer(String nombre, String mail, LocalDate fechaNac, int ci, String password) {
        super(nombre, mail, fechaNac, ci, password);
    }

}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/Empresa.java

Expone la versión usada para detectar formularios desactualizados.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad Empresa titular de vehículos y responsable de guías de viaje.
 */
@Entity
@Table(name = "empresas")
public class Empresa implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "nro_empresa")
    private int nroEmpresa;
    
    @Version
    private long version;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(name = "nombre_publico", nullable = false, length = 150)
    private String nombrePublico;

    @Column(nullable = false, length = 255)
    private String direccion;

    @ManyToMany
    @JoinTable(
        name = "empresa_ciudadanos",
        joinColumns = @JoinColumn(name = "nro_empresa"),
        inverseJoinColumns = @JoinColumn(name = "ciudadano_id")
    )
    private List<Ciudadano> ciudadanos = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "empresa_rubros",
        joinColumns = @JoinColumn(name = "nro_empresa"),
        inverseJoinColumns = @JoinColumn(name = "rubro_codigo")
    )
    private List<Rubro> rubros = new ArrayList<>();

    @OneToMany(mappedBy = "empresa", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vehiculo> vehiculos = new ArrayList<>();

    @OneToMany(mappedBy = "empresa", cascade = CascadeType.ALL)
    private List<GuiaDeViaje> guias = new ArrayList<>();

    public long getVersion() { return version; }

    public Empresa() {
    }

    public Empresa(int nroEmpresa, String razonSocial, String nombrePublico, String direccion) {
        this.nroEmpresa = nroEmpresa;
        this.razonSocial = razonSocial;
        this.nombrePublico = nombrePublico;
        this.direccion = direccion;
    }

    public int getNroEmpresa() {
        return nroEmpresa;
    }

    public void setNroEmpresa(int nroEmpresa) {
        this.nroEmpresa = nroEmpresa;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getNombrePublico() {
        return nombrePublico;
    }

    public void setNombrePublico(String nombrePublico) {
        this.nombrePublico = nombrePublico;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public List<Ciudadano> getCiudadanos() {
        return ciudadanos;
    }

    public void setCiudadanos(List<Ciudadano> ciudadanos) {
        this.ciudadanos = ciudadanos;
    }

    public List<Rubro> getRubros() {
        return rubros;
    }

    public void setRubros(List<Rubro> rubros) {
        this.rubros = rubros;
    }

    public List<Vehiculo> getVehiculos() {
        return vehiculos;
    }

    public void setVehiculos(List<Vehiculo> vehiculos) {
        this.vehiculos = vehiculos;
    }

    public List<GuiaDeViaje> getGuias() {
        return guias;
    }

    public void setGuias(List<GuiaDeViaje> guias) {
        this.guias = guias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Empresa empresa = (Empresa) o;
        return nroEmpresa == empresa.nroEmpresa;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nroEmpresa);
    }
    
    public boolean isActiva() { 
    	return activa; 
    }
    
    public void setActiva(boolean activa) { 
    	this.activa = activa; 
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/FlujoIdentidad.java

Conserva state, desafío PKCE y nonce para el login móvil de un solo uso.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;
import java.time.Instant;
import jakarta.persistence.*;
@Entity @Table(name="flujos_identidad")
public class FlujoIdentidad {
 @Id @Column(length=64)private String hash;
 @Column(nullable=false,length=43)private String challenge;
 @Column(nullable=false,length=43)private String nonce;
 @Column(nullable=false)private Instant vence;
 protected FlujoIdentidad(){}
 public FlujoIdentidad(String hash,String challenge,String nonce,Instant vence){
  this.hash=hash;this.challenge=challenge;this.nonce=nonce;this.vence=vence;
 }
 public String getChallenge(){return challenge;}
 public String getNonce(){return nonce;}
 public Instant getVence(){return vence;}
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/GuiaDeViaje.java

Permite que el chofer sea un Usuario con rol CHOFER; exige validar esa función en negocio.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad central GuiaDeViaje que coordina el transporte, vehículos, choferes y eventos de ruta.
 */
@Entity
@Table(name = "guias_de_viaje")
public class GuiaDeViaje implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 150)
    private String origen;

    @Column(nullable = false, length = 150)
    private String destino;

    @Column(name = "rubro_cliente", nullable = false, length = 100)
    private String rubroCliente;

    @Column(name = "volumen_carga", nullable = false)
    private float volumenCarga;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEstado estado;

    /**
     * Versión para bloqueo optimista (Optimistic Locking) requerido para
     * resolución de conflictos en sincronización móvil offline (CU-C2 / AC014).
     */
    @Version
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nro_empresa", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_matricula", nullable = false)
    private Vehiculo vehiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chofer_id", nullable = false)
    private Usuario chofer;

    @ManyToMany
    @JoinTable(
        name = "guia_tipos_carga",
        joinColumns = @JoinColumn(name = "guia_id"),
        inverseJoinColumns = @JoinColumn(name = "tipo_carga_codigo")
    )
    private List<TipoCarga> tiposCarga = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PuntoTracking> puntosTracking = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PesadaBalanza> pesadas = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventoViaje> eventos = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL)
    private List<Infraccion> infracciones = new ArrayList<>();

    public GuiaDeViaje() {
        this.estado = TipoEstado.SIN_INICIAR;
    }

    public GuiaDeViaje(LocalDate fecha, String origen, String destino, String rubroCliente,
                       float volumenCarga, Empresa empresa, Vehiculo vehiculo, Usuario chofer) {
        this.fecha = fecha != null ? fecha : LocalDate.now();
        this.origen = origen;
        this.destino = destino;
        this.rubroCliente = rubroCliente;
        this.volumenCarga = volumenCarga;
        this.empresa = empresa;
        this.vehiculo = vehiculo;
        this.chofer = chofer;
        this.estado = TipoEstado.SIN_INICIAR;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public String getRubroCliente() {
        return rubroCliente;
    }

    public void setRubroCliente(String rubroCliente) {
        this.rubroCliente = rubroCliente;
    }

    public float getVolumenCarga() {
        return volumenCarga;
    }

    public void setVolumenCarga(float volumenCarga) {
        this.volumenCarga = volumenCarga;
    }

    public TipoEstado getEstado() {
        return estado;
    }

    public void setEstado(TipoEstado estado) {
        this.estado = estado;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public Usuario getChofer() {
        return chofer;
    }

    public void setChofer(Usuario chofer) {
        this.chofer = chofer;
    }

    public List<TipoCarga> getTiposCarga() {
        return tiposCarga;
    }

    public void setTiposCarga(List<TipoCarga> tiposCarga) {
        this.tiposCarga = tiposCarga;
    }

    public List<PuntoTracking> getPuntosTracking() {
        return puntosTracking;
    }

    public void setPuntosTracking(List<PuntoTracking> puntosTracking) {
        this.puntosTracking = puntosTracking;
    }

    public List<PesadaBalanza> getPesadas() {
        return pesadas;
    }

    public void setPesadas(List<PesadaBalanza> pesadas) {
        this.pesadas = pesadas;
    }

    public List<EventoViaje> getEventos() {
        return eventos;
    }

    public void setEventos(List<EventoViaje> eventos) {
        this.eventos = eventos;
    }

    public List<Infraccion> getInfracciones() {
        return infracciones;
    }

    public void setInfracciones(List<Infraccion> infracciones) {
        this.infracciones = infracciones;
    }

    public void agregarPuntoTracking(PuntoTracking punto) {
        puntosTracking.add(punto);
        punto.setGuia(this);
    }

    public void agregarPesada(PesadaBalanza pesada) {
        pesadas.add(pesada);
        pesada.setGuia(this);
    }

    public void agregarEvento(EventoViaje evento) {
        eventos.add(evento);
        evento.setGuia(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuiaDeViaje that = (GuiaDeViaje) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/SesionAcceso.java

Guarda solo SHA-256 del token, el usuario y el vencimiento en PostgreSQL.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Cacheable(false)
@Table(name = "sesiones_acceso")
public class SesionAcceso {
    @Id @Column(length = 64) private String hash;
    @ManyToOne(optional = false) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @Column(nullable = false) private Instant vence;
    protected SesionAcceso() {}
    public SesionAcceso(String hash, Usuario usuario, Instant vence) {
        this.hash = hash; this.usuario = usuario; this.vence = vence;
    }
    public Usuario getUsuario() { return usuario; }
    public Instant getVence() { return vence; }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/Usuario.java

Agrega roles e identidad de proveedor, expone versión y mueve la relación de guías al usuario canónico.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad base abstracta Usuario con estrategia de herencia JOINED.
 */
@Entity
@Cacheable(false)
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING)
public abstract class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    
    @Version
    private long version;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name="origen_identidad", nullable=false, length=10)
    private String origenIdentidad = "LOCAL";
    @Column(name="uid_externo", length=200)
    private String uidExterno;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="usuario_roles", joinColumns=@JoinColumn(name="usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name="rol", nullable=false, length=30)
    private java.util.Set<uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario> roles = new java.util.HashSet<>();
    @OneToMany(mappedBy="chofer")
    private java.util.List<GuiaDeViaje> guias = new java.util.ArrayList<>();

    public long getVersion() { return version; }
    public String getOrigenIdentidad() { return origenIdentidad; }
    public void setOrigenIdentidad(String v) { origenIdentidad=v; }
    public String getUidExterno() { return uidExterno; }
    public void setUidExterno(String v) { uidExterno=v; }
    public java.util.Set<uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario> getRoles() { return roles; }
    public java.util.List<GuiaDeViaje> getGuias() { return guias; }
    public void setGuias(java.util.List<GuiaDeViaje> v) { guias=v; }


    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String mail;

    @Column(name = "fecha_nac", nullable = false)
    private LocalDate fechaNac;

    @Column(nullable = false, unique = true)
    private int ci;

    @Column(nullable = false, length = 255)
    private String password;

    public Usuario() {
    }

    public Usuario(String nombre, String mail, LocalDate fechaNac, int ci, String password) {
        this.nombre = nombre;
        this.mail = mail;
        this.fechaNac = fechaNac;
        this.ci = ci;
        this.password = password;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public LocalDate getFechaNac() {
        return fechaNac;
    }

    public void setFechaNac(LocalDate fechaNac) {
        this.fechaNac = fechaNac;
    }

    public int getCi() {
        return ci;
    }

    public void setCi(int ci) {
        this.ci = ci;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getContrasena() {
        return password;
    }

    public void setContrasena(String contrasena) {
        this.password = contrasena;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return ci == usuario.ci;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ci);
    }
    
    public boolean isActivo() { 
    	return activo; 
    }
    
    public void setActivo(boolean activo) { 
    	this.activo = activo;
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/VinculoEmpresa.java

Representa funciones de responsable y chofer en una empresa sin crear dos personas con la misma CI.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vinculos_empresa")
public class VinculoEmpresa {
    @Id @Column(length = 64) private String clave;
    @ManyToOne(optional = false) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @ManyToOne(optional = false) @JoinColumn(name = "nro_empresa") private Empresa empresa;
    @Column(nullable = false) private boolean responsable;
    @Column(nullable = false) private boolean chofer;
    @Version private long version;
    protected VinculoEmpresa() {}
    public VinculoEmpresa(Usuario u, Empresa e) {
        clave = clave(e.getNroEmpresa(), u.getId()); usuario = u; empresa = e;
    }
    public static String clave(int empresa, int usuario) { return empresa + ":" + usuario; }
    public Usuario getUsuario() { return usuario; }
    public Empresa getEmpresa() { return empresa; }
    public boolean isResponsable() { return responsable; }
    public boolean isChofer() { return chofer; }
    public void setResponsable(boolean valor) { responsable = valor; }
    public void setChofer(boolean valor) { chofer = valor; }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/EmpresaResumen.java

Incluye versión. Su lista ciudadanos conserva la asociación antigua; para el vínculo canónico usar listarVinculos.

```java
package uy.edu.fing.grupo07.CargaUY.dto;

import java.util.List;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;

public record EmpresaResumen(int numero, String razonSocial, String nombrePublico,
        String direccion, boolean activa, List<Integer> ciudadanos, long version) {
	
    public static EmpresaResumen desde(Empresa e) {
        return new EmpresaResumen(e.getNroEmpresa(), e.getRazonSocial(),
            e.getNombrePublico(), e.getDireccion(), e.isActiva(),
            e.getCiudadanos().stream().map(c -> c.getId()).toList(), e.getVersion());
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/UsuarioResumen.java

Devuelve roles y versión sin devolver password ni UID externo.

```java
package uy.edu.fing.grupo07.CargaUY.dto;

import java.time.LocalDate;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

public record UsuarioResumen(int id, String tipo, String nombre, String mail,
        LocalDate fechaNac, int ci, boolean activo,
        Integer nroFuncionario, String departamento, String cargo, long version, java.util.Set<TipoUsuario> roles) {
	
    public static UsuarioResumen desde(Usuario u) {
        String tipo = u instanceof Ciudadano ? "CIUDADANO"
            : u instanceof Chofer ? "CHOFER"
            : u instanceof Funcionario ? "FUNCIONARIO"
            : u instanceof Autoridad ? "AUTORIDAD" : "ADMINISTRADOR";
        return new UsuarioResumen(u.getId(), tipo, u.getNombre(), u.getMail(),
            u.getFechaNac(), u.getCi(), u.isActivo(),
            u instanceof Funcionario f ? f.getNroFuncionario() : null,
            u instanceof Funcionario f ? f.getDepartamento() : null,
            u instanceof Autoridad a ? a.getCargo() : null, u.getVersion(), java.util.Set.copyOf(u.getRoles()));
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/integration/ClientePdi.java

Cliente SOAP 1.1 y WS-Addressing, cuatro llamadas concurrentes, reintento acotado y circuito con prueba de recuperación.

```java
package uy.edu.fing.grupo07.CargaUY.integration;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import uy.edu.fing.grupo07.CargaUY.security.Config;

@Singleton @LocalBean @Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ClientePdi {
    private static final String S="http://schemas.xmlsoap.org/soap/envelope/";
    private static final String A="http://www.w3.org/2005/08/addressing";
    private static final String P="http://pdi.dnic.gub.uy/";
    private static final String ACTION="urn:cargauy:pdi:consultarCiudadano";
    private static final Logger LOG=Logger.getLogger(ClientePdi.class.getName());
    private final Semaphore capacidad=new Semaphore(4);
    private final AtomicLong llamadas=new AtomicLong(), fallas=new AtomicLong(), rechazadas=new AtomicLong();
    private HttpClient http; private URI endpoint;
    private int consecutivas; private long abiertoHasta; private boolean prueba;
    @PostConstruct public void iniciar(){
        endpoint=URI.create(Config.valor("pdi.endpoint","http://localhost:8081/pdi/ws"));
        boolean local=Boolean.parseBoolean(Config.valor("p1.entorno.local","false"))
            && ("localhost".equals(endpoint.getHost())||"127.0.0.1".equals(endpoint.getHost()));
        if(!"https".equals(endpoint.getScheme())&&!("http".equals(endpoint.getScheme())&&local))
            throw new IllegalStateException("PDI requiere HTTPS fuera del entorno local");
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    }
    public ResultadoPdi consultar(int ci){return consultar(ci,UUID.randomUUID().toString());}
    public ResultadoPdi consultar(int ci,String correlacion){
        if(ci<=0) throw new IllegalArgumentException("CI inválida");
        try{correlacion=UUID.fromString(correlacion).toString();}
        catch(Exception e){correlacion=UUID.randomUUID().toString();}
        if(!capacidad.tryAcquire()){rechazadas.incrementAndGet();return ResultadoPdi.indisponible();}
        boolean admitida=false, exito=false;
        long inicio=System.nanoTime();
        try{
            admitida=admitir();
            if(!admitida){rechazadas.incrementAndGet();return ResultadoPdi.indisponible();}
            for(int intento=0;intento<2;intento++){
                String id="urn:uuid:"+UUID.randomUUID();
                try{
                    String xml="<s:Envelope xmlns:s=\""+S+"\" xmlns:w=\""+A+"\" xmlns:p=\""+P+"\">"
                        +"<s:Header><w:Action s:mustUnderstand=\"1\">"+ACTION+"</w:Action>"
                        +"<w:To s:mustUnderstand=\"1\">"+esc(endpoint.toString())+"</w:To>"
                        +"<w:MessageID>"+id+"</w:MessageID><w:ReplyTo><w:Address>"+A+"/anonymous"
                        +"</w:Address></w:ReplyTo></s:Header><s:Body>"
                        +"<p:consultarCiudadano><ci>"+ci+"</ci></p:consultarCiudadano></s:Body></s:Envelope>";
                    llamadas.incrementAndGet();
                    HttpResponse<String> r=http.send(HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(2)).header("Content-Type","text/xml;charset=UTF-8")
                        .header("SOAPAction","\""+ACTION+"\"").header("X-Correlation-ID",correlacion)
                        .POST(HttpRequest.BodyPublishers.ofString(xml)).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if(r.statusCode()!=200&&r.statusCode()!=500) throw new IOException("HTTP");
                    ResultadoPdi resultado=interpretar(r.body(),ci,id);
                    if(r.statusCode()==500&&resultado.estado()!=ResultadoPdi.Estado.NO_ENCONTRADO)
                        throw new IOException("Estado HTTP inconsistente");
                    exito=true;return resultado;
                }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                catch(Exception e){if(intento==0) continue;}
            }
            fallas.incrementAndGet();return ResultadoPdi.indisponible();
        }finally{
            if(admitida) terminar(exito);
            capacidad.release();
            LOG.info("{\"evento\":\"pdi.consulta\",\"correlacion\":\""+escJson(correlacion)
                +"\",\"ok\":"+exito+",\"duracionMs\":"+((System.nanoTime()-inicio)/1000000)+"}");
        }
    }
    private synchronized boolean admitir(){
        if(abiertoHasta==0)return true;
        if(System.nanoTime()<abiertoHasta||prueba)return false;
        prueba=true;return true; // Una sola consulta prueba recuperación.
    }
    private synchronized void terminar(boolean exito){
        if(prueba){
            prueba=false;
            if(exito){consecutivas=0;abiertoHasta=0;}
            else abiertoHasta=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        }else if(abiertoHasta==0){
            if(exito)consecutivas=0;
            else if(++consecutivas>=3)abiertoHasta=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        }
    }
    public long[] metricas(){return new long[]{llamadas.get(),fallas.get(),rechazadas.get()};}
    ResultadoPdi interpretar(String xml,int ci,String id)throws Exception{
        if(xml.length()>65536)throw new IOException("Respuesta grande");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        Document d=f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element envelope=d.getDocumentElement();
        if(!S.equals(envelope.getNamespaceURI())||!"Envelope".equals(envelope.getLocalName()))
            throw new IOException("Envelope inválido");
        Element header=unico(envelope,S,"Header"), body=unico(envelope,S,"Body");
        if(!id.equals(unico(header,A,"RelatesTo").getTextContent()))throw new IOException("Correlación SOAP inválida");
        String action=unico(header,A,"Action").getTextContent();
        NodeList fault=body.getElementsByTagNameNS(S,"Fault");
        if(fault.getLength()>0){
            if(!(A+"/fault").equals(action))throw new IOException("Action de Fault inválida");
            Element detalle=unico((Element)fault.item(0),null,"detail");
            Element ausente=unico(detalle,P,"CiudadanoNoEncontrado");
            if(Integer.parseInt(texto(ausente,"ci"))!=ci)throw new IOException("CI inválida");
            return new ResultadoPdi(ResultadoPdi.Estado.NO_ENCONTRADO,ci,null,null,null,null,
                "No existen datos para esa CI");
        }
        if(!(ACTION+"Response").equals(action))throw new IOException("Action inválida");
        Element r=unico(body,P,"consultarCiudadanoResponse");
        if(Integer.parseInt(texto(r,"ci"))!=ci)throw new IOException("CI inválida");
        String vigente=texto(r,"vigente");
        if(!Set.of("true","false","1","0").contains(vigente))throw new IOException("Booleano inválido");
        return new ResultadoPdi(ResultadoPdi.Estado.DISPONIBLE,ci,texto(r,"nombre"),texto(r,"apellido"),
            LocalDate.parse(texto(r,"fechaNacimiento")),vigente.equals("true")||vigente.equals("1"),"PDI consultada");
    }
    private Element unico(Element padre,String ns,String nombre)throws IOException{
        Element resultado=null;
        for(Node n=padre.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element e&&nombre.equals(e.getLocalName())
                    && Objects.equals(ns,e.getNamespaceURI())){
                if(resultado!=null)throw new IOException("Elemento duplicado");resultado=e;
            }
        if(resultado==null)throw new IOException("Elemento ausente: "+nombre);
        return resultado;
    }
    private String texto(Element p,String campo)throws IOException{
        String v=unico(p,null,campo).getTextContent().trim();
        if(v.isBlank())throw new IOException("Campo vacío");return v;
    }
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");}
    private static String escJson(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","").replace("\r","");}
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/Acceso.java

DTO de identidad y roles actuales; no contiene contraseña ni credenciales de proveedor.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.io.Serializable;
import java.util.Set;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

public record Acceso(int id, int ci, String nombre, Set<TipoUsuario> roles)
        implements Serializable {
    public Acceso { roles = Set.copyOf(roles); }
    public boolean tiene(TipoUsuario rol) { return roles.contains(rol); }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/AutenticacionService.java

Coordina LDAP y gub.uy sin fallback de contraseñas; expone login, validarToken y logout.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import jakarta.ejb.*;

@Stateless @LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AutenticacionService {
    @EJB private LdapClient ldap;
    @EJB private GubOidcClient gub;
    @EJB private SesionesService sesiones;
    @EJB private FlujosService flujos;

    public SesionesService.Login login(String uid, String password) {
        // No hay fallback a contraseñas de PostgreSQL si LDAP se cae.
        ldap.autenticar(uid, password);
        return sesiones.emitirLdap(uid);
    }
    public SesionesService.Login loginGub(String code, String verifier, String nonce) {
        return sesiones.emitirGub(gub.validar(code, verifier, nonce));
    }
    public FlujosService.Inicio prepararMovil(String challenge){return flujos.preparar(challenge);}
    public SesionesService.Login loginMovil(String code,String state,String verifier){
        String nonce=flujos.consumir(state,verifier);
        return sesiones.emitirGub(gub.validar(code,verifier,nonce,true));
    }
    public Acceso validarToken(String token) { return sesiones.validarToken(token); }
    public void logout(String token) { sesiones.logout(token); }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/BootstrapAdmin.java

Crea únicamente el perfil local del administrador de pruebas si fue habilitado; no guarda ni cambia su password LDAP.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.time.LocalDate;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Singleton @Startup
public class BootstrapAdmin {
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    @PostConstruct
    public void iniciar() {
        if (!Boolean.parseBoolean(Config.valor("p1.bootstrap","false"))) return;
        if (!Boolean.parseBoolean(Config.valor("p1.entorno.local","false")))
            throw new IllegalStateException("Bootstrap solo permitido en entorno local");
        String uid = Config.requerido("bootstrap.admin.uid");
        if (!uid.matches("[a-zA-Z0-9._-]{1,64}")) throw new IllegalStateException("UID inválido");
        long existe = em.createQuery(
            "select count(u) from Usuario u where u.origenIdentidad='LDAP' and u.uidExterno=:uid",
            Long.class).setParameter("uid",uid).getSingleResult();
        if (existe>0) return; // No reactivar ni cambiar roles de una cuenta existente.
        int ci = Integer.parseInt(Config.requerido("bootstrap.admin.ci"));
        if (em.createQuery("select count(u) from Usuario u where u.ci=:ci",Long.class)
                .setParameter("ci",ci).getSingleResult()>0)
            throw new IllegalStateException("La CI de bootstrap ya está ocupada");
        Administrador admin = new Administrador(Config.requerido("bootstrap.admin.nombre"),
            Config.requerido("bootstrap.admin.mail"),LocalDate.parse(
                Config.requerido("bootstrap.admin.nacimiento")),ci,"{LDAP}");
        admin.setOrigenIdentidad("LDAP"); admin.setUidExterno(uid);
        admin.getRoles().add(TipoUsuario.ADMINISTRADOR);
        em.persist(admin);
        // No crea un usuario LDAP, no conoce su contraseña y no modifica datos existentes.
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/Config.java

Resuelve propiedades de WildFly o variables de entorno; evita credenciales en el código.

```java
package uy.edu.fing.grupo07.CargaUY.security;

public final class Config {
    private Config() {}
    public static String valor(String clave, String defecto) {
        String valor = System.getProperty(clave);
        if (valor == null || valor.isBlank())
            valor = System.getenv(clave.toUpperCase().replace('.', '_'));
        return valor == null || valor.isBlank() ? defecto : valor;
    }
    public static String requerido(String clave) {
        String v = valor(clave, "");
        if (v.isBlank()) throw new IllegalStateException("Falta configurar " + clave);
        return v;
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/ControlAcceso.java

Autoriza en negocio por rol y por vínculo con la empresa.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.util.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

@Stateless @LocalBean
public class ControlAcceso {
    @EJB private SesionesService sesiones;
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    public Acceso exigir(String token, TipoUsuario... roles) {
        Acceso a = sesiones.validarToken(token);
        if (roles.length > 0 && Arrays.stream(roles).noneMatch(a::tiene))
            throw new ErrorAcceso(403, "Operación no permitida para este rol");
        return a;
    }
    public Acceso admin(String token) { return exigir(token, TipoUsuario.ADMINISTRADOR); }
    public Acceso empresa(String token, int numero, boolean editar) {
        Acceso a = exigir(token);
        if (a.tiene(TipoUsuario.ADMINISTRADOR) || a.tiene(TipoUsuario.FUNCIONARIO)
                || !editar && a.tiene(TipoUsuario.AUTORIDAD)) return a;
        Empresa e = em.find(Empresa.class, numero);
        VinculoEmpresa v = em.find(VinculoEmpresa.class, VinculoEmpresa.clave(numero, a.id()));
        if (!a.tiene(TipoUsuario.CIUDADANO) || e == null || !e.isActiva() || v == null || !v.isResponsable())
            throw new ErrorAcceso(403, "Sin acceso a esa empresa");
        return a;
    }
    public void choferAsignable(String token, int numero, int usuario) {
        empresa(token, numero, true);
        Usuario u = em.find(Usuario.class, usuario);
        VinculoEmpresa v = em.find(VinculoEmpresa.class, VinculoEmpresa.clave(numero, usuario));
        if (u == null || !u.isActivo() || !u.getRoles().contains(TipoUsuario.CHOFER)
                || v == null || !v.isChofer())
            throw new ErrorAcceso(409, "El chofer debe estar activo y vinculado a la empresa");
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/Cripto.java

Genera tokens aleatorios, sus hashes y el desafío PKCE; compara valores sensibles.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class Cripto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Cripto() {}
    public static String aleatorio() {
        byte[] datos = new byte[32]; RANDOM.nextBytes(datos);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
    public static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String pkce(String valor) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean igual(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/ErrorAcceso.java

Representa 401, 403 y conflictos de identidad; pide rollback de la operación.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class ErrorAcceso extends RuntimeException {
    private final int estado;
    public ErrorAcceso(int estado, String mensaje) { super(mensaje); this.estado = estado; }
    public int getEstado() { return estado; }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/FlujosService.java

Prepara y consume de forma transaccional el flujo móvil; expira a los tres minutos.

```java
package uy.edu.fing.grupo07.CargaUY.security;
import java.time.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.FlujoIdentidad;
@Stateless @LocalBean
public class FlujosService {
 public record Inicio(String state,String nonce,String url){}
 @PersistenceContext(unitName="CargaUYPersistenceUnit")private EntityManager em;
 public Inicio preparar(String challenge){
  habilitado();
  if(challenge==null||!challenge.matches("[A-Za-z0-9_-]{43}"))throw new ErrorAcceso(400,"PKCE challenge inválido");
  String state=Cripto.aleatorio(),nonce=Cripto.aleatorio();
  em.persist(new FlujoIdentidad(Cripto.hash(state),challenge,nonce,Instant.now().plusSeconds(180)));
  String url=GubOidcClient.autorizacion()+"?response_type=code&scope="+GubOidcClient.url(GubOidcClient.scopes())
   +"&client_id="+GubOidcClient.url(GubOidcClient.clientId())+"&redirect_uri="+GubOidcClient.url(redirect())
   +"&state="+state+"&nonce="+nonce+"&code_challenge="+challenge+"&code_challenge_method=S256";
  return new Inicio(state,nonce,url);
 }
 @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
 public String consumir(String state,String verifier){
  habilitado();
  if(state==null||!state.matches("[A-Za-z0-9_-]{43}")||verifier==null||!verifier.matches("[A-Za-z0-9._~-]{43,128}"))
   throw new ErrorAcceso(401,"Flujo inválido");
  FlujoIdentidad f=em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE);
  if(f==null||!f.getVence().isAfter(Instant.now())||!Cripto.igual(f.getChallenge(),Cripto.pkce(verifier)))
   throw new ErrorAcceso(401,"Flujo vencido o PKCE inválido");
  String nonce=f.getNonce();em.remove(f);return nonce;
 }
 private static void habilitado(){
  if(GubOidcClient.real() && (!Boolean.parseBoolean(Config.valor("gub.mobile.enabled","false")) || !GubOidcClient.pkce()))
   throw new ErrorAcceso(409,"Móvil requiere callback registrado y soporte PKCE confirmado");
 }
 public static String redirect(){return Config.valor("gub.mobile.redirect","uy.cargauy:/oauth/callback");}
 @Schedule(hour="*",minute="*/15",persistent=false)
 public void limpiar(){em.createQuery("delete from FlujoIdentidad f where f.vence<=:ahora")
   .setParameter("ahora",Instant.now()).executeUpdate();}
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/GubOidcClient.java

Intercambia el código OIDC, valida firma RS256, issuer, audiencia, nonce y fechas; verifica userinfo.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.io.StringReader;
import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.*;
import java.util.*;
import jakarta.ejb.*;
import jakarta.json.*;

@Stateless @LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class GubOidcClient {
    public record Identidad(String subject, int ci, String nombre, String mail, LocalDate nacimiento) {}
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public static String issuer() {
        return Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test");
    }
    public static String clientId() { return Config.valor("gub.client.id", "cargauy"); }
    public static String redirect() { return Config.requerido("gub.redirect"); }
    public static String url(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    public static boolean real() { return "iduruguay".equals(Config.valor("gub.proveedor","keycloak")); }
    public static boolean pkce() { return Boolean.parseBoolean(Config.valor("gub.pkce",real()?"false":"true")); }
    public static String autorizacion() {
        return real() ? issuer()+"/authorize" : issuer()+"/protocol/openid-connect/auth";
    }
    public static String scopes() { return real()?"openid profile personal_info email document":"openid profile email"; }
    private URI endpoint(String sufijo) {
        String ruta = real() ? (sufijo.equals("certs")?"/jwks":"/"+sufijo) : "/protocol/openid-connect/"+sufijo;
        URI u = URI.create(real() && sufijo.equals("certs")
            ? Config.valor("gub.jwks",issuer()+ruta) : issuer()+ruta);
        boolean local = Boolean.parseBoolean(Config.valor("p1.entorno.local","false"))
            && ("localhost".equals(u.getHost()) || "127.0.0.1".equals(u.getHost()));
        if (!"https".equals(u.getScheme()) && !("http".equals(u.getScheme()) && local))
            throw new ErrorAcceso(503,"El proveedor de identidad requiere HTTPS");
        return u;
    }
    public Identidad validar(String code,String verifier,String nonce){return validar(code,verifier,nonce,false);}
    public Identidad validar(String code, String verifier, String nonce, boolean movil) {
        try {
            if (code == null || code.length() > 4096 || verifier == null || nonce == null)
                throw new ErrorAcceso(401,"Respuesta de identidad inválida");
            String datos = "grant_type=authorization_code"
                + "&redirect_uri=" + url(movil?FlujosService.redirect():redirect()) + "&code=" + url(code)
                + (pkce() ? "&code_verifier=" + url(verifier) : "");
            String basic = Base64.getEncoder().encodeToString((url(clientId())+":"
                +url(Config.requerido("gub.client.secret"))).getBytes(StandardCharsets.UTF_8));
            JsonObject token = pedir(HttpRequest.newBuilder(endpoint("token"))
                .header("Authorization","Basic "+basic)
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(datos)));
            JsonObject claims = validarIdToken(token.getString("id_token"), nonce);
            String acceso = token.getString("access_token");
            JsonObject info = pedir(HttpRequest.newBuilder(endpoint("userinfo"))
                .header("Authorization","Bearer " + acceso).GET());
            if (!claims.getString("sub").equals(info.getString("sub")))
                throw new ErrorAcceso(401,"Identidad inconsistente");
            if (real()) {
                // No se trata un pasaporte extranjero como CI.
                String pais = info.getString("pais_documento",""), tipo = info.getString("tipo_documento","");
                if (!pais.equalsIgnoreCase(Config.requerido("gub.document.pais"))
                        || !tipo.equalsIgnoreCase(Config.requerido("gub.document.tipo")))
                    throw new ErrorAcceso(409,"Se requiere documento de tipo CI uruguaya configurado");
            }
            String documento = info.getString(real()?"numero_documento":"ci","").replaceAll("[. \\-]","");
            if (!documento.matches("[0-9]{7,8}")) throw new ErrorAcceso(409,"Documento ausente o incompatible");
            int ci = Integer.parseInt(documento);
            // ID Uruguay no anuncia fecha de nacimiento; no se inventa ni se consulta un mock para completarla.
            LocalDate nacimiento = real()?null:LocalDate.parse(info.getString("fechaNacimiento"));
            String mail = info.getString("email",""), nombre = info.getString("name",
                info.getString("nombre_completo",""));
            if (ci <= 0 || nombre.isBlank() || nombre.length()>100 || mail.length()>150
                    || !mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                    || nacimiento!=null && nacimiento.isAfter(LocalDate.now())
                    || !info.getBoolean("email_verified",false))
                throw new ErrorAcceso(409,"Perfil externo incompleto o correo sin verificar");
            // Separa los identificadores de proveedores aunque dos emitan el mismo sub.
            String subject = real()?Cripto.hash(issuer()+"\n"+claims.getString("sub")):claims.getString("sub");
            return new Identidad(subject,ci,nombre,mail,nacimiento);
        } catch (ErrorAcceso e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ErrorAcceso(503,"Proveedor interrumpido");
        } catch (Exception e) { throw new ErrorAcceso(503,"No se pudo verificar el proveedor de identidad"); }
    }
    private JsonObject validarIdToken(String jwt, String nonce) throws Exception {
        String[] p = jwt.split("\\.");
        if (p.length != 3) throw new ErrorAcceso(401,"ID token inválido");
        Base64.Decoder d = Base64.getUrlDecoder();
        JsonObject h = json(new String(d.decode(p[0]),StandardCharsets.UTF_8));
        if (!"RS256".equals(h.getString("alg"))) throw new ErrorAcceso(401,"Algoritmo no admitido");
        JsonArray keys = pedir(HttpRequest.newBuilder(endpoint("certs")).GET()).getJsonArray("keys");
        JsonObject key = keys.stream().map(v -> (JsonObject)v)
            .filter(k -> h.getString("kid").equals(k.getString("kid"))
                && "RSA".equals(k.getString("kty"))).findFirst()
            .orElseThrow(() -> new ErrorAcceso(401,"Clave desconocida"));
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
            new BigInteger(1,d.decode(key.getString("n"))),
            new BigInteger(1,d.decode(key.getString("e")))));
        Signature firma = Signature.getInstance("SHA256withRSA"); firma.initVerify(pub);
        firma.update((p[0]+"."+p[1]).getBytes(StandardCharsets.US_ASCII));
        if (!firma.verify(d.decode(p[2]))) throw new ErrorAcceso(401,"Firma de identidad inválida");
        JsonObject c = json(new String(d.decode(p[1]),StandardCharsets.UTF_8));
        JsonValue aud = c.get("aud");
        boolean audiencia = aud instanceof JsonString s ? clientId().equals(s.getString())
            : aud instanceof JsonArray a && a.stream().anyMatch(v -> v instanceof JsonString s
                && clientId().equals(s.getString()));
        long ahora = Instant.now().getEpochSecond();
        if (!issuer().equals(c.getString("iss")) || !audiencia
                || !Cripto.igual(nonce,c.getString("nonce",""))
                || c.getJsonNumber("exp").longValue() <= ahora
                || c.getJsonNumber("iat").longValue() > ahora+30
                || c.containsKey("nbf") && c.getJsonNumber("nbf").longValue()>ahora+30
                || c.containsKey("azp") && !clientId().equals(c.getString("azp"))
                || aud instanceof JsonArray a && a.size()>1 && !c.containsKey("azp"))
            throw new ErrorAcceso(401,"Claims de identidad inválidos");
        return c;
    }
    private JsonObject pedir(HttpRequest.Builder b) throws Exception {
        HttpResponse<String> r = http.send(b.timeout(Duration.ofSeconds(3)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode()!=200) throw new ErrorAcceso(r.statusCode()>=500?503:401,
            "Proveedor rechazó la operación");
        if (r.body().length()>131072) throw new ErrorAcceso(503,"Respuesta demasiado grande");
        return json(r.body());
    }
    private JsonObject json(String s) {
        try (JsonReader reader = Json.createReader(new StringReader(s))) { return reader.readObject(); }
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/IdentidadesService.java

Permite a administradores vincular proveedores y asignar roles; gestiona vínculos de responsables y choferes.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.util.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Stateless @LocalBean
public class IdentidadesService {
    @EJB private ControlAcceso control;
    @EJB private SesionesService sesiones;
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    public void vincular(String token, int id, String origen, String uid) {
        control.admin(token);
        if (!Set.of("LDAP","GUB").contains(origen) || uid == null || uid.isBlank() || uid.length()>200)
            throw new ErrorAcceso(400,"Origen o identidad inválidos");
        if ("LDAP".equals(origen) && !uid.matches("[a-zA-Z0-9._-]{1,64}"))
            throw new ErrorAcceso(400,"UID LDAP inválido");
        Usuario u = requerido(id);
        boolean interno = u.getRoles().contains(TipoUsuario.ADMINISTRADOR)
            || u.getRoles().contains(TipoUsuario.AUTORIDAD);
        if (interno != "LDAP".equals(origen))
            throw new ErrorAcceso(409,"Roles internos requieren LDAP; los demás requieren GUB");
        u.setOrigenIdentidad(origen); u.setUidExterno(uid);
        u.setPassword("{"+origen+"}"); // Eliminar copia innecesaria de credencial local.
        sesiones.revocarUsuario(id);
    }
    public void roles(String token, int id, Set<TipoUsuario> roles) {
        Acceso actor = control.admin(token);
        Usuario u = requerido(id);
        if (roles==null || roles.isEmpty()) throw new ErrorAcceso(400,"Al menos un rol");
        boolean internos = roles.stream().anyMatch(r -> r==TipoUsuario.ADMINISTRADOR||r==TipoUsuario.AUTORIDAD);
        boolean externos = roles.stream().anyMatch(r -> r!=TipoUsuario.ADMINISTRADOR&&r!=TipoUsuario.AUTORIDAD);
        if (internos && externos) throw new ErrorAcceso(409,"No mezclar cuentas LDAP y gub.uy");
        if (actor.id()==id && !roles.contains(TipoUsuario.ADMINISTRADOR))
            throw new ErrorAcceso(409,"No quitar tu propio rol administrador");
        if ("LDAP".equals(u.getOrigenIdentidad()) && !internos
                || "GUB".equals(u.getOrigenIdentidad()) && internos)
            throw new ErrorAcceso(409,"Los roles no coinciden con el proveedor de la cuenta");
        u.getRoles().clear(); u.getRoles().addAll(roles); sesiones.revocarUsuario(id);
    }
    public void vinculo(String token, int empresa, int usuario, boolean responsable, boolean chofer) {
        control.exigir(token,TipoUsuario.ADMINISTRADOR,TipoUsuario.FUNCIONARIO);
        Empresa e = em.find(Empresa.class,empresa); Usuario u = requerido(usuario);
        if (e==null || !e.isActiva() || !u.isActivo()) throw new ErrorAcceso(409,"Cuenta o empresa inactiva");
        if (responsable && !u.getRoles().contains(TipoUsuario.CIUDADANO)
                || chofer && !u.getRoles().contains(TipoUsuario.CHOFER))
            throw new ErrorAcceso(409,"El usuario no tiene el rol requerido");
        em.lock(e,LockModeType.PESSIMISTIC_WRITE);
        VinculoEmpresa v = em.find(VinculoEmpresa.class,VinculoEmpresa.clave(empresa,usuario));
        if (v==null) { v=new VinculoEmpresa(u,e); em.persist(v); }
        v.setResponsable(responsable); v.setChofer(chofer);
        // false/false conserva el vínculo sin permisos, útil para trazabilidad.
    }
    public record VinculoVista(int usuario,String nombre,boolean responsable,boolean chofer){}
    public java.util.List<VinculoVista> listar(String token,int empresa){
        control.empresa(token,empresa,false);
        return em.createQuery("select v from VinculoEmpresa v where v.empresa.nroEmpresa=:empresa order by v.usuario.id",VinculoEmpresa.class)
            .setParameter("empresa",empresa).setMaxResults(100).getResultList().stream()
            .map(v->new VinculoVista(v.getUsuario().getId(),v.getUsuario().getNombre(),v.isResponsable(),v.isChofer())).toList();
    }
    private Usuario requerido(int id) {
        Usuario u=em.find(Usuario.class,id);
        if(u==null) throw new ErrorAcceso(404,"Usuario inexistente");
        return u;
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/LdapClient.java

Hace bind LDAP con el usuario final, rechaza password vacío y distingue credencial inválida de caída del directorio.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.net.URI;
import java.util.Hashtable;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.Rdn;
import jakarta.ejb.*;

@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class LdapClient {
    public void autenticar(String uid, String password) {
        if (uid == null || !uid.matches("[a-zA-Z0-9._-]{1,64}")
                || password == null || password.isBlank())
            throw new ErrorAcceso(401, "Credenciales inválidas");
        String url = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        URI uri = URI.create(url);
        boolean local = ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
            && Boolean.parseBoolean(Config.valor("p1.entorno.local", "false"));
        if (!"ldaps".equals(uri.getScheme()) && !("ldap".equals(uri.getScheme()) && local))
            throw new ErrorAcceso(503, "LDAP requiere LDAPS fuera del entorno local");
        DirContext contexto = null;
        try {
            Hashtable<String,Object> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, url);
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, new Rdn("uid", uid).toString() + ","
                + Config.valor("ldap.people.dn", "ou=people,dc=cargauy,dc=local"));
            env.put(Context.SECURITY_CREDENTIALS, password);
            env.put("com.sun.jndi.ldap.connect.timeout", "1500");
            env.put("com.sun.jndi.ldap.read.timeout", "1500");
            env.put(Context.REFERRAL, "throw");
            contexto = new InitialDirContext(env);
            contexto.getAttributes("", new String[] { "uid" }); // Fuerza el bind.
        } catch (AuthenticationException e) {
            throw new ErrorAcceso(401, "Credenciales inválidas");
        } catch (NamingException e) {
            throw new ErrorAcceso(503, "Directorio temporalmente no disponible");
        } finally {
            if (contexto != null) try { contexto.close(); } catch (NamingException ignored) {}
        }
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/SesionesService.java

Emite y revoca sesiones; con ID Uruguay exige un perfil completo vinculado y una CI coincidente.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.time.*;
import java.util.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Stateless
@LocalBean
public class SesionesService {
    public record Login(String token, Acceso usuario, Instant vence) {}
    @PersistenceContext(unitName = "CargaUYPersistenceUnit") private EntityManager em;

    // Solo invocada por AutenticacionService después de validar LDAP.
    public Login emitirLdap(String uid) {
        Usuario u = em.createQuery(
            "select u from Usuario u where u.origenIdentidad='LDAP' and u.uidExterno=:uid",
            Usuario.class).setParameter("uid", uid).getResultStream().findFirst()
            .orElseThrow(() -> new ErrorAcceso(401, "Credenciales inválidas"));
        if (!u.getRoles().contains(TipoUsuario.ADMINISTRADOR)
                && !u.getRoles().contains(TipoUsuario.AUTORIDAD))
            throw new ErrorAcceso(403, "Cuenta interna sin rol habilitado");
        return emitir(u);
    }
    // Claims ya verificados criptográficamente por GubOidcClient.
    public Login emitirGub(GubOidcClient.Identidad i) {
        Usuario u = em.createQuery(
            "select u from Usuario u where u.origenIdentidad='GUB' and u.uidExterno=:uid",
            Usuario.class).setParameter("uid", i.subject()).getResultStream().findFirst().orElse(null);
        if (u == null) {
            long existe = em.createQuery("select count(u) from Usuario u where u.ci=:ci", Long.class)
                .setParameter("ci", i.ci()).getSingleResult();
            if (existe > 0) throw new ErrorAcceso(409,
                "La CI existe: un administrador debe vincular la identidad verificada. Referencia: "+i.subject());
            if (i.nacimiento()==null) throw new ErrorAcceso(409,
                "Un administrador debe crear el perfil completo y vincular la identidad verificada. Referencia: "+i.subject());
            Ciudadano c = new Ciudadano(i.nombre(), i.mail(), i.nacimiento(), i.ci(), "{GUB}");
            c.getRoles().add(TipoUsuario.CIUDADANO);
            c.setOrigenIdentidad("GUB"); c.setUidExterno(i.subject());
            em.persist(c); em.flush(); u = c;
        }
        if (u.getCi()!=i.ci()) throw new ErrorAcceso(409,"La identidad no corresponde a la CI del perfil vinculado");
        if (u.getRoles().contains(TipoUsuario.ADMINISTRADOR)
                || u.getRoles().contains(TipoUsuario.AUTORIDAD))
            throw new ErrorAcceso(403, "Las cuentas internas deben utilizar LDAP");
        return emitir(u);
    }
    private Login emitir(Usuario u) {
        if (!u.isActivo()) throw new ErrorAcceso(401, "Cuenta no habilitada");
        String token = Cripto.aleatorio();
        Instant vence = Instant.now().plus(Duration.ofMinutes(30));
        em.persist(new SesionAcceso(Cripto.hash(token), u, vence));
        return new Login(token, acceso(u), vence);
    }
    public Acceso validarToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new ErrorAcceso(401, "Sesión inválida");
        SesionAcceso s = em.find(SesionAcceso.class, Cripto.hash(token));
        if (s == null || !s.getVence().isAfter(Instant.now()) || !s.getUsuario().isActivo())
            throw new ErrorAcceso(401, "Sesión vencida o revocada");
        return acceso(s.getUsuario()); // Roles actuales, no una copia vieja en el token.
    }
    public void logout(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return;
        SesionAcceso s = em.find(SesionAcceso.class, Cripto.hash(token));
        if (s != null) em.remove(s);
    }
    public void revocarUsuario(int id) {
        em.createQuery("delete from SesionAcceso s where s.usuario.id=:id")
            .setParameter("id", id).executeUpdate();
    }
    @Schedule(hour="*", minute="*/15", persistent=false)
    public void limpiarVencidas() {
        em.createQuery("delete from SesionAcceso s where s.vence<=:ahora")
            .setParameter("ahora", Instant.now()).executeUpdate();
    }
    private Acceso acceso(Usuario u) {
        return new Acceso(u.getId(), u.getCi(), u.getNombre(), u.getRoles());
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/ConsultaCiudadanoService.java

Aplica permisos antes de consultar datos personales mediante PDI.

```java
package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.integration.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Stateless @LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConsultaCiudadanoService {
    @EJB private ControlAcceso control;
    @EJB private ClientePdi pdi;
    public ResultadoPdi consultar(String token,int ci,String correlacion){
        Acceso a=control.exigir(token);
        if(a.ci()!=ci&&!a.tiene(TipoUsuario.ADMINISTRADOR)&&!a.tiene(TipoUsuario.FUNCIONARIO))
            throw new ErrorAcceso(403,"No podés consultar datos personales de otra persona");
        return pdi.consultar(ci,correlacion);
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/EstadoService.java

Comprueba disponibilidad de la base para readiness.

```java
package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
import jakarta.persistence.*;
@Stateless @LocalBean @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EstadoService {
    @PersistenceContext(unitName="CargaUYPersistenceUnit")private EntityManager em;
    public boolean listo(){
        try{return ((Number)em.createNativeQuery("select 1").getSingleResult()).intValue()==1;}
        catch(RuntimeException e){return false;}
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/EvaluadorToleranciasService.java

Aplica las tolerancias actuales en kg y km en cada evaluación; no crea ni tramita casos.

```java
package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
@Stateless @LocalBean
public class EvaluadorToleranciasService {
    @EJB private ReglasLecturaService reglas;
    // Servicio interno del motor: el llamador ya identifica y valida vehículo/viaje.
    public boolean excesoCarga(double medidoKg,double pesoPropioKg,double capacidadKg){
        if(!Double.isFinite(medidoKg)||!Double.isFinite(pesoPropioKg)||!Double.isFinite(capacidadKg)
                ||medidoKg<0||pesoPropioKg<0||capacidadKg<0)throw new ErrorNegocio("Pesos inválidos");
        var r=reglas.actuales();
        return medidoKg>pesoPropioKg+capacidadKg+r.cargaKg();
    }
    public boolean superaDesvio(double distanciaKm){
        if(!Double.isFinite(distanciaKm)||distanciaKm<0)throw new ErrorNegocio("Distancia inválida");
        return distanciaKm>reglas.actuales().desvioKm();
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/FuncionarioEJB.java

Retira la antigua vía de alta sin identidad. Conserva la interfaz por compatibilidad de compilación.

```java
package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;
@Stateless @LocalBean
public class FuncionarioEJB implements FuncionarioEJBLocal {
    @Override public void crear(Funcionario f) {
        throw new ErrorNegocio("Alta antigua retirada: usar gestión con identidad autenticada");
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/GestionPerfilesService.java

Reemplaza las firmas inseguras por operaciones que exigen token; agrega paginación y comprobación de versiones.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.ejb.LocalBean;
import jakarta.ejb.EJB;
import uy.edu.fing.grupo07.CargaUY.security.*;
import static uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.*;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;

@Stateless
@LocalBean
public class GestionPerfilesService {
    @EJB private ControlAcceso control;
    @EJB private SesionesService sesiones;
    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public int crearUsuario(String token, DatosUsuario d) {
        control.admin(token);
        validarUsuario(d);
        long existentes = em.createQuery(
            "select count(u) from Usuario u where u.ci = :ci", Long.class)
            .setParameter("ci", d.ci()).getSingleResult();
        if (existentes > 0) throw new ErrorNegocio("Ya existe un usuario con esa CI");
        String password = d.password();
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ErrorNegocio("Contraseña: mínimo 8 caracteres y máximo 72 bytes UTF-8");
        char[] caracteres = password.toCharArray();
        String hash;
        try { hash = BCrypt.withDefaults().hashToString(12, caracteres); }
        finally { Arrays.fill(caracteres, '\0'); }
        Usuario u = switch (d.tipo()) {
            case CIUDADANO -> new Ciudadano();
            case CHOFER -> new Chofer();
            case FUNCIONARIO -> new Funcionario();
            case AUTORIDAD -> new Autoridad();
            case ADMINISTRADOR -> new Administrador();
        };
        copiarDatos(u, d);
        u.setPassword(hash);
        u.getRoles().add(d.tipo());
        em.persist(u);
        em.flush();
        return u.getId();
    }

    public UsuarioResumen consultarUsuario(String token, int id) {
        Acceso actor=control.exigir(token); if(actor.id()!=id && !actor.tiene(ADMINISTRADOR) && !actor.tiene(FUNCIONARIO)) throw new ErrorAcceso(403,"Perfil ajeno");
        return UsuarioResumen.desde(usuario(id));
    }

    public List<UsuarioResumen> listarUsuarios(String token, TipoUsuario tipo, int pagina, int tam) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        paginar(pagina,tam);
        // TYPE es el subtipo JPA, no una cadena concatenada al JPQL.
        List<Usuario> usuarios = tipo == null
            ? em.createQuery("select u from Usuario u order by u.id", Usuario.class)
                .setFirstResult(pagina*tam).setMaxResults(tam).getResultList()
            : em.createQuery("select u from Usuario u where type(u) = :tipo order by u.id",
                Usuario.class).setParameter("tipo", clase(tipo)).setFirstResult(pagina*tam).setMaxResults(tam).getResultList();
        return usuarios.stream().map(UsuarioResumen::desde).toList();
    }

    public void modificarUsuario(String token, int id, long versionEsperada, DatosUsuario d) {
        Acceso actor=control.exigir(token); if(actor.id()!=id && !actor.tiene(ADMINISTRADOR)) throw new ErrorAcceso(403,"Perfil ajeno");
        Usuario u = usuario(id);
        if(u.getVersion()!=versionEsperada) throw new ErrorAcceso(409,"Perfil modificado; recargá");
        validarUsuario(d);
        if (!clase(d.tipo()).isInstance(u) || u.getCi() != d.ci())
            throw new ErrorNegocio("Esta operación no cambia tipo ni CI");
        // La edición de perfil no cambia credenciales.
        copiarDatos(u, d);
    }

    public void cambiarEstadoUsuario(String token, int id, boolean activo) {
        Acceso actor=control.admin(token); if(actor.id()==id && !activo) throw new ErrorAcceso(409,"No desactivar tu propia cuenta"); sesiones.revocarUsuario(id);
        usuario(id).setActivo(activo);
    }

    public void crearEmpresa(String token, int numero, String razon, String nombre, String direccion) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        Validaciones.positivo(numero, "Número de empresa");
        if (em.find(Empresa.class, numero) != null)
            throw new ErrorNegocio("Ya existe esa empresa");
        em.persist(new Empresa(numero,
            Validaciones.texto(razon, "Razón social", 150),
            Validaciones.texto(nombre, "Nombre público", 150),
            Validaciones.texto(direccion, "Dirección", 255)));
    }

    public EmpresaResumen consultarEmpresa(String token, int numero) {
        control.empresa(token,numero,false);
        return EmpresaResumen.desde(empresa(numero));
    }

    public List<EmpresaResumen> listarEmpresas(String token, int pagina, int tam) {
        Acceso actor=control.exigir(token); paginar(pagina,tam);
        boolean global=actor.tiene(ADMINISTRADOR)||actor.tiene(FUNCIONARIO)||actor.tiene(AUTORIDAD);
        if(!global&&!actor.tiene(CIUDADANO)) return List.of();
        var q=em.createQuery(global ? "select e from Empresa e order by e.nroEmpresa"
            : "select v.empresa from VinculoEmpresa v where v.usuario.id=:id and v.responsable=true and v.empresa.activa=true order by v.empresa.nroEmpresa",Empresa.class);
        if(!global) q.setParameter("id",actor.id());
        return q.setFirstResult(pagina*tam).setMaxResults(tam).getResultList().stream()
            .map(EmpresaResumen::desde).toList();
    }

    public void modificarEmpresa(String token, int numero, long versionEsperada, String razon, String nombre, String direccion) {
        control.empresa(token,numero,true);
        Empresa e = empresa(numero);
        if(e.getVersion()!=versionEsperada) throw new ErrorAcceso(409,"Empresa modificada; recargá");
        e.setRazonSocial(Validaciones.texto(razon, "Razón social", 150));
        e.setNombrePublico(Validaciones.texto(nombre, "Nombre público", 150));
        e.setDireccion(Validaciones.texto(direccion, "Dirección", 255));
    }

    public void cambiarEstadoEmpresa(String token, int numero, boolean activa) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        empresa(numero).setActiva(activa);
    }

    public void asociarCiudadano(String token, int numeroEmpresa, int idCiudadano) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        Empresa e = empresa(numeroEmpresa);
        // Serializa cambios de asociación sobre la misma empresa.
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        if (!e.isActiva() || !c.isActivo())
            throw new ErrorNegocio("Empresa y ciudadano deben estar activos");
        if (e.getCiudadanos().stream().noneMatch(x -> x.getId()==c.getId())) e.getCiudadanos().add(c);
        if (c.getEmpresas().stream().noneMatch(x -> x.getNroEmpresa()==e.getNroEmpresa())) c.getEmpresas().add(e);
        if(!c.getRoles().contains(CIUDADANO)) throw new ErrorAcceso(409,"Falta rol CIUDADANO");
        VinculoEmpresa v=em.find(VinculoEmpresa.class,VinculoEmpresa.clave(numeroEmpresa,idCiudadano));
        if(v==null) { v=new VinculoEmpresa(c,e); em.persist(v); }
        v.setResponsable(true);
    }

    public void desasociarCiudadano(String token, int numeroEmpresa, int idCiudadano) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        Empresa e = empresa(numeroEmpresa);
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        e.getCiudadanos().removeIf(x -> x.getId()==c.getId());
        c.getEmpresas().removeIf(x -> x.getNroEmpresa()==e.getNroEmpresa());
        VinculoEmpresa v=em.find(VinculoEmpresa.class,VinculoEmpresa.clave(numeroEmpresa,idCiudadano));
        if(v!=null) v.setResponsable(false);
    }

    private void paginar(int pagina,int tam) {
        if(pagina<0||pagina>100000||tam<1||tam>100) throw new ErrorNegocio("Paginación inválida");
    }
    private Usuario usuario(int id) {
        Usuario u = em.find(Usuario.class, Validaciones.positivo(id, "ID"));
        if (u == null) throw new ErrorNegocio("Usuario inexistente");
        return u;
    }
    private Empresa empresa(int numero) {
        Empresa e = em.find(Empresa.class, Validaciones.positivo(numero, "Empresa"));
        if (e == null) throw new ErrorNegocio("Empresa inexistente");
        return e;
    }
    private void validarUsuario(DatosUsuario d) {
        if (d == null || d.tipo() == null) throw new ErrorNegocio("Tipo obligatorio");
        Validaciones.texto(d.nombre(), "Nombre", 100);
        String mail = Validaciones.texto(d.mail(), "Correo", 150);
        if (!mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ErrorNegocio("Correo inválido");
        Validaciones.positivo(d.ci(), "CI");
        if (d.fechaNac() == null || d.fechaNac().isAfter(LocalDate.now()))
            throw new ErrorNegocio("Fecha de nacimiento inválida");
        if (d.tipo() == TipoUsuario.FUNCIONARIO) {
            if (d.nroFuncionario() == null) throw new ErrorNegocio("Número obligatorio");
            Validaciones.positivo(d.nroFuncionario(), "Número de funcionario");
            Validaciones.texto(d.departamento(), "Departamento", 100);
        }
        if (d.tipo() == TipoUsuario.AUTORIDAD)
            Validaciones.texto(d.cargo(), "Cargo", 100);
    }
    private void copiarDatos(Usuario u, DatosUsuario d) {
        u.setNombre(d.nombre().trim());
        u.setMail(d.mail().trim().toLowerCase(Locale.ROOT));
        u.setFechaNac(d.fechaNac());
        u.setCi(d.ci());
        if (u instanceof Funcionario f) {
            f.setNroFuncionario(d.nroFuncionario());
            f.setDepartamento(d.departamento().trim());
        }
        if (u instanceof Autoridad a) a.setCargo(d.cargo().trim());
    }
    private Class<? extends Usuario> clase(TipoUsuario tipo) {
        return switch (tipo) {
            case CIUDADANO -> Ciudadano.class;
            case CHOFER -> Chofer.class;
            case FUNCIONARIO -> Funcionario.class;
            case AUTORIDAD -> Autoridad.class;
            case ADMINISTRADOR -> Administrador.class;
        };
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/GestionPlataformaService.java

Exige permisos para catálogos y reglas; las escrituras requieren administrador.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;
import jakarta.ejb.LocalBean;
import jakarta.ejb.EJB;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

@Stateless
@LocalBean
public class GestionPlataformaService {
    @EJB private ControlAcceso control;
    public record RubroVista(int codigo, String nombre, String descripcion) {}
    public record TipoCargaVista(int codigo, String descripcion) {}
    public record ReglaVista(int cargaKg, double desvioKm, long version) {}

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public int crearRubro(String token, String nombre, String descripcion) {
        control.admin(token);
        Rubro r = new Rubro(Validaciones.texto(nombre, "Nombre", 100),
            descripcion(descripcion, 500));
        em.persist(r);
        em.flush();
        return r.getCodigo();
    }
    
    public List<RubroVista> listarRubros(String token) {
        control.exigir(token);
        return em.createQuery("select r from Rubro r order by r.codigo", Rubro.class)
            .getResultList().stream()
            .map(r -> new RubroVista(r.getCodigo(), r.getNombre(), r.getDesc())).toList();
    }
    
    public void modificarRubro(String token, int codigo, String nombre, String descripcion) {
        control.admin(token);
        Rubro r = requerido(Rubro.class, codigo);
        r.setNombre(Validaciones.texto(nombre, "Nombre", 100));
        r.setDesc(descripcion(descripcion, 500));
    }
    
    public void eliminarRubro(String token, int codigo) {
        control.admin(token);
        Rubro r = requerido(Rubro.class, codigo);
        long referencias = em.createQuery(
            "select count(e) from Empresa e join e.rubros r where r.codigo = :codigo",
            Long.class).setParameter("codigo", codigo).getSingleResult();
        if (referencias > 0) throw new ErrorNegocio("El rubro está asociado a empresas");
        em.remove(r);
        // La FK sigue protegiendo si hay una asociación concurrente.
        em.flush();
    }
    
    public int crearTipoCarga(String token, String descripcion) {
        control.admin(token);
        TipoCarga t = new TipoCarga(Validaciones.texto(descripcion, "Descripción", 255));
        em.persist(t);
        em.flush();
        return t.getCodigo();
    }
    
    public List<TipoCargaVista> listarTiposCarga(String token) {
        control.exigir(token);
        return em.createQuery("select t from TipoCarga t order by t.codigo", TipoCarga.class)
            .getResultList().stream()
            .map(t -> new TipoCargaVista(t.getCodigo(), t.getDesc())).toList();
    }
    
    public void modificarTipoCarga(String token, int codigo, String descripcion) {
        control.admin(token);
        requerido(TipoCarga.class, codigo).setDesc(
            Validaciones.texto(descripcion, "Descripción", 255));
    }
    
    public void eliminarTipoCarga(String token, int codigo) {
        control.admin(token);
        TipoCarga t = requerido(TipoCarga.class, codigo);
        long referencias = em.createQuery(
            "select count(g) from GuiaDeViaje g join g.tiposCarga t where t.codigo = :codigo",
            Long.class).setParameter("codigo", codigo).getSingleResult();
        if (referencias > 0) throw new ErrorNegocio("El tipo está utilizado en guías");
        em.remove(t);
        em.flush();
    }
    
    public ReglaVista consultarRegla(String token) {
        control.admin(token);
        ReglaFiscalizacion r = em.find(ReglaFiscalizacion.class, 1);
        if (r == null) throw new ErrorNegocio("Todavía no se configuró la regla");
        return vista(r);
    }
    
    public void crearRegla(String token, int cargaKg, double desvioKm) {
        control.admin(token);
        validarUmbrales(cargaKg, desvioKm);
        if (em.find(ReglaFiscalizacion.class, 1) != null)
            throw new ErrorNegocio("La regla ya existe; usá editar");
        em.persist(new ReglaFiscalizacion(cargaKg, desvioKm));
    }
    
    public void editarRegla(String token, int cargaKg, double desvioKm, long versionEsperada) {
        control.admin(token);
        validarUmbrales(cargaKg, desvioKm);
        ReglaFiscalizacion r = requerido(ReglaFiscalizacion.class, 1);
        if (r.getVersion() != versionEsperada)
            throw new ErrorNegocio("La regla cambió; consultala de nuevo antes de guardar");
        r.setUmbralCarga(cargaKg);
        r.setUmbralTracking(desvioKm);
        em.flush(); // @Version también detecta cambios concurrentes posteriores a la lectura.
    }
    
    private ReglaVista vista(ReglaFiscalizacion r) {
        return new ReglaVista(r.getUmbralCarga(), r.getUmbralTracking(), r.getVersion());
    }
    
    private void validarUmbrales(int cargaKg, double desvioKm) {
        if (cargaKg < 0 || !Double.isFinite(desvioKm) || desvioKm < 0)
            throw new ErrorNegocio("Umbrales no negativos: carga en kg y desvío en km");
    }
    
    private <T> T requerido(Class<T> clase, int codigo) {
        T entidad = em.find(clase, Validaciones.positivo(codigo, "Código"));
        if (entidad == null) throw new ErrorNegocio("No existe ese registro");
        return entidad;
    }
    
    private String descripcion(String texto, int maximo) {
        if (texto == null) return null;
        if (texto.length() > maximo) throw new ErrorNegocio("Descripción demasiado larga");
        return texto.trim();
    }
}
```

## ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/ReglasLecturaService.java

Lee la configuración actual para el motor sin conservar una copia estática desactualizada.

```java
package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;

@Stateless @LocalBean
public class ReglasLecturaService {
    public record Snapshot(int cargaKg,double desvioKm,long version){}
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    // Solo lectura interna para el motor; no exponer como endpoint público.
    public Snapshot actuales(){
        ReglaFiscalizacion r=em.find(ReglaFiscalizacion.class,1,
            java.util.Map.of("jakarta.persistence.cache.retrieveMode",CacheRetrieveMode.BYPASS));
        if(r==null)throw new ErrorNegocio("Regla sin configurar");
        em.refresh(r);
        return new Snapshot(r.getUmbralCarga(),r.getUmbralTracking(),r.getVersion());
    }
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/integration/ClientePdiTest.java

WS-Addressing, inexistencia, XML inseguro, reintentos y saturación.

```java
package uy.edu.fing.grupo07.CargaUY.integration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientePdiTest {
    private static final String ID="urn:uuid:11111111-1111-4111-8111-111111111111";
    private String respuesta(String id,boolean ausente){
        String s="http://schemas.xmlsoap.org/soap/envelope/",a="http://www.w3.org/2005/08/addressing";
        String cuerpo=ausente?
            "<s:Fault><faultcode>s:Client</faultcode><faultstring>No encontrado</faultstring><detail>"
            +"<p:CiudadanoNoEncontrado><ci>12345678</ci></p:CiudadanoNoEncontrado></detail></s:Fault>":
            "<p:consultarCiudadanoResponse><ci>12345678</ci><nombre>Juan</nombre><apellido>Perez</apellido>"
            +"<fechaNacimiento>1985-05-15</fechaNacimiento><vigente>true</vigente></p:consultarCiudadanoResponse>";
        return "<s:Envelope xmlns:s=\""+s+"\" xmlns:w=\""+a+"\" xmlns:p=\"http://pdi.dnic.gub.uy/\">"
            +"<s:Header><w:Action>"+(ausente?a+"/fault":"urn:cargauy:pdi:consultarCiudadanoResponse")
            +"</w:Action><w:RelatesTo>"+id+"</w:RelatesTo></s:Header><s:Body>"+cuerpo+"</s:Body></s:Envelope>";
    }
    @Test void respuestaExitosaConAddressing()throws Exception{
        assertEquals(ResultadoPdi.Estado.DISPONIBLE,new ClientePdi().interpretar(respuesta(ID,false),12345678,ID).estado());
    }
    @Test void ciudadanoInexistenteNoEsFallaDeRed()throws Exception{
        assertEquals(ResultadoPdi.Estado.NO_ENCONTRADO,new ClientePdi().interpretar(respuesta(ID,true),12345678,ID).estado());
    }
    @Test void rechazaRespuestaDeOtraSolicitud(){
        assertThrows(Exception.class,()->new ClientePdi().interpretar(respuesta(ID,false),12345678,"otro"));
    }
    @Test void rechazaDTD(){
        assertThrows(Exception.class,()->new ClientePdi().interpretar(
            "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///no-leer'>]>"+respuesta(ID,false),12345678,ID));
    }
    @Test void limitaReintentosYAbreCircuito()throws Exception{
        var llamadas=new java.util.concurrent.atomic.AtomicInteger();
        var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        servidor.createContext("/pdi/ws",ex->{llamadas.incrementAndGet();ex.sendResponseHeaders(503,-1);ex.close();});
        String anterior=System.getProperty("pdi.endpoint"),local=System.getProperty("p1.entorno.local");
        try{
            servidor.start();System.setProperty("pdi.endpoint","http://127.0.0.1:"+servidor.getAddress().getPort()+"/pdi/ws");
            System.setProperty("p1.entorno.local","true");
            ClientePdi c=new ClientePdi();c.iniciar();
            for(int i=0;i<4;i++)assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE,c.consultar(12345678).estado());
            assertEquals(6,llamadas.get());
        }finally{
            servidor.stop(0);restaurar("pdi.endpoint",anterior);restaurar("p1.entorno.local",local);
        }
    }
    @Test void saturacionRechazaSinCrearColaIlimitada()throws Exception{
        var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var ejecutorServidor=java.util.concurrent.Executors.newFixedThreadPool(4);servidor.setExecutor(ejecutorServidor);
        var ejecutor=java.util.concurrent.Executors.newFixedThreadPool(12);
        servidor.createContext("/pdi/ws",ex->{
            String req=new String(ex.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var m=java.util.regex.Pattern.compile("<w:MessageID>([^<]+)</w:MessageID>").matcher(req);
            String id=m.find()?m.group(1):"";
            try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] data=respuesta(id,false).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
        });
        String anterior=System.getProperty("pdi.endpoint"),local=System.getProperty("p1.entorno.local");
        try{
            servidor.start();System.setProperty("pdi.endpoint","http://127.0.0.1:"+servidor.getAddress().getPort()+"/pdi/ws");
            System.setProperty("p1.entorno.local","true");
            ClientePdi c=new ClientePdi();c.iniciar();var salida=new java.util.ArrayList<java.util.concurrent.Future<ResultadoPdi>>();
            var inicio=new java.util.concurrent.CyclicBarrier(12);
            for(int i=0;i<12;i++)salida.add(ejecutor.submit(()->{inicio.await();return c.consultar(12345678);}));
            int disponibles=0;
            for(var futuro:salida)if(futuro.get(8,java.util.concurrent.TimeUnit.SECONDS).estado()==ResultadoPdi.Estado.DISPONIBLE)disponibles++;
            assertTrue(disponibles<=4);assertTrue(c.metricas()[2]>=8);
        }finally{
            servidor.stop(0);ejecutor.shutdownNow();ejecutorServidor.shutdownNow();
            restaurar("pdi.endpoint",anterior);restaurar("p1.entorno.local",local);
        }
    }
    private void restaurar(String clave,String v){if(v==null)System.clearProperty(clave);else System.setProperty(clave,v);}
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/FlujosP2Test.java

PKCE, expiración y consumo de un flujo móvil.

```java
package uy.edu.fing.grupo07.CargaUY.security;
import java.lang.reflect.Field;
import java.time.Instant;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.FlujoIdentidad;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FlujosP2Test {
 private FlujosService servicio(EntityManager em)throws Exception{
  var s=new FlujosService();Field f=FlujosService.class.getDeclaredField("em");f.setAccessible(true);f.set(s,em);return s;
 }
 @Test void iniciaConPkceS256YPersistencia()throws Exception{
  var em=mock(EntityManager.class);var inicio=servicio(em).preparar(Cripto.pkce(Cripto.aleatorio()));
  assertEquals(43,inicio.state().length());assertTrue(inicio.url().contains("code_challenge_method=S256"));
  verify(em).persist(any(FlujoIdentidad.class));
 }
 @Test void consumeSoloConVerificadorCorrecto()throws Exception{
  var em=mock(EntityManager.class);String state=Cripto.aleatorio(),v=Cripto.aleatorio();
  var f=new FlujoIdentidad(Cripto.hash(state),Cripto.pkce(v),"nonce",Instant.now().plusSeconds(60));
  when(em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE)).thenReturn(f);
  assertEquals("nonce",servicio(em).consumir(state,v));verify(em).remove(f);
 }
 @Test void noConsumeConVerificadorAjeno()throws Exception{
  var em=mock(EntityManager.class);String state=Cripto.aleatorio(),v=Cripto.aleatorio();
  var f=new FlujoIdentidad(Cripto.hash(state),Cripto.pkce(v),"nonce",Instant.now().plusSeconds(60));
  when(em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE)).thenReturn(f);
  assertThrows(ErrorAcceso.class,()->servicio(em).consumir(state,Cripto.aleatorio()));
  verify(em,never()).remove(any());
 }
 @Test void flujoVencidoEsRechazado()throws Exception{
  var em=mock(EntityManager.class);String state=Cripto.aleatorio(),v=Cripto.aleatorio();
  var f=new FlujoIdentidad(Cripto.hash(state),Cripto.pkce(v),"nonce",Instant.now().minusSeconds(1));
  when(em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE)).thenReturn(f);
  assertThrows(ErrorAcceso.class,()->servicio(em).consumir(state,v));
 }
 @Test void stateNoRegistradoNoAutoriza()throws Exception{
  assertThrows(ErrorAcceso.class,()->servicio(mock(EntityManager.class)).consumir(Cripto.aleatorio(),Cripto.aleatorio()));
 }
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/IdUruguayP2Test.java

Prueba el canje HTTP Basic con credenciales ficticias, claims documentales, identidad separada por issuer, nacimiento ausente y bloqueo móvil.

```java
package uy.edu.fing.grupo07.CargaUY.security;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdUruguayP2Test {
 @Test void canjeBasicYDocumentoSinInventarNacimiento() throws Exception {
  String[] claves={"gub.proveedor","gub.issuer","gub.client.id","gub.client.secret","gub.redirect",
   "gub.document.pais","gub.document.tipo","gub.jwks","gub.pkce","p1.entorno.local"};
  Map<String,String> previos=new HashMap<>();for(String k:claves)previos.put(k,System.getProperty(k));
  var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  List<String> errores=Collections.synchronizedList(new ArrayList<>());
  try {
   var gen=java.security.KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var par=gen.generateKeyPair();
   var pub=(java.security.interfaces.RSAPublicKey)par.getPublic();
   java.util.function.Function<byte[],String> b64=b->Base64.getUrlEncoder().withoutPadding().encodeToString(b);
   String issuer="http://127.0.0.1:"+servidor.getAddress().getPort()+"/oidc/v1";
   long ahora=Instant.now().getEpochSecond();
   String payload=jakarta.json.Json.createObjectBuilder().add("sub","persona-verificada")
    .add("iss",issuer).add("aud","890192").add("nonce","nonce-prueba").add("iat",ahora).add("exp",ahora+60).build().toString();
   String partes=b64.apply("{\"alg\":\"RS256\",\"kid\":\"prueba\"}".getBytes(StandardCharsets.UTF_8))
    +"."+b64.apply(payload.getBytes(StandardCharsets.UTF_8));
   var firma=java.security.Signature.getInstance("SHA256withRSA");firma.initSign(par.getPrivate());
   firma.update(partes.getBytes(StandardCharsets.US_ASCII));String jwt=partes+"."+b64.apply(firma.sign());
   String jwks="{\"keys\":[{\"kid\":\"prueba\",\"kty\":\"RSA\",\"n\":\""
    +b64.apply(pub.getModulus().toByteArray())+"\",\"e\":\""+b64.apply(pub.getPublicExponent().toByteArray())+"\"}]}";
   servidor.createContext("/oidc/v1/jwks",e->{byte[] b=jwks.getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
   servidor.createContext("/oidc/v1/token",e->{
    String esperado="Basic "+Base64.getEncoder().encodeToString("890192:secreto-ficticio".getBytes(StandardCharsets.UTF_8));
    if(!esperado.equals(e.getRequestHeaders().getFirst("Authorization")))errores.add("Basic");
    String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
    if(body.contains("client_secret")||body.contains("code_verifier"))errores.add("formulario");
    if(!body.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080"))errores.add("redirect");
    byte[] b=("{\"id_token\":\""+jwt+"\",\"access_token\":\"acceso-ficticio\"}").getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
   servidor.createContext("/oidc/v1/userinfo",e->{
    if(!"Bearer acceso-ficticio".equals(e.getRequestHeaders().getFirst("Authorization")))errores.add("Bearer");
    byte[] b=("{\"sub\":\"persona-verificada\",\"pais_documento\":\"UY\",\"tipo_documento\":\"CI\","
      +"\"numero_documento\":\"1.234.567-8\",\"name\":\"Persona Prueba\",\"email\":\"persona@example.invalid\","
      +"\"email_verified\":true}").getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
   System.setProperty("gub.proveedor","iduruguay");System.setProperty("gub.issuer",issuer);
   System.setProperty("gub.client.id","890192");System.setProperty("gub.client.secret","secreto-ficticio");
   System.setProperty("gub.redirect","http://localhost:8080");System.setProperty("p1.entorno.local","true");
   System.setProperty("gub.jwks",issuer+"/jwks");System.setProperty("gub.document.pais","UY");
   System.setProperty("gub.document.tipo","CI");System.setProperty("gub.pkce","false");
   servidor.start();
   var identidad=new GubOidcClient().validar("codigo-ficticio",Cripto.aleatorio(),"nonce-prueba");
   assertEquals(12345678,identidad.ci());assertNull(identidad.nacimiento());
   assertEquals(Cripto.hash(issuer+"\npersona-verificada"),identidad.subject());
   assertTrue(errores.isEmpty(),errores.toString());
   assertEquals(issuer+"/authorize",GubOidcClient.autorizacion());
   assertTrue(GubOidcClient.scopes().contains("document"));
   System.setProperty("gub.document.tipo","PASAPORTE");
   assertThrows(ErrorAcceso.class,()->new GubOidcClient().validar("otro-codigo",Cripto.aleatorio(),"nonce-prueba"));
  } finally {
   servidor.stop(0);
   for(String k:claves){if(previos.get(k)==null)System.clearProperty(k);else System.setProperty(k,previos.get(k));}
  }
 }
 @Test void movilRealBloqueadoSinCallbackRegistrado(){
  String antes=System.getProperty("gub.proveedor"), habilitado=System.getProperty("gub.mobile.enabled");
  try {
   System.setProperty("gub.proveedor","iduruguay");System.setProperty("gub.mobile.enabled","false");
   assertThrows(ErrorAcceso.class,()->new FlujosService().preparar(Cripto.pkce(Cripto.aleatorio())));
   assertThrows(ErrorAcceso.class,()->new FlujosService().consumir(Cripto.aleatorio(),Cripto.aleatorio()));
  } finally{
   if(antes==null)System.clearProperty("gub.proveedor");else System.setProperty("gub.proveedor",antes);
   if(habilitado==null)System.clearProperty("gub.mobile.enabled");else System.setProperty("gub.mobile.enabled",habilitado);
  }
 }
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/OidcP2Test.java

Rechaza alg=none y comprueba firmas y claims con claves RSA de prueba.

```java
package uy.edu.fing.grupo07.CargaUY.security;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OidcP2Test {
    @Test void rechazaAlgoritmoNoneSinConsultarClaves()throws Exception{
        String encabezado=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"alg\":\"none\",\"kid\":\"x\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Method m=GubOidcClient.class.getDeclaredMethod("validarIdToken",String.class,String.class);m.setAccessible(true);
        var fallo=assertThrows(InvocationTargetException.class,()->m.invoke(new GubOidcClient(),encabezado+".e30.firma","nonce"));
        assertInstanceOf(ErrorAcceso.class,fallo.getCause());
    }
@Test void verificaFirmaIssuerAudienciaNonceYVencimiento()throws Exception{
        var generador=java.security.KeyPairGenerator.getInstance("RSA");generador.initialize(2048);
        var par=generador.generateKeyPair();var publica=(java.security.interfaces.RSAPublicKey)par.getPublic();
        var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        java.util.function.Function<byte[],String> b64=bytes->java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String jwks="{\"keys\":[{\"kid\":\"prueba\",\"kty\":\"RSA\",\"n\":\""
            +b64.apply(publica.getModulus().toByteArray())+"\",\"e\":\""
            +b64.apply(publica.getPublicExponent().toByteArray())+"\"}]}";
        servidor.createContext("/realms/prueba/protocol/openid-connect/certs",ex->{
            byte[] datos=jwks.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,datos.length);ex.getResponseBody().write(datos);ex.close();
        });
        String anterior=System.getProperty("gub.issuer"),local=System.getProperty("p1.entorno.local");
        try{
            servidor.start();String issuer="http://127.0.0.1:"+servidor.getAddress().getPort()+"/realms/prueba";
            System.setProperty("gub.issuer",issuer);System.setProperty("p1.entorno.local","true");
            Method m=GubOidcClient.class.getDeclaredMethod("validarIdToken",String.class,String.class);m.setAccessible(true);
            for(int caso=0;caso<5;caso++){
                long ahora=java.time.Instant.now().getEpochSecond();
                var payload=jakarta.json.Json.createObjectBuilder().add("sub","ciudadano")
                    .add("iss",caso==1?"https://otro.example":issuer).add("aud",caso==2?"otro":"cargauy")
                    .add("nonce",caso==3?"otro":"nonce").add("iat",ahora).add("exp",caso==4?ahora-5:ahora+60).build();
                String h=b64.apply("{\"alg\":\"RS256\",\"kid\":\"prueba\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String cuerpo=h+"."+b64.apply(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var firma=java.security.Signature.getInstance("SHA256withRSA");firma.initSign(par.getPrivate());
                firma.update(cuerpo.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String jwt=cuerpo+"."+b64.apply(firma.sign());
                if(caso==0)assertNotNull(m.invoke(new GubOidcClient(),jwt,"nonce"));
                else assertThrows(InvocationTargetException.class,()->m.invoke(new GubOidcClient(),jwt,"nonce"));
            }
        }finally{
            servidor.stop(0);
            if(anterior==null)System.clearProperty("gub.issuer");else System.setProperty("gub.issuer",anterior);
            if(local==null)System.clearProperty("p1.entorno.local");else System.setProperty("p1.entorno.local",local);
        }
    }
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/SeguridadP2Test.java

Tokens, logout, estado del usuario, roles, pertenencia y ausencia de fallback ante caída de LDAP.

```java
package uy.edu.fing.grupo07.CargaUY.security;

import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeguridadP2Test {
    private void inyectar(Object destino,String nombre,Object valor)throws Exception{
        Field f=destino.getClass().getDeclaredField(nombre);f.setAccessible(true);f.set(destino,valor);
    }
    private Administrador admin(){
        Administrador u=new Administrador("Admin","admin@example.invalid",LocalDate.of(1990,1,1),
            90000001,"{LDAP}");u.setId(7);u.getRoles().add(TipoUsuario.ADMINISTRADOR);return u;
    }
    @Test void pkceUsaElVectorDeReferencia(){
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Cripto.pkce("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }
    @Test void tokensNoSeRepitenYElHashNoEsElToken(){
        Set<String> tokens=new HashSet<>();
        for(int i=0;i<1000;i++){String t=Cripto.aleatorio();assertEquals(43,t.length());assertTrue(tokens.add(t));
            assertEquals(64,Cripto.hash(t).length());assertNotEquals(t,Cripto.hash(t));}
    }
    @Test void tokenVencidoEsRechazado()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();
        when(em.find(SesionAcceso.class,Cripto.hash(token)))
            .thenReturn(new SesionAcceso(Cripto.hash(token),admin(),Instant.now().minusSeconds(1)));
        assertEquals(401,assertThrows(ErrorAcceso.class,()->s.validarToken(token)).getEstado());
    }
    @Test void bajaInvalidaLaSesionAunqueElTokenNoVencio()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();var u=admin();u.setActivo(false);
        when(em.find(SesionAcceso.class,Cripto.hash(token)))
            .thenReturn(new SesionAcceso(Cripto.hash(token),u,Instant.now().plusSeconds(60)));
        assertThrows(ErrorAcceso.class,()->s.validarToken(token));
    }
    @Test void rolesSeLeenDeLaCuentaActual()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();var u=admin();
        when(em.find(SesionAcceso.class,Cripto.hash(token)))
            .thenReturn(new SesionAcceso(Cripto.hash(token),u,Instant.now().plusSeconds(60)));
        assertTrue(s.validarToken(token).tiene(TipoUsuario.ADMINISTRADOR));
        u.getRoles().clear();u.getRoles().add(TipoUsuario.AUTORIDAD);
        assertFalse(s.validarToken(token).tiene(TipoUsuario.ADMINISTRADOR));
    }
    @Test void logoutBorraLaSesion()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();var entidad=new SesionAcceso(Cripto.hash(token),admin(),Instant.now());
        when(em.find(SesionAcceso.class,Cripto.hash(token))).thenReturn(entidad);
        s.logout(token);verify(em).remove(entidad);
    }
    @Test void empresaAjenaSeRechazaEnNegocio()throws Exception{
        var sesiones=mock(SesionesService.class);var control=new ControlAcceso();var em=mock(EntityManager.class);
        inyectar(control,"sesiones",sesiones);inyectar(control,"em",em);
        when(sesiones.validarToken("token")).thenReturn(new Acceso(7,12345678,"Ana",Set.of(TipoUsuario.CIUDADANO)));
        when(em.find(Empresa.class,100)).thenReturn(new Empresa(100,"SA","Empresa","Montevideo"));
        assertEquals(403,assertThrows(ErrorAcceso.class,()->control.empresa("token",100,true)).getEstado());
    }
    @Test void passwordVacioNoHaceBindAnonimo(){
        assertEquals(401,assertThrows(ErrorAcceso.class,()->new LdapClient().autenticar("martin.admin","")).getEstado());
    }
    @Test void caidaLdapNoEmiteSesion()throws Exception{
        var auth=new AutenticacionService();var ldap=mock(LdapClient.class);var sesiones=mock(SesionesService.class);
        inyectar(auth,"ldap",ldap);inyectar(auth,"sesiones",sesiones);
        doThrow(new ErrorAcceso(503,"Caído")).when(ldap).autenticar("uid","password");
        assertThrows(ErrorAcceso.class,()->auth.login("uid","password"));
        verifyNoInteractions(sesiones);
    }
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/EvaluadorToleranciasServiceTest.java

Comprueba que una nueva evaluación use las tolerancias actualizadas.

```java
package uy.edu.fing.grupo07.CargaUY.service;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EvaluadorToleranciasServiceTest {
 private EvaluadorToleranciasService servicio(ReglasLecturaService reglas)throws Exception{
  var s=new EvaluadorToleranciasService();Field f=EvaluadorToleranciasService.class.getDeclaredField("reglas");
  f.setAccessible(true);f.set(s,reglas);return s;
 }
 @Test void cargaUsaLaConfiguracionDeCadaEvaluacion()throws Exception{
  var reglas=mock(ReglasLecturaService.class);
  when(reglas.actuales()).thenReturn(new ReglasLecturaService.Snapshot(500,5,0),new ReglasLecturaService.Snapshot(100,5,1));
  var s=servicio(reglas);
  assertFalse(s.excesoCarga(20200,10000,10000));assertTrue(s.excesoCarga(20200,10000,10000));
 }
 @Test void desvioUsaKilometrosFraccionariosActualizados()throws Exception{
  var reglas=mock(ReglasLecturaService.class);
  when(reglas.actuales()).thenReturn(new ReglasLecturaService.Snapshot(500,1,0),new ReglasLecturaService.Snapshot(500,0.5,1));
  var s=servicio(reglas);assertFalse(s.superaDesvio(0.8));assertTrue(s.superaDesvio(0.8));
 }
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/GestionPerfilesServiceTest.java

Versión actualizada de los tests de la guía 1, con seguridad inyectada.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import java.lang.reflect.Field;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionPerfilesServiceTest {
    private GestionPerfilesService servicio(EntityManager em) throws Exception {
        GestionPerfilesService s = new GestionPerfilesService();
        Field f = GestionPerfilesService.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(s, em);
        Field seguridad=GestionPerfilesService.class.getDeclaredField("control");
        seguridad.setAccessible(true);
        var control=mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        when(control.admin("token")).thenReturn(new uy.edu.fing.grupo07.CargaUY.security.Acceso(
            99,90000001,"Admin",java.util.Set.of(TipoUsuario.ADMINISTRADOR)));
        seguridad.set(s,control);
        Field sesiones=GestionPerfilesService.class.getDeclaredField("sesiones");
        sesiones.setAccessible(true);sesiones.set(s,mock(uy.edu.fing.grupo07.CargaUY.security.SesionesService.class));
        return s;
    }
    @Test void asociacionRepetidaNoDuplicaNingunLado() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Empresa empresa = new Empresa(10, "Empresa SA", "Empresa", "Montevideo");
        Ciudadano ciudadano = new Ciudadano("Ana", "ana@example.com", LocalDate.of(1990,1,1),
            12345678, "hash-de-prueba");
        ciudadano.setId(7);
        ciudadano.getRoles().add(TipoUsuario.CIUDADANO);
        when(em.find(Empresa.class, 10)).thenReturn(empresa);
        when(em.find(Ciudadano.class, 7)).thenReturn(ciudadano);
        GestionPerfilesService s = servicio(em);
        s.asociarCiudadano("token", 10, 7);
        s.asociarCiudadano("token", 10, 7);
        assertEquals(1, empresa.getCiudadanos().size());
        assertEquals(1, ciudadano.getEmpresas().size());
        s.desasociarCiudadano("token", 10, 7);
        assertTrue(empresa.getCiudadanos().isEmpty());
        assertTrue(ciudadano.getEmpresas().isEmpty());
    }
    @Test void ciudadanoInactivoNoPuedeAsociarse() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Empresa e = new Empresa(10, "Empresa SA", "Empresa", "Montevideo");
        Ciudadano c = new Ciudadano();
        c.setActivo(false);
        when(em.find(Empresa.class, 10)).thenReturn(e);
        when(em.find(Ciudadano.class, 7)).thenReturn(c);
        assertThrows(ErrorNegocio.class, () -> servicio(em).asociarCiudadano("token", 10, 7));
        assertTrue(e.getCiudadanos().isEmpty());
    }
    @Test void bajaNoEliminaElRegistro() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Ciudadano c = new Ciudadano();
        when(em.find(Usuario.class, 7)).thenReturn(c);
        servicio(em).cambiarEstadoUsuario("token", 7, false);
        assertFalse(c.isActivo());
        verify(em, never()).remove(any());
    }
    @Test void resumenNoIncluyeCredencial() {
        Ciudadano c = new Ciudadano("Ana", "ana@example.com", LocalDate.of(1990,1,1),
            12345678, "secreto-que-no-se-debe-mostrar");
        assertFalse(UsuarioResumen.desde(c).toString().contains(c.getPassword()));
    }

    @Test @SuppressWarnings("unchecked")
    void altaGuardaHashEnLugarDeContrasenaPlana() throws Exception {
        EntityManager em = mock(EntityManager.class);
        jakarta.persistence.TypedQuery<Long> q = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery("select count(u) from Usuario u where u.ci = :ci", Long.class)).thenReturn(q);
        when(q.setParameter("ci", 12345678)).thenReturn(q);
        when(q.getSingleResult()).thenReturn(0L);
        java.util.concurrent.atomic.AtomicReference<Usuario> guardado =
            new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocacion -> {
            Usuario u = invocacion.getArgument(0); u.setId(7); guardado.set(u); return null;
        }).when(em).persist(any(Usuario.class));
        DatosUsuario datos = new DatosUsuario(TipoUsuario.CIUDADANO, "Ana",
            "ana@example.com", LocalDate.of(1990,1,1), 12345678,
            "ClavePrueba123!", null, null, null);
        assertEquals(7, servicio(em).crearUsuario("token", datos));
        assertNotEquals(datos.password(), guardado.get().getPassword());
        assertTrue(at.favre.lib.crypto.bcrypt.BCrypt.verifyer()
            .verify(datos.password().toCharArray(), guardado.get().getPassword()).verified);
    }
    @Test @SuppressWarnings("unchecked")
    void altaRechazaCedulaDuplicada() throws Exception {
        EntityManager em = mock(EntityManager.class);
        jakarta.persistence.TypedQuery<Long> q = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery("select count(u) from Usuario u where u.ci = :ci", Long.class)).thenReturn(q);
        when(q.setParameter("ci", 12345678)).thenReturn(q);
        when(q.getSingleResult()).thenReturn(1L);
        DatosUsuario datos = new DatosUsuario(TipoUsuario.CIUDADANO, "Ana",
            "ana@example.com", LocalDate.of(1990,1,1), 12345678,
            "ClavePrueba123!", null, null, null);
        assertThrows(ErrorNegocio.class, () -> servicio(em).crearUsuario("token", datos));
        verify(em, never()).persist(any());
    }
}
```

## ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/GestionPlataformaServiceTest.java

Versión actualizada de los tests de reglas.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionPlataformaServiceTest {
    private GestionPlataformaService servicio(EntityManager em) throws Exception {
        GestionPlataformaService s = new GestionPlataformaService();
        Field f = GestionPlataformaService.class.getDeclaredField("em");
        f.setAccessible(true); f.set(s, em);
        Field seguridad=GestionPlataformaService.class.getDeclaredField("control");
        seguridad.setAccessible(true);seguridad.set(s,mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class));
        return s;
    }
    @Test void rechazaUnaVersionVieja() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);
        assertThrows(ErrorNegocio.class, () -> servicio(em).editarRegla("token", 100, 0.5, 99));
        assertEquals(500, regla.getUmbralCarga());
    }
    @Test void permiteKilometrosFraccionarios() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);
        servicio(em).editarRegla("token", 100, 0.5, 0);
        assertEquals(0.5, regla.getUmbralTracking());
        verify(em).flush();
    }
    @Test void rechazaNaN() throws Exception {
        EntityManager em = mock(EntityManager.class);
        assertThrows(ErrorNegocio.class, () -> servicio(em).crearRegla("token", 500, Double.NaN));
        verify(em, never()).persist(any());
    }
}
```

## gub-callback-root/src/main/webapp/index.jsp

Recibe el callback en la URI raíz registrada y lo retransmite a un destino fijo; no crea sesión ni intercambia tokens.

```jsp
<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ page import="java.net.URLEncoder,java.nio.charset.StandardCharsets" %>
<%
response.setHeader("Cache-Control","no-store");
response.setHeader("Pragma","no-cache");
response.setHeader("Referrer-Policy","no-referrer");
response.setHeader("X-Content-Type-Options","nosniff");
String state=request.getParameter("state"), code=request.getParameter("code"), error=request.getParameter("error");
String contexto=application.getInitParameter("cargauy.context");
if(contexto==null || !contexto.matches("/[A-Za-z0-9_-]+")) { response.sendError(503); return; }
if(code!=null || error!=null || state!=null) {
    if(state==null || !state.matches("[A-Za-z0-9_-]{43}") || (code==null)==(error==null)
        || code!=null && (code.isBlank() || code.length()>4096)
        || error!=null && !error.matches("[A-Za-z0-9_]{1,100}")) {
        response.sendError(400); return;
    }
    String campo=error!=null?"error":"code", valor=error!=null?error:code;
    // Destino fijo del servidor: nunca se acepta una URL de retorno proporcionada por el visitante.
    response.setStatus(303);
    response.setHeader("Location",contexto+"/gub/callback?state="+URLEncoder.encode(state,StandardCharsets.UTF_8)
        +"&"+campo+"="+URLEncoder.encode(valor,StandardCharsets.UTF_8));
    return;
}
%>
<!doctype html><html lang="es"><head><meta charset="UTF-8"><title>CargaUY</title></head>
<body><p><a href="<%=contexto%>/login">Ingresar a CargaUY</a></p></body></html>
```

## gub-callback-root/src/main/webapp/logout.jsp

Página de retorno en /logout; no equivale a ejecutar el cierre local ni el cierre SSO.

```jsp
<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%
response.setHeader("Cache-Control","no-store");
response.setHeader("Referrer-Policy","no-referrer");
%>
<!doctype html><html lang="es"><head><meta charset="UTF-8"><title>Retorno de identidad</title></head>
<body><p>Retorno del proveedor de identidad.</p><p>Para cerrar tu sesión en CargaUY, usá su botón Salir.</p>
<a href="<%=application.getInitParameter("cargauy.context")%>/login">Volver a CargaUY</a></body></html>
```

## gub-callback-root/src/main/webapp/WEB-INF/jboss-web.xml

Archivo de referencia de la segunda guía; ver instrucciones en la guía principal.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jboss-web><context-root>/</context-root></jboss-web>
```

## gub-callback-root/src/main/webapp/WEB-INF/web.xml

Archivo de referencia de la segunda guía; ver instrucciones en la guía principal.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
  <context-param><param-name>cargauy.context</param-name><param-value>/CargaUY-web</param-value></context-param>
  <servlet><servlet-name>retorno-logout</servlet-name><jsp-file>/logout.jsp</jsp-file></servlet>
  <servlet-mapping><servlet-name>retorno-logout</servlet-name><url-pattern>/logout</url-pattern></servlet-mapping>
  <welcome-file-list><welcome-file>index.jsp</welcome-file></welcome-file-list>
</web-app>
```

## scripts/p1/carga-pdi.ps1

Genera una prueba reproducible del componente PDI y reporta estados y p95.

```powershell
# Ejecutar con PowerShell 7. No mide el volumen de tracking exigido por la letra.
param([string]$Base='http://localhost:8080/CargaUY-web',[string]$Uid='martin.admin',
      [int]$Cantidad=100,[int]$Concurrencia=12)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Requiere PowerShell 7'}
if($Cantidad -lt 1 -or $Cantidad -gt 10000 -or $Concurrencia -lt 1 -or $Concurrencia -gt 64){throw 'Carga fuera del rango de esta prueba'}
$secure=Read-Host 'Contraseña LDAP de CargaUY' -AsSecureString
$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$login=$null
try{
 $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
 $login=Invoke-RestMethod "$Base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{uid=$Uid;password=$password}|ConvertTo-Json)
 $token=$login.token
 $resultados=1..$Cantidad|ForEach-Object -ThrottleLimit $Concurrencia -Parallel {
  $reloj=[Diagnostics.Stopwatch]::StartNew()
  try{
   $r=Invoke-RestMethod "$using:Base/api/ciudadanos/12345678/pdi" -Headers @{Authorization='Bearer '+$using:token}
   [pscustomobject]@{estado=$r.estado;duracionMs=$reloj.Elapsed.TotalMilliseconds}
  }catch{[pscustomobject]@{estado='ERROR_HTTP';duracionMs=$reloj.Elapsed.TotalMilliseconds}}
 }
 $ordenados=@($resultados.duracionMs|Sort-Object)
 $indice=[Math]::Max(0,[Math]::Ceiling(0.95*$ordenados.Count)-1)
 $resultados|Group-Object estado|Select-Object Name,Count
 Write-Output ('P95_ms='+[Math]::Round($ordenados[$indice],2))
 $destino=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path '.local-p1/pdi-carga.csv'
 $resultados|Export-Csv -LiteralPath $destino -NoTypeInformation
 Write-Output 'Guardar estos resultados como evidencia de la configuración ensayada; no extrapolarlos a toda la plataforma.'
}finally{
 if($login){try{Invoke-RestMethod "$Base/api/auth/logout" -Headers @{Authorization='Bearer '+$login.token} -Method Post|Out-Null}catch{}}
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
 $password=$null;$login=$null;$token=$null
}
```

## scripts/p1/configurar-wildfly.ps1

Aplica propiedades con la CLI de WildFly; reemplaza el acceso problemático de Eclipse a VM arguments.

```powershell
param([Parameter(Mandatory=$true)][string]$WildFlyHome)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$propsPath=Join-Path $projectRoot '.local-p1/wildfly.properties'
$cliPath=Join-Path $WildFlyHome 'bin/jboss-cli.bat'
if(-not (Test-Path -LiteralPath $cliPath)){throw 'No se encontró jboss-cli.bat'}
$lines=[IO.File]::ReadAllLines($propsPath)
$commands=New-Object 'System.Collections.Generic.List[string]'
foreach($line in $lines){
    if([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')){continue}
    $parts=$line.Split('=',2)
    $key=$parts[0]; $value=$parts[1]
    if($key -notmatch '^[a-zA-Z0-9.]+$' -or $value.Contains('"') -or $value.Contains('\')){
        throw 'Propiedad no admitida por el generador CLI'
    }
    $commands.Add("if (outcome == success) of /system-property=$key" + ':read-resource')
    $commands.Add("/system-property=$key" + ':write-attribute(name=value,value="' + $value + '")')
    $commands.Add('else')
    $commands.Add("/system-property=$key" + ':add(value="' + $value + '")')
    $commands.Add('end-if')
}
$destino=Join-Path $projectRoot '.local-p1/configurar.cli'
[IO.File]::WriteAllLines($destino,$commands,(New-Object Text.UTF8Encoding($false)))
& $cliPath '--connect' "--file=$destino"
if($LASTEXITCODE -ne 0){throw 'WildFly rechazó la configuración; revisar la salida'}
Write-Output 'Propiedades configuradas. Reiniciar WildFly después de aplicar la migración y publicar el EAR.'
```

## scripts/p1/empaquetar-callback.ps1

Empaqueta el receptor raíz como WAR independiente, sin modificar el EAR.

```powershell
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sourcePath=Join-Path $projectRoot 'gub-callback-root/src/main/webapp'
$targetPath=Join-Path $projectRoot 'target/p1-callback'
New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
$warPath=Join-Path $targetPath 'gub-callback-root.war'
if(Test-Path -LiteralPath $warPath){throw 'El WAR ya existe. Renombrarlo o quitar ese archivo antes de regenerar.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($sourcePath,$warPath)
Write-Output "WAR creado: $warPath"
Write-Output 'Desplegar este WAR adicional en el mismo WildFly que CargaUY; no modificar el context-root del EAR.'
```

## scripts/p1/migracion-02.sql

Extiende el esquema sin borrar usuarios/empresas; cambia la FK de guía al usuario canónico.

```sql
BEGIN;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS origen_identidad varchar(10) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS uid_externo varchar(200);
CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_identidad ON usuarios(origen_identidad,uid_externo)
    WHERE uid_externo IS NOT NULL;
CREATE TABLE IF NOT EXISTS usuario_roles(
    usuario_id integer NOT NULL REFERENCES usuarios(id),
    rol varchar(30) NOT NULL,
    PRIMARY KEY(usuario_id,rol)
);
INSERT INTO usuario_roles(usuario_id,rol)
SELECT id,tipo_usuario FROM usuarios
WHERE tipo_usuario IN ('ADMINISTRADOR','AUTORIDAD','FUNCIONARIO','CIUDADANO','CHOFER')
ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS flujos_identidad(
    hash varchar(64) PRIMARY KEY,
    challenge varchar(43) NOT NULL,
    nonce varchar(43) NOT NULL,
    vence timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_flujos_vence ON flujos_identidad(vence);
CREATE TABLE IF NOT EXISTS sesiones_acceso(
    hash varchar(64) PRIMARY KEY,
    usuario_id integer NOT NULL REFERENCES usuarios(id),
    vence timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_sesiones_vence ON sesiones_acceso(vence);
CREATE TABLE IF NOT EXISTS vinculos_empresa(
    clave varchar(64) PRIMARY KEY,
    usuario_id integer NOT NULL REFERENCES usuarios(id),
    nro_empresa integer NOT NULL REFERENCES empresas(nro_empresa),
    responsable boolean NOT NULL DEFAULT false,
    chofer boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE(nro_empresa,usuario_id)
);
INSERT INTO vinculos_empresa(clave,usuario_id,nro_empresa,responsable,chofer,version)
SELECT ec.nro_empresa::text || ':' || ec.ciudadano_id::text,
       ec.ciudadano_id,ec.nro_empresa,true,false,0
FROM empresa_ciudadanos ec ON CONFLICT DO NOTHING;

-- Canonizar las referencias de guía hacia Usuario para admitir múltiples roles.
-- Detectar la FK existente evita depender de su nombre generado por Hibernate.
DO $$
DECLARE fila record;
BEGIN
 FOR fila IN
    SELECT c.conname FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)
    WHERE c.conrelid='guias_de_viaje'::regclass AND c.contype='f'
      AND a.attname='chofer_id'
 LOOP
    EXECUTE format('ALTER TABLE guias_de_viaje DROP CONSTRAINT %I',fila.conname);
 END LOOP;
END $$;
ALTER TABLE guias_de_viaje ADD CONSTRAINT fk_guia_usuario_chofer
    FOREIGN KEY(chofer_id) REFERENCES usuarios(id);
COMMIT;
```

## scripts/p1/preparar-local.ps1

Genera contraseñas y secreto OIDC aleatorios una sola vez en una carpeta ignorada por Git.

```powershell
param(
    [string]$Uid = 'martin.admin',
    [int]$Ci = 90000001
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$privatePath = Join-Path $projectRoot '.local-p1'
if ($Uid -notmatch '^[a-zA-Z0-9._-]{1,64}$' -or $Ci -le 0) { throw 'UID o CI inválidos' }
$ignorePath = Join-Path $projectRoot '.gitignore'
if (-not (Test-Path -LiteralPath $ignorePath) -or
    -not ([IO.File]::ReadAllText($ignorePath) -match '(?m)^/?\.local-p1/?\s*$')) {
    throw 'Primero agregar /.local-p1/ a .gitignore'
}
if (Test-Path -LiteralPath (Join-Path $privatePath 'servicios.env')) {
    throw 'Ya existe configuración local. No regenerar: LDAP y Keycloak conservan sus datos.'
}
New-Item -ItemType Directory -Path $privatePath -Force | Out-Null
function New-Secret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
$ldapRoot = New-Secret
$ldapApp = New-Secret
$kcAdmin = New-Secret
$gubPassword = New-Secret
$clientSecret = New-Secret
$utf8 = New-Object Text.UTF8Encoding($false)
$envText = @"
LDAP_ROOT_PASSWORD=$ldapRoot
LDAP_APP_UID=$Uid
LDAP_APP_PASSWORD=$ldapApp
KC_BOOTSTRAP_ADMIN_USERNAME=gestor-idp
KC_BOOTSTRAP_ADMIN_PASSWORD=$kcAdmin
"@
[IO.File]::WriteAllText((Join-Path $privatePath 'servicios.env'),$envText,$utf8)
$realm = @{
    realm='cargauy-test'; enabled=$true; sslRequired='none'; registrationAllowed=$false
    users=@(@{
        id='11111111-1111-4111-8111-111111111111'
        username='ciudadano.prueba'; enabled=$true; emailVerified=$true
        email='ana@example.invalid'; firstName='Ana'; lastName='Prueba'
        attributes=@{ci=@('80000001'); fechaNacimiento=@('1990-01-01')}
        credentials=@(@{type='password'; value=$gubPassword; temporary=$false})
    })
    clients=@(@{
        clientId='cargauy'; enabled=$true; protocol='openid-connect'
        publicClient=$false; secret=$clientSecret; standardFlowEnabled=$true
        directAccessGrantsEnabled=$false; implicitFlowEnabled=$false
        redirectUris=@('http://localhost:8080/CargaUY-web/gub/callback','uy.cargauy:/oauth/callback')
        webOrigins=@('http://localhost:8080')
        defaultClientScopes=@('profile','email')
        attributes=@{'pkce.code.challenge.method'='S256'; 'id.token.signed.response.alg'='RS256'}
        protocolMappers=@(
            @{
                name='ci'; protocol='openid-connect'; protocolMapper='oidc-usermodel-attribute-mapper'
                config=@{'user.attribute'='ci'; 'claim.name'='ci'; 'jsonType.label'='String';
                    'id.token.claim'='true'; 'access.token.claim'='true'; 'userinfo.token.claim'='true'}
            },
            @{
                name='nacimiento'; protocol='openid-connect'; protocolMapper='oidc-usermodel-attribute-mapper'
                config=@{'user.attribute'='fechaNacimiento'; 'claim.name'='fechaNacimiento'; 'jsonType.label'='String';
                    'id.token.claim'='true'; 'access.token.claim'='true'; 'userinfo.token.claim'='true'}
            }
        )
    })
}
[IO.File]::WriteAllText((Join-Path $privatePath 'gub-realm.json'),
    ($realm | ConvertTo-Json -Depth 30),$utf8)
$props = @"
p1.entorno.local=true
p1.bootstrap=true
bootstrap.admin.uid=$Uid
bootstrap.admin.ci=$Ci
bootstrap.admin.nombre=Administrador de pruebas
bootstrap.admin.mail=admin@example.invalid
bootstrap.admin.nacimiento=1990-01-01
ldap.url=ldap://127.0.0.1:1389
ldap.people.dn=ou=people,dc=cargauy,dc=local
gub.proveedor=keycloak
gub.pkce=true
gub.issuer=http://localhost:8083/realms/cargauy-test
gub.client.id=cargauy
gub.client.secret=$clientSecret
gub.redirect=http://localhost:8080/CargaUY-web/gub/callback
gub.mobile.redirect=uy.cargauy:/oauth/callback
pdi.endpoint=http://localhost:8081/pdi/ws
p1.pruebas=false
"@
[IO.File]::WriteAllText((Join-Path $privatePath 'wildfly.properties'),$props,$utf8)
$cuentas = @"
Administrador de CargaUY (login LDAP)
Usuario: $Uid
Contraseña: $ldapApp
CI de prueba: $Ci

Administrador del directorio (solo herramientas LDAP)
DN: cn=manager,dc=cargauy,dc=local
Contraseña: $ldapRoot

Ciudadano del simulador gub.uy (Keycloak)
Usuario: ciudadano.prueba
Contraseña: $gubPassword
CI de prueba: 80000001

Administrador de Keycloak (solo consola localhost:8083)
Usuario: gestor-idp
Contraseña: $kcAdmin
"@
[IO.File]::WriteAllText((Join-Path $privatePath 'cuentas-locales.txt'),$cuentas,$utf8)
Write-Output 'Archivos privados creados en .local-p1. Abrir cuentas-locales.txt para consultar las credenciales.'
Write-Output 'No regenerar archivos ni subirlos al repositorio.'
```

## scripts/p1/probar-api.ps1

Verifica login, bearer, PDI, ciudadano inexistente y revocación.

```powershell
param([string]$Base='http://localhost:8080/CargaUY-web',[string]$Uid='martin.admin')
$ErrorActionPreference='Stop'
$secure=Read-Host 'Contraseña LDAP de CargaUY' -AsSecureString
$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$login=$null
try{
 $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
 $body=@{uid=$Uid;password=$password}|ConvertTo-Json
 $login=Invoke-RestMethod "$Base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body
 $headers=@{Authorization='Bearer '+$login.token}
 $me=Invoke-RestMethod "$Base/api/auth/me" -Headers $headers
 if($me.roles -notcontains 'ADMINISTRADOR'){throw 'La cuenta no tiene rol administrador'}
 $ok=Invoke-RestMethod "$Base/api/ciudadanos/12345678/pdi" -Headers $headers
 $no=Invoke-RestMethod "$Base/api/ciudadanos/11111111/pdi" -Headers $headers
 if($ok.estado -ne 'DISPONIBLE' -or $no.estado -ne 'NO_ENCONTRADO'){throw 'PDI no respondió según el contrato nuevo'}
 Invoke-RestMethod "$Base/api/auth/logout" -Headers $headers -Method Post|Out-Null
 $revocado=$false
 try{Invoke-RestMethod "$Base/api/auth/me" -Headers $headers|Out-Null}
 catch{if([int]$_.Exception.Response.StatusCode -eq 401){$revocado=$true}else{throw}}
 if(-not $revocado){throw 'El token no quedó revocado'}
 Write-Output 'API_OK: LDAP, bearer, PDI, ciudadano inexistente y logout.'
}finally{
 if($login){try{Invoke-RestMethod "$Base/api/auth/logout" -Headers @{Authorization='Bearer '+$login.token} -Method Post|Out-Null}catch{}}
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
 $password=$null;$body=$null;$login=$null
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/ApiP1.java

Login LDAP, login GUB móvil, identidad, logout, listado de empresas y health checks.

```java
package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.json.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.service.*;

@Path("/") @Produces(MediaType.APPLICATION_JSON)
public class ApiP1 {
    @EJB private AutenticacionService auth;
    @EJB private GestionPerfilesService perfiles;
    @EJB private EstadoService estado;
    @Context private ContainerRequestContext request;
    private String token(){return (String)request.getProperty("token");}
    @POST @Path("auth/login") @Consumes(MediaType.APPLICATION_JSON)
    public Response login(JsonObject datos) {
        try {
            var l=auth.login(datos.getString("uid",""),datos.getString("password",""));
            return Response.ok(Json.createObjectBuilder().add("token",l.token())
                .add("vence",l.vence().toString()).build()).header("Cache-Control","no-store").build();
        } catch(ErrorAcceso e){return Response.status(e.getEstado()).entity(
            Json.createObjectBuilder().add("error",e.getMessage()).build()).build();}
    }
    @POST @Path("auth/gub/iniciar") @Consumes(MediaType.APPLICATION_JSON)
    public JsonObject prepararMovil(JsonObject entrada){
        var i=auth.prepararMovil(entrada.getString("challenge",""));
        return Json.createObjectBuilder().add("state",i.state()).add("nonce",i.nonce()).add("url",i.url()).build();
    }
    @POST @Path("auth/gub/terminar") @Consumes(MediaType.APPLICATION_JSON)
    public Response terminarMovil(JsonObject entrada){
        var l=auth.loginMovil(entrada.getString("code",""),entrada.getString("state",""),entrada.getString("verifier",""));
        return Response.ok(Json.createObjectBuilder().add("token",l.token()).add("vence",l.vence().toString()).build())
            .header("Cache-Control","no-store").build();
    }
    @GET @Path("auth/me")
    public JsonObject me(){
        Acceso a=auth.validarToken(token()); JsonArrayBuilder roles=Json.createArrayBuilder();
        a.roles().forEach(r->roles.add(r.name()));
        return Json.createObjectBuilder().add("id",a.id()).add("nombre",a.nombre())
            .add("roles",roles).build();
    }
    @POST @Path("auth/logout")
    public Response salir(){auth.logout(token());return Response.noContent().build();}
    @GET @Path("empresas")
    public JsonArray empresas(@QueryParam("pagina") @DefaultValue("0") int pagina,
            @QueryParam("tam") @DefaultValue("20") int tam) {
        JsonArrayBuilder lista=Json.createArrayBuilder();
        perfiles.listarEmpresas(token(),pagina,tam).forEach(e->lista.add(Json.createObjectBuilder()
            .add("numero",e.numero()).add("nombre",e.nombrePublico()).add("version",e.version())));
        return lista.build();
    }
    @GET @Path("public/ready")
    public Response ready(){boolean ok=estado.listo();return Response.status(ok?200:503)
        .entity(Json.createObjectBuilder().add("estado",ok?"UP":"DOWN").build()).build();}
    @GET @Path("public/health")
    public JsonObject health(){return Json.createObjectBuilder().add("estado","UP").build();}
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/AplicacionRest.java

Publica REST bajo /api.

```java
package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
@ApplicationPath("/api")
public class AplicacionRest extends Application {}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/AuthFilter.java

Valida bearer y establece el contexto REST; no acepta una cookie como sustituto del bearer.

```java
package uy.edu.fing.grupo07.CargaUY.web.api;
import java.io.IOException;
import java.security.Principal;
import jakarta.annotation.Priority;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;
import uy.edu.fing.grupo07.CargaUY.security.*;

@Provider @PreMatching @Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {
    @EJB private AutenticacionService auth;
    @Context private jakarta.servlet.http.HttpServletRequest http;
    @Override public void filter(ContainerRequestContext r) throws IOException {
        if(!uy.edu.fing.grupo07.CargaUY.web.WebSeguridad.transporte(http)) { r.abortWith(Response.status(403).build()); return; }
        String p=r.getUriInfo().getPath();
        if((p.equals("auth/login")||p.equals("auth/gub/iniciar")||p.equals("auth/gub/terminar"))&&r.getMethod().equals("POST")
                ||(p.equals("public/health")||p.equals("public/ready"))&&r.getMethod().equals("GET")) return;
        String h=r.getHeaderString("Authorization");
        String token=h!=null&&h.startsWith("Bearer ")?h.substring(7):null;
        try {
            Acceso actor=auth.validarToken(token); r.setProperty("token",token);
            r.setSecurityContext(new SecurityContext() {
                public Principal getUserPrincipal() { return () -> Integer.toString(actor.id()); }
                public boolean isUserInRole(String rol) {
                    try{return actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.valueOf(rol));}
                    catch(IllegalArgumentException e){return false;}
                }
                public boolean isSecure(){return http.isSecure();}
                public String getAuthenticationScheme(){return "Bearer";}
            });
        } catch(ErrorAcceso e) {
            r.abortWith(Response.status(e.getEstado()).header("WWW-Authenticate","Bearer")
                .entity(java.util.Map.of("error",e.getMessage())).build());
        }
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/CiudadanosApi.java

Consulta PDI autorizada y conserva la correlación entre cliente, central y mock.

```java
package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.json.*;
import uy.edu.fing.grupo07.CargaUY.service.ConsultaCiudadanoService;

@Path("/ciudadanos") @Produces(MediaType.APPLICATION_JSON)
public class CiudadanosApi {
    @EJB private ConsultaCiudadanoService pdi;
    @Context private ContainerRequestContext req;
    @Context private jakarta.servlet.http.HttpServletRequest http;
    @GET @Path("{ci}/pdi")
    public JsonObject consultar(@PathParam("ci")int ci){
        var r=pdi.consultar((String)req.getProperty("token"),ci,(String)http.getAttribute("correlacion"));
        var json=Json.createObjectBuilder().add("estado",r.estado().name()).add("mensaje",r.mensaje());
        if(r.ci()!=null)json.add("ci",r.ci());
        if(r.nombre()!=null)json.add("nombre",r.nombre()).add("apellido",r.apellido())
            .add("fechaNacimiento",r.fechaNacimiento().toString()).add("vigente",r.vigente());
        return json.build();
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/ErroresApi.java

Traduce errores de autorización a respuestas HTTP.

```java
package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
@Provider
public class ErroresApi implements ExceptionMapper<ErrorAcceso> {
    @Override public Response toResponse(ErrorAcceso e){
        return Response.status(e.getEstado()).entity(java.util.Map.of("error",e.getMessage())).build();
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/NegocioApi.java

Traduce validaciones de negocio a HTTP 400 sin exponer detalles de infraestructura.

```java
package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import uy.edu.fing.grupo07.CargaUY.service.ErrorNegocio;
@Provider
public class NegocioApi implements ExceptionMapper<ErrorNegocio>{
 public Response toResponse(ErrorNegocio e){return Response.status(400)
  .entity(java.util.Map.of("error",e.getMessage())).build();}
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/OperacionesApi.java

Métricas por instancia, visibles a administradores.

```java
package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.json.*;
import uy.edu.fing.grupo07.CargaUY.web.CorrelacionFilter;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.integration.ClientePdi;
import uy.edu.fing.grupo07.CargaUY.service.ConsultaCiudadanoService;

@Path("/ops") @Produces(MediaType.APPLICATION_JSON)
public class OperacionesApi {
    @EJB private ControlAcceso control;
    @EJB private ClientePdi pdi;
    @Context private ContainerRequestContext req;
    @GET @Path("metrics")
    public JsonObject metricas(){
        control.admin((String)req.getProperty("token"));long[] p=pdi.metricas();
        return Json.createObjectBuilder().add("solicitudes",CorrelacionFilter.solicitudes.sum())
            .add("errores",CorrelacionFilter.errores.sum()).add("duracionTotalNanos",CorrelacionFilter.nanos.sum())
            .add("pdiIntentos",p[0]).add("pdiFallas",p[1]).add("pdiRechazos",p[2]).build();
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/ConfiguracionWeb.java

Configura cookies HttpOnly/SameSite y evita IDs de sesión en URLs.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import uy.edu.fing.grupo07.CargaUY.security.Config;

@WebListener
public class ConfiguracionWeb implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent e) {
        var c=e.getServletContext().getSessionCookieConfig();
        c.setHttpOnly(true); c.setAttribute("SameSite","Lax");
        c.setSecure(!Boolean.parseBoolean(Config.valor("p1.entorno.local","false")));
        e.getServletContext().setSessionTrackingModes(java.util.Set.of(SessionTrackingMode.COOKIE));
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/CorrelacionFilter.java

Propaga X-Correlation-ID y registra duración/estado de solicitudes sin registrar tokens o passwords.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import jakarta.json.Json;

@WebFilter("/*")
public class CorrelacionFilter implements Filter {
    public static final LongAdder solicitudes=new LongAdder(), errores=new LongAdder(), nanos=new LongAdder();
    private static final Logger LOG=Logger.getLogger(CorrelacionFilter.class.getName());
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)
            throws IOException,ServletException{
        var r=(HttpServletRequest)request;var s=(HttpServletResponse)response;
        String corr=r.getHeader("X-Correlation-ID");
        try{corr=UUID.fromString(corr).toString();}catch(Exception e){corr=UUID.randomUUID().toString();}
        r.setAttribute("correlacion",corr);s.setHeader("X-Correlation-ID",corr);
        long inicio=System.nanoTime();
        try{chain.doFilter(r,s);}
        finally{
            long duracion=System.nanoTime()-inicio;
            solicitudes.increment();nanos.add(duracion);if(s.getStatus()>=400)errores.increment();
            LOG.info(Json.createObjectBuilder().add("evento","http.solicitud")
                .add("correlacion",corr).add("metodo",r.getMethod()).add("status",s.getStatus())
                .add("duracionMs",duracion/1000000).build().toString());
        }
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/FuncionarioServlet.java

Devuelve 410 para retirar el formulario anterior que creaba funcionarios sin autorización.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
@WebServlet("/FuncionarioServlet")
public class FuncionarioServlet extends HttpServlet {
    @Override protected void service(HttpServletRequest r,HttpServletResponse s)throws IOException {
        s.sendError(410,"Pantalla de pruebas retirada; ingresar por /login");
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/GubServlet.java

Inicia OIDC y valida state; selecciona ID Uruguay o Keycloak y usa PKCE únicamente cuando está configurado.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import java.time.Instant;
import jakarta.ejb.EJB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet(urlPatterns={"/gub/iniciar","/gub/callback"})
public class GubServlet extends HttpServlet {
    @EJB private AutenticacionService auth;
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s) throws IOException {
        if(!WebSeguridad.transporte(r)) { s.sendError(403); return; }
        WebSeguridad.preparar(r,s);
        if(r.getServletPath().equals("/gub/iniciar")) {
            String state=Cripto.aleatorio(), nonce=Cripto.aleatorio(), verifier=Cripto.aleatorio();
            HttpSession ses=r.getSession(); ses.setAttribute("oidcState",state);
            ses.setAttribute("oidcNonce",nonce); ses.setAttribute("oidcVerifier",verifier);
            ses.setAttribute("oidcInicio",Instant.now());
            s.sendRedirect(GubOidcClient.autorizacion()+"?response_type=code"
                +"&scope="+GubOidcClient.url(GubOidcClient.scopes())+"&client_id="+GubOidcClient.url(GubOidcClient.clientId())
                +"&redirect_uri="+GubOidcClient.url(GubOidcClient.redirect())
                +"&state="+state+"&nonce="+nonce+(GubOidcClient.pkce()?"&code_challenge="+Cripto.pkce(verifier)
                +"&code_challenge_method=S256":""));
            return;
        }
        HttpSession ses=r.getSession();
        String state=(String)ses.getAttribute("oidcState");
        String nonce=(String)ses.getAttribute("oidcNonce");
        String verifier=(String)ses.getAttribute("oidcVerifier");
        Instant inicio=(Instant)ses.getAttribute("oidcInicio");
        ses.removeAttribute("oidcState"); ses.removeAttribute("oidcNonce");
        ses.removeAttribute("oidcVerifier"); ses.removeAttribute("oidcInicio");
        if(!Cripto.igual(state,r.getParameter("state"))||inicio==null
                ||inicio.plusSeconds(180).isBefore(Instant.now())) {
            WebSeguridad.error(s,401,"Inicio de sesión vencido o inválido"); return;
        }
        if(r.getParameter("error")!=null) {
            WebSeguridad.error(s,401,"El proveedor no autorizó el ingreso"); return;
        }
        try {
            WebSeguridad.iniciar(r,auth.loginGub(r.getParameter("code"),verifier,nonce));
            s.sendRedirect(r.getContextPath()+"/app/perfil");
        } catch(ErrorAcceso e) { WebSeguridad.error(s,e.getEstado(),e.getMessage()); }
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/LoginServlet.java

Presenta el login interno y autentica con LDAP.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    @EJB private AutenticacionService auth;
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s)
            throws IOException,ServletException {
        if(!WebSeguridad.transporte(r)) { s.sendError(403); return; }
        WebSeguridad.preparar(r,s);
        r.getRequestDispatcher("/WEB-INF/p2/login.jsp").forward(r,s);
    }
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s)
            throws IOException,ServletException {
        r.setCharacterEncoding("UTF-8");
        if(!WebSeguridad.transporte(r)||!WebSeguridad.csrf(r)) {
            WebSeguridad.error(s,403,"Recargá el formulario antes de ingresar"); return;
        }
        try {
            var login=auth.login(r.getParameter("uid"),r.getParameter("password"));
            WebSeguridad.iniciar(r,login);
            s.sendRedirect(r.getContextPath()+(login.usuario().tiene(
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)?"/app/admin":"/app/empresa"));
        } catch(ErrorAcceso e) {
            r.setAttribute("error",e.getMessage()); s.setStatus(e.getEstado()); doGet(r,s);
        }
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/LogoutServlet.java

Revoca el token y destruye la sesión web mediante POST protegido por CSRF.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {
    @EJB private AutenticacionService auth;
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s) throws IOException {
        if(!WebSeguridad.transporte(r)||!WebSeguridad.csrf(r)) { s.sendError(403); return; }
        auth.logout(WebSeguridad.token(r)); r.getSession().invalidate();
        s.sendRedirect(r.getContextPath()+"/login");
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/P1PruebasServlet.java

Devuelve 410 para retirar la pantalla de pruebas anterior.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
@WebServlet("/p1/pruebas")
public class P1PruebasServlet extends HttpServlet {
    @Override protected void service(HttpServletRequest r,HttpServletResponse s)throws IOException {
        s.sendError(410,"Pantalla de pruebas retirada; ingresar por /login");
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/PanelServlet.java

Adapta los formularios de administración, empresas y perfil a los servicios protegidos.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import java.time.LocalDate;
import jakarta.ejb.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.service.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet(urlPatterns={"/app/admin","/app/empresa","/app/perfil"})
public class PanelServlet extends HttpServlet {
    @EJB private GestionPerfilesService perfiles;
    @EJB private GestionPlataformaService plataforma;
    @EJB private ConsultaCiudadanoService pdi;
    @EJB private IdentidadesService identidades;
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s)throws IOException,ServletException{
        WebSeguridad.preparar(r,s);
        r.getRequestDispatcher("/WEB-INF/p2/panel.jsp").forward(r,s);
    }
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s)throws IOException,ServletException{
        r.setCharacterEncoding("UTF-8");
        try { Object resultado=ejecutar(r); r.setAttribute("resultado",
                resultado==null?"Operación completada":resultado.toString()); }
        catch(ErrorAcceso e){s.setStatus(e.getEstado());r.setAttribute("error",e.getMessage());}
        catch(ErrorNegocio|IllegalArgumentException e){s.setStatus(400);r.setAttribute("error",e.getMessage());}
        catch(EJBException e){
            boolean conflicto=false;
            for(Throwable causa=e;causa!=null;causa=causa.getCause())
                if(causa instanceof jakarta.persistence.OptimisticLockException
                    || causa instanceof java.sql.SQLException sql && java.util.Arrays.asList("23505","23503","23514").contains(sql.getSQLState()))
                    conflicto=true;
            s.setStatus(conflicto?409:500);
            r.setAttribute("error",conflicto?"Conflicto al guardar; recargá y revisá referencias":"Error interno; revisar el registro del servidor");
            getServletContext().log("Fallo de operación; correlacion="+r.getAttribute("correlacion"),e);
        }
        doGet(r,s);
    }
    private Object ejecutar(HttpServletRequest r) {
        String accion = r.getParameter("accion");
        if (accion == null) throw new ErrorNegocio("Acción obligatoria");
        String token=WebSeguridad.token(r);
        if(r.getServletPath().equals("/app/perfil") &&
                !java.util.Set.of("consultarUsuario","modificarUsuario").contains(accion))
            throw new ErrorAcceso(403,"Acción no disponible en perfil");
        if(r.getServletPath().equals("/app/empresa") &&
                !java.util.Set.of("listarEmpresas","consultarEmpresa","crearEmpresa","modificarEmpresa",
                    "asociar","desasociar","vinculo","listarVinculos","consultarPdi").contains(accion))
            throw new ErrorAcceso(403,"Acción no disponible en empresas");
        switch (accion) {
            case "crearUsuario": return perfiles.crearUsuario(token, datos(r, true));
            case "listarUsuarios": return perfiles.listarUsuarios(token, tipoOpcional(r), pagina(r), tam(r));
            case "consultarUsuario": return perfiles.consultarUsuario(token, entero(r, "id"));
            case "modificarUsuario": perfiles.modificarUsuario(token, entero(r, "id"), Long.parseLong(r.getParameter("version")), datos(r, false)); break;
            case "bajaUsuario": perfiles.cambiarEstadoUsuario(token, entero(r, "id"), false); break;
            case "reactivarUsuario": perfiles.cambiarEstadoUsuario(token, entero(r, "id"), true); break;
            case "crearEmpresa": perfiles.crearEmpresa(token, entero(r, "numero"), r.getParameter("razon"),
                    r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "consultarEmpresa": return perfiles.consultarEmpresa(token, entero(r, "numero"));
            case "listarEmpresas": return perfiles.listarEmpresas(token, pagina(r), tam(r));
            case "modificarEmpresa": perfiles.modificarEmpresa(token, entero(r, "numero"), Long.parseLong(r.getParameter("version")),
                    r.getParameter("razon"), r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "bajaEmpresa": perfiles.cambiarEstadoEmpresa(token, entero(r, "numero"), false); break;
            case "reactivarEmpresa": perfiles.cambiarEstadoEmpresa(token, entero(r, "numero"), true); break;
            case "asociar": perfiles.asociarCiudadano(token, entero(r, "numero"), entero(r, "id")); break;
            case "desasociar": perfiles.desasociarCiudadano(token, entero(r, "numero"), entero(r, "id")); break;
            case "crearRubro": return plataforma.crearRubro(token, r.getParameter("nombreRubro"), r.getParameter("descripcion"));
            case "listarRubros": return plataforma.listarRubros(token);
            case "modificarRubro": plataforma.modificarRubro(token, entero(r, "codigo"),
                    r.getParameter("nombreRubro"), r.getParameter("descripcion")); break;
            case "eliminarRubro": plataforma.eliminarRubro(token, entero(r, "codigo")); break;
            case "crearTipoCarga": return plataforma.crearTipoCarga(token, r.getParameter("descripcion"));
            case "listarTiposCarga": return plataforma.listarTiposCarga(token);
            case "modificarTipoCarga": plataforma.modificarTipoCarga(token, entero(r, "codigo"), r.getParameter("descripcion")); break;
            case "eliminarTipoCarga": plataforma.eliminarTipoCarga(token, entero(r, "codigo")); break;
            case "consultarRegla": return plataforma.consultarRegla(token);
            case "crearRegla": plataforma.crearRegla(token, entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm"))); break;
            case "editarRegla": plataforma.editarRegla(token, entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm")),
                    Long.parseLong(r.getParameter("version"))); break;
            case "consultarPdi": return pdi.consultar(token, entero(r, "ci"), (String)r.getAttribute("correlacion"));
            case "listarVinculos": return identidades.listar(token,entero(r,"numero"));
            case "vincularIdentidad": identidades.vincular(token, entero(r,"id"),r.getParameter("origen"),r.getParameter("uid"));break;
            case "roles": identidades.roles(token,entero(r,"id"),java.util.Arrays.stream(
                r.getParameter("roles").split(",")).map(String::trim).map(TipoUsuario::valueOf)
                .collect(java.util.stream.Collectors.toSet()));break;
            case "vinculo": identidades.vinculo(token,entero(r,"numero"),entero(r,"id"),
                r.getParameter("responsable")!=null,r.getParameter("chofer")!=null);break;
            default: throw new ErrorNegocio("Acción desconocida");
        }
        return null;
    }
    private int pagina(HttpServletRequest r) { return Integer.parseInt(r.getParameter("pagina")==null?"0":r.getParameter("pagina")); }
    private int tam(HttpServletRequest r) { return Integer.parseInt(r.getParameter("tam")==null?"20":r.getParameter("tam")); }
    private int entero(HttpServletRequest r, String nombre) {
        return Integer.parseInt(r.getParameter(nombre));
    }
    private TipoUsuario tipoOpcional(HttpServletRequest r) {
        String tipo = r.getParameter("tipo");
        return tipo == null || tipo.isBlank() ? null : TipoUsuario.valueOf(tipo);
    }
    private DatosUsuario datos(HttpServletRequest r, boolean alta) {
        String nro = r.getParameter("nroFuncionario");
        return new DatosUsuario(tipoOpcional(r), r.getParameter("nombre"), r.getParameter("mail"),
            LocalDate.parse(r.getParameter("fechaNac")), entero(r, "ci"),
            alta ? r.getParameter("password") : null,
            nro == null || nro.isBlank() ? null : Integer.valueOf(nro),
            r.getParameter("departamento"), r.getParameter("cargo"));
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/SesionFilter.java

Protege las pantallas autenticadas y verifica CSRF en operaciones de escritura.

```java
package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@WebFilter(urlPatterns="/app/*", dispatcherTypes=DispatcherType.REQUEST)
public class SesionFilter implements Filter {
    @EJB private AutenticacionService auth;
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException,ServletException {
        var r=(HttpServletRequest)request; var s=(HttpServletResponse)response;
        if(!WebSeguridad.transporte(r)) { s.sendError(403); return; }
        WebSeguridad.preparar(r,s);
        try {
            Acceso actor=auth.validarToken(WebSeguridad.token(r));
            if(r.getServletPath().equals("/app/admin")&&!actor.tiene(TipoUsuario.ADMINISTRADOR))
                throw new ErrorAcceso(403,"Se requiere administrador");
            if(!r.getMethod().equals("GET")&&!r.getMethod().equals("HEAD")&&!WebSeguridad.csrf(r))
                throw new ErrorAcceso(403,"Formulario vencido: recargá la página");
            r.setAttribute("actor",actor);
            chain.doFilter(r,s);
        } catch(ErrorAcceso e) {
            if(e.getEstado()==401) {
                r.getSession().invalidate(); s.sendRedirect(r.getContextPath()+"/login");
            } else WebSeguridad.error(s,e.getEstado(),e.getMessage());
        }
    }
}
```

## web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/WebSeguridad.java

CSRF, rotación de sesión, límites de transporte y cabeceras de respuesta.

```java
package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import java.net.InetAddress;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

public final class WebSeguridad {
    private WebSeguridad() {}
    public static void preparar(HttpServletRequest r, HttpServletResponse s) {
        s.setHeader("Cache-Control","no-store");
        s.setHeader("X-Content-Type-Options","nosniff");
        s.setHeader("X-Frame-Options","DENY");
        s.setHeader("Content-Security-Policy","default-src 'self'; form-action 'self'; frame-ancestors 'none'");
        HttpSession h=r.getSession();
        if(h.getAttribute("csrf")==null) h.setAttribute("csrf",Cripto.aleatorio());
        r.setAttribute("csrf",h.getAttribute("csrf"));
    }
    public static boolean csrf(HttpServletRequest r) {
        HttpSession s=r.getSession(false);
        return s!=null && Cripto.igual((String)s.getAttribute("csrf"),r.getParameter("csrf"));
    }
    public static String token(HttpServletRequest r) {
        HttpSession s=r.getSession(false);
        return s==null?null:(String)s.getAttribute("token");
    }
    public static void iniciar(HttpServletRequest r, SesionesService.Login login) {
        r.getSession(); r.changeSessionId();
        r.getSession().setAttribute("token",login.token());
        r.getSession().setAttribute("csrf",Cripto.aleatorio());
        r.getSession().setMaxInactiveInterval(15*60);
    }
    public static boolean transporte(HttpServletRequest r) throws IOException {
        return r.isSecure() || Boolean.parseBoolean(Config.valor("p1.entorno.local","false"))
            && InetAddress.getByName(r.getRemoteAddr()).isLoopbackAddress();
    }
    public static void error(HttpServletResponse s, int codigo,String texto) throws IOException {
        s.setStatus(codigo); s.setContentType("text/plain;charset=UTF-8"); s.getWriter().println(texto);
    }
}
```

## web/src/main/webapp/WEB-INF/p2/login.jsp

Entrada para administradores/autoridades LDAP y usuarios externos con Usuario gub.uy.

```jsp
<%@ page contentType="text/html;charset=UTF-8" %>
<%! private String h(Object o){return o==null?"":o.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
<!doctype html><html lang="es"><head><meta charset="UTF-8"><title>Ingresar a CargaUY</title></head>
<body><h1>Ingresar a CargaUY</h1>
<p><%=h(request.getAttribute("error"))%></p>
<h2>Administradores y autoridades</h2>
<form method="post" action="<%=request.getContextPath()%>/login">
<input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>">
<label>Usuario del directorio <input name="uid" required autocomplete="username"></label>
<label>Contraseña <input type="password" name="password" required autocomplete="current-password"></label>
<button>Ingresar</button></form>
<h2>Ciudadanos, choferes y funcionarios</h2>
<a href="<%=request.getContextPath()%>/gub/iniciar">Ingresar con Usuario gub.uy</a>
</body></html>
```

## web/src/main/webapp/WEB-INF/p2/panel.jsp

Vistas de administración, empresas y perfil con operaciones condicionadas por rol; los servicios vuelven a autorizar.

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="uy.edu.fing.grupo07.CargaUY.security.Acceso,uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario,java.util.*" %>
<%! private String h(Object o){return o==null?"":o.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
<% Acceso actor=(Acceso)request.getAttribute("actor");
String ruta=request.getServletPath();
List<String> acciones=ruta.equals("/app/admin") ? Arrays.asList(
"crearUsuario","listarUsuarios","consultarUsuario","modificarUsuario","bajaUsuario","reactivarUsuario",
"crearEmpresa","consultarEmpresa","listarEmpresas","modificarEmpresa","bajaEmpresa","reactivarEmpresa",
"asociar","desasociar","vincularIdentidad","roles","vinculo","listarVinculos","crearRubro","listarRubros","modificarRubro",
"eliminarRubro","crearTipoCarga","listarTiposCarga","modificarTipoCarga","eliminarTipoCarga","consultarRegla",
"crearRegla","editarRegla","consultarPdi") : ruta.equals("/app/perfil") ?
Arrays.asList("consultarUsuario","modificarUsuario") :
actor.tiene(TipoUsuario.FUNCIONARIO)?Arrays.asList("listarEmpresas","consultarEmpresa","crearEmpresa","modificarEmpresa","asociar","desasociar","vinculo","listarVinculos","consultarPdi"):
actor.tiene(TipoUsuario.CIUDADANO)?Arrays.asList("listarEmpresas","consultarEmpresa","modificarEmpresa","listarVinculos","consultarPdi"):
Arrays.asList("listarEmpresas","consultarEmpresa"); %>
<!doctype html>
<html lang="es">
<head><meta charset="UTF-8"><title>CargaUY · Gestión</title></head>
<body>
<h1><%=ruta.equals("/app/admin")?"Administración":ruta.equals("/app/perfil")?"Mi perfil":"Empresas"%></h1>
<p>Usuario: <%=h(actor.nombre())%> · ID: <%=actor.id()%> · CI: <%=actor.ci()%></p>
<nav><a href="<%=request.getContextPath()%>/app/perfil">Mi perfil</a>
<a href="<%=request.getContextPath()%>/app/empresa">Empresas</a>
<% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){ %><a href="<%=request.getContextPath()%>/app/admin">Administración</a><% } %></nav>
<form method="post" action="<%=request.getContextPath()%>/logout">
<input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>"><button>Cerrar sesión</button></form>
<p><%=h(request.getAttribute("error"))%></p><pre><%=h(request.getAttribute("resultado"))%></pre>
<p>Elegí una operación y completá solo los campos que necesita.
El resultado aparece en esta página. Para editar, consultá primero la versión actual.</p>
<form method="post" action="<%= request.getContextPath() + request.getServletPath() %>">
<input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>">
<label>Operación <select name="accion">
<% for(String a:acciones){ %><option value="<%=h(a)%>"><%=h(a.replaceAll("([a-z])([A-Z])","$1 $2"))%></option><% } %>
</select></label>
<label>Página (desde 0) <input name="pagina" type="number" min="0" value="0"></label>
<label>Filas por página <input name="tam" type="number" min="1" max="100" value="20"></label>
<label>Versión consultada del registro a editar <input name="version" type="number" min="0" value="0"></label>
<% if(ruta.equals("/app/admin")) { %><fieldset><legend>Identidad y permisos</legend>
<label>Origen <select name="origen"><option>LDAP</option><option>GUB</option></select></label>
<label>UID LDAP o subject GUB <input name="uid"></label>
<label>Roles separados por coma <input name="roles" placeholder="CIUDADANO,CHOFER"></label>
</fieldset><% } %>
<fieldset><legend>Usuario</legend>
<label>ID <input name="id" type="number" value="<%=actor.id()%>"></label>
<label>Tipo <select name="tipo">
<option value="">Todos (solo listado)</option>
<option>CIUDADANO</option><option>CHOFER</option><option>FUNCIONARIO</option>
<option>AUTORIDAD</option><option>ADMINISTRADOR</option>
</select></label>
<label>Nombre <input name="nombre"></label>
<label>Correo <input name="mail" type="email"></label>
<label>Nacimiento <input name="fechaNac" type="date"></label>
<label>CI sin puntos ni guiones <input name="ci" type="number"></label>
<label>Contraseña, solo alta <input name="password" type="password" autocomplete="new-password"></label>
<label>Número funcionario <input name="nroFuncionario" type="number"></label>
<label>Departamento <input name="departamento"></label>
<label>Cargo autoridad <input name="cargo"></label>
</fieldset>
<fieldset><legend>Empresa y asociación</legend>
<% if(actor.tiene(TipoUsuario.ADMINISTRADOR)||actor.tiene(TipoUsuario.FUNCIONARIO)){ %>
<label><input name="responsable" type="checkbox">Responsable de empresa</label>
<label><input name="chofer" type="checkbox">Chofer de empresa</label>
<% } %>
<label>Número <input name="numero" type="number"></label>
<label>Razón social <input name="razon"></label>
<label>Nombre público <input name="nombrePublico"></label>
<label>Dirección <input name="direccion"></label>
<p>Asociar/desasociar: usá número de empresa e ID del ciudadano.</p>
</fieldset>
<% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){ %><fieldset><legend>Catálogos</legend>
<label>Código <input name="codigo" type="number"></label>
<label>Nombre rubro <input name="nombreRubro"></label>
<label>Descripción <input name="descripcion"></label>
</fieldset>
<fieldset><legend>Regla</legend>
<label>Tolerancia kg <input name="cargaKg" type="number" min="0"></label>
<label>Desvío km <input name="desvioKm" type="number" min="0" step="0.01"></label>

</fieldset><% } %>
<button type="submit">Ejecutar</button>
</form>
</body></html>
```

