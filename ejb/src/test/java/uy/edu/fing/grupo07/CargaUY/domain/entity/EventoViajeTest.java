package uy.edu.fing.grupo07.CargaUY.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - EventoViaje e IncidenteRuta (AC012, AC014)")
class EventoViajeTest {

    @Test
    @DisplayName("Debe generar UUID si no se proporciona para garantizar idempotencia")
    void testUuidAutogenerado() {
        EventoViaje ev = new EventoViaje();
        assertNotNull(ev.getUuid());
        assertFalse(ev.getUuid().isBlank());
    }

    @Test
    @DisplayName("Debe respetar el UUID suministrado por el cliente móvil (offline-first)")
    void testUuidSuministrado() {
        String uuidCliente = "offline-uuid-9876-abcd";
        EventoViaje ev = new EventoViaje(uuidCliente, LocalDateTime.now(), TipoEvento.INICIO, -34.9, -56.1, null);
        assertEquals(uuidCliente, ev.getUuid());
    }

    @Test
    @DisplayName("Debe vincular bidireccionalmente IncidenteRuta con EventoViaje")
    void testVinculacionIncidenteRuta() {
        EventoViaje ev = new EventoViaje("ev-inc-1", LocalDateTime.now(), TipoEvento.INCIDENTE, -34.9, -56.1, null);
        IncidenteRuta inc = new IncidenteRuta("https://storage.cargauy.uy/fotos/pinchadura.jpg",
                "Rotura de neumático trasero en Ruta 1", null);

        ev.setIncidente(inc);

        assertNotNull(ev.getIncidente());
        assertEquals(ev, inc.getEvento());
        assertEquals("Rotura de neumático trasero en Ruta 1", ev.getIncidente().getDescripcion());
    }
}
