package com.example.cargauyandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Utilidades para comprimir fotos de incidentes (galería o cámara) a un Data URI JPEG Base64
 * liviano compatible con almacenamiento offline SQLite y lotes de sincronización REST (< 1 MiB).
 */
object ImagenUtils {

    private const val MAX_DIMENSION_PX = 640
    private const val MAX_BASE64_LENGTH = 80_000
    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"

    fun codificarUriADataUri(context: Context, uri: Uri): String? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            var sampleSize = 1
            while (max(boundsOptions.outWidth / sampleSize, boundsOptions.outHeight / sampleSize) > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            codificarBitmapADataUri(bitmap)
        } catch (_: Exception) {
            null
        }
    }

    fun codificarBitmapADataUri(bitmap: Bitmap): String {
        val redimensionado = redimensionarSiNecesario(bitmap, MAX_DIMENSION_PX)
        val calidades = intArrayOf(65, 50, 35, 25)
        var base64 = ""
        for (calidad in calidades) {
            val out = ByteArrayOutputStream()
            redimensionado.compress(Bitmap.CompressFormat.JPEG, calidad, out)
            base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (base64.length <= MAX_BASE64_LENGTH) {
                break
            }
        }
        return DATA_URI_PREFIX + base64
    }

    fun decodificarDataUriABitmap(dataUri: String?): Bitmap? {
        if (dataUri.isNullOrBlank() || !dataUri.startsWith("data:image/")) {
            return null
        }
        val comaIdx = dataUri.indexOf(',')
        if (comaIdx < 0 || comaIdx + 1 >= dataUri.length) {
            return null
        }
        return try {
            val bytes = Base64.decode(dataUri.substring(comaIdx + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun redimensionarSiNecesario(origen: Bitmap, maxLado: Int): Bitmap {
        val mayor = max(origen.width, origen.height)
        if (mayor <= maxLado || mayor <= 0) {
            return origen
        }
        val escala = maxLado.toFloat() / mayor.toFloat()
        val nuevoAncho = max(1, (origen.width * escala).roundToInt())
        val nuevoAlto = max(1, (origen.height * escala).roundToInt())
        return Bitmap.createScaledBitmap(origen, nuevoAncho, nuevoAlto, true)
    }
}
