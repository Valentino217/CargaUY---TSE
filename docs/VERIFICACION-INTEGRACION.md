# Verificación de Martin-integracion

Fecha: 20 de octubre de 2026.

## Resultado

- `mvn clean verify`: BUILD SUCCESS; comprobación posterior `mvn verify` también exitosa con la navegación final.
- EJB: 119 pruebas, cero fallos y cero errores.
- Web: 59 pruebas, cero fallos y cero errores.
- Total: **178 pruebas**. No equivale a afirmar una cobertura del 80 %.
- Se generó el EAR final en `ear/target/CargaUY.ear`.

## Despliegue real aislado

Se creó un esquema PostgreSQL temporal separado del esquema habitual y una copia de WildFly 41 en HTTP 8180, con administración 10090 y bind a loopback. Se probaron los mapeos JPA, el registro único de REST y las dos colas declaradas en el EAR.

La migración 03 terminó en COMMIT en el esquema temporal y pudo repetirse sin borrar los registros de prueba. No se ejecutó en el esquema habitual. La prueba sobre el esquema nuevo no sustituye revisar duplicados y hacer respaldo antes de migrar datos reales.

Las comprobaciones HTTP devolvieron 200 en login, health, ready, auth/me, empresas, consulta de guías y pantallas JSP/JSF de administración, inicio, vehículos, guías, ITV, PNC, dashboard, infracciones, detalle de infracción y registro de pesajes. La selección de guías mostró el vehículo y el usuario con múltiples roles asignados mediante el vínculo canónico.

Una solicitud REST sin token devolvió 401 con JSON. Un usuario de prueba cuya entidad era Ciudadano y tenía roles CIUDADANO+CHOFER consultó su guía con 200; intentar consultar otra CI devolvió 403.

Un evento INICIO recorrió REST → TrackingQueue → TrackingMDB → PostgreSQL. Se comprobó su persistencia por UUID y el cambio de la guía a EN_CURSO.

Para estas pruebas se crearon datos y sesiones ficticias exclusivamente en el esquema temporal. La autenticación LDAP/OIDC no se dio por verificada mediante esas sesiones. El recurso auxiliar de QA se añadió únicamente a un EAR temporal, nunca a los fuentes ni al EAR final del proyecto. El servidor, esquema y artefactos privados de esa prueba se retiraron al finalizar.

## Límites

- El servidor habitual de Eclipse y el esquema habitual de cargauy no se modificaron. Para publicarlo allí hay que seguir `INTEGRACION-MARTIN-VALENTINO.md`, incluida la migración 03.
- No se volvió a comprobar el bind LDAP ni el mock PDI en Docker: el motor Docker no estaba disponible en esta sesión. Las pruebas unitarias de esos componentes sí están incluidas en Maven.
- No se hizo login real de una persona con ID Uruguay ni se completó su logout SSO o el login móvil. Se mantienen los pendientes documentados del proveedor.
- No se compiló ni ejecutó el APK Android. Se ajustó el cliente al contexto/puerto y al bearer; falta validar esa aplicación con un SDK/emulador configurado.
- No se probó TLS completo ni una carga de producción. Las operaciones heredadas no recibieron una auditoría integral de autorización.
