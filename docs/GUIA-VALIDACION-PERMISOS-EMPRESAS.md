# Guía: validación de permisos de empresas y ciudadano de prueba

Fecha: 28/10/2026. Guía para implementar manualmente sobre la rama actual. Solo se agrega este documento: el código de la aplicación no se modifica. Los fragmentos son una propuesta y todavía no se compilaron ni probaron como funcionalidad integrada.

## 1. Alcance que vamos a implementar

La captura dice “autorizaciones vigentes”, pero no enumera permisos propios de la empresa. Para esta propuesta interpretamos la consulta como: **mostrar el estado registral de la empresa y la habilitación documental de sus vehículos, comprobando PNC e ITV**. Confirmar esta interpretación con el grupo/docente antes de presentarla como cumplimiento definitivo del requisito.

Reglas:

- Debe existir una sesión auténtica de CIUDADANO o ADMINISTRADOR. El administrador sirve para preparar y diagnosticar; también se ensaya el ciudadano.
- Un ciudadano puede consultar el resumen de cualquier transportista, no solo de sus propias empresas. Este CU es una consulta como potencial cliente, no una operación de edición. Si el docente establece otra política, adaptar el servicio.
- Empresa activa no significa que toda su documentación esté vigente.
- Cada vehículo requiere habilitación administrativa, PNC vigente e ITV vigente.
- Un documento que vence hoy se considera vigente durante ese día, conforme al criterio actual del proyecto.
- Si existe historial, un documento antiguo vencido no invalida otro vigente. Para el resumen se toma el vencimiento más reciente.
- Sin empresa: error de empresa inexistente. Sin vehículos: información insuficiente. Error de base: consulta temporalmente no disponible. No convertir estos resultados en “permiso vencido”.
- No se emite un certificado legal ni se consulta un registro externo: se informa lo registrado en CargaUY.

## 2. Diseño y archivos

```text
Ciudadano autenticado
        ↓
/empresa/validar-permisos.xhtml
        ↓
ValidacionPermisosBean
        ↓
ValidacionPermisosService
        ↓
Empresa + Vehiculo + PNC + ITV en PostgreSQL
        ↓
DTO de solo lectura con resultado, fecha y motivos
```

| Crear | Módulo y ruta relativa |
|---|---|
| DTO | `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/ValidacionEmpresaDTO.java` |
| Servicio | `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/ValidacionPermisosService.java` |
| Bean | `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/ValidacionPermisosBean.java` |
| Vista | `web/src/main/webapp/empresa/validar-permisos.xhtml` |

En Eclipse, crear las clases dentro de esos paquetes en `src/main/java`. Crear el XHTML dentro de `web/src/main/webapp/empresa`.

## 3. DTO de resultado

Crear `ValidacionEmpresaDTO.java`:

```java
package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public record ValidacionEmpresaDTO(
        int numero, String razonSocial, boolean activa,
        LocalDate fechaConsulta, List<Unidad> unidades
) implements Serializable {
    public ValidacionEmpresaDTO {
        unidades = List.copyOf(unidades);
    }

    public record Unidad(
            String matricula, boolean habilitacionAdministrativa,
            LocalDate vencimientoPnc, String estadoPnc,
            LocalDate vencimientoItv, String estadoItv,
            String resultado
    ) implements Serializable {}

    public long habilitadas() {
        return unidades.stream().filter(u -> "VIGENTE".equals(u.resultado())).count();
    }
}
```

El DTO lleva datos ya calculados; la pantalla no obtiene entidades JPA ni decide la regla. `List.copyOf` evita que la lista del resultado se modifique accidentalmente.

No devolvemos un booleano “empresa habilitada”: una flota puede tener estados mixtos. Mostramos empresa activa/inactiva y resultado por unidad, sin inventar una regla global de autorización de empresa.

## 4. Servicio EJB

Crear `ValidacionPermisosService.java`:

