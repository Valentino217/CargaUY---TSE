package com.example.cargauyandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

object PkceUtil {
    private val random = SecureRandom()

    /**
     * Genera un code_verifier criptográficamente seguro de 43 caracteres (Base64 URL sin padding),
     * compatible con RFC 7636 y con las validaciones de FlujosService en el backend.
     */
    fun generarVerifier(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /**
     * Genera el code_challenge S256 a partir del code_verifier.
     * Retorna exactamente 43 caracteres en Base64 URL-safe sin padding.
     */
    fun generarChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.encodeToString(
            hash,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }
}

object OidcAuthManager {
    private const val TAG = "CARGAUY_OIDC"

    @Volatile
    var pendingVerifier: String? = null
        private set

    @Volatile
    var pendingState: String? = null
        private set

    @Volatile
    private var lastCompletedState: String? = null

    @Volatile
    private var lastCompletedResponse: TerminarGubResponse? = null

    @Volatile
    private var loopbackServer: ServerSocket? = null

    private fun iniciarLoopbackCallback(context: Context) {
        detenerLoopbackCallback()
        Thread {
            try {
                val server = ServerSocket(8080, 5, InetAddress.getByName("127.0.0.1"))
                server.soTimeout = 180_000
                loopbackServer = server
                Log.d(TAG, "Loopback OIDC escuchando en http://127.0.0.1:8080 ...")
                while (!server.isClosed) {
                    val socket = server.accept()
                    socket.use { client ->
                        val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                        val requestLine = reader.readLine() ?: return@use
                        val parts = requestLine.split(" ")
                        val pathAndQuery = if (parts.size >= 2) parts[1] else "/"
                        if (pathAndQuery.contains("code=") || pathAndQuery.contains("error=")) {
                            val reqUri = Uri.parse("http://localhost:8080$pathAndQuery")
                            val query = reqUri.encodedQuery ?: ""
                            val deepLink = "uy.cargauy://oauth/callback?$query"
                            val html = "<html><head><meta charset='utf-8'/><meta http-equiv='refresh' content='0;url=$deepLink'/></head>" +
                                "<body style='font-family:sans-serif;text-align:center;padding:32px;'>" +
                                "<script>window.location.replace('$deepLink');</script>" +
                                "<h3>Autenticación recibida</h3><p>Regresando a CargaUY...</p>" +
                                "<p><a href='$deepLink'>Volver a la aplicación</a></p></body></html>"
                            val bodyBytes = html.toByteArray(StandardCharsets.UTF_8)
                            val response = "HTTP/1.1 302 Found\r\n" +
                                "Location: $deepLink\r\n" +
                                "Content-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${bodyBytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            client.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
                            client.getOutputStream().write(bodyBytes)
                            client.getOutputStream().flush()
                            detenerLoopbackCallback()
                        } else {
                            val msg = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                            client.getOutputStream().write(msg.toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Loopback OIDC finalizado: ${e.message}")
            }
        }.apply { isDaemon = true; start() }
    }

    private fun detenerLoopbackCallback() {
        try {
            loopbackServer?.close()
        } catch (_: Exception) {
        } finally {
            loopbackServer = null
        }
    }

    /**
     * Inicia el flujo OIDC PKCE:
     * 1. Genera code_verifier y code_challenge.
     * 2. Llama a POST /api/auth/gub/iniciar.
     * 3. Resuelve el host si Keycloak se ejecuta en localhost o en emulador (10.0.2.2).
     * 4. Abre la pantalla de login del proveedor usando Chrome Custom Tabs (o navegador estándar de fallback).
     */
    suspend fun iniciarFlujoLogin(context: Context): Result<String> {
        return try {
            val verifier = PkceUtil.generarVerifier()
            val challenge = PkceUtil.generarChallenge(verifier)

            pendingVerifier = verifier
            Log.d(TAG, "Iniciando login OIDC con challenge S256 ($challenge)...")

            val response = RetrofitClient.executeWithFallback { api ->
                api.iniciarGub(IniciarGubRequest(challenge))
            }

            pendingState = response.state
            iniciarLoopbackCallback(context.applicationContext)
            val urlFinal = RetrofitClient.resolverUrlProveedor(response.url)
            Log.d(TAG, "Abriendo CustomTab/Navegador en: $urlFinal (state=${response.state})")

            val uriFinal = Uri.parse(urlFinal)
            try {
                // Preferir Chrome Custom Tabs para evitar desvíos a búsquedas de Google y cerrar la pestaña automáticamente
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                customTabsIntent.launchUrl(context, uriFinal)
            } catch (eTab: Exception) {
                Log.w(TAG, "Fallo CustomTabs, usando navegador estándar: ${eTab.message}")
                val browserIntent = Intent(Intent.ACTION_VIEW, uriFinal).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }

            Result.success(urlFinal)
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar flujo OIDC: ${e.message}", e)
            pendingVerifier = null
            pendingState = null
            Result.failure(e)
        }
    }

    /**
     * Procesa el redirect recibido en uy.cargauy:/oauth/callback:
     * 1. Valida code y state.
     * 2. Envía POST /api/auth/gub/terminar con (code, state, verifier).
     * 3. Actualiza SesionMovil con token, cédula y nombre.
     */
    suspend fun procesarCallback(uri: Uri): Result<TerminarGubResponse> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            try {
                val error = uri.getQueryParameter("error")
                if (!error.isNullOrBlank()) {
                    val desc = uri.getQueryParameter("error_description") ?: error
                    throw IllegalStateException("El proveedor de identidad reportó un error: $desc")
                }

                val code = uri.getQueryParameter("code")
                    ?: throw IllegalArgumentException("El callback no contiene el código de autorización (code)")
                val state = uri.getQueryParameter("state")
                    ?: throw IllegalArgumentException("El callback no contiene el parámetro state")

                val prevResp = lastCompletedResponse
                if (state == lastCompletedState && prevResp != null) {
                    return@withContext Result.success(prevResp)
                }

                val verifier = pendingVerifier
                    ?: throw IllegalStateException("No hay un verifier PKCE pendiente en la sesión actual")

                val stateEsperado = pendingState
                if (stateEsperado != null && stateEsperado != state) {
                    throw IllegalStateException("El parámetro 'state' recibido no coincide con la solicitud original (posible CSRF)")
                }

                Log.d(TAG, "Completando login OIDC con code y verifier...")
                val respuesta = RetrofitClient.executeWithFallback { api ->
                    api.terminarGub(TerminarGubRequest(code = code, state = state, verifier = verifier))
                }

                // Guardar sesión en SesionMovil reactiva
                SesionMovil.token = respuesta.token
                SesionMovil.cedulaAsociada = respuesta.ci
                SesionMovil.nombreChofer = respuesta.nombre

                lastCompletedState = state
                lastCompletedResponse = respuesta

                // Limpiar estado temporal
                detenerLoopbackCallback()
                pendingVerifier = null
                pendingState = null

                Log.d(TAG, "Login OIDC exitoso: ${respuesta.nombre} (CI: ${respuesta.ci})")
                Result.success(respuesta)
            } catch (e: Exception) {
                Log.e(TAG, "Fallo al procesar callback OIDC: ${e.message}", e)
                detenerLoopbackCallback()
                pendingVerifier = null
                pendingState = null
                Result.failure(e)
            }
        }
    }
}
