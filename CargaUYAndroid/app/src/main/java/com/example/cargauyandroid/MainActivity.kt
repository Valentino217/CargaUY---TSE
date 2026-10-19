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
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CargaUYAndroidTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavegationComponent()
                    }
                }
            }
        }
    }
}

@Composable
fun NavegationComponent() {
    val context = LocalContext.current
    val syncRepository = remember { SyncRepository(context) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var cedulaTexto by rememberSaveable { mutableStateOf(Constants.DEFAULT_CEDULA.toString()) }
    val cedulaActual = cedulaTexto.toIntOrNull() ?: Constants.DEFAULT_CEDULA

    var enLinea by remember { mutableStateOf(syncRepository.servidorAlcanzable) }
    var pendientesCount by remember { mutableIntStateOf(syncRepository.dbHelper.contarEventosPendientes()) }
    var sincronizando by remember { mutableStateOf(false) }
    var mensajeGlobalSync by remember { mutableStateOf<String?>(null) }
    var syncTick by remember { mutableIntStateOf(0) }

    fun refrescarEstadoLocal() {
        enLinea = syncRepository.servidorAlcanzable
        pendientesCount = syncRepository.dbHelper.contarEventosPendientes()
    }

    fun ejecutarSincronizacionManualOAutomatica(mostrarSiVacio: Boolean = false) {
        scope.launch {
            refrescarEstadoLocal()
            val pendientesAntes = syncRepository.dbHelper.contarEventosPendientes()
            if (pendientesAntes == 0) {
                if (mostrarSiVacio) {
                    mensajeGlobalSync = "No hay eventos pendientes por sincronizar."
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

    // Solicitar permiso de ubicación al iniciar para tener coordenadas GPS listas
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        refrescarEstadoLocal()
        if (enLinea && pendientesCount > 0) {
            ejecutarSincronizacionManualOAutomatica()
        }
    }

    // CU Crítico 2: Escuchar cuando el teléfono recupera conexión a internet para sincronizar automáticamente
    DisposableEffect(cedulaActual) {
        val callback = syncRepository.registrarCallbackConectividad {
            scope.launch {
                enLinea = true
                val pendientes = syncRepository.dbHelper.contarEventosPendientes()
                pendientesCount = pendientes
                if (pendientes > 0) {
                    sincronizando = true
                    val resumen = syncRepository.sincronizarPendientes(cedulaActual)
                    sincronizando = false
                    pendientesCount = syncRepository.dbHelper.contarEventosPendientes()
                    if (resumen != null) {
                        mensajeGlobalSync = "Conexión recuperada: ${resumen.mensaje}"
                        syncTick++
                    }
                }
            }
        }
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
            onSincronizarClick = { ejecutarSincronizacionManualOAutomatica(mostrarSiVacio = true) },
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
                    onEventoChange = {
                        refrescarEstadoLocal()
                        ejecutarSincronizacionManualOAutomatica(mostrarSiVacio = true)
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
                    text = if (enLinea) "● En línea con CargaUY" else "○ Modo sin conexión (Offline)",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = if (pendientesCount > 0) {
                        "$pendientesCount evento(s) guardado(s) en el teléfono pendiente(s) de sincronizar"
                    } else {
                        "Todos los eventos están sincronizados"
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
                    Text(if (sincronizando) "Sincronizando..." else "Sincronizar ($pendientesCount)")
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
    onEventoChange: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var buscandoViajeActivo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "CargaUY - App Chofer",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E3A8A)
        )
        Text(
            text = "Gestión de guías de viaje y registro de eventos en ruta",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF475569)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Identificación del Chofer",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cedulaTexto,
                    onValueChange = { nuevo ->
                        onCedulaChange(nuevo.filter { it.isDigit() })
                    },
                    label = { Text("Cédula de Identidad (CI)") },
                    placeholder = { Text("Ej: 11223344") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Chofer de prueba precargado: 11223344 (Carlos Chofer)",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Operaciones disponibles:",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { navController.navigate("viajes") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ver Mis Guías de Viaje")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                val ci = cedulaTexto.toIntOrNull() ?: Constants.DEFAULT_CEDULA
                buscandoViajeActivo = true
                mensajeError = null
                scope.launch {
                    val resultado = syncRepository.consultarGuiasChofer(ci)
                    buscandoViajeActivo = false
                    val guiaActiva = resultado.guias.firstOrNull {
                        it.estado.equals("EN_CURSO", ignoreCase = true)
                    } ?: resultado.guias.firstOrNull {
                        it.estado.equals("SIN_INICIAR", ignoreCase = true)
                    } ?: resultado.guias.firstOrNull()

                    if (guiaActiva != null) {
                        navController.navigate("registrarEvento/${guiaActiva.id}")
                    } else {
                        mensajeError = "No se encontraron guías asignadas para la CI $ci. Consulte 'Ver Mis Guías de Viaje' primero con conexión."
                    }
                }
            },
            enabled = !buscandoViajeActivo,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (buscandoViajeActivo) "Buscando viaje asignado..."
                else "Registrar Evento en Viaje Asignado"
            )
        }

        if (pendientesCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onEventoChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sincronizar Eventos Pendientes ($pendientesCount)")
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
                    text = "Guías de Viaje",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )
                Text(
                    text = "Chofer CI: $cedulaChofer",
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
                    text = "No se encontraron guías de viaje para la CI $cedulaChofer.",
                    color = Color(0xFF64748B)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(guias) { viaje ->
                    TarjetaGuiaViaje(
                        viaje = viaje,
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
            Text("Volver al menú principal")
        }
    }
}

@Composable
fun TarjetaGuiaViaje(
    viaje: Viaje,
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
                    text = "Guía #${viaje.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E3A8A)
                )
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = viaje.estado,
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
                text = "Fecha: ${viaje.fecha ?: "-"} • Rubro: ${viaje.rubroCliente ?: "-"} (${viaje.volumenCarga} m³)",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )
            Text(
                text = "Vehículo: ${viaje.vehiculoMatricula ?: "S/D"} (${viaje.vehiculoMarcaModelo ?: "-"})",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )
            Text(
                text = "Empresa: ${viaje.empresaRazonSocial ?: "-"}",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRegistrarEvento,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Registrar Evento")
                }
                OutlinedButton(
                    onClick = onVerEventos,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver Eventos")
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
    val scope = rememberCoroutineScope()
    val guiaLocal = remember(guiaId) { syncRepository.dbHelper.obtenerGuiaPorId(guiaId) }

    var tipoSeleccionado by rememberSaveable { mutableStateOf(TipoEventoOption.INICIO) }

    // Coordenadas GPS automáticas con fallback
    var coordenadas by remember { mutableStateOf(syncRepository.obtenerUbicacionActual()) }

    // Campos 2.C y 2.D (Carga parcial y Descarga parcial)
    var cantidadKg by rememberSaveable { mutableStateOf("") }
    var queSeCargaDescarga by rememberSaveable { mutableStateOf(guiaLocal?.rubroCliente ?: "") }
    var empresaCarga by rememberSaveable { mutableStateOf(guiaLocal?.empresaRazonSocial ?: "") }

    // Campos 2.E (Incidente)
    var descripcionIncidente by rememberSaveable { mutableStateOf("") }
    var fotoIncidente by rememberSaveable { mutableStateOf("") }

    var errorValidacion by remember { mutableStateOf<String?>(null) }
    var resultadoRegistro by remember { mutableStateOf<ResultadoOperacionEvento?>(null) }
    var guardando by remember { mutableStateOf(false) }

    // Selector de foto opcional de galería (Flujo 2.E.3.A)
    val selectorFotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            fotoIncidente = uri.lastPathSegment ?: uri.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Registrar Evento de Viaje",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E3A8A)
        )
        Text(
            text = if (guiaLocal != null) {
                "Guía #${guiaLocal.id}: ${guiaLocal.origen} → ${guiaLocal.destino} (${guiaLocal.estado})"
            } else {
                "Guía #$guiaId"
            },
            fontSize = 14.sp,
            color = Color(0xFF475569)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjeta de Ubicación y Hora automática
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ubicación GPS y Hora Automática",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Lat: ${coordenadas.first}, Lon: ${coordenadas.second}",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
                OutlinedButton(
                    onClick = { coordenadas = syncRepository.obtenerUbicacionActual() }
                ) {
                    Text("Actualizar GPS", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Seleccione el tipo de evento:",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TipoEventoOption.entries.forEach { opcion ->
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

        // Formulario dinámico según el flujo alternativo (2.A, 2.B, 2.C, 2.D, 2.E)
        when (tipoSeleccionado) {
            TipoEventoOption.INICIO, TipoEventoOption.FIN -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Evento: ${tipoSeleccionado.etiqueta}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Se guardará automáticamente la fecha/hora actual, las coordenadas de ubicación (${coordenadas.first}, ${coordenadas.second}) y un identificador único UUID.",
                            fontSize = 13.sp,
                            color = Color(0xFF1E3A8A)
                        )
                    }
                }
            }

            TipoEventoOption.CARGA_PARCIAL, TipoEventoOption.DESCARGA_PARCIAL -> {
                val esDescarga = (tipoSeleccionado == TipoEventoOption.DESCARGA_PARCIAL)
                val verbo = if (esDescarga) "descarga" else "carga"

                OutlinedTextField(
                    value = cantidadKg,
                    onValueChange = { nuevo ->
                        cantidadKg = nuevo.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Cantidad de $verbo en Kg *") },
                    placeholder = { Text("Ej: 5000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = queSeCargaDescarga,
                    onValueChange = { queSeCargaDescarga = it },
                    label = { Text(if (esDescarga) "¿Qué se descargó? *" else "¿Qué se carga? *") },
                    placeholder = { Text("Ej: Madera de Eucaliptus / Soja") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = empresaCarga,
                    onValueChange = { empresaCarga = it },
                    label = { Text("Empresa a la que pertenece la carga *") },
                    placeholder = { Text("Ej: Transportes del Plata S.A.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TipoEventoOption.INCIDENTE -> {
                OutlinedTextField(
                    value = descripcionIncidente,
                    onValueChange = { descripcionIncidente = it },
                    label = { Text("Descripción del incidente *") },
                    placeholder = { Text("Ej: Pinchadura de neumático en Ruta 5 km 98") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = fotoIncidente,
                    onValueChange = { fotoIncidente = it },
                    label = { Text("Foto adjunta (opcional)") },
                    placeholder = { Text("Seleccione una foto o ingrese referencia (ej: foto_incidente.jpg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { selectorFotoLauncher.launch("image/*") }
                    ) {
                        Text("Adjuntar foto de galería")
                    }
                    if (fotoIncidente.isNotBlank()) {
                        OutlinedButton(onClick = { fotoIncidente = "" }) {
                            Text("Quitar foto")
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
            val bgRes = if (resultadoRegistro!!.guardadoOffline) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
            val txtRes = if (resultadoRegistro!!.guardadoOffline) Color(0xFF92400E) else Color(0xFF166534)
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "UUID: ${resultadoRegistro!!.evento.uuid}",
                        color = txtRes,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                errorValidacion = null
                var detalleFinal: String? = null
                var fotoFinal: String? = null

                when (tipoSeleccionado) {
                    TipoEventoOption.INICIO, TipoEventoOption.FIN -> {
                        detalleFinal = null
                    }

                    TipoEventoOption.CARGA_PARCIAL, TipoEventoOption.DESCARGA_PARCIAL -> {
                        if (cantidadKg.isBlank() || (cantidadKg.toDoubleOrNull() ?: 0.0) <= 0.0) {
                            errorValidacion = "Debe ingresar una cantidad en Kg válida mayor a 0."
                            return@Button
                        }
                        if (queSeCargaDescarga.isBlank()) {
                            errorValidacion = "Debe indicar qué mercadería se carga o descarga."
                            return@Button
                        }
                        if (empresaCarga.isBlank()) {
                            errorValidacion = "Debe indicar a qué empresa pertenece la carga."
                            return@Button
                        }
                        detalleFinal = EventoFormatter.formatearDetalleCargaParcial(
                            esDescarga = (tipoSeleccionado == TipoEventoOption.DESCARGA_PARCIAL),
                            cantidadKg = cantidadKg,
                            mercaderia = queSeCargaDescarga,
                            empresa = empresaCarga
                        )
                    }

                    TipoEventoOption.INCIDENTE -> {
                        if (descripcionIncidente.isBlank()) {
                            errorValidacion = "Debe ingresar la descripción del incidente."
                            return@Button
                        }
                        detalleFinal = descripcionIncidente.trim()
                        fotoFinal = fotoIncidente.trim().takeIf { it.isNotBlank() }
                    }
                }

                guardando = true
                scope.launch {
                    val coords = syncRepository.obtenerUbicacionActual()
                    coordenadas = coords
                    val res = syncRepository.registrarEvento(
                        guiaId = guiaId,
                        tipo = tipoSeleccionado,
                        latitud = coords.first,
                        longitud = coords.second,
                        descripcion = detalleFinal,
                        foto = fotoFinal,
                        cedulaChofer = cedulaChofer
                    )
                    guardando = false
                    resultadoRegistro = res
                    onEventoRegistrado(res.mensajeUsuario)
                }
            },
            enabled = !guardando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (guardando) "Registrando evento..." else "Confirmar y Registrar Evento")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { navController.navigate("eventosGuia/$guiaId") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Ver Eventos de Guía")
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
            text = "Eventos de la Guía #$guiaId",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E3A8A)
        )
        Text(
            text = "Historial cronológico (servidor + teléfono offline)",
            fontSize = 13.sp,
            color = Color(0xFF475569)
        )

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
                    text = "No hay eventos registrados para esta guía aún.",
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
                    val badgeBg = if (esPendiente) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                    val badgeTxt = if (esPendiente) Color(0xFF92400E) else Color(0xFF166534)

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
                                        text = if (esPendiente) "PENDIENTE EN TELÉFONO" else "SINCRONIZADO",
                                        color = badgeTxt,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fecha y hora: ${ev.tiempo.replace('T', ' ')}",
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "Ubicación: ${ev.latitud}, ${ev.longitud}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )

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
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Foto adjunta: ${ev.foto}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF0369A1)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "UUID: ${ev.uuid}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
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
            Button(
                onClick = { navController.navigate("registrarEvento/$guiaId") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Nuevo Evento")
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
