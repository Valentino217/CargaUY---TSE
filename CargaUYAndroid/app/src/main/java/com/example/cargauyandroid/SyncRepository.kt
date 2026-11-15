package com.example.cargauyandroid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class ConsultaGuiasResult(
    val guias: List<Viaje>,
    val desdeCacheOffline: Boolean,
    val mensajeAviso: String? = null
)

class SyncRepository(context: Context) {

    private val appContext = context.applicationContext
    val dbHelper = OfflineDatabaseHelper(appContext)
    private val syncMutex = Mutex()

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

    fun registrarCallbackConectividad(
        onNetworkAvailable: () -> Unit,
        onNetworkLost: (() -> Unit)? = null
    ): ConnectivityManager.NetworkCallback? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                onNetworkLost?.invoke()
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

    private fun tienePermisoUbicacion(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun redondearCoordenadas(loc: Location): Pair<Double, Double> {
        return Pair(
            Math.round(loc.latitude * 10000.0) / 10000.0,
            Math.round(loc.longitude * 10000.0) / 10000.0
        )
    }

    @SuppressLint("MissingPermission")
    private fun obtenerMejorLastKnownLocation(locationManager: LocationManager?): Location? {
        if (locationManager == null) return null
        val habilitados = try {
            locationManager.getProviders(true)
        } catch (_: Exception) {
            emptyList()
        }
        val ordenados = (listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            "fused"
        ) + habilitados).distinct()
        var mejor: Location? = null
        for (provider in ordenados) {
            val loc = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
            if (loc != null && (mejor == null || loc.time > mejor.time)) {
                mejor = loc
            }
        }
        return mejor
    }

    @SuppressLint("MissingPermission")
    fun obtenerUbicacionActual(): Pair<Double, Double> {
        if (tienePermisoUbicacion()) {
            try {
                val locationManager =
                    appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val loc = obtenerMejorLastKnownLocation(locationManager)
                if (loc != null) {
                    return redondearCoordenadas(loc)
                }
            } catch (_: Exception) {
            }
        }
        return Pair(Constants.DEFAULT_LATITUD, Constants.DEFAULT_LONGITUD)
    }

