package uy.edu.fing.grupo07.CargaUY.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - GuiaDeViaje y Agregados")
class GuiaDeViajeTest {

    @Test
    @DisplayName("GuiaDeViaje debe inicializarse en estado SIN_INICIAR")
    void testEstadoInicial() {
        GuiaDeViaje guia = new GuiaDeViaje();
        assertEquals(TipoEstado.SIN_INICIAR, guia.getEstado());
        assertEquals(0, guia.getVersion());
    }

    @Test
    @DisplayName("Debe vincular puntos de tracking, pesadas y eventos correctamente")
    void testAgregadosGuiaDeViaje() {
        Empresa empresa = new Empresa(999, "Logistica del Sur", "LogSur", "Montevideo");
        Vehiculo vehiculo = new Vehiculo("555", "Volvo", "FH", 12000, 28000, true, empresa);
        Chofer chofer = new Chofer("Esteban Quito", "esteban@logsur.uy",
                LocalDate.of(1992, 3, 10), 51234567, "pass");

        GuiaDeViaje guia = new GuiaDeViaje(LocalDate.now(), "Montevideo", "Rivera",
                "Forestal", 25.5f, empresa, vehiculo, chofer);

        // Agregar punto tracking
        PuntoTracking punto = new PuntoTracking(-34.9011, -56.1645, LocalDateTime.now(), null);
        guia.agregarPuntoTracking(punto);
        assertEquals(1, guia.getPuntosTracking().size());
        assertEquals(guia, punto.getGuia());

        // Agregar pesada
        PesadaBalanza pesada = new PesadaBalanza(LocalDate.now(), LocalTime.of(10, 30), 27500, null);
        guia.agregarPesada(pesada);
        assertEquals(1, guia.getPesadas().size());
        assertEquals(guia, pesada.getGuia());

        // Agregar evento
        EventoViaje evento = new EventoViaje(null, LocalDateTime.now(), TipoEvento.CARGA, -34.9011, -56.1645, null);
        guia.agregarEvento(evento);
        assertEquals(1, guia.getEventos().size());
        assertEquals(guia, evento.getGuia());
        assertNotNull(evento.getUuid(), "El UUID debe autogenerarse para deduplicación");
    }
}
