package com.example.cargauyandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `EventoLocal sin descripcion ni foto genera EventoViajeDto con incidente nulo`() {
        val local = EventoLocal(
            uuid = "uuid-inicio-1",
            guiaId = 10L,
            tipo = TipoEventoOption.INICIO.codigo,
            tiempo = "2026-10-19T14:00:00",
            latitud = -34.9011,
            longitud = -56.1645,
            descripcion = null,
            foto = null
        )

        val dto = local.toDto()
        assertEquals("uuid-inicio-1", dto.uuid)
        assertEquals("INICIO", dto.tipo)
        assertEquals(10L, dto.guiaId)
        assertNull(dto.incidente)
    }

    @Test
    fun `EventoLocal de carga parcial formatea descripcion y se mapea a IncidenteDto`() {
        val detalle = EventoFormatter.formatearDetalleCargaParcial(
            esDescarga = false,
            cantidadKg = "4500",
            mercaderia = "Madera",
            empresa = "Transportes del Plata S.A."
        )
        assertEquals(
            "Carga parcial: 4500 Kg | Mercadería: Madera | Empresa: Transportes del Plata S.A.",
            detalle
        )

        val local = EventoLocal(
            uuid = "uuid-carga-1",
            guiaId = 1L,
            tipo = TipoEventoOption.CARGA_PARCIAL.codigo,
            tiempo = "2026-10-19T14:30:00",
            latitud = -34.85,
            longitud = -56.15,
            descripcion = detalle,
            foto = null
        )

        val dto = local.toDto()
        assertNotNull(dto.incidente)
        assertEquals(detalle, dto.incidente?.descripcion)
        assertNull(dto.incidente?.foto)
    }

    @Test
    fun `EventoLocal de incidente con foto mapea foto y descripcion al DTO`() {
        val local = EventoLocal(
            uuid = "uuid-inc-1",
            guiaId = 2L,
            tipo = TipoEventoOption.INCIDENTE.codigo,
            tiempo = "2026-10-19T15:00:00",
            latitud = -33.25,
            longitud = -56.02,
            descripcion = "Pinchadura de neumático",
            foto = "pinchadura.jpg"
        )

        val dto = local.toDto()
        assertEquals("INCIDENTE", dto.tipo)
        assertNotNull(dto.incidente)
        assertEquals("Pinchadura de neumático", dto.incidente?.descripcion)
        assertEquals("pinchadura.jpg", dto.incidente?.foto)
    }
}