    @SuppressLint("MissingPermission")
    suspend fun obtenerUbicacionActualAsync(): Pair<Double, Double> {
        if (!tienePermisoUbicacion()) {
            return Pair(Constants.DEFAULT_LATITUD, Constants.DEFAULT_LONGITUD)
        }
        val locationManager =
            appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val ultima = obtenerMejorLastKnownLocation(locationManager)
        if (ultima != null && (System.currentTimeMillis() - ultima.time) < 30_000L) {
            return redondearCoordenadas(ultima)
        }
        if (locationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val proveedores = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).filter { p ->
                try {
                    locationManager.isProviderEnabled(p)
                } catch (_: Exception) {
                    false
                }
            }
            for (provider in proveedores) {
                val fresca = withTimeoutOrNull(1500L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        try {
                            locationManager.getCurrentLocation(
                                provider,
                                signal,
                                ContextCompat.getMainExecutor(appContext)
                            ) { location ->
                                if (cont.isActive) {
                                    cont.resume(location)
                                }
                            }
                        } catch (_: Exception) {
                            if (cont.isActive) {
                                cont.resume(null)
                            }
                        }
                    }
                }
                if (fresca != null) {
                    return redondearCoordenadas(fresca)
                }
            }
        }
        return if (ultima != null) {
            redondearCoordenadas(ultima)
        } else {
            Pair(Constants.DEFAULT_LATITUD, Constants.DEFAULT_LONGITUD)
        }
    }

    fun obtenerFechaHoraIsoActual(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    private suspend fun asegurarTokenLocal(cedula: Int, forzarRenovacion: Boolean = false) {
        if (SesionMovil.esSesionOidc && SesionMovil.token.isNotBlank()) {
            // Preservar sesión OIDC autenticada: no reemplazar por /api/auth/dev-token
            return
        }
        if (!forzarRenovacion && SesionMovil.token.isNotBlank() && (SesionMovil.cedulaAsociada == null || SesionMovil.cedulaAsociada == cedula)) {
            return
        }
        try {
            val resp = RetrofitClient.executeWithFallback { api ->
                api.obtenerDevToken(mapOf("ci" to cedula))
            }
            val tk = resp["token"] as? String
            if (!tk.isNullOrBlank()) {
                SesionMovil.token = tk
                SesionMovil.cedulaAsociada = cedula
                SesionMovil.esSesionOidc = false
                Log.d("CARGAUY", "Token dev obtenido automaticamente para CI=$cedula")
            }
        } catch (e: Exception) {
            Log.w("CARGAUY", "No se pudo obtener token dev automatico para CI=$cedula: ${e.message}")
        }
    }

    suspend fun consultarGuiasChofer(cedula: Int): ConsultaGuiasResult = withContext(Dispatchers.IO) {
        if (!hayConexionInternet()) {
            servidorAlcanzable = false
            Log.d("CARGAUY", "Sin conexion a internet detectada. Mostrando cache offline.")
            val cache = dbHelper.obtenerGuiasCache(cedula)
            return@withContext ConsultaGuiasResult(
                guias = cache,
                desdeCacheOffline = true,
                mensajeAviso = "Sin señal: viendo tus viajes guardados en el teléfono."
            )
        }

        try {
            Log.d("CARGAUY", "Consultando guias para CI=$cedula")
            asegurarTokenLocal(cedula)
            val remotas = try {
                RetrofitClient.executeWithFallback { api -> api.obtenerViajes(cedula) }
            } catch (e: retrofit2.HttpException) {
                if ((e.code() == 401 || e.code() == 403) && !SesionMovil.esSesionOidc) {
                    asegurarTokenLocal(cedula, forzarRenovacion = true)
                    RetrofitClient.executeWithFallback { api -> api.obtenerViajes(cedula) }
                } else throw e
            }
            servidorAlcanzable = true
            Log.d("CARGAUY", "Guias recibidas de ${RetrofitClient.activeBaseUrl}: ${remotas.size}")
            dbHelper.guardarGuiasCache(cedula, remotas)
            return@withContext ConsultaGuiasResult(
                guias = remotas,
                desdeCacheOffline = false
            )
        } catch (e: retrofit2.HttpException) {
            servidorAlcanzable = e.code() < 500
            Log.w("CARGAUY", "Error HTTP ${e.code()} al consultar guias: ${e.message}")
            val cache = dbHelper.obtenerGuiasCache(cedula)
            return@withContext ConsultaGuiasResult(
                guias = cache,
                desdeCacheOffline = true,
                mensajeAviso = SyncAuthErrorHelper.mensajeConsultaHttpError(e.code(), SesionMovil.esSesionOidc)
            )
        } catch (e: Exception) {
            servidorAlcanzable = false
            Log.e("CARGAUY", "Fallo al consultar guias: ${e.message}", e)
            val cache = dbHelper.obtenerGuiasCache(cedula)
            return@withContext ConsultaGuiasResult(
                guias = cache,
                desdeCacheOffline = true,
                mensajeAviso = "No se pudo conectar al servidor (${e.message}). Mostrando tus viajes guardados."
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
        latitud: Double? = null,
        longitud: Double? = null,
        descripcion: String?,
        foto: String?,
        cedulaChofer: Int,
        uuidPersonalizado: String? = null,
        cantidadKg: Double? = null,
        mercaderia: String? = null,
        empresaCarga: String? = null
    ): ResultadoOperacionEvento = withContext(Dispatchers.IO) {
        val uuid = uuidPersonalizado?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val tiempo = obtenerFechaHoraIsoActual()
        val ubicacion = if (latitud == null || longitud == null) {
            obtenerUbicacionActualAsync()
        } else {
            Pair(latitud, longitud)
        }
        val latFinal = latitud ?: ubicacion.first
        val lonFinal = longitud ?: ubicacion.second

        val eventoLocal = EventoLocal(
            uuid = uuid,
            guiaId = guiaId,
            tipo = tipo.codigo,
            tiempo = tiempo,
            latitud = latFinal,
            longitud = lonFinal,
            descripcion = descripcion?.takeIf { it.isNotBlank() },
            foto = foto?.takeIf { it.isNotBlank() },
            cantidadKg = cantidadKg,
            mercaderia = mercaderia?.takeIf { it.isNotBlank() },
            empresaCarga = empresaCarga?.takeIf { it.isNotBlank() },
            estadoSync = EstadoSyncLocal.PENDIENTE.name
        )

        if (tipo == TipoEventoOption.INICIO) {
            val otraEnCurso = dbHelper.obtenerGuiaEnCurso(cedulaChofer, excluirGuiaId = guiaId)
            if (otraEnCurso != null) {
                val msg = "Ya tenés el Viaje #${otraEnCurso.id} en curso. Finalizalo antes de iniciar otro."
                return@withContext ResultadoOperacionEvento(
                    evento = eventoLocal.copy(estadoSync = EstadoSyncLocal.ERROR.name, mensajeSync = msg),
                    guardadoOffline = false,
                    rechazadoPorServidor = true,
                    mensajeUsuario = msg
                )
            }
        }

        val insertado = dbHelper.insertarEventoLocal(eventoLocal)
        if (!insertado) {
            return@withContext ResultadoOperacionEvento(
                evento = eventoLocal.copy(estadoSync = EstadoSyncLocal.DUPLICADO.name),
                guardadoOffline = false,
                duplicadoDetectado = true,
                mensajeUsuario = "Este evento ya fue registrado."
            )
        }

        // Si no hay conexión (Flujo 3.A): actualizar estado local de la guía y guardar para sincronizar luego
        if (!hayConexionInternet()) {
            if (tipo == TipoEventoOption.INICIO) {
                dbHelper.actualizarEstadoGuiaLocal(guiaId, "EN_CURSO")
            } else if (tipo == TipoEventoOption.FIN) {
                dbHelper.actualizarEstadoGuiaLocal(guiaId, "FINALIZADO")
            }
            servidorAlcanzable = false
            Log.d("CARGAUY", "Sin conexion a internet detectada por el sistema. Guardado offline (Flujo 3.A).")
            return@withContext ResultadoOperacionEvento(
                evento = eventoLocal,
                guardadoOffline = true,
                mensajeUsuario = "${tipo.etiqueta} guardado en el teléfono. Se enviará solo cuando vuelva la señal."
            )
        }

        // Intentar sincronizar directamente con el backend; si no hay red, cae en el catch (Flujo 3.A)
        return@withContext try {
            Log.d("CARGAUY", "Enviando evento $uuid (${tipo.codigo}) al servidor...")
            asegurarTokenLocal(cedulaChofer)
            val matriculaCache = dbHelper.obtenerGuiaPorId(guiaId)?.vehiculoMatricula
            val resultados = try {
                RetrofitClient.executeWithFallback { api ->
                    api.sincronizarEventos(listOf(eventoLocal.toDto(matriculaCache)))
                }
            } catch (e: retrofit2.HttpException) {
                if (SyncAuthErrorHelper.esErrorAutenticacion(e.code()) && !SesionMovil.esSesionOidc) {
                    asegurarTokenLocal(cedulaChofer, forzarRenovacion = true)
                    RetrofitClient.executeWithFallback { api ->
                        api.sincronizarEventos(listOf(eventoLocal.toDto(matriculaCache)))
                    }
                } else {
                    throw e
                }
            }
            servidorAlcanzable = true
            val resultado = resultados.firstOrNull { it.uuid == uuid }

            when (EstadoSyncLocal.desdeRespuestaServidor(resultado?.estado)) {
                EstadoSyncLocal.SINCRONIZADO -> {
                    dbHelper.actualizarEstadoEvento(
                        uuid,
                        EstadoSyncLocal.SINCRONIZADO,
                        resultado?.mensaje ?: "Enviado"
                    )
                    try {
                        val guiasActualizadas = RetrofitClient.executeWithFallback { api ->
                            api.obtenerViajes(cedulaChofer)
                        }
                        dbHelper.guardarGuiasCache(cedulaChofer, guiasActualizadas)
                    } catch (_: Exception) {
                    }
                    if (tipo == TipoEventoOption.INICIO) {
                        dbHelper.actualizarEstadoGuiaLocal(guiaId, "EN_CURSO")
                    } else if (tipo == TipoEventoOption.FIN) {
                        dbHelper.actualizarEstadoGuiaLocal(guiaId, "FINALIZADO")
                    }
                    ResultadoOperacionEvento(
                        evento = eventoLocal.copy(estadoSync = EstadoSyncLocal.SINCRONIZADO.name),
                        guardadoOffline = false,
                        mensajeUsuario = "${tipo.etiqueta} registrado correctamente."
                    )
                }

                EstadoSyncLocal.DUPLICADO -> {
                    dbHelper.eliminarEventoDuplicado(uuid)
                    ResultadoOperacionEvento(
                        evento = eventoLocal.copy(estadoSync = EstadoSyncLocal.DUPLICADO.name),
                        guardadoOffline = false,
                        duplicadoDetectado = true,
                        mensajeUsuario = "Este evento ya estaba registrado."
                    )
                }

                EstadoSyncLocal.ERROR -> {
                    val msgRechazo = resultado?.mensaje ?: "Operación no permitida"
                    dbHelper.actualizarEstadoEvento(
                        uuid,
                        EstadoSyncLocal.ERROR,
                        msgRechazo
                    )
                    try {
                        val guiasActualizadas = RetrofitClient.executeWithFallback { api ->
                            api.obtenerViajes(cedulaChofer)
                        }
                        dbHelper.guardarGuiasCache(cedulaChofer, guiasActualizadas)
                    } catch (_: Exception) {
                    }
                    ResultadoOperacionEvento(
                        evento = eventoLocal.copy(
                            estadoSync = EstadoSyncLocal.ERROR.name,
                            mensajeSync = msgRechazo
                        ),
                        guardadoOffline = false,
                        rechazadoPorServidor = true,
                        mensajeUsuario = "No se pudo registrar: $msgRechazo"
                    )
                }

                EstadoSyncLocal.PENDIENTE -> {
                    val msgError = resultado?.mensaje ?: "No se pudo confirmar el envío"
                    ResultadoOperacionEvento(
                        evento = eventoLocal,
                        guardadoOffline = true,
                        mensajeUsuario = "No se pudo enviar ahora ($msgError). Quedó guardado para reintentar."
                    )
                }
            }
        } catch (e: retrofit2.HttpException) {
            if (tipo == TipoEventoOption.INICIO) {
                dbHelper.actualizarEstadoGuiaLocal(guiaId, "EN_CURSO")
            } else if (tipo == TipoEventoOption.FIN) {
                dbHelper.actualizarEstadoGuiaLocal(guiaId, "FINALIZADO")
            }
            servidorAlcanzable = e.code() < 500
            Log.w("CARGAUY", "Error HTTP ${e.code()} al registrar evento: ${e.message}")
            ResultadoOperacionEvento(
                evento = eventoLocal,
                guardadoOffline = true,
                mensajeUsuario = SyncAuthErrorHelper.mensajeRegistroHttpError(
                    e.code(),
                    SesionMovil.esSesionOidc,
                    tipo.etiqueta
                )
            )
        } catch (e: Exception) {
            if (tipo == TipoEventoOption.INICIO) {
                dbHelper.actualizarEstadoGuiaLocal(guiaId, "EN_CURSO")
            } else if (tipo == TipoEventoOption.FIN) {
                dbHelper.actualizarEstadoGuiaLocal(guiaId, "FINALIZADO")
            }
            servidorAlcanzable = false
            Log.w("CARGAUY", "Guardado offline por falta de conexion: ${e.message}")
            ResultadoOperacionEvento(
                evento = eventoLocal,
                guardadoOffline = true,
                mensajeUsuario = "${tipo.etiqueta} guardado en el teléfono. Se enviará cuando vuelva la señal."
            )
        }
    }

    /**
     * CU Crítico 2: Sincronizar eventos móviles sin conexión.
     */
    suspend fun sincronizarPendientes(cedulaChofer: Int): ResumenSincronizacion? = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val pendientes = dbHelper.listarEventosPendientes()
            if (pendientes.isEmpty()) {
                return@withContext null
            }

            if (!hayConexionInternet()) {
                servidorAlcanzable = false
                Log.d("CARGAUY", "Intento de sincronizar sin conexion a internet activa. Cancelado.")
                return@withContext ResumenSincronizacion(
                    totalEnviados = pendientes.size,
                    aceptados = 0,
                    duplicados = 0,
                    errores = pendientes.size,
                    rutaActualizada = false,
                    mensaje = "Sin señal. Quedan ${pendientes.size} evento(s) por enviar."
                )
            }

            var aceptados = 0
            var duplicados = 0
            var errores = 0

            return@withContext try {
                Log.d("CARGAUY", "Sincronizando lote de ${pendientes.size} eventos pendientes...")
                asegurarTokenLocal(cedulaChofer)

                val guiasPrevias = dbHelper.obtenerGuiasCache(cedulaChofer)
                var rutaActualizada = false
                var pendientesAEnviar = pendientes
                var conflictosPendientes: List<EventoLocal> = emptyList()
                var mapaGuiasServidor: Map<Long, Viaje> = emptyMap()

                val guiasServidor: List<Viaje>? = try {
                    RetrofitClient.executeWithFallback { api -> api.obtenerViajes(cedulaChofer) }
                } catch (e: retrofit2.HttpException) {
                    if (SyncAuthErrorHelper.esErrorAutenticacion(e.code()) && !SesionMovil.esSesionOidc) {
                        asegurarTokenLocal(cedulaChofer, forzarRenovacion = true)
                        try {
                            RetrofitClient.executeWithFallback { api -> api.obtenerViajes(cedulaChofer) }
                        } catch (e2: retrofit2.HttpException) {
                            if (SyncAuthErrorHelper.esErrorAutenticacion(e2.code())) throw e2 else null
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        throw e
                    }
                } catch (_: Exception) {
                    null
                }

                if (guiasServidor != null) {
                    servidorAlcanzable = true
                    mapaGuiasServidor = guiasServidor.associateBy { it.id }
                    val resultadoFiltro = SyncConflictResolver.filtrarPendientesContraGuias(
                        pendientes = pendientes,
                        guiasPrevias = guiasPrevias,
                        guiasServidor = guiasServidor
                    )
                    rutaActualizada = resultadoFiltro.rutaActualizada
                    conflictosPendientes = resultadoFiltro.enConflicto
                    pendientesAEnviar = resultadoFiltro.enviables
                }

                val eventosPorUuid = pendientesAEnviar.associateBy { it.uuid }
                val dtos = pendientesAEnviar.map { ev ->
                    val mat = mapaGuiasServidor[ev.guiaId]?.vehiculoMatricula
                        ?: dbHelper.obtenerGuiaPorId(ev.guiaId)?.vehiculoMatricula
                    ev.toDto(mat)
                }
                val subLotes = if (dtos.isEmpty()) emptyList() else SyncBatchChunker.particionarLotes(dtos)

                for (subLote in subLotes) {
                    val respuestas = try {
                        RetrofitClient.executeWithFallback { api ->
                            api.sincronizarEventos(subLote)
                        }
                    } catch (e: retrofit2.HttpException) {
                        if (SyncAuthErrorHelper.esErrorAutenticacion(e.code()) && !SesionMovil.esSesionOidc) {
                            asegurarTokenLocal(cedulaChofer, forzarRenovacion = true)
                            RetrofitClient.executeWithFallback { api ->
                                api.sincronizarEventos(subLote)
                            }
                        } else {
                            throw e
                        }
                    }
                    servidorAlcanzable = true

                    val mapaRespuestas = respuestas.associateBy { it.uuid }
                    for (dtoEnviado in subLote) {
                        val ev = eventosPorUuid[dtoEnviado.uuid] ?: continue
                        val r = mapaRespuestas[ev.uuid]
                        when (EstadoSyncLocal.desdeRespuestaServidor(r?.estado)) {
                            EstadoSyncLocal.SINCRONIZADO -> {
                                aceptados++
                                dbHelper.actualizarEstadoEvento(
                                    ev.uuid,
                                    EstadoSyncLocal.SINCRONIZADO,
                                    r?.mensaje ?: "Enviado"
                                )
                            }

                            EstadoSyncLocal.DUPLICADO -> {
                                duplicados++
                                dbHelper.eliminarEventoDuplicado(ev.uuid)
                            }

                            EstadoSyncLocal.ERROR -> {
                                errores++
                                dbHelper.actualizarEstadoEvento(
                                    ev.uuid,
                                    EstadoSyncLocal.ERROR,
                                    r?.mensaje ?: "No se pudo procesar el evento"
                                )
                            }

                            EstadoSyncLocal.PENDIENTE -> {
                                errores++
                                dbHelper.actualizarEstadoEvento(
                                    ev.uuid,
                                    EstadoSyncLocal.PENDIENTE,
                                    r?.mensaje ?: "Pendiente de envío"
                                )
                            }
                        }
                    }
                }

                for (conflicto in conflictosPendientes) {
                    errores++
                    dbHelper.actualizarEstadoEvento(
                        conflicto.uuid,
                        EstadoSyncLocal.ERROR,
                        SyncConflictResolver.mensajeConflictoGuia(conflicto.guiaId)
                    )
                }
                if (guiasServidor != null) {
                    dbHelper.guardarGuiasCache(cedulaChofer, guiasServidor)
                }

                val partesMensaje = mutableListOf<String>()
                if (aceptados > 0) {
                    partesMensaje.add(if (aceptados == 1) "1 evento enviado" else "$aceptados eventos enviados")
                }
                if (rutaActualizada) {
                    partesMensaje.add("Recorrido actualizado")
                }
                if (errores > 0) {
                    partesMensaje.add(if (errores == 1) "1 evento con error" else "$errores eventos con error")
                }

                ResumenSincronizacion(
                    totalEnviados = pendientes.size,
                    aceptados = aceptados,
                    duplicados = duplicados,
                    errores = errores,
                    rutaActualizada = rutaActualizada,
                    mensaje = partesMensaje.joinToString(" • ").ifBlank { "Todo al día." }
                )
            } catch (e: retrofit2.HttpException) {
                servidorAlcanzable = e.code() < 500
                Log.w("CARGAUY", "Error HTTP ${e.code()} al sincronizar lote pendiente: ${e.message}")
                val restantes = (pendientes.size - aceptados - duplicados).coerceAtLeast(0)
                val msg = SyncAuthErrorHelper.mensajeSyncHttpError(
                    httpCode = e.code(),
                    esSesionOidc = SesionMovil.esSesionOidc,
                    aceptados = aceptados,
                    restantes = restantes
                )
                ResumenSincronizacion(
                    totalEnviados = pendientes.size,
                    aceptados = aceptados,
                    duplicados = duplicados,
                    errores = restantes,
                    rutaActualizada = false,
                    mensaje = msg
                )
            } catch (e: Exception) {
                servidorAlcanzable = false
                Log.w("CARGAUY", "Fallo al sincronizar lote pendiente: ${e.message}")
                val restantes = (pendientes.size - aceptados - duplicados).coerceAtLeast(0)
                val msg = if (aceptados + duplicados > 0) {
                    "Se enviaron $aceptados evento(s). Quedan $restantes por enviar cuando vuelva la señal."
                } else {
                    "Sin señal. Quedan ${pendientes.size} evento(s) por enviar."
                }
                ResumenSincronizacion(
                    totalEnviados = pendientes.size,
                    aceptados = aceptados,
                    duplicados = duplicados,
                    errores = restantes,
                    rutaActualizada = false,
                    mensaje = msg
                )
            }
        }
    }

    suspend fun listarEventosCombinadosGuia(guiaId: Long): List<EventoLocal> = withContext(Dispatchers.IO) {
        val locales = dbHelper.listarEventosPorGuiaLocal(guiaId)
        val mapaPorUuid = LinkedHashMap<String, EventoLocal>()

        if (!hayConexionInternet()) {
            servidorAlcanzable = false
            return@withContext locales.sortedBy { it.tiempo }
        }

        try {
            val remotos = RetrofitClient.executeWithFallback { api ->
                api.listarEventosPorGuia(guiaId)
            }
            servidorAlcanzable = true
            for (rem in remotos) {
                if (rem.tipo.equals("POSICION", ignoreCase = true) && (rem.distanciaDesvioKm ?: 0.0) <= 5.0) {
                    continue
                }
                mapaPorUuid[rem.uuid] = EventoLocal(
                    uuid = rem.uuid,
                    guiaId = rem.guiaId,
                    tipo = rem.tipo,
                    tiempo = rem.tiempo,
                    latitud = rem.latitud ?: 0.0,
                    longitud = rem.longitud ?: 0.0,
                    descripcion = rem.incidente?.descripcion,
                    foto = rem.incidente?.foto,
                    distanciaDesvioKm = rem.distanciaDesvioKm,
                    cantidadKg = rem.cantidadKg,
                    mercaderia = rem.mercaderia,
                    empresaCarga = rem.empresaCarga,
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
