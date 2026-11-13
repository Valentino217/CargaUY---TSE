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

    @Test
    fun `EventoLocal toDto preserva latitud y longitud GPS reales`() {
        val local = EventoLocal(
            uuid = "ev-gps-1",
            guiaId = 42L,
            tipo = "INICIO",
            tiempo = "2026-11-13T10:15:00",
            latitud = -34.9011,
            longitud = -56.1645
        )
        val dto = local.toDto("STP 4200")
        assertEquals(-34.9011, dto.latitud ?: 0.0, 0.00001)
        assertEquals(-56.1645, dto.longitud ?: 0.0, 0.00001)
        assertEquals("STP 4200", dto.matricula)
    }

    @Test
    fun `TraceHeaderHelper genera traceparent W3C valido y preserva traceId desde X-Correlation-ID`() {
        val corr = "123e4567-e89b-12d3-a456-426614174000"
        val tp1 = TraceHeaderHelper.generarTraceparent(corr)
        val tp2 = TraceHeaderHelper.generarTraceparent(corr)
        val regex = Regex("^00-([0-9a-f]{32})-([0-9a-f]{16})-01$")

        val m1 = regex.matchEntire(tp1)
        val m2 = regex.matchEntire(tp2)
        org.junit.Assert.assertNotNull(m1)
        org.junit.Assert.assertNotNull(m2)
        assertEquals("123e4567e89b12d3a456426614174000", m1!!.groupValues[1])
        assertEquals(m1.groupValues[1], m2!!.groupValues[1])
        org.junit.Assert.assertNotEquals(m1.groupValues[2], m2.groupValues[2])

        val desdeTexto = TraceHeaderHelper.generarTraceparent("texto-libre")
        assertTrue(regex.matches(desdeTexto))
    }

    @Test
    fun `SyncConflictResolver detecta guias reasignadas y actualizaciones de ruta sin descartar eventos vigentes`() {
        val previas = listOf(
            Viaje(
                id = 10L,
                fecha = "2026-11-13",
                origen = "Montevideo",
                destino = "Durazno",
                rubroCliente = "Granos",
                volumenCarga = 20f,
                estado = "EN_CURSO",
                version = 1
            ),
            Viaje(
                id = 20L,
                fecha = "2026-11-13",
                origen = "Salto",
                destino = "Paysandu",
                rubroCliente = "Madera",
                volumenCarga = 15f,
                estado = "EN_CURSO",
                version = 1
            )
        )
        val servidor = listOf(
            Viaje(
                id = 10L,
                fecha = "2026-11-13",
                origen = "Montevideo",
                destino = "Rivera",
                rubroCliente = "Granos",
                volumenCarga = 20f,
                estado = "EN_CURSO",
                version = 2
            )
        )
        val evVigente = EventoLocal(
            uuid = "ev-10",
            guiaId = 10L,
            tipo = "CARGA_PARCIAL",
            tiempo = "2026-11-13T11:00:00",
            latitud = -33.3,
            longitud = -56.5
        )
        val evReasignada = EventoLocal(
            uuid = "ev-20",
            guiaId = 20L,
            tipo = "FIN",
            tiempo = "2026-11-13T11:05:00",
            latitud = -32.3,
            longitud = -58.0
        )

        val res = SyncConflictResolver.filtrarPendientesContraGuias(
            pendientes = listOf(evVigente, evReasignada),
            guiasPrevias = previas,
            guiasServidor = servidor
        )

        assertEquals(listOf(evVigente), res.enviables)
        assertEquals(listOf(evReasignada), res.enConflicto)
        assertTrue(res.rutaActualizada)
        assertTrue(SyncConflictResolver.mensajeConflictoGuia(20L).contains("#20"))
    }
}