```java
package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.dto.ValidacionEmpresaDTO;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;

@Stateless
@LocalBean
public class ValidacionPermisosService {
    @EJB private ControlAcceso control;

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public ValidacionEmpresaDTO consultar(String token, int numero) {
        control.exigir(token, TipoUsuario.CIUDADANO, TipoUsuario.ADMINISTRADOR);
        if (numero <= 0) throw new ErrorAcceso(400, "Número de empresa inválido");

        Empresa empresa = em.find(Empresa.class, numero);
        if (empresa == null) throw new ErrorAcceso(404, "Empresa inexistente");

        LocalDate hoy = LocalDate.now();
        List<Vehiculo> vehiculos = em.createQuery(
                "select v from Vehiculo v where v.empresa.nroEmpresa=:numero order by v.matricula",
                Vehiculo.class).setParameter("numero", numero).getResultList();

        var unidades = vehiculos.stream().map(v -> resumir(v, hoy)).toList();
        return new ValidacionEmpresaDTO(empresa.getNroEmpresa(), empresa.getRazonSocial(),
                empresa.isActiva(), hoy, unidades);
    }

    private ValidacionEmpresaDTO.Unidad resumir(Vehiculo v, LocalDate hoy) {
        List<LocalDate> pncs = v.getPncs() == null ? List.of()
                : v.getPncs().stream().map(p -> p.getFechaVencimiento()).toList();
        List<LocalDate> itvs = v.getItv() == null ? List.of()
                : v.getItv().stream().map(i -> i.getFechaVencimiento()).toList();

        LocalDate pnc = ultimo(pncs), itv = ultimo(itvs);
        String epnc = estado(pncs, pnc, hoy), eitv = estado(itvs, itv, hoy);
        String resultado;
        if (!v.isEstadoHabilitacion()) resultado = "INHABILITADA";
        else if ("VENCIDO".equals(epnc) || "VENCIDO".equals(eitv)) resultado = "NO_VIGENTE";
        else if (!"VIGENTE".equals(epnc) || !"VIGENTE".equals(eitv)) resultado = "INFORMACION_INCOMPLETA";
        else resultado = "VIGENTE";

        return new ValidacionEmpresaDTO.Unidad(v.getMatricula(), v.isEstadoHabilitacion(),
                pnc, epnc, itv, eitv, resultado);
    }

    private LocalDate ultimo(List<LocalDate> fechas) {
        return fechas.stream().filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    private String estado(List<LocalDate> historial, LocalDate ultima, LocalDate hoy) {
        if (ultima != null && !ultima.isBefore(hoy)) return "VIGENTE";
        if (historial.isEmpty() || historial.stream().anyMatch(Objects::isNull))
            return "SIN_INFORMACION";
        return "VENCIDO";
    }
}
```

La autorización se ejecuta antes de consultar la empresa. No se usa `control.empresa`, porque ese control exige pertenencia para un ciudadano y esta propuesta permite consultar otras transportistas. Eso no permite editar ni leer sus usuarios, infracciones o viajes.

Los PNC/ITV se leen dentro de la transacción EJB y se convierten allí al DTO; así no dependemos de cargar relaciones lazy desde Facelets. Para flotas grandes, añadir paginación real y consultas de proyección; el ejemplo puede producir consultas adicionales por vehículo y no debe presentarse como solución optimizada bajo carga.

## 5. Bean JSF

Crear `ValidacionPermisosBean.java`:

```java
package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import uy.edu.fing.grupo07.CargaUY.dto.ValidacionEmpresaDTO;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
import uy.edu.fing.grupo07.CargaUY.service.ValidacionPermisosService;

@Named("validacionPermisosBean")
@ViewScoped
public class ValidacionPermisosBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ValidacionPermisosBean.class.getName());

    @EJB private ValidacionPermisosService servicio;
    private Integer numeroEmpresa;
    private ValidacionEmpresaDTO resultado;

    public void consultar() {
        resultado = null;
        try {
            if (numeroEmpresa == null || numeroEmpresa <= 0) {
                mensaje("Ingresá un número de empresa válido");
                return;
            }
            resultado = servicio.consultar(SesionActual.token(), numeroEmpresa);
        } catch (ErrorAcceso e) {
            mensaje(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo validar la empresa " + numeroEmpresa, e);
            mensaje("Consulta temporalmente no disponible. Intentá nuevamente.");
        }
    }

    private void mensaje(String texto) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, "Consulta de permisos", texto));
    }

    public Integer getNumeroEmpresa() { return numeroEmpresa; }
    public void setNumeroEmpresa(Integer numeroEmpresa) { this.numeroEmpresa = numeroEmpresa; }
    public ValidacionEmpresaDTO getResultado() { return resultado; }
}
```

