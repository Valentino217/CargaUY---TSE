Documento de Arquitectura de Software — carga.uy
Grupo 07 · Taller de Sistemas Empresariales / Taller de Sistemas de Información Java EE · Laboratorio 2026
Integrantes: Valentino Torres, Martin Gallardo, Maicol Cosentino y Manuel Felix.
Revisión: 9 de octubre de 2026.
Estado: borrador revisado para validación del equipo.

Esta versión reorganiza y corrige el texto del SAD original según la letra disponible en el Drive e incorpora el alcance de los opcionales 6, 7, 8, 9 y 10. No acredita funcionalidades implementadas ni resultados de pruebas. Las decisiones nuevas figuran como propuestas; el grupo debe elegirlas, comprenderlas y justificar sus alternativas antes de presentar la versión definitiva.
Los diagramas originales estaban incrustados como imágenes y no se verificaron visualmente. Las relaciones descritas aquí sirven como especificación para actualizarlos; no sustituyen esa revisión.

1. Introducción y alcance
La plataforma carga.uy brinda soporte a la gestión, supervisión y fiscalización del transporte de carga en rutas nacionales. Centraliza empresas, vehículos, permisos, guías, eventos y casos de fiscalización, y ofrece canales diferenciados para ciudadanos, choferes, funcionarios, autoridades, administradores y público general.
Este documento describe la vista conceptual, casos de uso, restricciones, atributos de calidad, estructura lógica, despliegue, implementación y decisiones arquitectónicas.
Se incluyen todos los requisitos obligatorios y los opcionales seleccionados: 6 (FaaS), 7 (firma digital de actas), 8 (LDAP), 9 (WS-* en PDI) y 10 (NoSQL para posiciones). Cada uno vale un punto: total seleccionado, cinco puntos. La letra exige al menos cuatro para Ingeniería y dos para Tecnólogo.
Los opcionales restantes se analizan en el anexo como alternativas; no forman parte del compromiso de esta versión.
Fuentes: letra del laboratorio 2026 y SAD original del grupo. La letra prevalece ante contradicciones de alcance; las elecciones de implementación requieren validación del equipo.

2. Vista conceptual y modelo de dominio
Los ciudadanos responsables gestionan los datos operativos de su empresa; los choferes registran eventos móviles; los funcionarios gestionan empresas y resuelven casos; las autoridades consultan indicadores; los administradores gestionan cuentas, roles, nodos y parámetros.
Conceptos y relaciones que deben reflejarse en el modelo:
• Usuario: identidad local, estado y roles. Se vincula con una identidad externa gub.uy o con una cuenta interna LDAP.
• Ciudadano: CI y correo; datos adicionales obtenidos de PDI. La primera autenticación debe completar CI y correo.
• Empresa: nombre público, razón social, número de empresa y dirección principal; rubro según el modelo acordado.
• Asociación Ciudadano–Empresa: vínculo y función de responsable o chofer. La cardinalidad debe quedar explícita en el modelo del grupo.
• Vehículo: empresa, matrícula, marca, modelo, peso propio, capacidad de carga, Permiso Nacional de Circulación y vencimiento de ITV.
• Guía/Viaje: rubro del cliente, volumen de carga, fecha, origen y destino; vehículo y chofer asignados.
• Evento móvil: UUID generado en el dispositivo, viaje, tipo, fecha del evento, ubicación y datos específicos; foto opcional en incidentes.
• Posición GPS: identificador estable, vehículo, fecha del evento, coordenadas y fecha de recepción; vínculo a viaje cuando pueda determinarse.
• Pesaje: vehículo, fecha, balanza y peso medido; referencia estable para trazabilidad.
• Caso de fiscalización: causa, evidencia, empresa, estado y referencias al hecho que lo originó.
• Descargo: caso, empresa, contenido y fecha.
• Registro de auditoría: actor humano o identidad del sistema, acción, fundamento, fecha e identificador de correlación.
• Regla de fiscalización: parámetros vigentes, unidades, versión y fecha de actualización.
• Acta firmada: caso, contenido emitido, documento firmado y metadatos necesarios para verificarlo.
• Nodo periférico: identificación, tipo, estado y configuración de integración.
Los rubros y tipos de carga son datos precargados y no requieren ABM según la sección específica de backoffice de la letra. Las restricciones únicas y relaciones definitivas deben documentarse junto con el modelo de datos.

