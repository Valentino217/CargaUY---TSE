# Guía P1: usuarios, perfiles, empresas, PDI y plataforma
Fecha: 14 de octubre de 2026. Rama observada: Martin.

Esta guía está adaptada al código existente de CargaUY. Incluye código completo para copiar, cambios de entidades, ejecución en Eclipse, pruebas y razones de diseño. Los ejemplos están en `docs/P1/ejemplos`: no se aplicaron al código activo ni a tu base. Las rutas indicadas debajo de cada archivo son los destinos reales desde la raíz del proyecto.

## 1. Qué hacemos ahora y qué queda para después
Ahora: alta de los cinco subtipos de usuario con hash BCrypt, consulta y edición, baja/reactivación lógica; ABM de empresa; asociación/desasociación ciudadano–empresa; ABM de rubros/tipos de carga; edición de reglas en kg y km; cliente SOAP con fallback; pruebas locales.
Después: login, validarToken, emisión/renovación de tokens, AuthFilter REST y filtro de sesión JSP, integración gub.uy/LDAP y autorización de servicios con identidad real. No se registran filtros que acepten cualquier token ni se inventa una identidad administrativa hardcodeada.
El alta con hash no es un login: únicamente prepara almacenamiento de credenciales del modelo actual. Si mantienen LDAP y gub.uy, revisarán qué cuentas necesitan contraseña local; no es el diseño final de identidad.
El ABM de rubros y tipos de carga se incluye porque lo pediste en tu reparto. La letra permite precargarlos y no exige su gestión. Confirmarlo con el grupo evita invertir tiempo innecesario.
El umbral de tracking será una distancia en kilómetros, según tu aclaración. El número de puntos desviados es otro concepto; el motor de fiscalización debe convertir sus distancias a km antes de comparar.

## 2. Qué encontré en el proyecto
• Usuario es abstracto; Ciudadano, Chofer, Funcionario, Autoridad y Administrador usan herencia JPA JOINED.
• La CI es int y única en usuarios. Se conserva para compatibilidad. A futuro conviene String para no perder formato/ceros y validar dígito verificador.
• Una misma CI no puede ser a la vez dos registros de tipos diferentes. Chofer no hereda de Ciudadano. Esta guía no modifica esa decisión del dominio: el grupo debe resolver el caso de personas con múltiples funciones.
• Empresa es dueña de la asociación ManyToMany con Ciudadano. Ciudadano es el lado inverso.
• Empresa tiene cascade ALL hacia vehículos/guías. Por eso no hacemos remove de empresas: podría arrastrar información operativa e histórica.
• El formulario de funcionario actual guarda una contraseña directamente. El reemplazo de FuncionarioEJB al final deriva al servicio común para evitar esa ruta de texto plano.
• BCrypt ya está declarado en ejb/pom.xml, con versión administrada por el pom raíz. No agregar otra librería ni otra versión.
• Existe docker/mock-pdi/mock_pdi.py con WSDL básico embebido. Aunque todavía no lo tengan documentado como contrato, podemos usarlo como base comprobable.
• Ese mock devuelve Juan Perez para cualquier CI y no define ciudadano inexistente. Es una simulación, no verificación real de identidad.
• mvnw.cmd contiene rutas de otra máquina (usuario valen). Para compilar desde Eclipse usá Run As → Maven build, no ese archivo sin corregirlo.

## 3. Preparación: conservar datos entre despliegues
Primero hacé una copia de respaldo de la base si tiene datos importantes.
En ejb/src/main/resources/META-INF/persistence.xml reemplazá:
```xml
<property name="jakarta.persistence.schema-generation.database.action" value="drop-and-create"/>
```
por:
```xml
<property name="jakarta.persistence.schema-generation.database.action" value="none"/>
```
Podés quitar schema-generation.create-source y drop-source: con none no se usan. Conservá el datasource java:jboss/datasources/PostgreSQLDS.
¿Por qué? drop-and-create vuelve a crear el esquema en cada despliegue. none conserva la base y hace que las modificaciones de esquema sean explícitas. En producción convienen migraciones versionadas, no depender de Hibernate para alterar tablas automáticamente.

Como tu despliegue ya creó las tablas, ejecutá este SQL una vez en pgAdmin → base cargauy → Query Tool, antes de desplegar las entidades cambiadas:
```sql
BEGIN;
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE empresas
    ADD COLUMN IF NOT EXISTS activa boolean NOT NULL DEFAULT true;
ALTER TABLE empresas
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE reglas_fiscalizacion
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE reglas_fiscalizacion
    ALTER COLUMN umbral_tracking TYPE double precision
    USING umbral_tracking::double precision;
COMMIT;
```
Antes de añadir protección contra asociaciones repetidas, verificá:
```sql
SELECT nro_empresa, ciudadano_id, count(*)
FROM empresa_ciudadanos
GROUP BY nro_empresa, ciudadano_id
HAVING count(*) > 1;
```
Si no devuelve filas, podés crear:
```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_empresa_ciudadano
ON empresa_ciudadanos (nro_empresa, ciudadano_id);
```
Si hay duplicados existentes, revisarlos con el grupo antes de limpiar: no se incluye borrado automático. El índice protege incluso frente a otras rutas de escritura.
Estos cambios no migran contraseñas viejas a BCrypt: los registros previos que guardaron texto plano deben tratarse al incorporar autenticación. No hagas doble hash de contraseñas existentes sin identificar su formato.

## 4. Cambios pequeños en entidades existentes
En Usuario.java, después del campo id y antes de la siguiente anotación de campo, agregá:
```java
@Version
private long version;

@Column(nullable = false)
private boolean activo = true;
```
Dentro de la clase, agregá:
```java
public boolean isActivo() { return activo; }
public void setActivo(boolean activo) { this.activo = activo; }
```
En Empresa.java, después del campo nroEmpresa, agregá:
```java
@Version
private long version;

@Column(nullable = false)
private boolean activa = true;
```
Y los métodos:
```java
public boolean isActiva() { return activa; }
public void setActiva(boolean activa) { this.activa = activa; }
```
No pongas estos campos entre @Id y el identificador: las anotaciones existentes deben seguir aplicándose al campo original.
Ya usan import jakarta.persistence.*; no hace falta otro import para estas anotaciones.
@Version detecta actualizaciones concurrentes dentro del trabajo transaccional. Para detectar además formularios de perfiles abiertos hace tiempo, en una versión posterior hay que enviar la versión leída y compararla, como sí hacemos para reglas.
Baja lógica: activo=false, sin DELETE. La reactivación usa el mismo registro e identidad. Los futuros servicios de viajes, permisos y login deben comprobar esos estados: agregar la columna por sí solo no aplica las restricciones en módulos de otros integrantes.
No modificar los equals/hashCode ahora. Como Usuario compara por CI, no permitimos cambiarla en el formulario de edición.

