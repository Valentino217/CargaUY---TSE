# Guía de Conexión: App Móvil CargaUYAndroid y Servidor Central (WildFly)

Esta guía explica detalladamente los pasos y consideraciones técnicas necesarias para que cualquier integrante del equipo pueda conectar la aplicación móvil **CargaUYAndroid** con el backend central **CargaUY** (WildFly / Jakarta EE).

---

## 1. Fundamentos de Red: ¿Por qué no funciona simplemente `localhost`?

* **`localhost` (127.0.0.1) dentro de Android**: En el emulador o en un teléfono físico, `localhost` apunta al **propio dispositivo móvil**, no a la computadora de desarrollo donde corre WildFly.
* **`10.0.2.2` (Emulador estándar)**: Es la dirección virtual que Android Emulator provee como alias al `127.0.0.1` de la computadora anfitriona.
* **Nuevos emuladores (API 35/36/37 con Wi-Fi virtual `netsim`)**: La interfaz Wi-Fi emulada (`10.0.2.16`) sale a menudo por NAT usando la **IP local de red (LAN)** de tu PC (ej. `10.0.0.16` o `192.168.1.X`), por lo que WildFly debe aceptar conexiones en todas las interfaces de red (`0.0.0.0`).
* **Dispositivos físicos por USB o Wi-Fi**: Requieren estar en la misma red Wi-Fi y usar la IP LAN de la PC, o bien usar un túnel mediante `adb reverse`.

---

## 2. Configuración del Servidor Central (WildFly)

Para que el servidor acepte peticiones provenientes del emulador o de la red local, debe cumplir dos condiciones: **escuchar en `0.0.0.0`** y tener el **puerto HTTP correcto**.

### Paso 2.1: Permitir conexiones externas en `standalone-full.xml`

Abre el archivo de configuración de WildFly:
`<directorio_wildfly>/standalone/configuration/standalone-full.xml`

Busca el bloque `<interfaces>` y asegúrate de que la interfaz `public` use `<any-address/>` o `0.0.0.0`:

```xml
<interfaces>
    <interface name="management">
        <inet-address value="${jboss.bind.address.management:127.0.0.1}"/>
    </interface>
    <interface name="public">
        <!-- Permite escuchar en todas las IPs de la PC (127.0.0.1, 10.0.2.2 y LAN) -->
        <any-address/>
    </interface>
    <interface name="unsecure">
        <inet-address value="${jboss.bind.address.unsecure:127.0.0.1}"/>
    </interface>
</interfaces>
```

> [!TIP]
> Si inicias WildFly desde **Eclipse / JBoss Tools**:
> Haz doble clic en el servidor WildFly en la pestaña *Servers* de Eclipse, ve a **Open launch configuration** -> pestaña **Arguments**, y verifica que en los argumentos de programa no esté forzado `-b localhost`. Puedes cambiarlo a `-b 0.0.0.0`.

### Paso 2.2: Verificar el Puerto HTTP (`8080` vs `8081`)

En Windows, programas como Steam (`steamwebhelper`), Oracle o software corporativo a menudo ocupan el puerto `8080`.

En `standalone-full.xml`, en la sección `<socket-binding-group>`:
```xml
<socket-binding name="http" port="${jboss.http.port:8081}"/>
```
*Si tu WildFly usa `8081` (o cualquier otro puerto), anótalo para configurarlo en la app.*

### Paso 2.3: Probar el endpoint desde la PC anfitriona

Antes de abrir el emulador, abre tu navegador o terminal y prueba la URL con la cédula del chofer de prueba:
```bash
curl -i "http://localhost:8081/CargaUY-web/api/chofer/guias?ci=11223344"
```
Debe devolver `HTTP/1.1 200 OK` con el JSON de las guías de Carlos Chofer.

---

## 3. Configuración en la App Móvil (`CargaUYAndroid`)

### Paso 3.1: Permisos y Tráfico HTTP (Cleartext) en `AndroidManifest.xml`

Como estamos desarrollando en ambiente local con `http://` (sin certificado SSL `https://`), es obligatorio permitir tráfico no cifrado en:
`app/src/main/AndroidManifest.xml`:

```xml
<manifest ...>
    <!-- Permisos requeridos -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <application
        ...
        android:usesCleartextTraffic="true">
        ...
    </application>
</manifest>
```

### Paso 3.2: Configurar las URLs en `Constants.kt`

En `app/src/main/java/com/example/cargauyandroid/Constants.kt`:

1. Conoce la IP de tu PC ejecutando `ipconfig` en la terminal (ejemplo: `10.0.0.16` o `192.168.1.50`).
2. Configura la lista de URLs candidatas:

