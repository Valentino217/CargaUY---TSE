package com.example.cargauyandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventoViajeStateAndCargoTest {

    @Test
    fun `opcionesPermitidasParaEstado respeta maquina de estados del viaje`() {
        assertEquals(
            listOf(TipoEventoOption.INICIO),
            TipoEventoOption.opcionesPermitidasParaEstado("SIN_INICIAR")
        )
        assertEquals(
            listOf(
                TipoEventoOption.CARGA_PARCIAL,
                TipoEventoOption.DESCARGA_PARCIAL,
                TipoEventoOption.INCIDENTE,
                TipoEventoOption.FIN
            ),
            TipoEventoOption.opcionesPermitidasParaEstado("EN_CURSO")
        )
        assertTrue(TipoEventoOption.opcionesPermitidasParaEstado("FINALIZADO").isEmpty())
        assertEquals(
            TipoEventoOption.entries.toList(),
            TipoEventoOption.opcionesPermitidasParaEstado(null)
        )
    }

    @Test
    fun `EventoLocal con campos estructurados de carga parcial mapea cantidadKg mercaderia y empresaCarga`() {
        val local = EventoLocal(
            uuid = "uuid-carga-struct-1",
            guiaId = 15L,
            tipo = TipoEventoOption.CARGA_PARCIAL.codigo,
            tiempo = "2026-11-01T10:00:00",
            latitud = -34.9,
            longitud = -56.16,
            cantidadKg = 3250.5,
            mercaderia = "Soja a granel",
            empresaCarga = "AgroSur S.A."
        )

        val dto = local.toDto()
        assertEquals("CARGA_PARCIAL", dto.tipo)
        assertNull(dto.incidente)
        assertEquals(3250.5, dto.cantidadKg!!, 0.001)
        assertEquals("Soja a granel", dto.mercaderia)
        assertEquals("AgroSur S.A.", dto.empresaCarga)
    }

    @Test
    fun `EventoLocal de incidente con Data URI Base64 conserva foto en IncidenteDto`() {
        val fakeDataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/"
        val local = EventoLocal(
            uuid = "uuid-inc-b64",
            guiaId = 15L,
            tipo = TipoEventoOption.INCIDENTE.codigo,
            tiempo = "2026-11-01T11:15:00",
            latitud = -33.5,
            longitud = -56.2,
            descripcion = "Rotura de manguera hidraulica",
            foto = fakeDataUri
        )

        val dto = local.toDto()
        assertEquals("INCIDENTE", dto.tipo)
        assertEquals("Rotura de manguera hidraulica", dto.incidente?.descripcion)
        assertEquals(fakeDataUri, dto.incidente?.foto)
        assertNull(dto.cantidadKg)
    }

    @Test
    fun `SyncBatchChunker particiona lotes offline con multiples fotos Base64 bajo el limite de 1 MiB preservando orden`() {
        val fotoPesada = "data:image/jpeg;base64," + "A".repeat(80_000)
        val eventos = buildList {
            add(
                EventoViajeDto(
                    uuid = "ev-0-inicio",
                    tiempo = "2026-11-01T08:00:00",
                    tipo = "INICIO",
                    latitud = -34.9,
                    longitud = -56.1,
                    guiaId = 99L
                )
            )
            for (i in 1..14) {
                add(
                    EventoViajeDto(
                        uuid = "ev-$i-inc",
                        tiempo = "2026-11-01T09:${i.toString().padStart(2, '0')}:00",
                        tipo = "INCIDENTE",
                        latitud = -34.5,
                        longitud = -56.0,
                        guiaId = 99L,
                        incidente = IncidenteDto(foto = fotoPesada, descripcion = "Incidente offline #$i")
                    )
                )
            }
            add(
                EventoViajeDto(
                    uuid = "ev-15-fin",
                    tiempo = "2026-11-01T12:00:00",
                    tipo = "FIN",
                    latitud = -34.1,
                    longitud = -55.9,
                    guiaId = 99L
                )
            )
        }

        val subLotes = SyncBatchChunker.particionarLotes(eventos)
        assertTrue("Se esperaban al menos 3 sub-lotes para >1.1 MB de fotos", subLotes.size >= 3)
        for (lote in subLotes) {
            assertTrue(lote.size <= SyncBatchChunker.MAX_EVENTOS_POR_LOTE)
            val bytesEstimados = lote.sumOf { SyncBatchChunker.estimarBytesJson(it) }
            assertTrue(
                "Cada sub-lote ($bytesEstimados B) debe quedar muy por debajo de 1 MiB (1048576 B)",
                bytesEstimados <= SyncBatchChunker.MAX_BYTES_ESTIMADOS_POR_LOTE
            )
        }
        assertEquals(eventos.map { it.uuid }, subLotes.flatten().map { it.uuid })
    }
}