## 5. Cómo crear los archivos en Eclipse
En CargaUY-ejb → src/main/java, creá los paquetes dto, service e integracion bajo uy.edu.fing.grupo07.CargaUY.
Clic derecho → New → Class/Enum; o creá el archivo y copiá el contenido completo. Para los records, si Eclipse no ofrece un asistente, crear el archivo .java con su nombre es suficiente.
Para cada bloque de la sección 6, copiá el código al destino indicado. Los archivos existentes ReglaFiscalizacion.java y FuncionarioEJB.java se reemplazan; no crees otra clase con el mismo nombre.
Creá los tests bajo ejb/src/test/java. Los paquetes de pruebas deben coincidir con los de los bloques.
En CargaUY-web creá P1PruebasServlet.java en el paquete web y WEB-INF/p1/pruebas.jsp bajo src/main/webapp.
El panel es una herramienta temporal de prueba, no la interfaz administrativa final. No necesita un login para probar negocio; está restringido por configuración y dirección local.

## 6. Código completo, archivo por archivo

### ErrorNegocio.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/ErrorNegocio.java`

Error de negocio que el servlet puede mostrar sin revelar la infraestructura. @ApplicationException(rollback=true) pide revertir toda la operación si una validación falla: no guardar una mitad y dejar otra sin actualizar.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class ErrorNegocio extends RuntimeException {
    public ErrorNegocio(String mensaje) { super(mensaje); }
}
```

### Validaciones.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/Validaciones.java`

Centraliza texto requerido, longitud y números positivos. Se valida en negocio aunque el HTML tenga restricciones, porque también puede llamar otro componente. La validación de correo es básica y no demuestra que exista el buzón.

```java
package uy.edu.fing.grupo07.CargaUY.service;

public final class Validaciones {
    private Validaciones() {}
    public static String texto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank())
            throw new ErrorNegocio(campo + " es obligatorio");
        String limpio = valor.trim();
        if (limpio.length() > maximo)
            throw new ErrorNegocio(campo + " supera " + maximo + " caracteres");
        return limpio;
    }
    public static int positivo(int valor, String campo) {
        if (valor <= 0) throw new ErrorNegocio(campo + " debe ser positivo");
        return valor;
    }
}
```

### TipoUsuario.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/TipoUsuario.java`

Enum para que el tipo tenga valores definidos, en vez de aceptar nombres de clase o SQL provenientes del formulario.

```java
package uy.edu.fing.grupo07.CargaUY.dto;

public enum TipoUsuario {
    CIUDADANO, CHOFER, FUNCIONARIO, AUTORIDAD, ADMINISTRADOR
}
```

### DatosUsuario.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/DatosUsuario.java`

Comando de entrada. Agrupa los datos comunes y los particulares de Funcionario/Autoridad. El password solo se usa en alta; nunca se devuelve el record ni se registra en logs.

```java
package uy.edu.fing.grupo07.CargaUY.dto;

import java.time.LocalDate;

// No usar este record para respuestas ni registrarlo en logs: contiene una contraseña.
public record DatosUsuario(TipoUsuario tipo, String nombre, String mail,
        LocalDate fechaNac, int ci, String password,
        Integer nroFuncionario, String departamento, String cargo) {}
```

### UsuarioResumen.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/UsuarioResumen.java`

DTO de salida con datos de perfil y sin contraseña ni hash. Copia solo campos escalares; evita exponer una entidad JPA completa. Usa record por ser una representación inmutable y breve en Java 17.

```java
package uy.edu.fing.grupo07.CargaUY.dto;

import java.time.LocalDate;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

public record UsuarioResumen(int id, String tipo, String nombre, String mail,
        LocalDate fechaNac, int ci, boolean activo,
        Integer nroFuncionario, String departamento, String cargo) {
    public static UsuarioResumen desde(Usuario u) {
        String tipo = u instanceof Ciudadano ? "CIUDADANO"
            : u instanceof Chofer ? "CHOFER"
            : u instanceof Funcionario ? "FUNCIONARIO"
            : u instanceof Autoridad ? "AUTORIDAD" : "ADMINISTRADOR";
        return new UsuarioResumen(u.getId(), tipo, u.getNombre(), u.getMail(),
            u.getFechaNac(), u.getCi(), u.isActivo(),
            u instanceof Funcionario f ? f.getNroFuncionario() : null,
            u instanceof Funcionario f ? f.getDepartamento() : null,
            u instanceof Autoridad a ? a.getCargo() : null);
    }
}
```

### EmpresaResumen.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/EmpresaResumen.java`

DTO de salida que materializa IDs de ciudadanos dentro del EJB. Evita que el JSP acceda a una colección LAZY después de terminar la transacción. Para grandes listados hay que agregar paginación y optimizar consultas.

```java
package uy.edu.fing.grupo07.CargaUY.dto;

import java.util.List;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;

public record EmpresaResumen(int numero, String razonSocial, String nombrePublico,
        String direccion, boolean activa, List<Integer> ciudadanos) {
    public static EmpresaResumen desde(Empresa e) {
        return new EmpresaResumen(e.getNroEmpresa(), e.getRazonSocial(),
            e.getNombrePublico(), e.getDireccion(), e.isActiva(),
            e.getCiudadanos().stream().map(c -> c.getId()).toList());
    }
}
```

### GestionPerfilesService.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/GestionPerfilesService.java`

Servicio principal. @Stateless indica que no guarda estado de una sesión entre llamadas; @LocalBean permite inyectar la clase directamente porque web y ejb están en el mismo EAR. @PersistenceContext obtiene el EntityManager gestionado por WildFly. Las transacciones REQUIRED son implícitas en EJB: no abrir conexiones ni hacer commit manual. crearUsuario valida, detecta CI repetida y crea un subtipo real con switch; la restricción UNIQUE de base sigue protegiendo carreras concurrentes. BCrypt usa salt aleatorio y costo 12; el costo debe medirse antes de fijarlo definitivamente. persist inserta; flush fuerza sincronización y obtiene el ID. En edición, la entidad obtenida por find está gestionada: sus setters se guardan al confirmar, sin merge. No cambiar tipo/CI evita romper identidad y herencia. Las asociaciones actualizan ambos lados en memoria y el propietario Empresa persiste la tabla intermedia. El bloqueo por empresa reduce carreras sobre esa asociación; el índice único es la protección final. No hacemos cascada de creación de ciudadanos ni borrado de empresas. La baja es lógica. El alta no autoriza a cualquiera a crear administradores: solo se expone aquí mediante la herramienta local temporal.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;

@Stateless
@LocalBean
public class GestionPerfilesService {
    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public int crearUsuario(DatosUsuario d) {
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
        em.persist(u);
        em.flush();
        return u.getId();
    }

    public UsuarioResumen consultarUsuario(int id) {
        return UsuarioResumen.desde(usuario(id));
    }

    public List<UsuarioResumen> listarUsuarios(TipoUsuario tipo) {
        // TYPE es el subtipo JPA, no una cadena concatenada al JPQL.
        List<Usuario> usuarios = tipo == null
            ? em.createQuery("select u from Usuario u order by u.id", Usuario.class)
                .getResultList()
            : em.createQuery("select u from Usuario u where type(u) = :tipo order by u.id",
                Usuario.class).setParameter("tipo", clase(tipo)).getResultList();
        return usuarios.stream().map(UsuarioResumen::desde).toList();
    }

    public void modificarUsuario(int id, DatosUsuario d) {
        Usuario u = usuario(id);
        validarUsuario(d);
        if (u.getClass() != clase(d.tipo()) || u.getCi() != d.ci())
            throw new ErrorNegocio("Esta operación no cambia tipo ni CI");
        // La edición de perfil no cambia credenciales.
        copiarDatos(u, d);
    }