`@ViewScoped` conserva el resultado mientras se trabaja con esa vista. El token se toma de la sesión, nunca de un ID de ciudadano editable en el formulario. Ante errores se elimina el resultado anterior para que no parezca una validación de la nueva empresa.

## 6. Vista Facelets

Crear `web/src/main/webapp/empresa/validar-permisos.xhtml`:

```xml
<ui:composition xmlns="http://www.w3.org/1999/xhtml"
                xmlns:h="jakarta.faces.html"
                xmlns:f="jakarta.faces.core"
                xmlns:ui="jakarta.faces.facelets"
                xmlns:p="http://primefaces.org/ui"
                template="/WEB-INF/templates/template.xhtml">
    <ui:define name="title">Validar permisos de empresa</ui:define>
    <ui:define name="content">
        <h1>Validar permisos de empresa</h1>
        <p>Consulta de habilitación administrativa, PNC e ITV de los vehículos registrados.</p>

        <h:form id="consultaPermisos">
            <p:messages id="mensajes"/>
            <p:outputLabel for="numero" value="Número de empresa"/>
            <p:inputNumber id="numero" value="#{validacionPermisosBean.numeroEmpresa}"
                           decimalPlaces="0" thousandSeparator="" minValue="1"
                           required="true" requiredMessage="Ingresá el número de empresa"/>
            <p:commandButton value="Consultar permisos"
                             action="#{validacionPermisosBean.consultar}"
                             process="@form" update="@form"/>

            <p:outputPanel id="resultado">
                <ui:fragment rendered="#{validacionPermisosBean.resultado ne null}">
                    <h2><h:outputText value="#{validacionPermisosBean.resultado.razonSocial()}"/></h2>
                    <p>Fecha de consulta: <h:outputText value="#{validacionPermisosBean.resultado.fechaConsulta()}"/></p>
                    <p>Estado registral: <h:outputText value="#{validacionPermisosBean.resultado.activa() ? 'ACTIVA' : 'INACTIVA'}"/></p>
                    <p:staticMessage severity="warn" summary="Empresa inactiva"
                                     detail="La vigencia de documentos de vehículos no revierte la baja de la empresa."
                                     rendered="#{not validacionPermisosBean.resultado.activa()}"/>
                    <p>Unidades con documentación vigente y habilitación administrativa:
                        <h:outputText value="#{validacionPermisosBean.resultado.habilitadas()}"/>
                    </p>
                    <p:dataTable value="#{validacionPermisosBean.resultado.unidades()}" var="u"
                                 emptyMessage="Sin vehículos registrados: información insuficiente para evaluar la flota.">
                        <p:column headerText="Matrícula"><h:outputText value="#{u.matricula()}"/></p:column>
                        <p:column headerText="Habilitación administrativa"><h:outputText value="#{u.habilitacionAdministrativa() ? 'ACTIVA' : 'INHABILITADA'}"/></p:column>
                        <p:column headerText="PNC"><h:outputText value="#{u.estadoPnc()}"/></p:column>
                        <p:column headerText="Vencimiento PNC"><h:outputText value="#{u.vencimientoPnc()}"/></p:column>
                        <p:column headerText="ITV"><h:outputText value="#{u.estadoItv()}"/></p:column>
                        <p:column headerText="Vencimiento ITV"><h:outputText value="#{u.vencimientoItv()}"/></p:column>
                        <p:column headerText="Resultado de la unidad"><h:outputText value="#{u.resultado()}"/></p:column>
                    </p:dataTable>
                    <p>El resultado refleja los registros de CargaUY; no constituye un certificado externo.</p>
                </ui:fragment>
            </p:outputPanel>
        </h:form>
    </ui:define>
</ui:composition>
```