3. Funcionalidades y autorización
Frontoffice público: autenticación gub.uy, validación de permisos de empresas y listados de información pública. Los datos operativos publicados se agregan con un umbral definido y justificado para impedir identificación individual.
Ciudadano responsable: consultar y actualizar su empresa; ABM de vehículos; registrar guías y asignar vehículo y chofer; consultar casos y presentar descargos dentro del plazo establecido.
Chofer: autenticación gub.uy, consulta de guía asignada, registro de inicio, fin, carga parcial, descarga parcial e incidente; operación offline y sincronización.
Funcionario: autenticación gub.uy; gestión de empresas y asociación de usuarios; consulta de vehículos y permisos; monitoreo de viajes y pesajes; análisis y resolución de casos con fundamento.
Autoridad: autenticación interna LDAP; dashboard con empresas y vehículos habilitados, viajes por período, volumen por rubro, infracciones por tipo y proporción de confirmadas sobre detectadas; filtros por carga, ubicación y período; exportación en al menos dos formatos.
Administrador: autenticación interna LDAP; alta, baja, modificación y consulta de usuarios y roles; gestión de nodos; edición de tolerancias sin redespliegue.

Matriz de permisos mínima:
• Público: únicamente operaciones expresamente públicas.
• Responsable: operaciones autorizadas sobre su propia empresa.
• Chofer: guías asignadas y eventos correspondientes.
• Funcionario: operaciones de gestión y fiscalización definidas para ese rol.
• Autoridad: consultas y reportes autorizados.
• Administrador: administración de usuarios, roles, nodos y reglas.
Los servicios de negocio comprueban rol, estado del usuario y pertenencia/asignación cuando corresponda. Ocultar botones o proteger rutas no sustituye autorización.
Pendiente: completar una matriz operación–rol–restricción y el comportamiento de usuarios con múltiples roles. No se permite autoasignarse privilegios administrativos mediante registro público.

4. Casos de uso y reglas operativas críticas
CU-01 — Autenticarse.
El canal redirige al simulador gub.uy o utiliza el acceso interno LDAP. El central valida el resultado, vincula la identidad con el usuario local y comprueba su estado. En el primer acceso del ciudadano solicita o valida CI y correo. La autenticación no asigna privilegios por datos enviados libremente por el cliente.
Se rechazan credenciales inválidas y usuarios inactivos. Una falla del proveedor genera un error controlado. Pendiente: protocolo del simulador, duración de accesos y política de cierre de sesión.

CU-02 — Registrar y sincronizar eventos móviles.
El móvil persiste el evento con UUID antes de enviarlo. El servidor comprueba usuario, asignación y transición; registra de manera idempotente y devuelve confirmación.
Si el UUID ya fue procesado correctamente, devuelve confirmación del procesamiento previo para que el móvil lo marque como sincronizado. Un reintento de FIN debe confirmarse aunque el viaje ya esté finalizado. Un UUID repetido con contenido diferente genera conflicto.
Estados de viaje propuestos, conservando el SAD: SIN_INICIAR, EN_CURSO y FINALIZADO. INICIO pasa a EN_CURSO; FIN pasa a FINALIZADO; carga, descarga e incidente requieren EN_CURSO. Un evento nuevo incompatible se rechaza de forma trazable.
La deduplicación se distingue de la validación de transiciones. Pendiente: política para eventos recibidos fuera de orden y sus dependencias.
La guía se modifica mientras esté SIN_INICIAR y se bloquea después de aceptar INICIO. Si cambia antes de recibir un INICIO offline, se propone detectar la versión utilizada por el móvil y devolver un conflicto explícito; la política definitiva debe aprobarla el equipo y probarse.