    public void cambiarEstadoUsuario(int id, boolean activo) {
        usuario(id).setActivo(activo);
    }

    public void crearEmpresa(int numero, String razon, String nombre, String direccion) {
        Validaciones.positivo(numero, "Número de empresa");
        if (em.find(Empresa.class, numero) != null)
            throw new ErrorNegocio("Ya existe esa empresa");
        em.persist(new Empresa(numero,
            Validaciones.texto(razon, "Razón social", 150),
            Validaciones.texto(nombre, "Nombre público", 150),
            Validaciones.texto(direccion, "Dirección", 255)));
    }

    public EmpresaResumen consultarEmpresa(int numero) {
        return EmpresaResumen.desde(empresa(numero));
    }

    public List<EmpresaResumen> listarEmpresas() {
        return em.createQuery("select e from Empresa e order by e.nroEmpresa", Empresa.class)
            .getResultList().stream().map(EmpresaResumen::desde).toList();
    }

    public void modificarEmpresa(int numero, String razon, String nombre, String direccion) {
        Empresa e = empresa(numero);
        e.setRazonSocial(Validaciones.texto(razon, "Razón social", 150));
        e.setNombrePublico(Validaciones.texto(nombre, "Nombre público", 150));
        e.setDireccion(Validaciones.texto(direccion, "Dirección", 255));
    }

    public void cambiarEstadoEmpresa(int numero, boolean activa) {
        empresa(numero).setActiva(activa);
    }

    public void asociarCiudadano(int numeroEmpresa, int idCiudadano) {
        Empresa e = empresa(numeroEmpresa);
        // Serializa cambios de asociación sobre la misma empresa.
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        if (!e.isActiva() || !c.isActivo())
            throw new ErrorNegocio("Empresa y ciudadano deben estar activos");
        if (!e.getCiudadanos().contains(c)) e.getCiudadanos().add(c);
        if (!c.getEmpresas().contains(e)) c.getEmpresas().add(e);
    }

