package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.IncidenteRuta;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Override
    public GuiaResumenDTO obtenerGuiaAsignadaChofer(Integer choferId, Integer ci) {
        if (choferId == null && ci == null) {
            throw new BusinessException("Debe indicar el choferId o la cédula (ci) del chofer.");
        }

        StringBuilder jpql = new StringBuilder(
                "SELECT g FROM GuiaDeViaje g " +
                "JOIN FETCH g.chofer c " +
                "JOIN FETCH g.empresa e " +
                "JOIN FETCH g.vehiculo v " +
                "WHERE (g.estado = :enCurso OR g.estado = :sinIniciar) "
        );

        if (choferId != null) {
            jpql.append("AND c.id = :choferId ");
        } else {
            jpql.append("AND c.ci = :ci ");
        }

        jpql.append("ORDER BY CASE WHEN g.estado = :enCurso THEN 1 ELSE 2 END, g.id DESC");

        TypedQuery<GuiaDeViaje> q = em.createQuery(jpql.toString(), GuiaDeViaje.class)
                .setParameter("enCurso", TipoEstado.EN_CURSO)
                .setParameter("sinIniciar", TipoEstado.SIN_INICIAR);

        if (choferId != null) {
            q.setParameter("choferId", choferId);
        } else {
            q.setParameter("ci", ci);
        }

        q.setMaxResults(1);
        List<GuiaDeViaje> resultados = q.getResultList();
        if (resultados.isEmpty()) {
            return null;
        }

        GuiaDeViaje guia = resultados.get(0);
        return new GuiaResumenDTO(
                guia.getId(),
                guia.getFecha(),
                guia.getOrigen(),
                guia.getDestino(),
                guia.getRubroCliente(),
                guia.getVolumenCarga(),
                guia.getEstado(),
                guia.getVersion(),
                guia.getVehiculo() != null ? guia.getVehiculo().getMatricula() : null,
                guia.getVehiculo() != null ? (guia.getVehiculo().getMarca() + " " + guia.getVehiculo().getModelo()) : null,
                guia.getEmpresa() != null ? guia.getEmpresa().getNroEmpresa() : null,
                guia.getEmpresa() != null ? guia.getEmpresa().getRazonSocial() : null,
                guia.getChofer() != null ? guia.getChofer().getId() : null,
                guia.getChofer() != null ? guia.getChofer().getNombre() : null
        );
    }

    @Override
    public EventoViajeDTO reportarIncidente(ReportarIncidenteDTO dto) {
        if (dto == null) {
            throw new BusinessException("Los datos del incidente no pueden ser nulos.");
        }
        if (dto.guiaId() == null) {
            throw new BusinessException("El ID de guía de viaje es obligatorio para reportar un incidente.");
        }
        if (dto.descripcion() == null || dto.descripcion().isBlank()) {
            throw new BusinessException("La descripción del incidente es obligatoria.");
        }

        String uuid = (dto.uuid() != null && !dto.uuid().isBlank()) ? dto.uuid() : UUID.randomUUID().toString();
        LocalDateTime tiempo = dto.tiempo() != null ? dto.tiempo() : LocalDateTime.now();

        IncidenteDTO incDto = new IncidenteDTO(dto.foto(), dto.descripcion());
        EventoViajeDTO evDto = new EventoViajeDTO(
                uuid,
                tiempo,
                TipoEvento.INCIDENTE,
                dto.latitud(),
                dto.longitud(),
                dto.guiaId(),
                incDto
        );

        EventoViaje guardado = guardarEvento(evDto);

        return new EventoViajeDTO(
                guardado.getUuid(),
                guardado.getTiempo(),
                guardado.getTipo(),
                guardado.getLatitud(),
                guardado.getLongitud(),
                guardado.getGuia() != null ? guardado.getGuia().getId() : null,
                incDto
        );
    }
}