CU-03 — Fiscalización automática.
Tracking reporta posiciones mediante mensajería asincrónica. Para Balanzas, el central solicita y recibe pesadas mediante request-response. La recepción se desacopla de la evaluación de reglas.
El procesador identifica el vehículo y busca un viaje en curso, pero no exige que exista para evaluar actividad sin guía.
Reglas mínimas:
• Exceso de carga: comparar peso medido con peso propio más capacidad de carga y aplicar la tolerancia configurable definida por el grupo.
• Permiso o ITV vencidos: evaluar vigencia a la fecha de la actividad/viaje.
• Viaje sin guía: detectar tracking o pesaje de un vehículo sin viaje asignado y en curso.
• Desvío: comparar posiciones con el recorrido esperado mediante un criterio documentado.
La propuesta de cinco puntos consecutivos fuera del umbral requiere definir orden por fecha del evento, distancia al recorrido, tratamiento de puntos tardíos y unidades. El criterio definitivo debe justificarse.
Antes de crear un caso se aplica una clave estable del hecho: la deduplicación del evento por sí sola no garantiza que dos evidencias del mismo hecho no generen dos casos.
Si faltan datos externos, se aplica ADR-003 y se registra evaluación pendiente o degradada, sin bloquear nuevas recepciones.

CU-04 — Tramitar un caso.
Estados exigidos: DETECTADA, NOTIFICADA, EN_DESCARGO, CONFIRMADA y DESESTIMADA.
Propuesta a validar: DETECTADA → NOTIFICADA al registrar la notificación; NOTIFICADA → EN_DESCARGO al recibir descargo; NOTIFICADA o EN_DESCARGO → CONFIRMADA/DESESTIMADA cuando se cumplan las condiciones de resolución. No se permiten cambios arbitrarios ni reaperturas implícitas.
Pendiente: plazo de descargo, resolución sin descargo, condiciones temporales y manejo de fallas de notificación.
El funcionario consulta evidencia y descargo, fundamenta y confirma la resolución. La actualización del caso y su registro de auditoría deben mantener consistencia transaccional. Estados terminales no se modifican desde operaciones ordinarias.
La notificación obligatoria es a la empresa. Notificar además al chofer es alcance adicional si el equipo lo mantiene.

CU-05 — Consultar PDI.
El central consulta por CI un servicio sustituto SOAP. Se distinguen éxito, ciudadano inexistente y servicio temporalmente indisponible.
Ante falla, informa indisponibilidad y conserva operativa la plataforma; no inventa datos. Se implementan timeouts, reintentos limitados y corte ante fallas sostenidas. Las opciones de caché o reconsulta quedan pendientes de definición.

CU-06 — Editar reglas.
Un administrador modifica tolerancias, valida rangos y unidades y guarda los parámetros. Los procesadores deben observar la actualización sin redespliegue. Pendiente: propagación, vigencia y manejo de versiones.

CU-07 — Emitir y verificar un acta.
Cuando corresponda según la resolución y reglas acordadas, el sistema genera el acta, firma el documento y conserva el resultado. La verificación comprueba integridad y firma; una alteración debe detectarse. Pendiente: formato, firmante y momento exacto de emisión.

5. Restricciones y atributos de calidad
Seguridad: contraseñas protegidas con hash y salt donde se almacenen; HTTPS en integraciones externas; secretos fuera del repositorio; autorización en servicios; auditoría de solo agregado.
Privacidad: definir y justificar el umbral mínimo de agregación para información pública. Separar consultas de permisos expresamente requeridas de exposición de datos operativos privados.
Disponibilidad: caída de PDI o Balanzas no debe causar caída del central. Definir timeout, cantidad y espera de reintentos, umbral y recuperación del mecanismo de corte y respuesta degradada.
Escalabilidad: soportar varias instancias web/aplicación. Evitar estado operativo únicamente local. Si se usan sesiones, definir su almacenamiento o mecanismo compatible con múltiples instancias.
Performance: p95 de consultas del frontoffice menor a 500 ms; sostener el volumen de tracking definido sin pérdida y sin degradación progresiva.
Capacidad: documentar N vehículos y frecuencia f reportes por segundo; volumen normal N×f y pico mínimo 3×N×f. Pendiente: valores y justificación.
Mensajería: definir distribución/particionamiento, política de entrega, confirmación, reintentos, mensajes fallidos y respuesta a saturación. No asumir orden de llegada como orden del recorrido.
Pruebas: objetivo aproximado de 80 % de cobertura de lógica y al menos una prueba por servicio; incluir autorización, transiciones, idempotencia y fallas externas.
Operación: todos los componentes deben distribuirse contenerizados; entorno local completo con un comando documentado; pipeline de compilación, pruebas y análisis estático; pipeline exitoso en la entrega.
El central se despliega en ANTEL Elastic Cloud y los periféricos en otras infraestructuras gratuitas compatibles con la letra.