    public void desasociarCiudadano(int numeroEmpresa, int idCiudadano) {
        Empresa e = empresa(numeroEmpresa);
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        e.getCiudadanos().remove(c);
        c.getEmpresas().remove(e);
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

### GestionPlataformaService.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/GestionPlataformaService.java`

Separa catálogos/configuración de perfiles. Usa una regla con ID 1 porque el modelo actual representa configuración global; no implica una regla por empresa. Validar cantidades no negativas y valores finitos evita NaN/infinito. desvioKm admite fracciones. La versión esperada detecta un formulario viejo y @Version detecta la carrera durante el guardado. No almacenar la regla en un static: se lee de PostgreSQL. El evaluador de fiscalización debe consultar este servicio o implementar invalidación de caché para observar cambios sin redesplegar. Los catálogos se eliminan solo cuando no tienen referencias; la FK sigue protegiendo concurrencia.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

@Stateless
@LocalBean
public class GestionPlataformaService {
    public record RubroVista(int codigo, String nombre, String descripcion) {}
    public record TipoCargaVista(int codigo, String descripcion) {}
    public record ReglaVista(int cargaKg, double desvioKm, long version) {}

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public int crearRubro(String nombre, String descripcion) {
        Rubro r = new Rubro(Validaciones.texto(nombre, "Nombre", 100),
            descripcion(descripcion, 500));
        em.persist(r);
        em.flush();
        return r.getCodigo();
    }
    public List<RubroVista> listarRubros() {
        return em.createQuery("select r from Rubro r order by r.codigo", Rubro.class)
            .getResultList().stream()
            .map(r -> new RubroVista(r.getCodigo(), r.getNombre(), r.getDesc())).toList();
    }
    public void modificarRubro(int codigo, String nombre, String descripcion) {
        Rubro r = requerido(Rubro.class, codigo);
        r.setNombre(Validaciones.texto(nombre, "Nombre", 100));
        r.setDesc(descripcion(descripcion, 500));
    }
    public void eliminarRubro(int codigo) {
        Rubro r = requerido(Rubro.class, codigo);
        long referencias = em.createQuery(
            "select count(e) from Empresa e join e.rubros r where r.codigo = :codigo",
            Long.class).setParameter("codigo", codigo).getSingleResult();
        if (referencias > 0) throw new ErrorNegocio("El rubro está asociado a empresas");
        em.remove(r);
        // La FK sigue protegiendo si hay una asociación concurrente.
        em.flush();
    }
    public int crearTipoCarga(String descripcion) {
        TipoCarga t = new TipoCarga(Validaciones.texto(descripcion, "Descripción", 255));
        em.persist(t);
        em.flush();
        return t.getCodigo();
    }
    public List<TipoCargaVista> listarTiposCarga() {
        return em.createQuery("select t from TipoCarga t order by t.codigo", TipoCarga.class)
            .getResultList().stream()
            .map(t -> new TipoCargaVista(t.getCodigo(), t.getDesc())).toList();
    }
    public void modificarTipoCarga(int codigo, String descripcion) {
        requerido(TipoCarga.class, codigo).setDesc(
            Validaciones.texto(descripcion, "Descripción", 255));
    }
    public void eliminarTipoCarga(int codigo) {
        TipoCarga t = requerido(TipoCarga.class, codigo);
        long referencias = em.createQuery(
            "select count(g) from GuiaDeViaje g join g.tiposCarga t where t.codigo = :codigo",
            Long.class).setParameter("codigo", codigo).getSingleResult();
        if (referencias > 0) throw new ErrorNegocio("El tipo está utilizado en guías");
        em.remove(t);
        em.flush();
    }
    public ReglaVista consultarRegla() {
        ReglaFiscalizacion r = em.find(ReglaFiscalizacion.class, 1);
        if (r == null) throw new ErrorNegocio("Todavía no se configuró la regla");
        return vista(r);
    }
    public void crearRegla(int cargaKg, double desvioKm) {
        validarUmbrales(cargaKg, desvioKm);
        if (em.find(ReglaFiscalizacion.class, 1) != null)
            throw new ErrorNegocio("La regla ya existe; usá editar");
        em.persist(new ReglaFiscalizacion(cargaKg, desvioKm));
    }
    public void editarRegla(int cargaKg, double desvioKm, long versionEsperada) {
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

### ReglaFiscalizacion.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/ReglaFiscalizacion.java`

Reemplazo completo del archivo existente: umbralTracking pasa de int a double, con significado inequívoco de kilómetros; agrega versión optimista. El constructor por defecto no persiste una fila automáticamente: crearRegla es una operación explícita. Se conserva ID fijo 1 por compatibilidad, aunque el setter de ID no debe utilizarse desde formularios.

```java
package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "reglas_fiscalizacion")
public class ReglaFiscalizacion implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private int id = 1;
    @Version
    private long version;
    @Column(name = "umbral_carga", nullable = false)
    private int umbralCarga;
    // Distancia en kilómetros; NO cantidad de puntos.
    @Column(name = "umbral_tracking", nullable = false)
    private double umbralTracking;

    public ReglaFiscalizacion() { this(500, 5.0); }
    public ReglaFiscalizacion(int umbralCarga, double umbralTracking) {
        this.umbralCarga = umbralCarga;
        this.umbralTracking = umbralTracking;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getVersion() { return version; }
    public int getUmbralCarga() { return umbralCarga; }
    public void setUmbralCarga(int valor) { this.umbralCarga = valor; }
    public double getUmbralTracking() { return umbralTracking; }
    public void setUmbralTracking(double valor) { this.umbralTracking = valor; }
    @Override public boolean equals(Object o) {
        return this == o || o instanceof ReglaFiscalizacion r && id == r.id;
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
```

### ResultadoPdi.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/integracion/ResultadoPdi.java`

Resultado tipado para distinguir datos disponibles de degradación. NO_ENCONTRADO está reservado para ampliar el contrato: el mock actual no permite demostrar ese estado. vigente=false NO equivale a ciudadano inexistente.

```java
package uy.edu.fing.grupo07.CargaUY.integracion;

import java.time.LocalDate;

public record ResultadoPdi(Estado estado, Integer ci, String nombre, String apellido,
        LocalDate fechaNacimiento, Boolean vigente, String mensaje) {
    public enum Estado { DISPONIBLE, NO_ENCONTRADO, NO_DISPONIBLE }
    public static ResultadoPdi indisponible() {
        return new ResultadoPdi(Estado.NO_DISPONIBLE, null, null, null, null, null,
            "PDI temporalmente no disponible; los datos no se verificaron");
    }
}
```

### ClientePdi.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/integracion/ClientePdi.java`

Adaptador SOAP 1.1 compatible con el mock encontrado, usando HttpClient de Java 17. El endpoint se configura fuera del código; la prioridad es propiedad pdi.endpoint, variable PDI_ENDPOINT y fallback local. La CI validada es numérica, por eso insertarla no permite inyectar XML. Se envía SOAPAction vacío conforme al WSDL. Los timeouts de conexión/petición y un reintento de lectura acotan esperas por intento; tres consultas fallidas abren el circuito durante diez segundos. El fallback no inventa datos ni borra el ciudadano local. El parser valida namespace, CI y campos; bloquea DTD/entidades externas para evitar lecturas de archivos o llamadas desde XML. NOT_SUPPORTED evita mantener una transacción de base abierta durante red. @Singleton mantiene el circuito entre llamadas de una instancia; @Lock WRITE evita carreras y serializa llamadas. Esta versión simple es para el prototipo: la cola de acceso al singleton no tiene el mismo timeout de la llamada HTTP; para carga concurrente real hay que reemplazar la serialización por concurrencia acotada y un mecanismo de resiliencia medido. El circuito es por instancia, no compartido entre servidores. No es una implementación WS-Security/WS-Addressing ni satisface aún el opcional 9. En despliegue externo usar HTTPS y el estándar WS-* acordado. No actualizar automáticamente nacimiento/identidad con datos ficticios del mock.

```java
package uy.edu.fing.grupo07.CargaUY.integracion;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;

@Singleton
@LocalBean
@Lock(LockType.WRITE)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ClientePdi {
    private static final Logger LOG = Logger.getLogger(ClientePdi.class.getName());
    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String PDI = "http://pdi.dnic.gub.uy/";
    private HttpClient http;
    private URI endpoint;
    private int fallasConsecutivas;
    private long abiertoHasta;
    @PostConstruct
    public void iniciar() {
        String url = System.getProperty("pdi.endpoint");
        if (url == null || url.isBlank()) url = System.getenv("PDI_ENDPOINT");
        if (url == null || url.isBlank()) url = "http://localhost:8081/pdi/ws";
        endpoint = URI.create(url);
        if (!"http".equals(endpoint.getScheme()) && !"https".equals(endpoint.getScheme()))
            throw new IllegalStateException("Endpoint PDI debe ser HTTP o HTTPS");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    }

    public ResultadoPdi consultar(int ci) {
        if (ci <= 0) throw new IllegalArgumentException("CI debe ser positiva");
        if (System.nanoTime() < abiertoHasta) return ResultadoPdi.indisponible();
        // Una consulta SOAP de lectura admite un reintento limitado.
        for (int intento = 0; intento < 2; intento++) {
            try {
                String xml = "<soap:Envelope xmlns:soap=\"" + SOAP
                    + "\" xmlns:pdi=\"" + PDI + "\"><soap:Body>"
                    + "<pdi:consultarCiudadano><ci>" + ci + "</ci>"
                    + "</pdi:consultarCiudadano></soap:Body></soap:Envelope>";
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofString(xml)).build();
                HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
                if (response.statusCode() != 200)
                    throw new IOException("Estado HTTP no exitoso");
                ResultadoPdi resultado = interpretar(response.body(), ci);
                fallasConsecutivas = 0;
                abiertoHasta = 0;
                return resultado;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                registrarFalla();
                return ResultadoPdi.indisponible();
            } catch (IOException e) {
                if (intento == 1) registrarFalla();
            } catch (Exception e) {
                // XML inválido o Fault: no fabricar un ciudadano ni reintentar errores de contrato.
                registrarFalla();
                return ResultadoPdi.indisponible();
            }
        }
        return ResultadoPdi.indisponible();
    }

    private void registrarFalla() {
        fallasConsecutivas++;
        if (fallasConsecutivas >= 3)
            abiertoHasta = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        LOG.warning("PDI: consulta no disponible; fallas consecutivas=" + fallasConsecutivas);
    }

    // Visibilidad de paquete para probar el contrato sin hacer llamadas de red.
    ResultadoPdi interpretar(String xml, int solicitada) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element envelope = doc.getDocumentElement();
        if (!SOAP.equals(envelope.getNamespaceURI()) || !"Envelope".equals(envelope.getLocalName()))
            throw new IOException("No es SOAP 1.1");
        NodeList bodies = doc.getElementsByTagNameNS(SOAP, "Body");
        if (bodies.getLength() != 1) throw new IOException("Body inválido");
        Element body = (Element) bodies.item(0);
        if (body.getElementsByTagNameNS(SOAP, "Fault").getLength() > 0)
            throw new IOException("SOAP Fault no tipificado por el contrato actual");
        NodeList respuestas = body.getElementsByTagNameNS(PDI, "consultarCiudadanoResponse");
        if (respuestas.getLength() != 1) throw new IOException("Respuesta inesperada");
        Element respuesta = (Element) respuestas.item(0);
        int ci = Integer.parseInt(campo(respuesta, "ci"));
        if (ci != solicitada) throw new IOException("CI de respuesta no coincide");
        String vigente = campo(respuesta, "vigente");
        if (!vigente.equals("true") && !vigente.equals("false")
                && !vigente.equals("1") && !vigente.equals("0"))
            throw new IOException("Booleano inválido");
        return new ResultadoPdi(ResultadoPdi.Estado.DISPONIBLE, ci,
            campo(respuesta, "nombre"), campo(respuesta, "apellido"),
            LocalDate.parse(campo(respuesta, "fechaNacimiento")),
            vigente.equals("true") || vigente.equals("1"), "Datos obtenidos del mock PDI");
    }
    private String campo(Element padre, String nombre) throws IOException {
        String valor = null;
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            // El WSDL actual define elementos locales sin namespace.
            if (n instanceof Element e && nombre.equals(e.getLocalName())
                    && (e.getNamespaceURI() == null || e.getNamespaceURI().isEmpty())) {
                if (valor != null) throw new IOException("Campo repetido");
                valor = e.getTextContent().trim();
            }
        }
        if (valor == null || valor.isEmpty()) throw new IOException("Campo obligatorio ausente");
        return valor;
    }
}
```

### P1PruebasServlet.java

**Destino:** `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/P1PruebasServlet.java`

Adaptador HTTP temporal: convierte parámetros y llama EJBs; no ejecuta JPQL ni guarda entidades. Solo funciona con -Dp1.pruebas=true y conexión loopback. doGet presenta el formulario y genera un token CSRF; doPost lo verifica antes de una escritura. Ese token de formulario NO es un token de autenticación. La respuesta text/plain evita interpretar datos como HTML, y no contiene contraseñas. WEB-INF impide abrir el JSP directamente. Cada petición ejecuta una sola acción; una respuesta de error no debe confundirse con éxito. Antes de publicar interfaces definitivas hay que agregar identidad real y autorización de negocio. No usar el panel detrás de un proxy que convierta accesos remotos en loopback.

```java
package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.service.*;
import uy.edu.fing.grupo07.CargaUY.integracion.ClientePdi;

@WebServlet("/p1/pruebas")
public class P1PruebasServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    @EJB private GestionPerfilesService perfiles;
    @EJB private GestionPlataformaService plataforma;
    @EJB private ClientePdi pdi;

    private boolean habilitado(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (!Boolean.getBoolean("p1.pruebas")
                || !InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
            resp.sendError(404);
            return false;
        }
        return true;
    }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        if (!habilitado(req, resp)) return;
        HttpSession session = req.getSession();
        if (session.getAttribute("p1csrf") == null)
            session.setAttribute("p1csrf", UUID.randomUUID().toString());
        req.setAttribute("csrf", session.getAttribute("p1csrf"));
        resp.setHeader("Cache-Control", "no-store");
        req.getRequestDispatcher("/WEB-INF/p1/pruebas.jsp").forward(req, resp);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (!habilitado(req, resp)) return;
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/plain; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        HttpSession session = req.getSession(false);
        String enviado = req.getParameter("csrf");
        String esperado = session == null ? null : (String) session.getAttribute("p1csrf");
        if (esperado == null || enviado == null || !MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), enviado.getBytes(StandardCharsets.UTF_8))) {
            resp.sendError(403);
            return;
        }
        try {
            Object resultado = ejecutar(req);
            resp.getWriter().println(resultado == null ? "Operación completada" : resultado);
        } catch (ErrorNegocio | IllegalArgumentException e) {
            resp.setStatus(400);
            resp.getWriter().println(e.getMessage());
        } catch (EJBException e) {
            // En particular, una carrera de unicidad o de @Version puede llegar aquí.
            getServletContext().log("Fallo de operación P1", e);
            resp.setStatus(409);
            resp.getWriter().println("No se pudo guardar. Revisá duplicados o cambios concurrentes.");
        }
    }
    private Object ejecutar(HttpServletRequest r) {
        String accion = r.getParameter("accion");
        if (accion == null) throw new ErrorNegocio("Acción obligatoria");
        switch (accion) {
            case "crearUsuario": return perfiles.crearUsuario(datos(r, true));
            case "listarUsuarios": return perfiles.listarUsuarios(tipoOpcional(r));
            case "consultarUsuario": return perfiles.consultarUsuario(entero(r, "id"));
            case "modificarUsuario": perfiles.modificarUsuario(entero(r, "id"), datos(r, false)); break;
            case "bajaUsuario": perfiles.cambiarEstadoUsuario(entero(r, "id"), false); break;
            case "reactivarUsuario": perfiles.cambiarEstadoUsuario(entero(r, "id"), true); break;
            case "crearEmpresa": perfiles.crearEmpresa(entero(r, "numero"), r.getParameter("razon"),
                    r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "consultarEmpresa": return perfiles.consultarEmpresa(entero(r, "numero"));
            case "listarEmpresas": return perfiles.listarEmpresas();
            case "modificarEmpresa": perfiles.modificarEmpresa(entero(r, "numero"),
                    r.getParameter("razon"), r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "bajaEmpresa": perfiles.cambiarEstadoEmpresa(entero(r, "numero"), false); break;
            case "reactivarEmpresa": perfiles.cambiarEstadoEmpresa(entero(r, "numero"), true); break;
            case "asociar": perfiles.asociarCiudadano(entero(r, "numero"), entero(r, "id")); break;
            case "desasociar": perfiles.desasociarCiudadano(entero(r, "numero"), entero(r, "id")); break;
            case "crearRubro": return plataforma.crearRubro(r.getParameter("nombreRubro"), r.getParameter("descripcion"));
            case "listarRubros": return plataforma.listarRubros();
            case "modificarRubro": plataforma.modificarRubro(entero(r, "codigo"),
                    r.getParameter("nombreRubro"), r.getParameter("descripcion")); break;
            case "eliminarRubro": plataforma.eliminarRubro(entero(r, "codigo")); break;
            case "crearTipoCarga": return plataforma.crearTipoCarga(r.getParameter("descripcion"));
            case "listarTiposCarga": return plataforma.listarTiposCarga();
            case "modificarTipoCarga": plataforma.modificarTipoCarga(entero(r, "codigo"), r.getParameter("descripcion")); break;
            case "eliminarTipoCarga": plataforma.eliminarTipoCarga(entero(r, "codigo")); break;
            case "consultarRegla": return plataforma.consultarRegla();
            case "crearRegla": plataforma.crearRegla(entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm"))); break;
            case "editarRegla": plataforma.editarRegla(entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm")),
                    Long.parseLong(r.getParameter("version"))); break;
            case "consultarPdi": return pdi.consultar(entero(r, "ci"));
            default: throw new ErrorNegocio("Acción desconocida");
        }
        return null;
    }
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

### pruebas.jsp

**Destino:** `web/src/main/webapp/WEB-INF/p1/pruebas.jsp`

Formulario único de desarrollo. Elegir acción y completar campos relacionados. El resultado se muestra como texto para comprobar el servicio sin construir toda la interfaz final. No contiene lógica de negocio. Las expresiones JSP solo imprimen contexto y el token generado por el servidor; no imprimen texto libre de usuarios.

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="es">
<head><meta charset="UTF-8"><title>Pruebas locales P1</title></head>
<body>
<h1>Pruebas locales de perfiles y plataforma</h1>
<p>Elegí una operación y completá solo los campos que necesita.
La respuesta se muestra como texto. Volvé atrás para otra operación.</p>
<form method="post" action="<%= request.getContextPath() %>/p1/pruebas">
<input type="hidden" name="csrf" value="<%= request.getAttribute("csrf") %>">
<label>Operación <select name="accion">
<option value="crearUsuario">crearUsuario</option>
<option value="listarUsuarios">listarUsuarios</option>
<option value="consultarUsuario">consultarUsuario</option>
<option value="modificarUsuario">modificarUsuario</option>
<option value="bajaUsuario">bajaUsuario</option>
<option value="reactivarUsuario">reactivarUsuario</option>
<option value="crearEmpresa">crearEmpresa</option>
<option value="consultarEmpresa">consultarEmpresa</option>
<option value="listarEmpresas">listarEmpresas</option>
<option value="modificarEmpresa">modificarEmpresa</option>
<option value="bajaEmpresa">bajaEmpresa</option>
<option value="reactivarEmpresa">reactivarEmpresa</option>
<option value="asociar">asociar</option>
<option value="desasociar">desasociar</option>
<option value="crearRubro">crearRubro</option>
<option value="listarRubros">listarRubros</option>
<option value="modificarRubro">modificarRubro</option>
<option value="eliminarRubro">eliminarRubro</option>
<option value="crearTipoCarga">crearTipoCarga</option>
<option value="listarTiposCarga">listarTiposCarga</option>
<option value="modificarTipoCarga">modificarTipoCarga</option>
<option value="eliminarTipoCarga">eliminarTipoCarga</option>
<option value="consultarRegla">consultarRegla</option>
<option value="crearRegla">crearRegla</option>
<option value="editarRegla">editarRegla</option>
<option value="consultarPdi">consultarPdi</option>
</select></label>
<fieldset><legend>Usuario</legend>
<label>ID <input name="id" type="number"></label>
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
<label>Número <input name="numero" type="number"></label>
<label>Razón social <input name="razon"></label>
<label>Nombre público <input name="nombrePublico"></label>
<label>Dirección <input name="direccion"></label>
<p>Asociar/desasociar: usá número de empresa e ID del ciudadano.</p>
</fieldset>
<fieldset><legend>Catálogos</legend>
<label>Código <input name="codigo" type="number"></label>
<label>Nombre rubro <input name="nombreRubro"></label>
<label>Descripción <input name="descripcion"></label>
</fieldset>
<fieldset><legend>Regla</legend>
<label>Tolerancia kg <input name="cargaKg" type="number" min="0"></label>
<label>Desvío km <input name="desvioKm" type="number" min="0" step="0.01"></label>
<label>Versión consultada (editar) <input name="version" type="number" min="0"></label>
</fieldset>
<button type="submit">Ejecutar</button>
</form>
</body></html>
```

### GestionPerfilesServiceTest.java

**Destino:** `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/GestionPerfilesServiceTest.java`

Prueba asociación idempotente, baja lógica sin remove, rechazo de inactivos y CI repetida, hash BCrypt verificable y DTO sin credenciales. Mockito simula el EntityManager; esto no valida el mapeo JPA real.

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
        return s;
    }
    @Test void asociacionRepetidaNoDuplicaNingunLado() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Empresa empresa = new Empresa(10, "Empresa SA", "Empresa", "Montevideo");
        Ciudadano ciudadano = new Ciudadano("Ana", "ana@example.com", LocalDate.of(1990,1,1),
            12345678, "hash-de-prueba");
        ciudadano.setId(7);
        when(em.find(Empresa.class, 10)).thenReturn(empresa);
        when(em.find(Ciudadano.class, 7)).thenReturn(ciudadano);
        GestionPerfilesService s = servicio(em);
        s.asociarCiudadano(10, 7);
        s.asociarCiudadano(10, 7);
        assertEquals(1, empresa.getCiudadanos().size());
        assertEquals(1, ciudadano.getEmpresas().size());
        s.desasociarCiudadano(10, 7);
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
        assertThrows(ErrorNegocio.class, () -> servicio(em).asociarCiudadano(10, 7));
        assertTrue(e.getCiudadanos().isEmpty());
    }
    @Test void bajaNoEliminaElRegistro() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Ciudadano c = new Ciudadano();
        when(em.find(Usuario.class, 7)).thenReturn(c);
        servicio(em).cambiarEstadoUsuario(7, false);
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
        assertEquals(7, servicio(em).crearUsuario(datos));
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
        assertThrows(ErrorNegocio.class, () -> servicio(em).crearUsuario(datos));
        verify(em, never()).persist(any());
    }
}
```

### ClientePdiTest.java

**Destino:** `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/integracion/ClientePdiTest.java`

Prueba el contrato, mismatch de CI, bloqueo de entidades externas, fallback y HTTP real con un servidor temporal local. Verifica dos intentos por consulta y ausencia de llamadas al abrir el circuito. No es una prueba de PDI gubernamental ni de WS-*.

```java
package uy.edu.fing.grupo07.CargaUY.integracion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientePdiTest {
    private String respuesta(String ci) {
        return "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<s:Body><p:consultarCiudadanoResponse xmlns:p=\"http://pdi.dnic.gub.uy/\">"
            + "<ci>" + ci + "</ci><nombre>Juan</nombre><apellido>Perez</apellido>"
            + "<fechaNacimiento>1985-05-15</fechaNacimiento><vigente>true</vigente>"
            + "</p:consultarCiudadanoResponse></s:Body></s:Envelope>";
    }
    @Test void interpretaElContratoActual() throws Exception {
        ResultadoPdi r = new ClientePdi().interpretar(respuesta("12345678"), 12345678);
        assertEquals(ResultadoPdi.Estado.DISPONIBLE, r.estado());
        assertEquals("Juan", r.nombre());
    }
    @Test void noAceptaOtraCedula() {
        assertThrows(Exception.class,
            () -> new ClientePdi().interpretar(respuesta("11111111"), 12345678));
    }
    @Test void noAceptaXmlConEntidadesExternas() {
        String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///no-leer'>]>"
            + respuesta("12345678");
        assertThrows(Exception.class, () -> new ClientePdi().interpretar(xml, 12345678));
    }
    @Test void fallbackNoInventaDatos() {
        ResultadoPdi r = ResultadoPdi.indisponible();
        assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE, r.estado());
        assertNull(r.nombre());
        assertNull(r.ci());
    }

    @Test void consultaHttpRealContraUnServicioLocalDePrueba() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pdi/ws", exchange -> {
            String peticion = new String(exchange.getRequestBody().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            byte[] bytes = respuesta(peticion.contains("<ci>12345678</ci>")
                ? "12345678" : "11111111").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        String anterior = System.getProperty("pdi.endpoint");
        try {
            server.start();
            System.setProperty("pdi.endpoint",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/pdi/ws");
            ClientePdi cliente = new ClientePdi(); cliente.iniciar();
            ResultadoPdi r = cliente.consultar(12345678);
            assertEquals(ResultadoPdi.Estado.DISPONIBLE, r.estado());
            assertEquals(12345678, r.ci());
        } finally {
            server.stop(0);
            if (anterior == null) System.clearProperty("pdi.endpoint");
            else System.setProperty("pdi.endpoint", anterior);
        }
    }
    @Test void reintentaConLimiteYCortaTrasFallasSostenidas() throws Exception {
        java.util.concurrent.atomic.AtomicInteger llamadas = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pdi/ws", exchange -> {
            llamadas.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        String anterior = System.getProperty("pdi.endpoint");
        try {
            server.start();
            System.setProperty("pdi.endpoint",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/pdi/ws");
            ClientePdi cliente = new ClientePdi(); cliente.iniciar();
            for (int i = 0; i < 3; i++)
                assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE, cliente.consultar(12345678).estado());
            assertEquals(6, llamadas.get()); // Dos intentos por consulta.
            assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE, cliente.consultar(12345678).estado());
            assertEquals(6, llamadas.get()); // Circuito abierto: ninguna llamada adicional.
        } finally {
            server.stop(0);
            if (anterior == null) System.clearProperty("pdi.endpoint");
            else System.setProperty("pdi.endpoint", anterior);
        }
    }
}
```

### GestionPlataformaServiceTest.java

**Destino:** `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/GestionPlataformaServiceTest.java`

Prueba rechazo de versión desactualizada, kilómetros fraccionarios y valores NaN. El incremento real de @Version y los bloqueos deben comprobarse también con WildFly/PostgreSQL.

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
        return s;
    }
    @Test void rechazaUnaVersionVieja() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);
        assertThrows(ErrorNegocio.class, () -> servicio(em).editarRegla(100, 0.5, 99));
        assertEquals(500, regla.getUmbralCarga());
    }
    @Test void permiteKilometrosFraccionarios() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);
        servicio(em).editarRegla(100, 0.5, 0);
        assertEquals(0.5, regla.getUmbralTracking());
        verify(em).flush();
    }
    @Test void rechazaNaN() throws Exception {
        EntityManager em = mock(EntityManager.class);
        assertThrows(ErrorNegocio.class, () -> servicio(em).crearRegla(500, Double.NaN));
        verify(em, never()).persist(any());
    }
}
```