Se utilizan métodos explícitos del record, como en otras vistas del proyecto. `h:outputText` escapa el texto recibido. `h:form` y los controles JSF participan del ciclo JSF; no se agrega un servlet para esta consulta.

## 7. Proteger e incorporar la ruta

La ruta propuesta no está bajo `/portal/`, que actualmente permite visitantes anónimos. El filtro ya valida sesión para `/empresa/*` y permite ciudadanos/funcionarios, además de administradores. El servicio propuesto restringe la ejecución a CIUDADANO o ADMINISTRADOR; si se desea permitir funcionarios también, acordarlo y agregar ese rol explícitamente.

En `web/src/main/webapp/WEB-INF/templates/template.xhtml`, incorporar un enlace en la navegación correspondiente a los ciudadanos, siguiendo el estilo del menú existente:

```xml
<p:menuitem value="Validar permisos"
            url="#{request.contextPath}/empresa/validar-permisos.xhtml"
            rendered="#{authBean.esCiudadanoEmpresa()}"/>
```

Ubicarlo dentro del `p:menubar` o `p:submenu` que corresponda, no suelto fuera del menú. Ocultar el enlace no reemplaza la autorización del filtro/servicio.

La pantalla anterior `/portal/validar-empresa.xhtml` debe conservarse como **consulta registral pública** o cambiarse de alcance deliberadamente. No dejarla presentando `empresa.activa` como prueba de autorización vigente. Ajustar sus etiquetas a “estado registral” y “habilitación administrativa” o dirigir al nuevo resultado autenticado. No trasladar toda la lógica a `PortalPublicoBean`.

## 8. Crear el ciudadano en CargaUY

Hay dos partes: el perfil del usuario en PostgreSQL y su identidad en un proveedor. **Crear un ciudadano no le permite entrar con usuario/contraseña por el formulario LDAP**, que está destinado a administradores/autoridades.

### Datos de prueba

Para aprovechar el simulador Keycloak ya preparado por el proyecto:

| Campo | Valor |
|---|---|
| Nombre | Ana Prueba |
| CI | `80000001` |
| Correo | `ana@example.invalid` |
| Nacimiento | `1990-01-01` |
| Rol | CIUDADANO |
| Usuario del simulador | `ciudadano.prueba` |
| Identidad externa del realm inicial | `11111111-1111-4111-8111-111111111111` |

Los valores de identidad corresponden al realm generado por `preparar-local.ps1`. Si el realm existente fue editado o recreado, verificar el ID real en Keycloak antes de vincular: no adivinar el identificador.

### Desde la interfaz administrativa

1. Ingresar por LDAP como `martin.admin` con la contraseña privada existente.
2. Abrir `/CargaUY-web/admin/usuarios.xhtml`.
3. Buscar primero la CI `80000001`. Si existe, consultar y reutilizar el perfil correcto; no intentar duplicarlo.
4. Si no existe, pulsar **Nuevo Usuario** y completar los datos anteriores con tipo CIUDADANO.
5. Guardar y anotar el ID del perfil de CargaUY. La contraseña que pide actualmente ese formulario no es una credencial de gub.uy ni de Keycloak.
6. En la gestión de identidad de ese usuario, elegir origen **GUB** e indicar el identificador externo real del usuario del simulador.
7. Guardar. `IdentidadesService` vincula el proveedor, elimina la credencial local innecesaria y revoca sesiones anteriores.

Revisar los botones que abren identidad/roles en la versión desplegada. Como alternativa administrativa existente, `/app/admin` ofrece la operación `vincularIdentidad`: ID del perfil, origen `GUB` y UID externo del simulador.