6. Vista lógica revisada
Presentación: interfaces web JSF/PrimeFaces según la decisión del SAD original y aplicación móvil. Si el equipo elige JSP debe revisar explícitamente esta decisión.
Servicios REST: operaciones requeridas por el móvil, autenticación/identidad según el contrato acordado y filtro de acceso. Las interfaces web pueden llamar a negocio directamente, conservando los controles de autorización.
Negocio: Autenticación; Gestión de Perfiles; Gestión de Plataforma; Vehículos; Viajes; Eventos; Fiscalización; Reportes; Emisión de Actas.
Integración: adaptador gub.uy, adaptador LDAP, cliente SOAP PDI con WS-*, cliente request-response Balanzas, receptor de tracking y adaptador al entorno FaaS.
Datos: JPA/EntityManager para PostgreSQL; repositorio de posiciones MongoDB; repositorio de documentos firmados según decisión pendiente.
Procesamiento: recepción asincrónica de tracking y ejecución de procesamiento real en funciones FaaS. El alcance exacto entre función y central se establece en ADR-001.
Fiscalización mantiene la consistencia de casos y auditoría en la base transaccional. La función no debe eludir esa lógica mediante escrituras arbitrarias.
Cada componente expone contratos y errores explícitos. Pendiente: DTO, endpoints, WSDL, mensajes, versiones y esquema de correlación.

7. Vista de despliegue e implementación
Componentes previstos:
• Central WildFly: web, REST, EJB, JPA y servicios de negocio.
• PostgreSQL: datos transaccionales, reglas, casos y auditoría.
• MongoDB: posiciones GPS.
• Artemis: mensajería prevista en el SAD original, sujeta a integración FaaS.
• Entorno FaaS y mecanismo de invocación: propuesta pendiente de proveedor o runtime.
• LDAP: autenticación interna, con configuración y datos iniciales reproducibles.
• Simuladores gub.uy y PDI; PDI expone SOAP y WS-* elegido.
• Simuladores periféricos Balanzas y Tracking.
• Instrumentación de logs, métricas, salud y trazas.
• Almacenamiento de actas y configuración de firma.

Relaciones para el diagrama actualizado:
Web/móvil → central; central → gub.uy/LDAP; central → PDI por SOAP/HTTPS; central → Balanzas por request-response/HTTPS; Tracking → mensajería → procesamiento FaaS; procesamiento → persistencia/servicios autorizados; central → PostgreSQL/MongoDB y repositorio de actas.
No todas las relaciones pendientes quedan implementadas por este esquema: el grupo debe decidir cómo la función se conecta a Artemis y dónde termina su responsabilidad.
Describir puertos, redes, persistencia, certificados, configuración, copias de seguridad y recuperación; distinguir entorno local y despliegue final.
El ambiente local debe incluir contenedores o equivalentes contenerizados para las funciones. Si se usa FaaS gestionado, explicar la compatibilidad de su empaquetado con la restricción de contenerización y validarla con el docente.
Los filtros web no sustituyen permisos de negocio. Pendiente: mecanismo concreto de tokens/sesiones, expiración, renovación y efecto de deshabilitar una cuenta.

8. Observabilidad, pruebas y validación
Logs estructurados: fecha, componente, nivel, operación e identificador de correlación; evitar contraseñas, tokens y datos personales innecesarios.
Métricas: eventos recibidos/procesados/fallidos/duplicados, latencia, cola pendiente, evaluaciones pendientes, errores externos, duración de funciones y latencias de consultas.
Salud: comprobaciones de estado y dependencias, distinguiendo falla parcial de indisponibilidad del central.
Trazado obligatorio: un flujo móvil → central → nodo periférico, por ejemplo una consulta operativa que requiera Balanzas; el equipo debe elegir el flujo y preservar la correlación.

