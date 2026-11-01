# Despliegue de la Función FaaS de Tracking y Cálculo de Desvío (`DESVIO_DE_RUTA`)

Este directorio contiene la función **Serverless (FaaS)** en [`lambda_function.py`](file:///c:/Users/valen/eclipse-workspace/CargaUY/faas/tracking-desvio-lambda/lambda_function.py), diseñada sin dependencias externas para desplegarse directamente en **AWS Lambda** o **Google Cloud Run Functions**.

---

## 1. Despliegue Rápido en AWS Lambda (Recomendado - 2 minutos)

1. Ingresar a la consola de **AWS Lambda** → **Create function** → **Author from scratch**.
2. Configurar:
   - **Function name**: `cargauy-tracking-desvio`
   - **Runtime**: `Python 3.12`
   - **Architecture**: `x86_64` o `arm64`
3. En **Advanced settings**:
   - Marcar **Enable function URL**.
   - **Auth type**: `NONE` (la función valida internamente la firma/secreto y devuelve cabeceras CORS).
   - Marcar **Configure CORS** (`Allow origin: *`, `Allow methods: POST, GET, OPTIONS`).
4. Hacer clic en **Create function**.
5. En la pestaña **Code**, reemplazar el contenido de `lambda_function.py` con el archivo [`faas/tracking-desvio-lambda/lambda_function.py`](file:///c:/Users/valen/eclipse-workspace/CargaUY/faas/tracking-desvio-lambda/lambda_function.py) y presionar **Deploy**.
6. *(Opcional)* En **Configuration → Environment variables**, agregar:
   - `CARGAUY_BACKEND_URL`: URL pública o túnel (`https://<tu-tunel>.ngrok-free.app/CargaUY-web/api/chofer/eventos/faas`) si se desea que la Lambda haga el `POST` directo hacia WildFly.
   - `FAAS_SHARED_SECRET`: Secreto compartido entre la función FaaS y WildFly.
7. Copiar la **Function URL** generada (ej. `https://xxxxxxxx.lambda-url.us-east-1.on.aws/`).

---

## 2. Alternativa: Google Cloud Run Functions

1. En Google Cloud Console → **Cloud Run functions** → **Create Function**.
2. Configurar:
   - **Runtime**: `Python 3.12`
   - **Entry point**: `evaluar_tracking_http`
   - **Authentication**: `Allow unauthenticated invocations`
3. Pegar el contenido de [`lambda_function.py`](file:///c:/Users/valen/eclipse-workspace/CargaUY/faas/tracking-desvio-lambda/lambda_function.py) en `main.py` y desplegar.
4. Copiar la URL HTTPS generada.

---

## 3. Configuración en CargaUY (WildFly / Interfaz Web)

La URL HTTPS de la función FaaS puede configurarse de dos maneras:
1. **Por variable de entorno o propiedad de sistema en WildFly**:
   - Variable de entorno: `FAAS_TRACKING_URL=https://xxxxxxxx.lambda-url.us-east-1.on.aws/`
   - Propiedad JVM: `-Dcargauy.faas.url=https://xxxxxxxx.lambda-url.us-east-1.on.aws/`
   - Secreto opcional: `FAAS_SHARED_SECRET=cargauy-faas-secret-2026`
2. **Directamente desde la pantalla de Monitoreo de Viaje (`detalleViaje.xhtml`)**:
   - En la sección **Telemetría GPS vía FaaS (Cloud)** se puede pegar la URL HTTPS de la función en vivo y probar el envío de coordenadas en ruta o con desvío.

---

## 4. Prueba con `curl` o Postman contra la URL HTTPS del FaaS

```bash
curl -X POST "https://<TU-FUNCTION-URL>/" \
  -H "Content-Type: application/json" \
  -d '{
    "uuid": "evt-test-desvio-001",
    "guiaId": 1,
    "tipo": "INCIDENTE",
    "latitud": -33.2333,
    "longitud": -54.3833,
    "origen": "Montevideo",
    "destino": "Rivera",
    "tiempo": "2026-10-25T15:00:00"
  }'
```

**Respuesta esperada (`200 OK`)**:
```json
{
  "uuid": "evt-test-desvio-001",
  "guiaId": 1,
  "tipo": "INCIDENTE",
  "latitud": -33.2333,
  "longitud": -54.3833,
  "origen": "Montevideo",
  "destino": "Rivera",
  "origenResuelto": "montevideo",
  "destinoResuelto": "rivera",
  "distanciaDesvioKm": 145.67,
  "puntoProyeccionRuta": {
    "latitud": -33.4125,
    "longitud": -55.9358
  },
  "fraccionAvanceRuta": 0.3725,
  "procesadoPorFaas": true,
  "faasTimestamp": "2026-10-25T18:00:00Z",
  "webhookEnviado": false,
  "webhookDetalle": "CARGAUY_BACKEND_URL no configurada (modo respuesta directa)"
}
```