### FuncionarioEJB.java

**Destino:** `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/FuncionarioEJB.java`

Puente de compatibilidad: la interfaz y el servlet existente conservan sus nombres, pero delegan el alta al nuevo servicio y dejan de persistir directamente una contraseña plana. Evita dos implementaciones de validación distintas. El formulario antiguo sigue siendo una pantalla de prueba sin autorización; no se convierte en administración segura por este cambio.

```java
package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;
import uy.edu.fing.grupo07.CargaUY.dto.*;

@Stateless
@LocalBean
public class FuncionarioEJB implements FuncionarioEJBLocal {
    @EJB private GestionPerfilesService perfiles;
    @Override
    public void crear(Funcionario funcionario) {
        int id = perfiles.crearUsuario(new DatosUsuario(TipoUsuario.FUNCIONARIO,
            funcionario.getNombre(), funcionario.getMail(), funcionario.getFechaNac(),
            funcionario.getCi(), funcionario.getPassword(), funcionario.getNroFuncionario(),
            funcionario.getDepartamento(), null));
        funcionario.setId(id);
    }
}
```

## 7. Compilar y publicar
1. Guardar todos los archivos en Eclipse.
2. Seleccionar los proyectos → Maven → Update Project.
3. En el proyecto raíz CargaUY, Run As → Maven build… → Goals: `clean package`. Revisar que use un JDK compatible y el release 17 del pom.
4. Ejecutar las pruebas con Run As → JUnit Test sobre las tres clases nuevas. Si tu launcher de Mockito requiere agente, configurar el agente de su versión o ejecutarlas con la configuración Maven correspondiente. La verificación realizada aquí usó el agente Mockito disponible y un JDK 25 compilando con --release 17.
5. En Servers → WildFly → Open launch configuration → Arguments → VM arguments, agregar:
```text
-Dp1.pruebas=true
-Dpdi.endpoint=http://localhost:8081/pdi/ws
```
Son argumentos de la JVM, no del programa.
6. Reiniciar WildFly y Publish del EAR. Abrir:
```text
http://localhost:8080/cargauy/p1/pruebas
```
Si devuelve 404, comprobar propiedad, acceso por localhost y publicación del módulo web. Si hay error de columna, ejecutar primero la migración y comprobar que persistence.xml use none.
Sin -Dp1.pruebas=true el panel queda deshabilitado. No habilitarlo en un servidor compartido o en producción.
El panel no usa credenciales fijas ni permite acceso remoto directo. El código de los servicios todavía no tiene autorización de negocio: eso se integrará junto con el login; no declararlo como seguridad terminada.