Escenarios mínimos:
• Acceso correcto, inválido, usuario inactivo y rol insuficiente; acceso a otra empresa rechazado.
• Primer acceso ciudadano y asociación a empresa.
• Duplicados de eventos móviles y de posiciones; duplicado de FIN confirmado; UUID reutilizado con otro contenido.
• Transiciones inválidas de viaje y caso rechazadas; sincronización con guía modificada.
• Evidencia duplicada o reprocesada sin casos adicionales.
• PDI inexistente, sin respuesta y recuperación; Balanzas indisponible; central sigue operativo.
• Reglas actualizadas y aplicadas sin redespliegue.
• LDAP correcto/incorrecto/indisponible; mapeo de roles.
• Mensajes WS-* válidos e inválidos según política.
• Acta original verifica y alterada falla; falla de firma manejada sin declarar acta firmada.
• FaaS procesa tracking real, tolera reintentos y respeta el orden lógico.
• MongoDB conserva posiciones y permite las consultas requeridas.
• Auditoría no editable ni eliminable desde la aplicación y permisos de base restringidos.

Prueba de carga: volumen normal, pico 3×, prueba sostenida y búsqueda del punto de quiebre. Registrar p95, throughput, pérdidas, duplicados, cola, errores y uso de recursos. Los resultados y cuellos de botella deben apoyarse en evidencia observada.
CI: compilar, probar y analizar calidad en cada modificación. No afirmar cobertura ni objetivos alcanzados antes de medirlos.

9. Registro de decisiones de arquitectura
Se conservan ocho registros, dentro del rango de cinco a ocho exigido. Las revisiones nuevas son propuestas para aprobación del grupo; completar evidencia y consecuencias observadas al implementar.

ADR-001 — Mensajería y procesamiento FaaS de tracking.
Contexto: recibir posiciones sin bloquear y ejecutar procesamiento en funciones por el opcional 6.
Base original: Artemis integrado en WildFly y MDB.
Propuesta: conservar recepción asincrónica e integrar un ejecutor FaaS; un MDB puede actuar como adaptador si el entorno elegido lo requiere. La función debe realizar procesamiento real, no solo reenvío.
Alternativas: adaptar Artemis; revisar el broker/disparador; mantener todo en MDB (no satisface por sí mismo el opcional 6).
Consecuencias: más despliegue y observabilidad, reintentos e idempotencia entre componentes.
Pendiente: runtime, conectividad, distribución, entrega, saturación y frontera función/central.

ADR-002 — Persistencia híbrida.
Base: PostgreSQL para transacciones y MongoDB para posiciones, cumpliendo opcional 10.
Alternativas: solo PostgreSQL; solo NoSQL; almacenamiento híbrido.
Justificación a validar: separar cargas y aprovechar estructuras de consulta de posiciones. No afirmar que PostgreSQL se saturaría sin mediciones.
Consecuencias: no asumir transacción atómica entre ambas bases; definir recuperación y deduplicación.
Pendiente: índices, retención, esquema y estrategia de consistencia.

ADR-003 — Resiliencia externa.
Base: adaptadores con timeouts y degradación.
Ampliación: reintentos limitados, corte ante fallas sostenidas y recuperación; PDI informa indisponibilidad, Balanzas conserva evaluaciones pendientes según política.
Alternativas: llamadas bloqueantes sin límites; mecanismos de resiliencia.
Consecuencias: resultados temporalmente incompletos y necesidad de reconsulta trazable.
Pendiente: valores y pruebas.

ADR-004 — Offline e idempotencia móvil.
Base: persistencia local y UUID en origen.
Ampliación: confirmación de duplicados, guía versionada o mecanismo equivalente y resolución explícita de conflictos.
Alternativas: exigir conexión; sincronización sin identidad estable; persistencia local con identidad y política de conflictos.
Consecuencias: se distinguen eventos confirmados, pendientes y rechazados.
Pendiente: política de llegada fuera de orden y cambios de guía.

ADR-005 — Auditoría y firma de actas.
Base: auditoría append-only y permisos de BD que impiden modificación y borrado.
Ampliación: creación, transiciones y resolución auditadas; documento de acta firmado y verificable por opcional 7.
Alternativas: sobrescribir estado sin historial; auditoría append-only; firma digital para integridad del documento.
Auditoría y firma cumplen funciones distintas y ninguna reemplaza a la otra.
Pendiente: formato, certificado, firmante, custodia y emisión.

