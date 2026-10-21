package com.example.cargauyandroid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ConsultaGuiasResult(
    val guias: List<Viaje>,
    val desdeCacheOffline: Boolean,
    val mensajeAviso: String? = null
)

class SyncRepository(context: Context) {

    private val appContext = context.applicationContext
    val dbHelper = OfflineDatabaseHelper(appContext)

    var baseDatosReiniciadaPorNuevaCompilacion: Boolean = false
        private set

    init {
        verificarYLimpiarSiNuevaInstalacion()
    }

    fun verificarYLimpiarSiNuevaInstalacion(): Boolean {
        return try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            val lastUpdate = pInfo.lastUpdateTime
            val prefs = appContext.getSharedPreferences("cargauy_prefs", Context.MODE_PRIVATE)
            val guardado = prefs.getLong("last_installed_time", -1L)
            if (guardado != lastUpdate) {
                dbHelper.limpiarBaseDeDatosCompleta()
                prefs.edit().putLong("last_installed_time", lastUpdate).apply()
                baseDatosReiniciadaPorNuevaCompilacion = true
                Log.d("CARGAUY", "Nueva compilacion/instalacion detectada (time=$lastUpdate). Base de datos local reiniciada.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w("CARGAUY", "No se pudo verificar actualizacion de app: ${e.message}")
            false
        }
    }

    fun limpiarBaseDeDatosManual() {
        dbHelper.limpiarBaseDeDatosCompleta()
        Log.d("CARGAUY", "Base de datos local limpiada manualmente.")
    }

    @Volatile
    var servidorAlcanzable: Boolean = true
        private set

    fun hayConexionInternet(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun registrarCallbackConectividad(onNetworkAvailable: () -> Unit): ConnectivityManager.NetworkCallback? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkAvailable()
            }
        }
        return try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            callback
        } catch (e: Exception) {
            null
        }
    }

    fun desregistrarCallbackConectividad(callback: ConnectivityManager.NetworkCallback?) {
        if (callback == null) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try {
            cm?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun obtenerUbicacionActual(): Pair<Double, Double> {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            try {
                val locationManager =
                    appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val providers = locationManager?.getProviders(true) ?: emptyList()
                for (provider in providers) {
                    val loc = locationManager?.getLastKnownLocation(provider)
                    if (loc != null) {
                        return Pair(
                            Math.round(loc.latitude * 10000.0) / 10000.0,
                            Math.round(loc.longitude * 10000.0) / 10000.0
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        return Pair(Constants.DEFAULT_LATITUD, Constants.DEFAULT_LONGITUD)
    }

    fun obtenerFechaHoraIsoActual(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    suspend fun consultarGuiasChofer(cedula: Int): ConsultaGuiasResult = withContext(Dispatchers.IO) {
        try {
            Log.d("CARGAUY", "Consultando guias para CI=$cedula")
            val remotas = RetrofitClient.executeWithFallback { api -> api.obtenerViajes(cedula) }
            servidorAlcanzable = true
            Log.d("CARGAUY", "Guias recibidas de ${RetrofitClient.activeBaseUrl}: ${remotas.size}")
            dbHelper.guardarGuiasCache(cedula, remotas)
            return@withContext ConsultaGuiasResult(
                guias = remotas,
                desdeCacheOffline = false
            )
        } catch (e: Exception) {
            servidorAlcanzable = false
            Log.e("CARGAUY", "Fallo al consultar guias: ${e.message}", e)
            val cache = dbHelper.obtenerGuiasCache(cedula)
            return@withContext ConsultaGuiasResult(
                guias = cache,
                desdeCacheOffline = true,
                mensajeAviso = "Modo sin conexion: mostrando guias guardadas en el telefono. Detalle: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    /**
     * CU Crítico 1: Registrar evento de viaje con UUID único.
     * - Si no hay conexión (Flujo 3.A): guarda de forma local para sincronizar al recuperar internet.
     * - Si hay conexión: guarda y sincroniza inmediatamente con el sistema, verificando duplicados (Flujo 3.B).
     */
    suspend fun registrarEvento(
        guiaId: Long,
        tipo: TipoEventoOption,
        latitud: Double,
        longitud: Double,
        descripcion: String?,
        foto: String?,
        cedulaChofer: Int,
        uuidPersonalizado: String? = null
    ): ResultadoOperacionEvento = withContext(Dispatchers.IO) {
        val uuid = uuidPersonalizado?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val tiempo = obtenerFechaHoraIsoActual()

        val eventoLocal = EventoLocal(
            uuid = uuid,
            guiaId = guiaId,
            tipo = tipo.codigo,
            tiempo = tiempo,
            latitud = latitud,
            longitud = longitud,
            descripcion = descripcion?.takeIf { it.isNotBlank() },
            foto = foto?.takeIf { it.isNotBlank() },
            estadoSync = EstadoSyncLocal.PENDIENTE.name
        )

        val insertado = dbHelper.insertarEventoLocal(eventoLocal)
        if (!insertado) {
            return@withContext ResultadoOperacionEvento(
                evento = eventoLocal.copy(estadoSync = EstadoSyncLocal.DUPLICADO.name),
                guardadoOffline = false,
                duplicadoDetectado = true,
                mensajeUsuario = "Ya existe un evento con el mismo identificador ($uuid) y no se volvera a registrar."
            )
        }

        // Actualizar estado local de la guía si es INICIO o FIN
        if (tipo == TipoEventoOption.INICIO) {
            dbHelper.actualizarEstadoGuiaLocal(guiaId, "EN_CURSO")
        } else if (tipo == TipoEventoOption.FIN) {
            dbHelper.actualizarEstadoGuiaLocal(guiaId, "FINALIZADO")
        }

        // Intentar sincronizar directamente con el backend; si no hay red, cae en el catch (Flujo 3.A)
        return@withContext try {
            Log.d("CARGAUY", "Enviando evento $uuid (${tipo.codigo}) al servidor...")
            val resultados = RetrofitClient.executeWithFallback { api ->
                api.sincronizarEventos(listOf(eventoLocal.toDto()))
            }
            servidorAlcanzable = true
            val resultado = resultados.firstOrNull { it.uuid == uuid }

            when (resultado?.estado?.uppercase()) {
                "ACCEPTED" -> {
                    dbHelper.actualizarEstadoEvento(
                        uuid,
                        EstadoSyncLocal.SINCRONIZADO,
                        resultado.mensaje ?: "Sincronizado con el servidor"
                    )
                    try {
                        val guiasActualizadas = RetrofitClient.executeWithFallback { api ->
                            api.obtenerViajes(cedulaChofer)
                        }
                        dbHelper.guardarGuiasCache(cedulaChofer, guiasActualizadas)
                    } catch (_: Exception) {
                    }
                    ResultadoOperacionEvento(
                        evento = eventoLocal.copy(estadoSync = EstadoSyncLocal.SINCRONIZADO.name),
                        guardadoOffline = false,
                        mensajeUsuario = "Evento '${tipo.etiqueta}' registrado y sincronizado en el sistema (UUID: ${uuid.take(8)}...)."
                    )
                }

                "DUPLICATE" -> {
                    dbHelper.eliminarEventoDuplicado(uuid)
                    ResultadoOperacionEvento(
                        evento = eventoLocal.copy(estadoSync = EstadoSyncLocal.DUPLICADO.name),
                        guardadoOffline = false,
                        duplicadoDetectado = true,
                        mensajeUsuario = "El sistema indica que ya existe el evento con UUID ${uuid.take(8)}... Se descarto el duplicado."
                    )
                }

                else -> {
                    val msgError = resultado?.mensaje ?: "No se pudo confirmar en el servidor"
                    ResultadoOperacionEvento(
                        evento = eventoLocal,
                        guardadoOffline = true,
                        mensajeUsuario = "El servidor devolvio un aviso ($msgError). El evento quedo guardado localmente para reintentar."
                    )
                }
            }
        } catch (e: Exception) {
            servidorAlcanzable = false
            Log.w("CARGAUY", "Guardado offline por falta de conexion: ${e.message}")
            ResultadoOperacionEvento(
                evento = eventoLocal,
                guardadoOffline = true,
                mensajeUsuario = "Sin conexion al servidor: Evento '${tipo.etiqueta}' guardado en el telefono (UUID: ${uuid.take(8)}...) para sincronizar al recuperar conexion."
            )
        }
    }

    /**
     * CU Crítico 2: Sincronizar eventos móviles sin conexión.
     */
    suspend fun sincronizarPendientes(cedulaChofer: Int): ResumenSincronizacion? = withContext(Dispatchers.IO) {
        val pendientes = dbHelper.listarEventosPendientes()
        if (pendientes.isEmpty()) {
            return@withContext null
        }

        return@withContext try {
            Log.d("CARGAUY", "Sincronizando lote de ${pendientes.size} eventos pendientes...")
            val dtos = pendientes.map { it.toDto() }
            val respuestas = RetrofitClient.executeWithFallback { api ->
                api.sincronizarEventos(dtos)
            }
            servidorAlcanzable = true

            var aceptados = 0
            var duplicados = 0
            var errores = 0

            val mapaRespuestas = respuestas.associateBy { it.uuid }
            for (ev in pendientes) {
                val r = mapaRespuestas[ev.uuid]
                when (r?.estado?.uppercase()) {
                    "ACCEPTED" -> {
                        aceptados++
                        dbHelper.actualizarEstadoEvento(
                            ev.uuid,
                            EstadoSyncLocal.SINCRONIZADO,
                            r.mensaje ?: "Sincronizado correctamente"
                        )
                    }

                    "DUPLICATE" -> {
                        duplicados++
                        dbHelper.eliminarEventoDuplicado(ev.uuid)
                    }

                    else -> {
                        errores++
                        dbHelper.actualizarEstadoEvento(
                            ev.uuid,
                            EstadoSyncLocal.PENDIENTE,
                            r?.mensaje ?: "Pendiente de reintento"
                        )
                    }
                }
            }

            // 4.B: Verificar si el viaje/ruta fue modificado desde el frontoffice y actualizar las guías locales
            val guiasPrevias = dbHelper.obtenerGuiasCache(cedulaChofer).associateBy { it.id }
            var rutaActualizada = false
            try {
                val guiasServidor = RetrofitClient.executeWithFallback { api ->
                    api.obtenerViajes(cedulaChofer)
                }
                for (gNueva in guiasServidor) {
                    val gAnt = guiasPrevias[gNueva.id]
                    if (gAnt != null && (gAnt.version != gNueva.version || gAnt.destino != gNueva.destino || gAnt.origen != gNueva.origen)) {
                        rutaActualizada = true
                    }
                }
                dbHelper.guardarGuiasCache(cedulaChofer, guiasServidor)
            } catch (_: Exception) {
            }

            val partesMensaje = mutableListOf<String>()
            if (aceptados > 0) {
                partesMensaje.add("$aceptados eventos sincronizados con el sistema")
            }
            if (duplicados > 0) {
                partesMensaje.add("$duplicados eventos duplicados omitidos ")
            }
            if (rutaActualizada) {
                partesMensaje.add("Ruta/guia actualizada desde el servidor ")
            }
            if (errores > 0) {
                partesMensaje.add("$errores eventos quedaron pendientes")
            }

            ResumenSincronizacion(
                totalEnviados = pendientes.size,
                aceptados = aceptados,
                duplicados = duplicados,
                errores = errores,
                rutaActualizada = rutaActualizada,
                mensaje = partesMensaje.joinToString(" • ").ifBlank { "Sincronizacion completada." }
            )
        } catch (e: Exception) {
            servidorAlcanzable = false
            Log.w("CARGAUY", "Fallo al sincronizar lote pendiente: ${e.message}")
            ResumenSincronizacion(
                totalEnviados = pendientes.size,
                aceptados = 0,
                duplicados = 0,
                errores = pendientes.size,
                rutaActualizada = false,
                mensaje = "Ofline (${pendientes.size} eventos siguen guardados en el telefono esperando red)."
            )
        }
    }

    suspend fun listarEventosCombinadosGuia(guiaId: Long): List<EventoLocal> = withContext(Dispatchers.IO) {
        val locales = dbHelper.listarEventosPorGuiaLocal(guiaId)
        val mapaPorUuid = LinkedHashMap<String, EventoLocal>()

        try {
            val remotos = RetrofitClient.executeWithFallback { api ->
                api.listarEventosPorGuia(guiaId)
            }
            servidorAlcanzable = true
            for (rem in remotos) {
                mapaPorUuid[rem.uuid] = EventoLocal(
                    uuid = rem.uuid,
                    guiaId = rem.guiaId,
                    tipo = rem.tipo,
                    tiempo = rem.tiempo,
                    latitud = rem.latitud,
                    longitud = rem.longitud,
                    descripcion = rem.incidente?.descripcion,
                    foto = rem.incidente?.foto,
                    estadoSync = EstadoSyncLocal.SINCRONIZADO.name
                )
            }
        } catch (_: Exception) {
            servidorAlcanzable = false
        }

        for (loc in locales) {
            if (!mapaPorUuid.containsKey(loc.uuid)) {
                mapaPorUuid[loc.uuid] = loc
            }
        }

        return@withContext mapaPorUuid.values.sortedBy { it.tiempo }
    }
}
