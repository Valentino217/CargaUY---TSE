package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.SyncResultDTO;

import java.util.List;

@Local
public interface GestionEventosServiceLocal {

    /**
     * Verifica si ya existe un evento registrado con el UUID indicado (idempotencia / deduplicación).
     */
    boolean existeEvento(String uuid);

    /**
     * Guarda un evento de viaje. Si ya existe un evento con ese UUID, lanza BusinessException.
     */
    EventoViaje guardarEvento(EventoViajeDTO dto);

    /**
     * Procesa un lote de eventos en sincronización offline.
     * Si un evento ya existe por UUID, devuelve estado DUPLICATE sin fallar el resto del lote.
     */
    List<SyncResultDTO> guardarEventosLote(List<EventoViajeDTO> eventos);

    /**
     * Retorna los eventos asociados a una guía ordenados cronológicamente por tiempo ASC (AC013).
     */
    List<EventoViajeDTO> listarEventosPorGuia(Long guiaId);

    /**
     * Obtiene la guía activa (EN_CURSO o SIN_INICIAR) asignada a un chofer identificado por ID o CI.
     */
    uy.edu.fing.grupo07.CargaUY.dto.GuiaResumenDTO obtenerGuiaAsignadaChofer(Integer choferId, Integer ci);

    /**
     * Registra un incidente en ruta asociado a un evento de tipo INCIDENTE.
     */
    EventoViajeDTO reportarIncidente(uy.edu.fing.grupo07.CargaUY.dto.ReportarIncidenteDTO dto);
}