ADR-006 — Simuladores y pruebas reproducibles.
Base: periféricos independientes contenerizados.
Ampliación: simular gub.uy, PDI, Balanzas y Tracking; reproducir latencias, errores y fallas sostenidas.
Alternativas: mocks internos rígidos; servicios independientes.
Consecuencias: contratos verificables y entorno más amplio.
Pendiente: responsables, datos iniciales y escenarios.

ADR-007 — Identidad y autorización.
Propuesta: gub.uy simulado para ciudadanos, choferes y funcionarios; LDAP para administradores y autoridades; identidad local y autorización en negocio.
Alternativas: login interno solo en BD (no cubre opcional 8); LDAP para acceso interno; extender LDAP a actores cuya autenticación debe ser gub.uy (no respeta la separación exigida).
Consecuencias: mapear identidades y administrar cuentas sin duplicar contraseñas innecesariamente.
Pendiente: protocolo gub.uy, directorio, gestión de roles y sesiones/tokens.

ADR-008 — SOAP y WS-* en PDI.
Base obligatoria: servicio sustituto SOAP.
Propuesta opcional 9: incorporar WS-Addressing o WS-Security con alcance verificable en cliente y simulador.
Alternativas: SOAP sin extensiones (solo obligatorio); Addressing; Security.
Consecuencias: contratos y configuración adicionales; certificados si se elige seguridad basada en ellos.
Pendiente: estándar elegido, políticas y pruebas negativas.

10. Plan de implementación y pendientes
Orden sugerido:
1. Resolver arquitectura de FaaS y acceso LDAP; confirmar JSF y contratos de identidad.
2. Implementar un login interno completo, pantalla protegida, logout y permisos en negocio.
3. Gestión de usuarios, roles, empresas y asociaciones; integración gub.uy.
4. PDI SOAP y WS-* con fallas controladas.
5. Tracking asincrónico, funciones, MongoDB y reglas idempotentes.
6. Ciclo de fiscalización, descargos, notificaciones y auditoría.
7. Actas firmadas y verificación.
8. Reportes, observabilidad, performance y entrega reproducible.
Este orden no reemplaza el reparto del grupo. Asignar responsables para LDAP, simulador gub.uy, mock PDI, nodos, FaaS y actas.
Pendientes prioritarios: runtime FaaS; puente de mensajería; directorio y roles; mecanismo de tokens/sesiones; criterio geográfico; tolerancias y unidades; plazos y transiciones de caso; firma; umbral de agregación; parámetros de resiliencia; volumen de carga; formato de contratos; actualización de diagramas.
El login inicial puede ser un prototipo progresivo, pero el acceso interno final debe usar LDAP para acreditar el opcional 8.

Anexo A. Opcionales seleccionados: trabajo y dificultad
Las dificultades son estimaciones, no resultados medidos.
6 — FaaS (1 punto): alta. Procesamiento efectivo de tracking en funciones; integración con mensajería, persistencia, reintentos, orden y entorno reproducible. Mayor cambio respecto al SAD.
7 — Firma digital (1 punto): media-alta. Generar acta, firmar, custodiar claves y verificar integridad. Imagen de firma o hash aislado no bastan. Validar formato y certificados de prueba con el docente.
8 — LDAP (1 punto): media. Directorio, cuentas, roles, integración central y respuesta a fallas. Cambia el login interno previsto con contraseña local.
9 — WS-* en PDI (1 punto): media; mayor con firma/cifrado. Elegir y demostrar una extensión SOAP en ambos extremos.
10 — NoSQL para posiciones (1 punto): media. Ya previsto con MongoDB; concretar documentos, índices, deduplicación, retención y coordinación transaccional.
Evaluación del conjunto: cinco puntos, pero el opcional 6 concentra el riesgo de integración. Revisarlo antes de comprometer la implementación.