**No vincular ese UUID de Keycloak a una cuenta real de ID Uruguay.** Ambos usan el origen genérico GUB en el modelo actual, pero sus sujetos son distintos. No alternar proveedores reutilizando perfiles como si las identidades fueran equivalentes; usar una base de demo o un perfil claramente reservado al simulador.

## 9. Cómo ingresar como ciudadano mientras falla gub.uy

### Opción A: proveedor real

Esperar a resolver `OIDC_ERROR` con el administrador del cliente. Luego vincular la identidad verificada real al perfil con su CI correspondiente. No usar la CI de prueba del simulador para una persona real.

### Opción B: Keycloak local, en un entorno de desarrollo identificado

Esta opción prueba un login OIDC real contra el simulador, **no demuestra integración exitosa con ID Uruguay**. No agrega un bypass ni un login que acepte cualquier contraseña.

1. Conservar `.local-p1` y no volver a ejecutar `preparar-local.ps1`.
2. Hacer una copia privada de `wildfly.properties` antes de cambiar de proveedor; mantener también la copia dentro de la carpeta ignorada.
3. Levantar el servicio `gub` ya declarado en Compose:

   ```powershell
   docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml up -d gub
   ```

4. Abrir `http://localhost:8083`, ingresar a la consola de Keycloak con las credenciales privadas de administrador del simulador y verificar realm `cargauy-test`, usuario `ciudadano.prueba` y cliente `cargauy`.
5. En `.local-p1/wildfly.properties`, configurar:

   ```properties
   gub.proveedor=keycloak
   gub.pkce=true
   gub.issuer=http://localhost:8083/realms/cargauy-test
   gub.client.id=cargauy
   gub.client.secret=SECRETO_DEL_CLIENTE_LOCAL_KEYCLOAK
   gub.redirect=http://localhost:8080/CargaUY-web/gub/callback
   ```

6. El secreto es el del cliente local `cargauy`, no el del cliente real 890192. Recuperarlo desde la configuración privada generada/cliente local. No publicarlo ni pegarlo en la terminal o el chat.
7. Verificar que el cliente permita exactamente ese callback y PKCE S256. Si el realm ya existía, editar `gub-realm.json` no garantiza actualizarlo: confirmar la configuración efectiva en Keycloak.
8. Aplicar con `configurar-wildfly.ps1` indicando la instalación propia y reiniciar WildFly. Mantener la excepción HTTP limitada al desarrollo local (`p1.entorno.local=true`).
9. Cerrar la sesión administrativa de CargaUY. Abrir otra ventana/perfil de navegador y entrar por el botón del proveedor externo.
10. Comprobar que redirige a `localhost:8083`, no a ID Uruguay. Ingresar con `ciudadano.prueba` y su contraseña en `.local-p1/cuentas-locales.txt`.
11. Verificar que CargaUY muestre a Ana Prueba y rol CIUDADANO. Si hay conflicto por CI existente, corregir la vinculación de identidad desde la sesión administrativa; no borrar el usuario ni duplicarlo.

El cliente valida tokens, firma y nonce también en el simulador. La vinculación administrativa previa evita depender del alta automática del primer ingreso.

Al volver al proveedor real, restaurar las propiedades privadas originales, reaplicar y reiniciar. Mantener los datos de simulación separados de los perfiles reales.

### ¿Hay que vincular al ciudadano con la empresa consultada?

Para **esta consulta** no: puede consultar la documentación resumida de otra empresa como cliente potencial. Para editar la empresa, administrar su flota o actuar como responsable sí necesita un vínculo y permisos adicionales. No mezclar la prueba de consulta con la administración.

Si se quiere ensayar también la asociación, crear o consultar una empresa desde la sesión del administrador, vincular al ID ciudadano como responsable y comprobarlo en Empresas y vínculos. No es una precondición de la consulta propuesta.

## 10. Preparar empresas y permisos de prueba

Como administrador, crear o reutilizar una empresa identificable, por ejemplo número libre `9201`, razón social “Transportes Demo Permisos”.