## 8. Prueba manual, en este orden
### Usuarios
1. Acción crearUsuario. Tipo CIUDADANO, nombre Ana, mail ana@example.com, nacimiento 1990-01-01, CI 12345678, contraseña propia de prueba de mínimo ocho caracteres. Los demás campos quedan vacíos.
2. Anotar el ID devuelto (no confundir ID con CI).
3. listarUsuarios: tipo vacío para todos, o CIUDADANO para filtrar.
4. consultarUsuario con el ID; no debe aparecer password.
5. Repetir alta con misma CI: debe fallar. Es una CI ilustrativa de mock, no una afirmación de identidad o validez del dígito verificador.
6. modificarUsuario: ID, tipo y CI originales, y todos los datos de perfil actualizados. Contraseña vacía; no se cambia.
7. bajaUsuario con ID: activo=false. reactivarUsuario: activo=true.
8. Para FUNCIONARIO, completar número y departamento; para AUTORIDAD, cargo. Para CHOFER y ADMINISTRADOR no hay campos particulares en el modelo actual.

### Empresas y asociación
1. crearEmpresa: número 100, razón Transportes SA, nombre público Transportes, dirección Montevideo.
2. listarEmpresas y consultarEmpresa con 100.
3. asociar: número 100 y el ID del ciudadano.
4. Repetir asociar; no debe duplicarse. Consultar empresa y verificar un único ID en ciudadanos.
5. desasociar: mismo número/ID. Consultar y verificar ausencia.
6. modificarEmpresa con todos sus campos. bajaEmpresa y reactivarEmpresa cambian estado, no borran vehículos/guías.
7. Intentar asociar empresa o ciudadano inactivo: debe fallar.
No se asocia CHOFER aquí porque el modelo actual de Empresa relaciona Ciudadano y Chofer es otro subtipo. Resolver esa relación es una decisión compartida de dominio.