Anexo B. Otros opcionales: alcance y dificultad
1 — Asistente para funcionarios (2 puntos, alta): consultas sobre fiscalización y normativa con fuentes; modelo local o servicio sin costo; controles de acceso y evaluación con casos de prueba, aciertos y errores. Sin evaluación no se otorga puntaje.
2 — Anomalías (2 puntos, alta): patrones adicionales a las reglas mínimas, datos y método justificado, generación de casos y evaluación de falsos positivos.
3 — Separación escritura/lectura para histórico (2 puntos, alta): CQRS, event sourcing u otro enfoque que evite impacto de reportes en la operativa; proyecciones, consistencia y recuperación. No exige implementar simultáneamente CQRS y event sourcing.
4 — Orquestador y escalado horizontal (2 puntos, alta): central en Kubernetes o equivalente, demostración durante carga, sesiones, salud, réplicas y dependencias compartidas.
5 — Integración entre grupos con contratos (2 puntos, media-alta): publicar OpenAPI/AsyncAPI u otro contrato pertinente y suite de pruebas de contrato en CI; requiere coordinación con otro grupo.
11 — Base geoespacial (1 punto, media): persistir rutas con soporte geográfico y utilizarlo en cálculo de desvíos; geometrías, sistema de coordenadas, índices y criterio de distancia.
12 — Infraestructura como código (1 punto, media-alta): aprovisionamiento completo automatizado con Terraform/Ansible o equivalente, configuración y despliegue reproducibles. Compose por sí solo no acredita automatización completa de infraestructura.

Anexo C. Cambios frente al SAD original
• Corregidos año, descripción inicial y referencias al laboratorio.
• Separados gub.uy y acceso interno LDAP.
• Explicitados autorización en negocio y límites por empresa.
• Precarga de rubros/tipos de carga conforme al alcance específico.
• Diferenciados Balanzas request-response y Tracking asincrónico.
• Corregida detección de actividad sin guía y precondiciones.
• Explicitadas carga total, tolerancias y criterios pendientes de desvío.
• Incorporados ciclo de caso y reglas de transición propuestas.
• Ampliada auditoría a creación, cambios y resolución.
• Aclaradas confirmación de duplicados y sincronización offline.
• Corregida referencia de resiliencia a ADR-003.
• Agregados contratos, observabilidad, privacidad, carga, pruebas, CI y despliegue.
• Integrados FaaS, firma, LDAP, WS-* y MongoDB con pendientes explícitos.
• Reorganizados ocho ADR; eliminadas afirmaciones de rendimiento sin evidencia.
• Incluido análisis de todos los opcionales y plan de implementación.
• Diagramas pendientes de reconstrucción y verificación visual.

Anexo D. Fuentes y declaración de uso de IA
Fuentes del proyecto:
Letra obligatorio: https://drive.google.com/file/d/1nnsk4vAR5AuRg6xI7MPzIbX3gmKNAOTM/view
SAD original: https://docs.google.com/document/d/1Ar_E_XVsR22V2Wez9llf2sq8nsrv3QKbzBaa16LbuEg/edit

Documentación técnica consultada como orientación, a validar con las versiones elegidas:
AWS Lambda, diseño e idempotencia: https://docs.aws.amazon.com/lambda/latest/dg/concepts-application-design.html
WildFly Elytron, integración LDAP (documentación de versión 17; no fija la versión del proyecto): https://docs.wildfly.org/17/WildFly_Elytron_Security.html
Apache CXF, WS-Security: https://cxf.apache.org/docs/ws-security.html
MongoDB, limitaciones de series temporales: https://www.mongodb.com/docs/manual/core/timeseries/timeseries-limitations/

La documentación de un proveedor no constituye una decisión de usarlo. El equipo debe contrastar las propuestas con documentación oficial compatible, pruebas y mediciones propias.
Este borrador fue preparado con asistencia de Codex para comparar documentos, organizar requisitos y redactar propuestas. No se verificó código ni se ejecutaron pruebas del sistema; los diagramas originales no se inspeccionaron visualmente.
Para la declaración final, completar nombre y versión de cada herramienta, partes donde se utilizó, finalidad y procedimiento de validación. El grupo conserva la autoría y responsabilidad de sus decisiones y debe poder explicar el contenido entregado. La letra no permite delegar la definición y justificación de arquitectura a herramientas de IA.


