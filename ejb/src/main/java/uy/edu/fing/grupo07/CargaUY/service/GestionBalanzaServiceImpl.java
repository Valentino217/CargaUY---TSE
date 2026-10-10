package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.integration.BalanzaClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación del servicio de gestión y registro de pesadas de balanza.
 */
@Stateless
public class GestionBalanzaServiceImpl implements GestionBalanzaServiceLocal {

    private static final Logger LOGGER = Logger.getLogger(GestionBalanzaServiceImpl.class.getName());

    @PersistenceContext(unitName = "PostgreSQL")
    private EntityManager em;

    @Inject
    private BalanzaClient balanzaClient;

    public GestionBalanzaServiceImpl() {
    }

    public GestionBalanzaServiceImpl(EntityManager em, BalanzaClient balanzaClient) {
        this.em = em;
        this.balanzaClient = balanzaClient;
    }

    @Override
    public PesadaBalanzaDTO registrarPesada(PesadaBalanzaDTO dto) {
        if (dto == null) {
            throw new BusinessException("La información de pesada no puede ser nula.");
        }
        if (dto.pesoRegistrado() <= 0) {
            throw new BusinessException("El peso registrado debe ser mayor a 0.");
        }

        GuiaDeViaje guia = null;

        if (dto.guiaId() != null) {
            guia = em.find(GuiaDeViaje.class, dto.guiaId());
            if (guia == null) {
                throw new BusinessException("No existe la guía de viaje con ID " + dto.guiaId());
            }
        } else {
            if (dto.matricula() == null || dto.matricula().isBlank()) {
                throw new BusinessException("Debe indicar la guíaId o la matrícula del vehículo para asociar la pesada.");
            }

            int matriculaInt;
            try {
                matriculaInt = Integer.parseInt(dto.matricula().trim());
            } catch (NumberFormatException nfe) {
                throw new BusinessException("La matrícula debe tener formato numérico: " + dto.matricula());
            }

            TypedQuery<GuiaDeViaje> q = em.createQuery(
                    "SELECT g FROM GuiaDeViaje g JOIN g.vehiculo v " +
                    "WHERE v.matricula = :matricula AND g.estado = :enCurso " +
                    "ORDER BY g.id DESC",
                    GuiaDeViaje.class
            );
            q.setParameter("matricula", matriculaInt);
            q.setParameter("enCurso", TipoEstado.EN_CURSO);
            q.setMaxResults(1);

            List<GuiaDeViaje> guias = q.getResultList();
            if (guias.isEmpty()) {
                throw new BusinessException("No existe ninguna guía de viaje EN_CURSO para el vehículo con matrícula " + dto.matricula());
            }
            guia = guias.get(0);
        }

        LocalDate fecha = dto.fecha() != null ? dto.fecha() : LocalDate.now();
        LocalTime hora = dto.hora() != null ? dto.hora() : LocalTime.now();

        PesadaBalanza pesada = new PesadaBalanza(fecha, hora, dto.pesoRegistrado(), guia);
        em.persist(pesada);

        LOGGER.log(Level.INFO, "Pesada registrada con éxito. ID: {0}, Guía: {1}, Peso: {2} kg",
                new Object[]{pesada.getIdPesada(), guia.getId(), pesada.getPesoRegistrado()});

        String matriculaStr = (guia.getVehiculo() != null)
                ? String.valueOf(guia.getVehiculo().getMatricula())
                : dto.matricula();

        return new PesadaBalanzaDTO(
                pesada.getIdPesada(),
                pesada.getFecha(),
                pesada.getHora(),
                pesada.getPesoRegistrado(),
                matriculaStr,
                guia.getId()
        );
    }

    @Override
    public PesadaBalanzaDTO registrarPesadaDesdeMock() {
        if (balanzaClient == null) {
            throw new BusinessException("El cliente de balanza no está configurado.");
        }
        PesadaBalanzaDTO mockDto = balanzaClient.consultarPesadaMock();
        return registrarPesada(mockDto);
    }

    @Override
    public List<PesadaBalanzaDTO> listarPesadasPorGuia(Long guiaId) {
        if (guiaId == null) {
            throw new BusinessException("El ID de guía no puede ser nulo.");
        }

        TypedQuery<PesadaBalanza> q = em.createQuery(
                "SELECT p FROM PesadaBalanza p WHERE p.guia.id = :guiaId ORDER BY p.fecha ASC, p.hora ASC",
                PesadaBalanza.class
        );
        q.setParameter("guiaId", guiaId);

        List<PesadaBalanza> pesadas = q.getResultList();
        List<PesadaBalanzaDTO> dtos = new ArrayList<>(pesadas.size());

        for (PesadaBalanza p : pesadas) {
            String matricula = (p.getGuia() != null && p.getGuia().getVehiculo() != null)
                    ? String.valueOf(p.getGuia().getVehiculo().getMatricula())
                    : null;
            dtos.add(new PesadaBalanzaDTO(
                    p.getIdPesada(),
                    p.getFecha(),
                    p.getHora(),
                    p.getPesoRegistrado(),
                    matricula,
                    p.getGuia() != null ? p.getGuia().getId() : null
            ));
        }

        return dtos;
    }
}
