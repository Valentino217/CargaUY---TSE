package com.example.cargauyandroid

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cargauyandroid.ui.theme.CargaUYAndroidTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var callbackOidcUri = mutableStateOf<Uri?>(null)

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.data?.scheme == "uy.cargauy") {
            callbackOidcUri.value = intent?.data
        }
        setContent {
            CargaUYAndroidTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavegationComponent(
                            deepLinkUri = callbackOidcUri.value,
                            onDeepLinkConsumido = { callbackOidcUri.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.scheme == "uy.cargauy") {
            callbackOidcUri.value = intent.data
        }
    }
}

@Composable
fun NavegationComponent(
    deepLinkUri: Uri? = null,
    onDeepLinkConsumido: () -> Unit = {}
) {
    val context = LocalContext.current
    val syncRepository = remember { SyncRepository(context) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var cedulaTexto by rememberSaveable {
        mutableStateOf(SesionMovil.cedulaAsociada?.toString() ?: "")
    }
    val cedulaActual = cedulaTexto.toIntOrNull() ?: SesionMovil.cedulaAsociada ?: Constants.DEFAULT_CEDULA

    var enLinea by remember { mutableStateOf(syncRepository.servidorAlcanzable) }
    var pendientesCount by remember { mutableIntStateOf(syncRepository.dbHelper.contarEventosPendientes()) }
    var sincronizando by remember { mutableStateOf(false) }
    var mensajeGlobalSync by remember { mutableStateOf<String?>(null) }
    var syncTick by remember { mutableIntStateOf(0) }
    var sesionTick by remember { mutableIntStateOf(0) }
    var autenticandoOidc by remember { mutableStateOf(false) }

    // Procesar retorno del login OIDC PKCE desde Keycloak / gub.uy
    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        autenticandoOidc = true
        mensajeGlobalSync = "Ingresando con gub.uy..."
        try {
            val resultado = OidcAuthManager.procesarCallback(uri)
            resultado.onSuccess { res ->
                mensajeGlobalSync = "¡Hola, ${res.nombre ?: "Chofer"}!"
                res.ci?.let { ci ->
                    cedulaTexto = ci.toString()
                }
                sesionTick++
            }.onFailure { err ->
                mensajeGlobalSync = "Error al iniciar sesión: ${err.message}"
            }
        } finally {
            autenticandoOidc = false
            onDeepLinkConsumido()
        }
    }

    fun refrescarEstadoLocal() {
        enLinea = syncRepository.servidorAlcanzable
        pendientesCount = syncRepository.dbHelper.contarEventosPendientes()
    }

    fun ejecutarSincronizacion(mostrarSiVacio: Boolean = false) {
        scope.launch {
            refrescarEstadoLocal()
            val pendientesAntes = syncRepository.dbHelper.contarEventosPendientes()
            if (pendientesAntes == 0) {
                if (mostrarSiVacio) {
                    mensajeGlobalSync = "Todo está al día."
                }
                return@launch
            }
            sincronizando = true
            val resumen = syncRepository.sincronizarPendientes(cedulaActual)
            sincronizando = false
            refrescarEstadoLocal()
            if (resumen != null) {
                mensajeGlobalSync = resumen.mensaje
                syncTick++
            }
        }
    }

    LaunchedEffect(Unit) {
        refrescarEstadoLocal()
        if (syncRepository.baseDatosReiniciadaPorNuevaCompilacion) {
        } else if (enLinea && pendientesCount > 0) {
            ejecutarSincronizacion()
        }
    }

    // CU Critico 2: Escuchar cuando el telefono recupera conexion a internet para sincronizar automaticamente
    DisposableEffect(cedulaActual) {
        val callback = syncRepository.registrarCallbackConectividad(
            onNetworkAvailable = {
                scope.launch {
                    refrescarEstadoLocal()
                    val pendientes = syncRepository.dbHelper.contarEventosPendientes()
                    pendientesCount = pendientes
                    if (pendientes > 0) {
                        sincronizando = true
                        val resumen = syncRepository.sincronizarPendientes(cedulaActual)
                        sincronizando = false
                        pendientesCount = syncRepository.dbHelper.contarEventosPendientes()
                        if (resumen != null) {
                            mensajeGlobalSync = "Señal recuperada: ${resumen.mensaje}"
                            syncTick++
                        }
                    }
                }
            },
            onNetworkLost = {
                scope.launch {
                    enLinea = false
                    refrescarEstadoLocal()
                }
            }
        )
        onDispose {
            syncRepository.desregistrarCallbackConectividad(callback)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BarraEstadoConexionSync(
            enLinea = enLinea,
            pendientesCount = pendientesCount,
            sincronizando = sincronizando,
            mensajeGlobalSync = mensajeGlobalSync,
            onSincronizarClick = { ejecutarSincronizacion(mostrarSiVacio = true) },
            onCerrarMensaje = { mensajeGlobalSync = null }
        )

        NavHost(
            navController = navController,
            startDestination = "Main",
            modifier = Modifier.weight(1f)
        ) {
            composable("Main") {
                MainScreen(
                    navController = navController,
                    cedulaTexto = cedulaTexto,
                    onCedulaChange = { cedulaTexto = it },
                    syncRepository = syncRepository,
                    pendientesCount = pendientesCount,
                    autenticandoOidc = autenticandoOidc,
                    sesionTick = sesionTick,
                    onMensajeGlobal = { mensajeGlobalSync = it },
                    onEventoChange = {
                        refrescarEstadoLocal()
                        ejecutarSincronizacion(mostrarSiVacio = true)
                    },
                    onLimpiarBd = {
                        syncRepository.limpiarBaseDeDatosManual()
                        refrescarEstadoLocal()
                        mensajeGlobalSync = "Datos locales borrados."
                        syncTick++
                    }
                )
            }

            composable("viajes") {
                PantallaViajes(
                    navController = navController,
                    cedulaChofer = cedulaActual,
                    syncRepository = syncRepository,
                    syncTick = syncTick,
                    onEstadoActualizado = { refrescarEstadoLocal() }
                )
            }

            composable(
                route = "registrarEvento/{guiaId}",
                arguments = listOf(navArgument("guiaId") { type = NavType.LongType })
            ) { backStackEntry ->
                val guiaId = backStackEntry.arguments?.getLong("guiaId") ?: 1L
                PantallaRegistrarEvento(
                    navController = navController,
                    guiaId = guiaId,
                    cedulaChofer = cedulaActual,
                    syncRepository = syncRepository,
                    onEventoRegistrado = { msg ->
                        refrescarEstadoLocal()
                        mensajeGlobalSync = msg
                        syncTick++
                    }
                )
            }

            composable(
                route = "eventosGuia/{guiaId}",
                arguments = listOf(navArgument("guiaId") { type = NavType.LongType })
            ) { backStackEntry ->
                val guiaId = backStackEntry.arguments?.getLong("guiaId") ?: 1L
                PantallaEventosGuia(
                    navController = navController,
                    guiaId = guiaId,
                    syncRepository = syncRepository,
                    syncTick = syncTick
                )
            }
        }
    }
}

@Composable
fun BarraEstadoConexionSync(
    enLinea: Boolean,
    pendientesCount: Int,
    sincronizando: Boolean,
    mensajeGlobalSync: String?,
    onSincronizarClick: () -> Unit,
    onCerrarMensaje: () -> Unit
) {
    val bgColor = if (enLinea) Color(0xFFE0F2FE) else Color(0xFFFEF3C7)
    val textColor = if (enLinea) Color(0xFF0369A1) else Color(0xFF92400E)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enLinea) "● Conectado" else "○ Sin señal",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = if (pendientesCount > 0) {
                        if (pendientesCount == 1) "1 evento pendiente de envío" else "$pendientesCount eventos pendientes de envío"
                    } else {
                        "Todo al día"
                    },
                    color = textColor,
                    fontSize = 12.sp
                )
            }

            if (pendientesCount > 0) {
                Button(
                    onClick = onSincronizarClick,
                    enabled = !sincronizando,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(if (sincronizando) "Enviando..." else "Enviar ($pendientesCount)")
                }
            }
        }

        if (!mensajeGlobalSync.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = mensajeGlobalSync,
                        fontSize = 12.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onCerrarMensaje,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("OK", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    navController: NavController,
    cedulaTexto: String,
    onCedulaChange: (String) -> Unit,
    syncRepository: SyncRepository,
    pendientesCount: Int,
    autenticandoOidc: Boolean = false,
    sesionTick: Int = 0,
    onMensajeGlobal: (String) -> Unit = {},
    onEventoChange: () -> Unit,
    onLimpiarBd: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var buscandoViajeActivo by remember { mutableStateOf(false) }
    var cargandoLoginGub by remember { mutableStateOf(false) }
    var cargandoDevToken by remember { mutableStateOf(false) }
    var mostrarAvanzadoDev by rememberSaveable { mutableStateOf(false) }
    var modoDevHabilitado by rememberSaveable { mutableStateOf(Constants.HABILITAR_OPCIONES_DEV) }
    var contadorClicksDev by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "CargaUY",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E3A8A),
            modifier = Modifier.clickable {
                contadorClicksDev++
                if (contadorClicksDev >= 5) {
                    modoDevHabilitado = !modoDevHabilitado
                    contadorClicksDev = 0
                    onMensajeGlobal(if (modoDevHabilitado) "Opciones de desarrollo habilitadas" else "Opciones de desarrollo ocultas")
                }
            }
        )
        Text(
            text = "Tus viajes y registro en ruta",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF475569)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta de Identificación y Autenticación OIDC con gub.uy / Keycloak
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (SesionMovil.estaAutenticado) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (SesionMovil.estaAutenticado) Color(0xFF86EFAC) else Color(0xFFCBD5E1)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (SesionMovil.estaAutenticado) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("● ", color = Color(0xFF16A34A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Sesión activa",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = SesionMovil.nombreChofer ?: "Chofer",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B),
                                fontSize = 15.sp
                            )
                            Text(
                                text = "CI: ${SesionMovil.cedulaAsociada ?: cedulaTexto}",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                SesionMovil.limpiar()
                                onCedulaChange("")
                                onMensajeGlobal("Sesión cerrada.")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB91C1C))
                        ) {
                            Text("Salir", fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(
                        text = "Iniciar sesión",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Ingresá con tu usuario gub.uy para ver tus viajes asignados.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            cargandoLoginGub = true
                            mensajeError = null
                            scope.launch {
                                val resultado = OidcAuthManager.iniciarFlujoLogin(context)
                                cargandoLoginGub = false
                                resultado.onFailure { err ->
                                    mensajeError = "No se pudo abrir gub.uy: ${err.message}"
                                }
                            }
                        },
                        enabled = !cargandoLoginGub && !autenticandoOidc,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F4C81)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (cargandoLoginGub || autenticandoOidc) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abriendo gub.uy...")
                        } else {
                            Text(
                                "Ingresar con gub.uy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        if (modoDevHabilitado) {
            Spacer(modifier = Modifier.height(12.dp))

            // Opciones de pruebas de desarrollo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mostrarAvanzadoDev = !mostrarAvanzadoDev }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mostrarAvanzadoDev) "▲ Ocultar opciones de pruebas (Dev)" else "▼ Opciones de pruebas y manual (Dev)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }

            if (mostrarAvanzadoDev) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Conexión con el Servidor:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "Activo: ${RetrofitClient.activeBaseUrl}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    RetrofitClient.actualizarBaseUrl("http://10.0.2.2:8080/")
                                    onMensajeGlobal("Cambiado a host de emulador 10.0.2.2:8080")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("10.0.2.2 (Emulador)", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    RetrofitClient.actualizarBaseUrl("http://10.0.0.16:8080/")
                                    onMensajeGlobal("Cambiado a host LAN 10.0.0.16:8080")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("10.0.0.16 (LAN)", fontSize = 10.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Configuración manual de chofer (sin OIDC)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cedulaTexto,
                            onValueChange = { nuevo ->
                                onCedulaChange(nuevo.filter { it.isDigit() })
                            },
                            label = { Text("Cédula de Identidad manual (CI)") },
                            placeholder = { Text("Ej: 11223344") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        var tokenTexto by remember(SesionMovil.token) { mutableStateOf(SesionMovil.token) }
                        OutlinedTextField(
                            value = tokenTexto,
                            onValueChange = {
                                tokenTexto = it.trim()
                                SesionMovil.token = tokenTexto
                            },
                            label = { Text("Token Bearer manual") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val ci = cedulaTexto.toIntOrNull() ?: Constants.DEFAULT_CEDULA
                                cargandoDevToken = true
                                scope.launch {
                                    try {
                                        val res = RetrofitClient.executeWithFallback { api ->
                                            api.obtenerDevToken(mapOf("ci" to ci))
                                        }
                                        val tok = (res["token"] as? String) ?: ""
                                        SesionMovil.token = tok
                                        SesionMovil.cedulaAsociada = ci
                                        SesionMovil.nombreChofer = (res["nombre"] as? String) ?: "Chofer Dev #$ci"
                                        tokenTexto = tok
                                        cargandoDevToken = false
                                        onMensajeGlobal("Token Dev obtenido para CI $ci")
                                    } catch (e: Exception) {
                                        cargandoDevToken = false
                                        onMensajeGlobal("Fallo al obtener token Dev: ${e.message}")
                                    }
                                }
                            },
                            enabled = !cargandoDevToken,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (cargandoDevToken) "Obteniendo token..." else "Obtener Token de Prueba Dev")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                RetrofitClient.actualizarBaseUrl(Constants.CLOUD_URL)
                                onMensajeGlobal("Cambiado a ElastiCloud (${Constants.CLOUD_URL})")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("☁️ ElastiCloud", fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onLimpiarBd,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Borrar datos guardados en el teléfono")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { navController.navigate("viajes") },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Mis Viajes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (pendientesCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onEventoChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar eventos pendientes ($pendientesCount)")
            }
        }

        if (mensajeError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = mensajeError!!,
                    color = Color(0xFF991B1B),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun PantallaViajes(
    navController: NavController,
    cedulaChofer: Int,
    syncRepository: SyncRepository,
    syncTick: Int,
    onEstadoActualizado: () -> Unit
) {
    var guias by remember { mutableStateOf<List<Viaje>>(emptyList()) }
    var desdeCache by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun cargarGuias() {
        scope.launch {
            cargando = true
            val res = syncRepository.consultarGuiasChofer(cedulaChofer)
            guias = res.guias
            desdeCache = res.desdeCacheOffline
            aviso = res.mensajeAviso
            cargando = false
            onEstadoActualizado()
        }
    }

    LaunchedEffect(cedulaChofer, syncTick) {
        cargarGuias()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mis Viajes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )
                Text(
                    text = SesionMovil.nombreChofer ?: "CI: $cedulaChofer",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            }
            OutlinedButton(onClick = { cargarGuias() }) {
                Text("Actualizar")
            }
        }

        if (aviso != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = aviso!!,
                    color = Color(0xFF92400E),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (cargando) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (guias.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tenés viajes asignados en este momento.",
                    color = Color(0xFF64748B)
                )
            }
        } else {
            val guiaEnCurso = guias.firstOrNull { it.estado.equals("EN_CURSO", ignoreCase = true) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(guias) { viaje ->
                    val otraGuiaEnCursoId = if (guiaEnCurso != null && guiaEnCurso.id != viaje.id) {
                        guiaEnCurso.id
                    } else {
                        null
                    }
                    TarjetaGuiaViaje(
                        viaje = viaje,
                        otraGuiaEnCursoId = otraGuiaEnCursoId,
                        onRegistrarEvento = {
                            navController.navigate("registrarEvento/${viaje.id}")
                        },
                        onVerEventos = {
                            navController.navigate("eventosGuia/${viaje.id}")
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }
    }
}

@Composable
fun TarjetaGuiaViaje(
    viaje: Viaje,
    otraGuiaEnCursoId: Long? = null,
    onRegistrarEvento: () -> Unit,
    onVerEventos: () -> Unit
) {
    val badgeBg = when (viaje.estado.uppercase()) {
        "EN_CURSO" -> Color(0xFFE0F2FE)
        "FINALIZADO" -> Color(0xFFDCFCE7)
        else -> Color(0xFFF1F5F9)
    }
    val badgeText = when (viaje.estado.uppercase()) {
        "EN_CURSO" -> Color(0xFF0369A1)
        "FINALIZADO" -> Color(0xFF166534)
        else -> Color(0xFF475569)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Viaje #${viaje.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E3A8A)
                )
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = TipoEventoOption.etiquetaEstadoViaje(viaje.estado),
                        color = badgeText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${viaje.origen} → ${viaje.destino}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Fecha: ${viaje.fecha ?: "-"}",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )
            Text(
                text = "Matrícula: ${viaje.vehiculoMatricula ?: "S/D"}",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )
            if (!viaje.empresaRazonSocial.isNullOrBlank()) {
                Text(
                    text = "Empresa: ${viaje.empresaRazonSocial}",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            val viajeFinalizado = viaje.estado.equals("FINALIZADO", ignoreCase = true)
            val viajeSinIniciar = viaje.estado.equals("SIN_INICIAR", ignoreCase = true)
            val bloqueadoPorOtraEnCurso = viajeSinIniciar && otraGuiaEnCursoId != null
            if (bloqueadoPorOtraEnCurso) {
                Text(
                    text = "Tenés el Viaje #$otraGuiaEnCursoId en curso. Finalizalo antes de iniciar este.",
                    fontSize = 12.sp,
                    color = Color(0xFFB45309),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRegistrarEvento,
                    enabled = !viajeFinalizado && !bloqueadoPorOtraEnCurso,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            viajeFinalizado -> "Finalizado"
                            bloqueadoPorOtraEnCurso -> "En espera"
                            viajeSinIniciar -> "Iniciar Viaje"
                            else -> "Registrar Evento"
                        }
                    )
                }
                OutlinedButton(
                    onClick = onVerEventos,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Historial")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaRegistrarEvento(
    navController: NavController,
    guiaId: Long,
    cedulaChofer: Int,
    syncRepository: SyncRepository,
    onEventoRegistrado: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guiaLocal by remember(guiaId) { mutableStateOf(syncRepository.dbHelper.obtenerGuiaPorId(guiaId)) }
    var estadoGuiaActual by remember(guiaId) { mutableStateOf(guiaLocal?.estado) }
    val otraGuiaEnCurso = remember(guiaId, estadoGuiaActual, cedulaChofer) {
        syncRepository.dbHelper.obtenerGuiaEnCurso(cedulaChofer, excluirGuiaId = guiaId)
    }

    val opcionesPermitidas = remember(estadoGuiaActual) {
        TipoEventoOption.opcionesPermitidasParaEstado(estadoGuiaActual)
    }
    val viajeFinalizado = estadoGuiaActual.equals("FINALIZADO", ignoreCase = true)
    val bloqueadoPorOtraEnCurso = estadoGuiaActual.equals("SIN_INICIAR", ignoreCase = true) && otraGuiaEnCurso != null

    var tipoSeleccionado by rememberSaveable {
        mutableStateOf(opcionesPermitidas.firstOrNull() ?: TipoEventoOption.INICIO)
    }

    LaunchedEffect(opcionesPermitidas) {
        if (opcionesPermitidas.isNotEmpty() && tipoSeleccionado !in opcionesPermitidas) {
            tipoSeleccionado = opcionesPermitidas.first()
        }
    }

    // Campos 2.C y 2.D (Carga parcial y Descarga parcial)
    var cantidadKg by rememberSaveable { mutableStateOf("") }
    var queSeCargaDescarga by rememberSaveable { mutableStateOf(guiaLocal?.rubroCliente ?: "") }
    var empresaCarga by rememberSaveable { mutableStateOf(guiaLocal?.empresaRazonSocial ?: "") }

    // Campos 2.E (Incidente con foto opcional comprimida en Base64 Data URI)
    var descripcionIncidente by rememberSaveable { mutableStateOf("") }
    var fotoIncidente by remember { mutableStateOf("") }

    var errorValidacion by remember { mutableStateOf<String?>(null) }
    var resultadoRegistro by remember { mutableStateOf<ResultadoOperacionEvento?>(null) }
    var guardando by remember { mutableStateOf(false) }

    // Selector de foto opcional desde galería (comprimida a JPEG Base64 liviano)
    val selectorFotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val dataUri = ImagenUtils.codificarUriADataUri(context, uri)
            if (dataUri != null) {
                fotoIncidente = dataUri
                errorValidacion = null
            } else {
                errorValidacion = "No se pudo cargar la imagen seleccionada."
            }
        }
    }

    // Captura de foto opcional con cámara del dispositivo
    val camaraFotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            fotoIncidente = ImagenUtils.codificarBitmapADataUri(bitmap)
            errorValidacion = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Registrar Evento",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E3A8A)
        )
        Text(
            text = if (guiaLocal != null) {
                "Viaje #${guiaLocal!!.id}: ${guiaLocal!!.origen} → ${guiaLocal!!.destino}"
            } else {
                "Viaje #$guiaId"
            },
            fontSize = 14.sp,
            color = Color(0xFF475569)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (viajeFinalizado) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Este viaje ya fue finalizado.",
                    color = Color(0xFF166534),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else if (bloqueadoPorOtraEnCurso) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ya tenés el Viaje #${otraGuiaEnCurso?.id} en curso. Finalizalo antes de iniciar otro.",
                    color = Color(0xFF92400E),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Text(
                text = "¿Qué querés registrar?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                opcionesPermitidas.forEach { opcion ->
                    FilterChip(
                        selected = (tipoSeleccionado == opcion),
                        onClick = {
                            tipoSeleccionado = opcion
                            errorValidacion = null
                        },
                        label = { Text(opcion.etiqueta) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tipoSeleccionado.descripcionAyuda,
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Formulario dinamico segun el flujo alternativo (2.A, 2.B, 2.C, 2.D, 2.E)
            when (tipoSeleccionado) {
                TipoEventoOption.INICIO, TipoEventoOption.FIN -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = tipoSeleccionado.etiqueta,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (tipoSeleccionado == TipoEventoOption.INICIO) {
                                    "Tocá el botón de abajo para dar comienzo al viaje."
                                } else {
                                    "Tocá el botón de abajo para finalizar el viaje."
                                },
                                fontSize = 13.sp,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                    }
                }

                TipoEventoOption.CARGA_PARCIAL, TipoEventoOption.DESCARGA_PARCIAL -> {
                    val esDescarga = (tipoSeleccionado == TipoEventoOption.DESCARGA_PARCIAL)

                    OutlinedTextField(
                        value = cantidadKg,
                        onValueChange = { nuevo ->
                            cantidadKg = nuevo.filter { it.isDigit() || it == '.' }
                        },
                        label = { Text(if (esDescarga) "Kilos descargados *" else "Kilos cargados *") },
                        placeholder = { Text("Ej: 5000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = queSeCargaDescarga,
                        onValueChange = { queSeCargaDescarga = it },
                        label = { Text("Mercadería *") },
                        placeholder = { Text("Ej: Madera") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = empresaCarga,
                        onValueChange = { empresaCarga = it },
                        label = { Text("Empresa / Cliente *") },
                        placeholder = { Text("Ej: Transportes del Plata S.A.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TipoEventoOption.INCIDENTE -> {
                    OutlinedTextField(
                        value = descripcionIncidente,
                        onValueChange = { descripcionIncidente = it },
                        label = { Text("¿Qué pasó? *") },
                        placeholder = { Text("Ej: Pinchadura de neumático") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Foto (opcional):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    selectorFotoLauncher.launch("image/*")
                                } catch (_: Exception) {
                                    errorValidacion = "No se pudo abrir la galería."
                                }
                            }
                        ) {
                            Text("Elegir de galería")
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    camaraFotoLauncher.launch(null)
                                } catch (_: Exception) {
                                    errorValidacion = "Cámara no disponible."
                                }
                            }
                        ) {
                            Text("Sacar foto")
                        }
                        if (fotoIncidente.isNotBlank()) {
                            OutlinedButton(
                                onClick = { fotoIncidente = "" },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                            ) {
                                Text("Quitar foto")
                            }
                        }
                    }

                    if (fotoIncidente.isNotBlank()) {
                        val previewBitmap = remember(fotoIncidente) {
                            ImagenUtils.decodificarDataUriABitmap(fotoIncidente)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = "Vista previa foto del incidente",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(
                                    text = "Foto adjunta",
                                    fontSize = 12.sp,
                                    color = Color(0xFF0369A1),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        if (errorValidacion != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorValidacion!!,
                    color = Color(0xFF991B1B),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (resultadoRegistro != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val bgRes = when {
                resultadoRegistro!!.rechazadoPorServidor -> Color(0xFFFEE2E2)
                resultadoRegistro!!.guardadoOffline -> Color(0xFFFEF3C7)
                else -> Color(0xFFDCFCE7)
            }
            val txtRes = when {
                resultadoRegistro!!.rechazadoPorServidor -> Color(0xFF991B1B)
                resultadoRegistro!!.guardadoOffline -> Color(0xFF92400E)
                else -> Color(0xFF166534)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = bgRes),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = resultadoRegistro!!.mensajeUsuario,
                        color = txtRes,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (!viajeFinalizado && !bloqueadoPorOtraEnCurso) {
            Button(
                onClick = {
                    errorValidacion = null
                    var detalleFinal: String? = null
                    var fotoFinal: String? = null
                    var cantidadKgVal: Double? = null
                    var mercaderiaVal: String? = null
                    var empresaVal: String? = null

                    when (tipoSeleccionado) {
                        TipoEventoOption.INICIO, TipoEventoOption.FIN -> {
                            detalleFinal = null
                        }

                        TipoEventoOption.CARGA_PARCIAL, TipoEventoOption.DESCARGA_PARCIAL -> {
                            val parsedKg = cantidadKg.toDoubleOrNull()
                            if (parsedKg == null || parsedKg <= 0.0) {
                                errorValidacion = "Ingresá una cantidad en kilos mayor a 0."
                                return@Button
                            }
                            if (queSeCargaDescarga.isBlank()) {
                                errorValidacion = "Indicá qué mercadería es."
                                return@Button
                            }
                            if (empresaCarga.isBlank()) {
                                errorValidacion = "Indicá la empresa o cliente."
                                return@Button
                            }
                            cantidadKgVal = parsedKg
                            mercaderiaVal = queSeCargaDescarga.trim()
                            empresaVal = empresaCarga.trim()
                        }

                        TipoEventoOption.INCIDENTE -> {
                            if (descripcionIncidente.isBlank()) {
                                errorValidacion = "Describí brevemente el incidente."
                                return@Button
                            }
                            detalleFinal = descripcionIncidente.trim()
                            fotoFinal = fotoIncidente.trim().takeIf { it.isNotBlank() }
                        }
                    }

                    guardando = true
                    scope.launch {
                        val res = syncRepository.registrarEvento(
                            guiaId = guiaId,
                            tipo = tipoSeleccionado,
                            descripcion = detalleFinal,
                            foto = fotoFinal,
                            cedulaChofer = cedulaChofer,
                            cantidadKg = cantidadKgVal,
                            mercaderia = mercaderiaVal,
                            empresaCarga = empresaVal
                        )
                        guardando = false
                        resultadoRegistro = res
                        guiaLocal = syncRepository.dbHelper.obtenerGuiaPorId(guiaId)
                        estadoGuiaActual = guiaLocal?.estado ?: estadoGuiaActual
                        if (!res.rechazadoPorServidor) {
                            if (tipoSeleccionado == TipoEventoOption.INCIDENTE) {
                                descripcionIncidente = ""
                                fotoIncidente = ""
                            } else if (tipoSeleccionado == TipoEventoOption.CARGA_PARCIAL || tipoSeleccionado == TipoEventoOption.DESCARGA_PARCIAL) {
                                cantidadKg = ""
                            }
                        }
                        onEventoRegistrado(res.mensajeUsuario)
                    }
                },
                enabled = !guardando,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    when {
                        guardando -> "Guardando..."
                        tipoSeleccionado == TipoEventoOption.INICIO -> "Confirmar Inicio de Viaje"
                        tipoSeleccionado == TipoEventoOption.FIN -> "Confirmar Fin de Viaje"
                        else -> "Guardar ${tipoSeleccionado.etiqueta}"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { navController.navigate("eventosGuia/$guiaId") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Ver Historial")
            }
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Volver")
            }
        }
    }
}

@Composable
fun PantallaEventosGuia(
    navController: NavController,
    guiaId: Long,
    syncRepository: SyncRepository,
    syncTick: Int
) {
    var eventos by remember { mutableStateOf<List<EventoLocal>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    val guiaLocal = remember(guiaId, syncTick) { syncRepository.dbHelper.obtenerGuiaPorId(guiaId) }
    val viajeFinalizado = guiaLocal?.estado?.equals("FINALIZADO", ignoreCase = true) == true

    LaunchedEffect(guiaId, syncTick) {
        cargando = true
        eventos = syncRepository.listarEventosCombinadosGuia(guiaId)
        cargando = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Historial del Viaje #$guiaId",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E3A8A)
        )
        if (guiaLocal != null) {
            Text(
                text = "${guiaLocal.origen} → ${guiaLocal.destino}",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (cargando) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (eventos.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Todavía no hay eventos registrados en este viaje.",
                    color = Color(0xFF64748B)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(eventos) { ev ->
                    val esPendiente = ev.estadoSync == EstadoSyncLocal.PENDIENTE.name
                    val esError = ev.estadoSync == EstadoSyncLocal.ERROR.name
                    val badgeBg = when {
                        esError -> Color(0xFFFEE2E2)
                        esPendiente -> Color(0xFFFEF3C7)
                        else -> Color(0xFFDCFCE7)
                    }
                    val badgeTxt = when {
                        esError -> Color(0xFF991B1B)
                        esPendiente -> Color(0xFF92400E)
                        else -> Color(0xFF166534)
                    }
                    val badgeLabel = when {
                        esError -> "Con error"
                        esPendiente -> "Pendiente de envío"
                        else -> "Enviado"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = TipoEventoOption.etiquetaDe(ev.tipo),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF1E3A8A)
                                )
                                Surface(
                                    color = badgeBg,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = badgeLabel,
                                        color = badgeTxt,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ev.tiempo.replace('T', ' '),
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )

                            if (ev.distanciaDesvioKm != null && ev.distanciaDesvioKm > 5.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ Desvío de ruta: ${String.format(java.util.Locale.US, "%.1f", ev.distanciaDesvioKm)} km",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB91C1C)
                                )
                            }

                            if (ev.cantidadKg != null || !ev.mercaderia.isNullOrBlank() || !ev.empresaCarga.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${ev.cantidadKg ?: "-"} kg • ${ev.mercaderia ?: "-"}${if (!ev.empresaCarga.isNullOrBlank()) " (${ev.empresaCarga})" else ""}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            if (!ev.descripcion.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = ev.descripcion,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            if (!ev.foto.isNullOrBlank()) {
                                val fotoBitmap = remember(ev.foto) {
                                    ImagenUtils.decodificarDataUriABitmap(ev.foto)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                if (fotoBitmap != null) {
                                    Image(
                                        bitmap = fotoBitmap.asImageBitmap(),
                                        contentDescription = "Foto del incidente",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Text(
                                        text = "Foto adjunta",
                                        fontSize = 12.sp,
                                        color = Color(0xFF0369A1)
                                    )
                                }
                            }

                            if (esError && !ev.mensajeSync.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Error: ${ev.mensajeSync}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!viajeFinalizado) {
                Button(
                    onClick = { navController.navigate("registrarEvento/$guiaId") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Registrar Evento")
                }
            }
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Volver")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainPreview() {
    CargaUYAndroidTheme {
        TarjetaGuiaViaje(
            viaje = Viaje(
                id = 1L,
                fecha = "2026-10-19",
                origen = "Montevideo",
                destino = "Rivera",
                rubroCliente = "Madera",
                volumenCarga = 45.0f,
                estado = "EN_CURSO",
                version = 1,
                vehiculoMatricula = "STP 1234",
                vehiculoMarcaModelo = "Camion 123",
                nroEmpresa = 101,
                empresaRazonSocial = "Transportes del Plata S.A.",
                choferId = 2,
                choferNombre = "Carlos Chofer"
            ),
            onRegistrarEvento = {},
            onVerEventos = {}
        )
    }
}