### Rubros y tipos
1. crearRubro: nombre y descripción; anotar código.
2. listarRubros, modificarRubro y eliminarRubro con el código.
3. crearTipoCarga: descripción; listar, modificar y eliminar.
4. La consulta individual de catálogos puede hacerse filtrando el listado en este panel; si necesitan pantalla de detalle, agregar el método consultar explícito.
5. La prueba de eliminación bloqueada requiere asociar el rubro a una empresa o el tipo a una guía a través de las funciones de ese módulo. El servicio comprueba referencias en negocio y la FK lo refuerza.

### Reglas
1. crearRegla: cargaKg=500, desvioKm=5.0. Solo se crea una vez.
2. consultarRegla: anotar version.
3. editarRegla: cargaKg=250, desvioKm=0.5 y la versión leída.
4. consultar otra vez; los valores y la versión deben actualizarse.
5. Repetir edición con versión vieja: rechazar, no sobrescribir.
6. Valores negativos, NaN o infinito deben rechazarse.
Criterio para el motor: exceso si pesoMedidoKg > pesoVehiculoKg + capacidadKg + cargaKg. Desvío si distanciaKm > desvioKm; cualquier criterio de varios puntos se define aparte. La implementación de ese motor pertenece a fiscalización.

### PDI
El proyecto trae un mock básico en docker/mock-pdi. Para levantar únicamente ese servicio sin chocar con tu PostgreSQL o WildFly local:
```powershell
docker compose up -d --build mock-pdi
```
Requiere Docker disponible. Alternativa con Python instalado:
```powershell
python docker/mock-pdi/mock_pdi.py
```
Esta alternativa usa puerto 8080 y CHOCA con WildFly si corre en la misma PC. Para usarla, modificar explícitamente el puerto del mock a 8081 o pasar a Docker; no ejecutar dos servicios sobre el mismo puerto.
Para Docker, verificar en navegador:
```text
http://localhost:8081/pdi/ws?wsdl
```
El WSDL embebido anuncia mock-pdi:8080 para conexiones entre contenedores; el cliente manual usa el endpoint configurado para tu host.
1. consultarPdi: CI 12345678 → DISPONIBLE con datos fijos.
2. Detener mock:
```powershell
docker compose stop mock-pdi
```
3. Repetir → NO_DISPONIBLE, sin ciudadano inventado.
4. Tras tres consultas fallidas, el circuito devuelve fallback sin nueva llamada durante diez segundos.
5. Levantar mock con `docker compose start mock-pdi`, esperar el intervalo de apertura y repetir.
NO_ENCONTRADO requiere ampliar y acordar el WSDL/mock con un resultado o Fault explícito. El código actual no interpreta vigente=false como inexistencia. No dar ese escenario por terminado.
Agregar después WS-Addressing/WS-Security si mantienen el opcional 9. El cliente SOAP básico no lo acredita.