```kotlin
package com.example.cargauyandroid

object Constants {
    // Reemplaza por la IP IPv4 de tu PC (obtenida con ipconfig)
    const val BASE_URL = "http://10.0.0.16:8081"

    // La app probará automáticamente estas direcciones en orden:
    val CANDIDATE_URLS = listOf(
        "http://10.0.0.16:8081",  // IP LAN de tu PC (recomendada para Wi-Fi virtual / física)
        "http://10.0.2.2:8081",   // Alias tradicional de loopback en Android Emulator
        "http://127.0.0.1:8081"   // Funciona con túnel 'adb reverse'
    )

    const val DEFAULT_CEDULA = 11223344
    const val DEFAULT_LATITUD = -34.9011
    const val DEFAULT_LONGITUD = -56.1645
}
```

---

## 4. Opciones de Conexión según el Entorno de Prueba

### Opción A: Usando el Emulador de Android (Recomendada)
1. Inicia WildFly.
2. Abre Android Studio y ejecuta la app en el emulador (**Run ▶**).
3. Gracias al mecanismo de `RetrofitClient.executeWithFallback`:
   - Probará `http://<IP_PC>:8081` y `http://10.0.2.2:8081`.
   - Se conectará automáticamente a la que responda primero y la recordará para las siguientes llamadas.

### Opción B: Usando un Dispositivo Físico (Celular por USB)
Si conectas tu teléfono por cable USB con depuración activada:
1. Abre una terminal en tu PC.
2. Ejecuta:
   ```bash
   adb reverse tcp:8081 tcp:8081
   ```
   *Esto redirige cualquier petición a `localhost:8081` dentro del celular hacia el puerto `8081` de tu PC.*
3. En `Constants.CANDIDATE_URLS`, `http://127.0.0.1:8081` funcionará de forma inmediata.

### Opción C: Dispositivo Físico por Wi-Fi (Sin Cable)
1. Conecta tu celular y tu PC a la **misma red Wi-Fi**.
2. Obtén la IP de tu PC con `ipconfig` (ej: `192.168.1.15`).
3. Agrega `"http://192.168.1.15:8081"` como primera opción en `Constants.CANDIDATE_URLS`.
4. Asegúrate de que el Firewall de Windows permita conexiones entrantes al puerto `8081`.

---

## 5. Pruebas de Funcionamiento (Casos de Uso)

### 1. Ver Guías (Online):
* En la pantalla principal, verifica que la cédula sea `11223344`.
* Pulsa **"Ver Mis Guías de Viaje"**.
* Deben aparecer las guías (Guía #1 Montevideo $\rightarrow$ Rivera, Guía #2 Nueva Palmira $\rightarrow$ Montevideo). La app guardará automáticamente una copia en su caché SQLite interno.

### 2. Registrar Eventos y Modo Offline (CU Crítico 1):
* Entra a una guía y pulsa **"Registrar Evento"**.
* Puedes registrar:
  - **Inicio de viaje** / **Fin de viaje**: Captura automática de fecha/hora y GPS.
  - **Carga parcial** / **Descarga parcial**: Ingreso de Kg, mercadería y empresa.
  - **Incidente**: Descripción y foto opcional.
* **Prueba sin internet**: Activa el **Modo Avión** en el emulador. Al guardar el evento, la app te avisará que se guardó localmente en el teléfono (estado `PENDIENTE`).

### 3. Sincronización Automática (CU Crítico 2):
* Con eventos pendientes en el teléfono, desactiva el **Modo Avión**.
* La app detectará la conexión y sincronizará en lote los eventos pendientes contra `/api/chofer/eventos/sync`.
* También puedes presionar el botón **"Sincronizar (N)"** en la barra superior en cualquier momento.

---

## 6. Diagnóstico y Preguntas Frecuentes (Troubleshooting)

| Síntoma | Causa Probable | Solución |
| :--- | :--- | :--- |
| `SocketTimeoutException: failed to connect to /10.0.2.2:8081` | WildFly está escuchando solo en `127.0.0.1` o el emulador usa Wi-Fi virtual (`netsim`). | En `standalone-full.xml`, cambia `<interface name="public">` a `<any-address/>` y agrega tu IP local en `Constants.CANDIDATE_URLS`. |
| `HTTP 404 Not Found` al conectar a `http://localhost:8080` | El puerto 8080 está ocupado por otro programa (ej: Steam CEF) en lugar de WildFly. | Revisa en `server.log` o `standalone-full.xml` qué puerto tiene asignado WildFly (típicamente `8081`). |
| `CLEARTEXT communication to ... not permitted by network security policy` | Falta habilitar tráfico no cifrado en Android. | Asegúrate de tener `android:usesCleartextTraffic="true"` en `AndroidManifest.xml`. |
| `ConnectException: Connection refused` | El servidor WildFly no está iniciado o se cayó. | Verifica en la pestaña *Servers* de Eclipse o consola que WildFly esté en estado `Started`. |
| Las guías no se actualizan tras cambios en el código | Android Studio no desplegó la nueva APK. | En Android Studio, presiona **Run 'app' (▶)** para forzar la instalación de la versión compilada más reciente. |