Registrar vehículos desde Vehículos y sus documentos desde ITV/PNC o las pantallas administrativas de flota disponibles. Los IDs de certificados deben ser libres. Usar fechas relativas al día del ensayo:

| Unidad | Habilitación administrativa | PNC | ITV | Resultado esperado |
|---|---|---|---|---|
| A | Activa | Mañana | Mañana | VIGENTE |
| B | Activa | Ayer | Mañana | NO_VIGENTE |
| C | Activa | Sin registro | Mañana | INFORMACION_INCOMPLETA |
| D | Suspendida | Mañana | Mañana | INHABILITADA |
| E | Activa | Hoy | Hoy | VIGENTE durante hoy |

La falta de un documento no se prueba poniendo su vencimiento en null si la BD lo prohíbe: usar un vehículo sin ese registro. Si la interfaz de alta reactiva automáticamente una unidad al completar documentación, verificar el estado final antes de ensayar la unidad D.

Preparar otra empresa sin vehículos y otra inactiva. Para “empresa inexistente”, elegir un número no registrado. Una empresa inactiva puede conservar vehículos con documentos vigentes; el resultado debe mostrar ambos hechos y no presentarla como activa por eso.

## 11. Compilar y publicar

1. Guardar las clases y el XHTML.
2. En Eclipse: F5 → Maven → Update Project.
3. Desde la raíz: `mvn clean verify`. Resolver errores de código/pruebas antes de publicar.
4. Publicar el EAR y reiniciar WildFly si se agregaron beans/clases.
5. Ingresar con el ciudadano y abrir:

   ```text
   http://localhost:8080/CargaUY-web/empresa/validar-permisos.xhtml
   ```

No se requiere migración por los DTO/servicio/vista propuestos, porque reutilizan entidades y tablas existentes. Si se decide agregar autorizaciones propias de Empresa, sí hay que diseñar ese modelo y sus migraciones por separado.

## 12. Pruebas antes de darlo por terminado

- Anónimo: entrar directamente a la URL y comprobar redirección al login, incluso sin usar el menú.
- Ciudadano auténtico: consultar una empresa distinta de sus vínculos.
- Empresa inexistente: mensaje específico y sin resultado de una consulta anterior.
- Flota con los cinco estados anteriores; verificar también el vencimiento exacto de hoy.
- Empresa sin vehículos e inactiva: no afirmar automáticamente una autorización empresarial.
- Historial: PNC antiguo vencido + PNC nuevo vigente debe mostrar vigente.
- Rol no permitido: rechazar la operación en el servicio aunque se invoque sin la interfaz.
- Error técnico: en un entorno de prueba, comprobar mensaje de indisponibilidad, no “empresa inexistente”. No detener la BD de trabajo compartida para hacerlo.
- Cambiar documentación y pulsar Consultar otra vez: el servicio debe calcular un resultado nuevo.

Agregar pruebas del servicio con JUnit/Mockito para esas reglas, especialmente límites de fechas e historial. Las pruebas de navegador deben cubrir el ingreso real del ciudadano y acceso directo sin sesión. Tener código copiado y `BUILD SUCCESS` no sustituye estas comprobaciones funcionales.

## 13. Cómo defenderlo ante el profesor

**¿Qué permiso valida?** PNC e ITV de la flota, junto con habilitación administrativa; el estado registral de la empresa se informa por separado. Es la interpretación acordada para este incremento, no una autorización empresarial nueva.

**¿Por qué EJB?** Centraliza autenticación y regla de negocio, y convierte relaciones JPA al DTO dentro de la transacción.

**¿Por qué un ciudadano puede consultar otra empresa?** El actor es un cliente potencial. Consulta solo un resumen de documentación; no obtiene permisos de administración.

**¿Cómo probaron al ciudadano si gub.uy falla?** Con una cuenta del proveedor OIDC local Keycloak, vinculada a un perfil CIUDADANO. El proveedor real sigue pendiente y no se lo da por probado.

**¿Una empresa activa está habilitada?** Activa significa que no está dada de baja. No sustituye la verificación documental ni certifica por sí misma que toda su flota pueda circular.