## 9. Explicación para defender ante el profesor
**¿Por qué EJB y no guardar desde un JSP?**
La lógica y transacciones viven en negocio. El JSP presenta, el servlet adapta HTTP y el EJB valida/persiste. Así otro canal puede reutilizar la lógica sin duplicarla.

**¿Qué es una entidad gestionada?**
Es una instancia cargada por el EntityManager dentro del contexto de persistencia. Hibernate detecta cambios de sus campos y sincroniza al confirmar la transacción. Por eso no necesitamos merge después de find y setters; merge se usa con otras semánticas, como entidades separadas del contexto.

**¿Por qué DTO en vez de devolver Usuario?**
Usuario incluye credenciales y relaciones JPA. El DTO expone únicamente los campos permitidos y se construye antes de salir del EJB. Evita también cargar relaciones desde una pantalla con el contexto ya cerrado.

**¿Por qué no borrar físicamente usuarios/empresas?**
Tienen referencias e historial. En Empresa hay cascadas que pueden borrar vehículos y guías. La baja conserva identidad y relaciones; los otros servicios deben respetar el estado.

**¿Por qué tocar ambos lados de la asociación?**
Empresa es el propietario que determina la tabla intermedia. Ciudadano es el inverso. Actualizar solo Ciudadano puede no guardar la asociación; actualizar ambos mantiene consistente el grafo en memoria.

**¿Por qué validar CI repetida si ya hay UNIQUE?**
La consulta permite un mensaje claro para el caso normal. UNIQUE protege la carrera en la que dos transacciones consultan al mismo tiempo. La consulta no sustituye la restricción de base.

**¿Por qué BCrypt y no cifrar?**
En una credencial local se necesita comprobar una contraseña, no recuperarla. BCrypt almacena un hash con salt y costo, no texto plano. No usamos hashCode, MD5 ni un hash rápido sin salt. El costo debe evaluarse en la máquina real. Cuando se integre LDAP, no duplicar innecesariamente su contraseña en PostgreSQL.

**¿Por qué no cambiar el tipo del usuario al editar?**
Es herencia JOINED con tablas por subtipo y campos distintos. Un setter no convierte un Funcionario en Ciudadano. Las funciones múltiples requieren otra decisión del modelo, no cambiar una cadena del formulario.

**¿Por qué kg y km explícitos?**
Un umbral no tiene significado sin unidad. Aquí cargaKg y desvioKm representan magnitudes distintas; un contador de posiciones desviadas no es una distancia.

**¿Por qué @Version?**
Evita sobrescribir cambios concurrentes silenciosamente. La regla devuelve versión y exige la que leyó el formulario. La base/JPA también comprueba que no cambió durante la transacción. No elimina la necesidad de manejar el conflicto en la interfaz.

**¿Por qué fallback y no un ciudadano ficticio?**
Una falla de red no demuestra ni existencia ni inexistencia. Se informa falta de verificación y se conserva operativa la plataforma. Los valores Juan/Perez son exclusivamente datos del mock cuando responde.

**¿Por qué timeouts, reintento limitado y circuito?**
Un timeout acota una espera; un reintento permite recuperarse de una falla breve; el circuito evita insistir ante fallas sostenidas. No aplicar reintentos de manera indiscriminada a operaciones que escriben sin idempotencia. La implementación de singleton serializado es sencilla para el prototipo y debe revisarse para carga real.

**¿Por qué no hacer filtros ahora?**
Un filtro de autenticación necesita una identidad confiable y una política de sesión/token. Crear uno que acepte valores arbitrarios da una falsa protección. El panel de prueba local resuelve el desarrollo sin simular seguridad terminada.

## 10. Qué falta para considerar P1 terminado
• Login y validarToken, filtros y logout con políticas reales de identidad.
• Autorización en servicios por rol y pertenencia, no solo en rutas.
• Decidir gub.uy/LDAP y el modelo de credenciales sin contradecir los opcionales.
• Múltiples roles, vínculo chofer–empresa y sus restricciones compartidas.
• WSDL PDI completo: ciudadano inexistente, errores y WS-* seleccionado.
• HTTPS en despliegue final y observabilidad/correlación.
• Cliente PDI con concurrencia acotada y resiliencia medida bajo carga.
• Pantallas finales diferenciadas; retirar el panel temporal y la prueba antigua.
• Consumo de reglas actualizadas por el motor de fiscalización.
• Paginación, conflictos de formularios de perfiles y restricciones complementarias.
Estos pendientes no bloquean la práctica local de ABM, pero impiden declarar P1 completo.

## 11. Verificación realizada y cómo continuar
Se compilaron los ejemplos Java, las entidades existentes con las ampliaciones propuestas y los tests con --release 17. Pasaron 15 pruebas mediante ejecución de métodos JUnit con Mockito y un servidor HTTP temporal para las pruebas de red.
No se modificó el código activo, no se aplicó la migración a PostgreSQL y no se desplegaron los ejemplos en tu WildFly. La compilación y los mocks no prueban el mapeo real, TYPE en JPQL, transacciones, bloqueos o incremento de versión en la base: seguir la prueba manual tras copiar los archivos.
Los JSP requieren prueba de renderizado en el servidor; compilar el servlet no compila el JSP.
No se reporta 80 % de cobertura ni cumplimiento de performance: esos resultados deben medirse.
Cuando completes una etapa, verificá y hacé un commit concreto. Ejemplo:
```powershell
git status
git add ejb/src/main/java ejb/src/main/resources/META-INF/persistence.xml
git commit -m "Agregar gestión de perfiles y baja lógica"
```
Agregar web/tests cuando corresponda, revisar exactamente qué se incluye y no agregar secretos. Podés versionar la migración en una carpeta de scripts con el procedimiento de ejecución acordado.

## 12. Documentación oficial para justificar decisiones
• Jakarta Persistence (propietario/inverso, entidades gestionadas y versión):
  https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html
• EJB y transacciones gestionadas:
  https://jakarta.ee/learn/docs/jakartaee-tutorial/current/supporttechs/transactions/transactions.html
• BCrypt de la librería usada por el proyecto:
  https://github.com/patrickfav/bcrypt
• HttpClient Java 17:
  https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html

Este material es una guía de implementación asistida. Revisar cada cambio, contrastarlo con el equipo y declarar el uso de IA según la letra. Las decisiones del proyecto y su defensa corresponden al grupo.
