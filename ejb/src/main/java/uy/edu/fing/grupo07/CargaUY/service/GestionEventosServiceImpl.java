package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.IncidenteRuta;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.dto.SyncResultDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Stateless
public class GestionEventosServiceImpl implements GestionEventosServiceLocal {

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public GestionEventosServiceImpl() {
    }

    // Constructor para tests con mock
    GestionEventosServiceImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean existeEvento(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        Long count = em.createQuery(
                "SELECT COUNT(e) FROM EventoViaje e WHERE e.uuid = :uuid", Long.class)
                .setParameter("uuid", uuid)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public EventoViaje guardarEvento(EventoViajeDTO dto) {
        if (dto == null) {
            throw new BusinessException("El evento a registrar no puede ser nulo.");
        }
        if (dto.uuid() == null || dto.uuid().isBlank()) {
            throw new BusinessException("El UUID del evento es obligatorio para garantizar idempotencia.");
        }
        if (dto.guiaId() == null) {
            throw new BusinessException("El ID de guía de viaje es obligatorio.");
        }
        if (existeEvento(dto.uuid())) {
            throw new BusinessException("El evento con UUID " + dto.uuid() + " ya fue registrado previamente.");
        }

        GuiaDeViaje guia = em.find(GuiaDeViaje.class, dto.guiaId());
        if (guia == null) {
            throw new BusinessException("No se encontró la guía de viaje con ID " + dto.guiaId());
        }

        LocalDateTime tiempo = dto.tiempo() != null ? dto.tiempo() : LocalDateTime.now();
        EventoViaje evento = new EventoViaje(dto.uuid(), tiempo, dto.tipo(), dto.latitud(), dto.longitud(), guia);

        if (dto.incidente() != null) {
            IncidenteRuta incidente = new IncidenteRuta(
                    dto.incidente().foto(),
                    dto.incidente().descripcion(),
                    evento
            );
            evento.setIncidente(incidente);
        }

        em.persist(evento);
        return evento;
    }

    @Override
    public List<SyncResultDTO> guardarEventosLote(List<EventoViajeDTO> eventos) {
        List<SyncResultDTO> resultados = new ArrayList<>();
        if (eventos == null || eventos.isEmpty()) {
            return resultados;
        }

        for (EventoViajeDTO dto : eventos) {
            if (dto == null || dto.uuid() == null || dto.uuid().isBlank()) {
                resultados.add(SyncResultDTO.error(dto != null ? dto.uuid() : null, "UUID no puede ser nulo o vacío"));
                continue;
            }

            if (existeEvento(dto.uuid())) {
                resultados.add(SyncResultDTO.duplicate(dto.uuid()));
                continue;
            }

            try {
                guardarEvento(dto);
                resultados.add(SyncResultDTO.accepted(dto.uuid()));
            } catch (BusinessException e) {
                resultados.add(SyncResultDTO.error(dto.uuid(), e.getMessage()));
            } catch (RuntimeException e) {
                resultados.add(SyncResultDTO.error(dto.uuid(), "Error inesperado: " + e.getMessage()));
            }
        }

        return resultados;
    }

    @Override
    public List<EventoViajeDTO> listarEventosPorGuia(Long guiaId) {
        if (guiaId == null) {
            throw new BusinessException("El ID de guía no puede ser nulo.");
        }

        List<EventoViaje> eventos = em.createQuery(
                "SELECT e FROM EventoViaje e LEFT JOIN FETCH e.incidente WHERE e.guia.id = :guiaId ORDER BY e.tiempo ASC",
                EventoViaje.class)
                .setParameter("guiaId", guiaId)
                .getResultList();

        List<EventoViajeDTO> dtos = new ArrayList<>();
        for (EventoViaje ev : eventos) {
            IncidenteDTO incDto = null;
            if (ev.getIncidente() != null) {
                incDto = new IncidenteDTO(ev.getIncidente().getFoto(), ev.getIncidente().getDescripcion());
            }
            dtos.add(new EventoViajeDTO(
                    ev.getUuid(),
                    ev.getTiempo(),
                    ev.getTipo(),
                    ev.getLatitud(),
                    ev.getLongitud(),
                    ev.getGuia() != null ? ev.getGuia().getId() : null,
                    incDto
            ));
        }

        return dtos;
    }
}
