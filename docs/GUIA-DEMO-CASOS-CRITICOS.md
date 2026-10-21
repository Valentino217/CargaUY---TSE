# Demo de los casos de uso críticos de CargaUY

Preparada el 21/10/2026 a partir de [Casos de uso criticos del Drive TSE 2026](https://docs.google.com/document/d/1U1se0UQrkhgl0nyJBUTLnfBs48Lc-BNMeLU2KsyITj8/edit) y del código actual del repositorio.

Esta guía propone qué mostrar, qué resultado comprobar y cómo explicar los pendientes. No se ejecutó una nueva demo completa sobre tu base habitual para preparar este documento. Una función encontrada en el código se marca como implementada, pero necesita ensayo en tu despliegue antes de presentarla como demostrada.

## 1. Recorrido recomendado: 25 a 35 minutos

| Orden | Parte | Tiempo | Qué demuestra |
|---|---|---|---|
| 1 | Login LDAP y contexto | 2 min | Identidad interna y permisos |
| 2 | Empresa, persona con varios roles y vínculo | 4 min | Datos compartidos entre los módulos |
| 3 | PDI disponible, inexistente y caída | 4 min | SOAP y resiliencia |
| 4 | Viaje, evento y monitoreo | 5 min | Registro y consulta de actividad |
| 5 | Sincronización móvil | 5 min | Persistencia local, reenvío y deduplicación; condicionado al ensayo Android |
| 6 | Pesaje y fiscalización | 5 min | Detección automática, evidencia y unicidad |
| 7 | Resolución de caso y pendientes | 5 min | Justificación, estado y auditoría |

El documento desarrolla cinco casos: registrar evento, sincronizar eventos, fiscalización automática, gestión de caso y consulta PDI. Además menciona el monitoreo en su lista inicial: lo mostramos como puente entre los eventos y la fiscalización.

Si disponen de solo 15 minutos, mostrar LDAP → empresa/vínculo → PDI → pesaje → caso/resolución. Reservar el móvil para una segunda parte; aclarar qué casos quedan sin demostración completa.

## 2. Preparación antes de la presentación

### Entorno

1. PostgreSQL iniciado y datasource de WildFly con conexión válida.
2. Migraciones del proyecto aplicadas, incluida `scripts/p1/migracion-03-integracion.sql`. No improvisar una migración durante la presentación.
3. Publicar la versión actual del EAR. El WildFly debe incluir mensajería para `TrackingQueue` y `FiscalizacionQueue`.
4. Iniciar Docker Desktop y, desde la raíz del proyecto, levantar solo LDAP y PDI:

   ```powershell
   docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml up -d --build ldap mock-pdi
   ```

5. Comprobar los contenedores:

   ```powershell
   docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml ps
   ```

6. Abrir `http://localhost:8080/CargaUY-web/login` y ensayar el login LDAP. Usuario preparado habitualmente: `martin.admin`; contraseña en `.local-p1/cuentas-locales.txt`.
7. Tener abiertas las pestañas de Inicio, Empresas, Tracking, Registrar Pesaje e Infracciones.

No regenerar `.local-p1`, no borrar volúmenes y no activar inicializadores de datos para reiniciar la demo. Utilizar registros de demostración identificables y conservar los datos existentes.

### Autenticación que sí se puede presentar

La cuenta personal de testing de gub.uy permite entrar al portal del proveedor, pero el cliente de CargaUY sigue devolviendo `OIDC_ERROR`. Por eso, usar LDAP para la demostración web y explicar el bloqueo del ingreso externo. Entrar como administrador permite recorrer módulos, pero no acredita el login ni la separación funcional del actor chofer, ciudadano o funcionario.

Frase de apertura:

> “Vamos a mostrar los casos críticos porque atraviesan interfaz, servicios, persistencia e integraciones. Usaremos el administrador LDAP para recorrer la aplicación; el ingreso de ciudadanos con gub.uy está integrado en código, pero su autorización en testing sigue bloqueada.”

### Datos para el ensayo

Preparar esta ficha con los IDs reales que devuelve la aplicación:

| Dato | Valor sugerido / completar |
|---|---|
| Empresa | Número libre, por ejemplo 9101; Transporte Demo TSE |
| Persona | Persona Demo; CI de prueba libre; correo ficticio |
| Roles de la persona | CIUDADANO y CHOFER |
| ID del usuario | __________ |
| Vehículo | Matrícula de prueba libre; tara 9.000 kg; capacidad 20.000 kg |
| Habilitaciones | ITV y PNC vigentes para aislar el exceso de carga |
| Guía de viaje | Empresa, vehículo y chofer anteriores; ID: __________ |
| Otra guía | Guía independiente para el caso sin infracción o para desestimar |
| Caso generado | ID: __________ |
| Umbral de carga vigente | __________ kg |

No fijar IDs de usuarios o guías en la exposición. Copiarlos de los resultados. Para repetir, usar otro número o consultar los registros existentes en lugar de volver a crearlos.

## 3. Inicio: LDAP y datos compartidos

### Acciones

1. Ingresar con el administrador LDAP.
2. Mostrar que termina en Inicio.
3. Entrar a **Empresas y vínculos** → **Registrar empresa** y crear la empresa de prueba.
4. Ir a **Registrar usuario**, completar los datos y marcar **CIUDADANO** y **CHOFER**.
5. Anotar el ID devuelto.
6. En **Vincular ciudadano**, buscar la empresa y vincular ese ID como responsable.
7. En **Vincular chofer**, vincular el mismo ID.
8. En **Consultar vínculos**, comprobar que la persona tiene ambas funciones.

### Qué decir

> “Una misma persona puede tener varios roles. Los roles indican qué funciones puede ejercer; el vínculo indica en qué empresa puede ejercerlas. Vincularla como chofer no elimina su función de responsable.”

LDAP valida las credenciales internas. El perfil y los roles de CargaUY están en PostgreSQL. Registrar un usuario en esta pantalla no crea su cuenta en LDAP ni en gub.uy: después se necesita la vinculación de identidad correspondiente para que esa persona pueda ingresar.

## 4. Caso: obtener datos del ciudadano mediante PDI

### Flujo principal

1. En Empresas, abrir **Consultar ciudadano** (`?seccion=pdi`).
2. Consultar CI `12345678`.
3. Mostrar el estado disponible y los datos ficticios de Juan Perez.

Explicar:

> “CargaUY consulta un servicio SOAP sustituto de PDI. Estos datos pertenecen al mock, no a un padrón real. Consultar no da de alta automáticamente una cuenta.”

### Alternativo: ciudadano inexistente

1. Consultar CI `99999999`, que no está en el catálogo actual del mock.
2. Comprobar `NO_ENCONTRADO` y el mensaje de que no existen datos.

El mock actual contiene `12345678`, `80000001` y `80000002`. A diferencia de la versión inicial, ahora existe un Fault SOAP explícito para ciudadano inexistente y el cliente lo interpreta.

### Alternativo: servicio no disponible

1. Detener solamente PDI:

   ```powershell
   docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml stop mock-pdi
   ```

2. Consultar nuevamente `12345678`.
3. Mostrar `NO_DISPONIBLE` y navegar a Empresas o Inicio para comprobar que CargaUY sigue respondiendo.
4. Repetir las consultas: tras tres consultas fallidas se abre el circuito durante diez segundos y se evita seguir llamando al servicio temporalmente.
5. Recuperar PDI:

   ```powershell
   docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml start mock-pdi
   ```

6. Esperar el intervalo de apertura y volver a consultar.

### Qué está y qué resta

Hay timeout, reintento acotado, límite de cuatro llamadas concurrentes, circuito y WS-Addressing en el cliente. La caída y recuperación básicas ya las probaste en esta conversación. Ensayar el escenario inexistente con la imagen reconstruida del mock actual. No afirmar que se acreditó HTTPS completo, carga sostenida o WS-Security por mostrar esta consulta local.

## 5. Caso: registrar evento y monitorear el viaje

### Preparar el viaje

1. Desde Inicio → Vehículos, registrar el vehículo para la empresa de demo.
2. Registrar ITV y PNC vigentes en sus pantallas.
3. Desde Guías, crear una guía con empresa, vehículo y persona con rol CHOFER vinculada a esa empresa.
4. Anotar el ID de guía y verificar origen, destino y estado inicial.

### Opción A: demo móvil completa, solo si se ensayó

1. Abrir Android, configurar la conexión y usar una sesión válida del chofer de demo.
2. Consultar sus guías y seleccionar la preparada.
3. Registrar **Inicio de viaje** y anotar UUID/hora.
4. En la web, abrir **Tracking y Balanzas**, seleccionar esa guía y consultar.
5. Comprobar evento único, fecha, coordenadas y estado `EN_CURSO` después del procesamiento.
6. Mostrar un incidente con descripción. Si se usa foto, explicar el alcance real: el código actual maneja una referencia, no demuestra por sí mismo la subida y recuperación de la imagen.
7. Si da el tiempo, registrar carga o descarga parcial y finalmente FIN. Hacer FIN después de las pruebas que requieren un viaje activo.

### Opción B: demo web del incidente

En `detalleViaje.xhtml`, seleccionar la guía → **Reportar Incidente**, completar descripción y coordenadas → **Guardar Incidente** → consultar la línea temporal.

Esta opción demuestra registro/consulta desde la web, pero **no cubre el actor móvil, el guardado sin conexión ni la captura GPS del teléfono**. La interfaz de prueba de la balanza tampoco acredita una integración periférica real por sí sola.

### Estado y límites

El backend ya tuvo una prueba aislada de INICIO recorriendo REST → `TrackingQueue` → `TrackingMDB` → PostgreSQL, con cambio a `EN_CURSO`. El móvil contiene pantallas de eventos y almacenamiento local, pero no se compiló ni ejecutó el APK en la verificación anterior.

La app tiene un campo temporal **Token de integración (Bearer)**. No es un login final. No pegar un token de administrador y decir que se probó al chofer. Para la demo móvil hace falta una sesión auténtica de ese usuario o un entorno de QA explícito, separado de la base habitual; si no está disponible, presentar esa parte como pendiente.

Además, carga/descarga parcial se describe actualmente en texto dentro del modelo móvil; el DTO de eventos del servidor no tiene campos propios para kg, mercadería y empresa. Falta comprobar y completar esa representación estructurada del caso de uso.

## 6. Caso: sincronizar eventos móviles sin conexión

Esta parte requiere Android funcionando y una sesión válida. Si no se cumplen esas condiciones, mostrar el diseño/código como avance y no simular que se ejecutó.

### Ensayo de flujo principal

1. Con conexión, consultar la guía para que quede en la caché local.
2. Cortar Wi-Fi/datos del emulador o dispositivo y comprobar que perdió acceso al backend.
3. Registrar un incidente. Mostrar el contador de pendientes y su UUID.
4. Salir de la pantalla y volver a entrar para comprobar que el evento está guardado localmente. Ensayar también reiniciar la app; el token temporal puede requerir volver a cargar una sesión válida.
5. Recuperar la conexión.
6. Observar la sincronización automática mientras la app está activa o pulsar **Sincronizar**.
7. Verificar que disminuyen los pendientes y que el evento aparece en Tracking web, una sola vez.

### Duplicado

Reenviar exactamente el mismo UUID después de comprobar que ya se persistió. Verificar respuesta de duplicado y una sola fila de evento. No generar un UUID nuevo: eso sería otro evento.

### Modificación de la guía mientras el móvil está offline

1. Con una guía cacheada, desconectar Android.
2. Modificar origen/destino de esa guía desde la web, si el estado permite editarla.
3. Recuperar conexión y sincronizar.
4. Comparar los datos locales con la nueva guía del servidor.

El código móvil compara versión/origen/destino y renueva la caché. Eso es una actualización posterior a la sincronización; no prueba una política completa del servidor para resolver eventos que fueron generados contra una versión antigua. El DTO de evento no envía una versión de guía que permita verificar ese conflicto.

### Pendiente relevante: aceptación y persistencia

El endpoint puede devolver `ACCEPTED` cuando encola el evento en JMS. El móvil lo marca sincronizado con esa respuesta. Todavía hay que asegurar y demostrar qué ocurre si falla el consumidor después de esa aceptación: **encolado no equivale a persistido**. La demo debe consultar Tracking después del envío y comprobar el resultado final.

## 7. Caso: fiscalización automática

### Escenario principal: exceso de carga

1. Verificar la tolerancia de carga vigente desde Administración (`/app/admin`, operación `consultarRegla`). No confiar en el valor por defecto si ya existe una regla.
2. Elegir una guía y vehículo de demo sin infracción previa por exceso de carga.
3. Abrir **Registrar Pesaje** (`registrarPesaje.xhtml`).
4. Seleccionar la guía y completar peso, fecha y hora.
5. Para mostrar la cadena asíncrona, desmarcar **Evaluar infracciones de inmediato**. Registrar la pesada, esperar el procesamiento y consultar Infracciones.
6. Si se ensayó el modo inmediato, puede usarse para explicar la regla en pantalla. Aclarar que en ese modo la pantalla también invoca el motor directamente, además del flujo de mensajería del registro.
7. Abrir el caso y mostrar peso, tara, capacidad y tolerancia en la evidencia.

Ejemplo con los datos propuestos y tolerancia de 500 kg:

```text
Tara:                    9.000 kg
Capacidad de carga:     20.000 kg
Máximo del vehículo:    29.000 kg
Tolerancia:                500 kg
Pesada de demo:         30.000 kg
Exceso calculado:        1.000 kg → supera la tolerancia
```

La comparación usa `exceso > tolerancia`: exactamente 29.500 kg no supera una tolerancia de 500 kg. No confundir capacidad de carga con peso bruto total.

### Alternativo: sin incumplimiento

Usar otra guía/vehículo, sin pesadas anteriores excesivas y con habilitaciones vigentes. Registrar un peso dentro del límite y comprobar que no aparece un caso nuevo. El motor consulta la pesada más alta de la guía: una pesada liviana posterior no borra un exceso anterior.

### Alternativo: evitar un caso repetido

Reevaluar una guía que ya tenga el caso generado y comprobar que no aumenta el número de casos de ese tipo para esa guía. El código evita duplicados por guía/tipo; falta acordar si esa granularidad satisface todos los escenarios del documento, que habla de la misma información/infracción.

### Qué no presentar como terminado

- El motor actual evalúa exceso de carga y habilitación del vehículo, incluyendo ITV/PNC ausentes o vencidos.
- `evaluarGuia` no ejecuta una regla de desvío de recorrido ni una de actividad sin viaje asignado. Tener un campo de umbral de tracking no demuestra esas detecciones.
- La generación del caso no envía automáticamente una notificación a la empresa. La operación `notificarInfraccion` cambia el estado a NOTIFICADA; falta acreditar un canal de entrega y automatizar el include del CU.
- No se ensayó aquí el fallo de dependencias durante la fiscalización ni sus reintentos/recuperación. No confundir la resiliencia de PDI con la del motor.

## 8. Caso: gestión de caso de fiscalización

### Flujo de resolución

1. Desde Inicio → **Fiscalización e Infracciones**, localizar el ID generado.
2. Abrir el detalle y mostrar tipo, estado, guía y evidencia.
3. Si se recorrerá descargo, ejecutar **Notificar** para pasar desde DETECTADA al estado que lo admite. Explicar que es una transición interna, no una entrega real acreditada.
4. Mostrar un descargo previamente preparado o registrar uno en el ensayo. Si se usa la misma sesión administrativa, decir que se prueba la operación y no el recorrido independiente del representante de empresa.
5. Intentar resolver sin fundamento: debe rechazarlo y mantener el estado.
6. Escribir una justificación concreta, por ejemplo: “Se confirma porque la pesada registrada supera el máximo del vehículo y la tolerancia vigente”.
7. Confirmar y comprobar el estado y los datos de auditoría.
8. En un segundo caso, repetir con **Desestimar** y justificar la decisión.

### Cancelar

Abrir la resolución y cancelar antes de enviarla, o volver a la bandeja sin confirmar. Consultar nuevamente y comprobar que el estado no cambió. Ajustar el paso al control que realmente tenga la pantalla; no afirmar que existe un botón específico si el ensayo no lo muestra.

### Auditoría

Mostrar usuario real que resolvió, acción, fundamento y marca de tiempo. El código contiene un instalador de trigger PostgreSQL para impedir UPDATE/DELETE de `registro_auditoria`, pero su fallo se registra sin bloquear necesariamente el despliegue.

Por eso, antes de afirmar “auditoría inmutable”, verificar que el trigger está instalado y hacer una prueba de rechazo en una base de demo con una cuenta de aplicación sin privilegios de propietario/superusuario. No modificar ni borrar auditoría de la base habitual durante la presentación.

Pendientes: validar el recorrido con funcionario auténtico, el de descargo con representante auténtico y revisar la autorización por caso/empresa en los servicios heredados. El acceso del administrador no sustituye esas pruebas.

## 9. Matriz de cierre: lo mostrado y lo pendiente

| Caso | Base implementada | Condición para decir que quedó demostrado | Pendiente principal |
|---|---|---|---|
| Registrar evento | REST, JMS, persistencia y pantallas; código Android | Evento visible una sola vez y cambio de estado observado | Login móvil, APK, foto real y datos estructurados de carga parcial |
| Sincronizar offline | SQLite, pendientes, UUID, reenvío y caché de guías | Corte/recuperación real y verificación final en servidor | Conflicto de versión y confirmación durable después de JMS |
| Fiscalización automática | Exceso y habilitaciones; evidencia y deduplicación | Pesada → caso observado en el despliegue de demo | Desvío, actividad sin viaje y notificación automática real |
| Gestionar caso | Bandeja, descargo, resolución y auditoría | Resolver con actor real y fundamento, comprobar estado/registro | Autorización integral y verificar protección de auditoría en la BD desplegada |
| PDI | SOAP, WS-Addressing, inexistente, timeout y circuito | Mostrar éxito, inexistente, caída y recuperación | Validación de TLS/carga y alcance del opcional WS-* |
| Monitoreo transversal | Tracking, pesadas, consulta de guía y dashboard | Mostrar el mismo ID desde evento/pesada hasta el caso | Validar actualización y permisos con cada actor |

## 10. Frases para cerrar y preguntas del profesor

**Cierre sugerido:**

> “La base integrada permite relacionar empresa, chofer, vehículo y guía; consultar PDI con degradación controlada; registrar actividad y generar casos por exceso de carga o habilitación. Los principales pendientes son completar el acceso externo y móvil, la resolución robusta de conflictos offline, las reglas de desvío y actividad sin viaje, y la notificación efectiva. Lo que mostramos desde el administrador no lo contamos como validación de todos los actores.”

| Pregunta | Respuesta que pueden sostener |
|---|---|
| ¿Por qué JMS? | Separa la recepción del procesamiento. La respuesta de aceptación debe distinguirse de la confirmación final de persistencia. |
| ¿Cómo evitan duplicados? | UUID para eventos y controles de unicidad en persistencia; casos actualmente por guía/tipo. Ensayar reenvío y concurrencia por separado. |
| ¿PDI es real? | No: el servicio SOAP es un sustituto local con catálogo de prueba. |
| ¿LDAP decide los permisos? | No. LDAP valida credenciales internas; CargaUY consulta perfil, roles y estado en PostgreSQL. |
| ¿gub.uy funciona? | La cuenta personal de testing funciona; la autorización del cliente de CargaUY sigue devolviendo OIDC_ERROR. |
| ¿Está todo en JSF? | Las vistas activas son Facelets; las de plataforma conservan formularios HTML coordinados por servlets. Los módulos heredados usan también backing beans JSF. |
| ¿Las pruebas prueban toda la demo? | No. La última verificación tuvo 194 pruebas aprobadas; hubo comprobaciones aisladas de despliegue y renderizado. El ensayo con los actores y datos reales del entorno sigue siendo necesario. |

## 11. Evidencia que conviene guardar en el ensayo

- Fecha, rama/commit y versión desplegada.
- IDs de empresa, usuario, guía, UUID de evento y caso para seguir un mismo recorrido.
- Capturas de PDI disponible/inexistente/no disponible; restaurar el mock al terminar.
- Antes/después del contador móvil y la consulta final del servidor.
- Pesada y evidencia del caso, fundamento y auditoría.
- Lista final: demostrado en vivo / implementado sin ensayo completo / pendiente o bloqueado.

No incluir contraseñas, bearer tokens, secretos OIDC ni capturas de `.local-p1` en las evidencias compartidas.

## Referencias del repositorio

- `docs/VERIFICACION-INTEGRACION.md`: prueba aislada previa, alcance y límites.
- `docs/INTEGRACION-MARTIN-VALENTINO.md`: preparación del entorno integrado.
- `docs/JSF-FACELETS.md`: vistas activas y publicación.
- `ejb/.../fiscalizacion/MotorFiscalizacionImpl.java`: reglas que efectivamente ejecuta el motor.
- `web/.../rest/ChoferResource.java` y `ejb/.../jms/TrackingMDB.java`: recepción y procesamiento de eventos.
- `CargaUYAndroid/.../SyncRepository.kt`: persistencia/sincronización móvil y actualización de caché.
- `docker/mock-pdi/mock_pdi.py` y `ejb/.../integration/ClientePdi.java`: contrato y resiliencia PDI actuales.
