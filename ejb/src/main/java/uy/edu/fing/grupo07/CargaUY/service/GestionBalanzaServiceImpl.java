package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.integration.BalanzaClient;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;

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

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    @Inject
    private BalanzaClient balanzaClient;

    @EJB
    private FiscalizacionProducerLocal fiscalizacionProducer;

    public GestionBalanzaServiceImpl() {
    }

    public GestionBalanzaServiceImpl(EntityManager em, BalanzaClient balanzaClient) {
        this.em = em;
        this.balanzaClient = balanzaClient;
    }

    public GestionBalanzaServiceImpl(
            EntityManager em,
            BalanzaClient balanzaClient,
            FiscalizacionProducerLocal fiscalizacionProducer
    ) {
        this.em = em;
        this.balanzaClient = balanzaClient;
        this.fiscalizacionProducer = fiscalizacionProducer;
    }

    @Override
    public PesadaBalanzaDTO registrarPesada(PesadaBalanzaDTO dto) {
        if (dto == null) {
            throw new BusinessException("La información de pesada no puede ser nula.");
        }
        if (dto.pesoRegistrado() <= 0) {
            throw new BusinessException("El peso registrado debe ser mayor a 0.");
        }

        LocalDate fecha = dto.fecha() != null ? dto.fecha() : LocalDate.now();
        LocalTime hora = dto.hora() != null ? dto.hora() : LocalTime.now();

        GuiaDeViaje guia = null;
        Vehiculo vehiculoSinGuia = null;

        if (dto.guiaId() != null) {
            guia = em.find(GuiaDeViaje.class, dto.guiaId());
            if (guia == null) {
                if (dto.matricula() != null && !dto.matricula().isBlank()) {
                    Vehiculo v = buscarVehiculoPorMatricula(dto.matricula().trim());
                    if (v != null && fiscalizacionProducer != null) {
                        fiscalizacionProducer.solicitarEvaluacionSinGuia(
                                v.getMatricula(),
                                "Pesaje en balanza (" + dto.pesoRegistrado() + " kg) con guía inexistente #" + dto.guiaId()
                        );
                    }
                }
                throw new BusinessException("No existe la guía de viaje con ID " + dto.guiaId());
            }
            if (guia.getEstado() != TipoEstado.EN_CURSO) {
                if (guia.getVehiculo() != null && fiscalizacionProducer != null) {
                    fiscalizacionProducer.solicitarEvaluacionSinGuia(
                            guia.getVehiculo().getMatricula(),
                            "Pesaje en balanza (" + dto.pesoRegistrado() + " kg) sobre guía #" + guia.getId()
                                    + " en estado " + guia.getEstado()
                    );
                }
                throw new BusinessException(
                        "Solo se pueden registrar pesajes en guías con estado EN_CURSO (la guía #"
                                + guia.getId() + " se encuentra en estado " + guia.getEstado() + ")."
                );
            }
        } else {
            if (dto.matricula() == null || dto.matricula().isBlank()) {
                throw new BusinessException("Debe indicar la guíaId o la matrícula del vehículo para asociar la pesada.");
            }

            String matriculaRaw = dto.matricula().trim();
            String matriculaUpper = matriculaRaw.toUpperCase(java.util.Locale.ROOT);
            Object matriculaParam;
            try {
                matriculaParam = Integer.parseInt(matriculaRaw);
            } catch (NumberFormatException nfe) {
                if (!matriculaRaw.matches("^[a-zA-Z0-9 ]+$")) {
                    throw new BusinessException("La matrícula debe tener formato numérico: " + dto.matricula());
                }
                matriculaParam = matriculaUpper;
            }

            TypedQuery<GuiaDeViaje> q = em.createQuery(
                    "SELECT g FROM GuiaDeViaje g JOIN g.vehiculo v "
                            + "WHERE UPPER(v.matricula) = :matricula AND g.estado = :enCurso "
                            + "ORDER BY g.id DESC",
                    GuiaDeViaje.class
            );
            try {
                q.setParameter("matricula", matriculaParam);
            } catch (IllegalArgumentException iae) {
                q.setParameter("matricula", matriculaUpper);
            }
            q.setParameter("enCurso", TipoEstado.EN_CURSO);
            q.setMaxResults(1);

            List<GuiaDeViaje> guias = q.getResultList();
            if (guias.isEmpty()) {
                vehiculoSinGuia = buscarVehiculoPorMatricula(matriculaRaw);
                if (vehiculoSinGuia == null) {
                    throw new BusinessException("No existe ninguna guía de viaje EN_CURSO para el vehículo con matrícula " + dto.matricula());
                }
            } else {
                guia = guias.get(0);
            }
        }

        if (guia == null && vehiculoSinGuia != null) {
            PesadaBalanza pesadaSinGuia = new PesadaBalanza(fecha, hora, dto.pesoRegistrado(), null, vehiculoSinGuia);
            em.persist(pesadaSinGuia);
            em.flush();

            LOGGER.log(
                    Level.WARNING,
                    "Pesada registrada para vehículo {0} SIN guía activa (ID pesada: {1}, Peso: {2} kg). Derivando a fiscalización VIAJE_SIN_GUIA.",
                    new Object[]{vehiculoSinGuia.getMatricula(), pesadaSinGuia.getIdPesada(), pesadaSinGuia.getPesoRegistrado()}
            );

            if (fiscalizacionProducer != null) {
                fiscalizacionProducer.solicitarEvaluacionSinGuia(
                        vehiculoSinGuia.getMatricula(),
                        "Pesaje en balanza #" + pesadaSinGuia.getIdPesada() + " (" + pesadaSinGuia.getPesoRegistrado()
                                + " kg el " + fecha + " " + hora + ")"
                );
            }

            return new PesadaBalanzaDTO(
                    pesadaSinGuia.getIdPesada(),
                    pesadaSinGuia.getFecha(),
                    pesadaSinGuia.getHora(),
                    pesadaSinGuia.getPesoRegistrado(),
                    vehiculoSinGuia.getMatricula(),
                    null
            );
        }

        PesadaBalanza pesada = new PesadaBalanza(fecha, hora, dto.pesoRegistrado(), guia);
        em.persist(pesada);
        em.flush();

        LOGGER.log(Level.INFO, "Pesada registrada con éxito. ID: {0}, Guía: {1}, Peso: {2} kg",
                new Object[]{pesada.getIdPesada(), guia.getId(), pesada.getPesoRegistrado()});

        if (fiscalizacionProducer != null && guia.getId() != null) {
            fiscalizacionProducer.solicitarEvaluacion(guia.getId());
        }

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

    private Vehiculo buscarVehiculoPorMatricula(String matriculaRaw) {
        if (matriculaRaw == null || matriculaRaw.isBlank()) {
            return null;
        }
        String limpia = matriculaRaw.trim();
        Vehiculo v = em.find(Vehiculo.class, limpia);
        String upper = limpia.toUpperCase(java.util.Locale.ROOT);
        if (v == null && !limpia.equals(upper)) {
            v = em.find(Vehiculo.class, upper);
        }
        String lower = limpia.toLowerCase(java.util.Locale.ROOT);
        if (v == null && !limpia.equals(lower)) {
            v = em.find(Vehiculo.class, lower);
        }
        if (v == null && !upper.equals(lower)) {
            jakarta.persistence.Query q = em.createQuery("SELECT v FROM Vehiculo v WHERE UPPER(TRIM(v.matricula)) = :mat");
            if (q != null) {
                q.setParameter("mat", upper);
                q.setMaxResults(1);
                List<?> encontrados = q.getResultList();
                if (encontrados != null && !encontrados.isEmpty() && encontrados.get(0) instanceof Vehiculo encontrado) {
                    v = encontrado;
                }
            }
        }
        return v;
    }

    @Override
    public PesadaBalanzaDTO registrarPesadaDesdeMock() {
        return registrarPesadaDesdeMock(null);
    }

    @Override
    public PesadaBalanzaDTO registrarPesadaDesdeMock(Long guiaIdFallback) {
        if (balanzaClient == null) {
            throw new BusinessException("El cliente de balanza no está configurado.");
        }
        PesadaBalanzaDTO mockDto = balanzaClient.consultarPesadaMock();
        try {
            return registrarPesada(mockDto);
        } catch (BusinessException be) {
            if (guiaIdFallback != null) {
                GuiaDeViaje gFallback = em.find(GuiaDeViaje.class, guiaIdFallback);
                if (gFallback != null) {
                    PesadaBalanzaDTO conGuia = new PesadaBalanzaDTO(
                            mockDto.idPesada(),
                            mockDto.fecha(),
                            mockDto.hora(),
                            mockDto.pesoRegistrado(),
                            gFallback.getVehiculo() != null ? gFallback.getVehiculo().getMatricula() : mockDto.matricula(),
                            guiaIdFallback
                    );
                    return registrarPesada(conGuia);
                }
                throw new BusinessException("No existe la guía de viaje seleccionada #" + guiaIdFallback);
            }
            throw be;
        }
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
            String matricula = null;
            if (p.getGuia() != null && p.getGuia().getVehiculo() != null) {
                matricula = String.valueOf(p.getGuia().getVehiculo().getMatricula());
            } else if (p.getVehiculo() != null) {
                matricula = String.valueOf(p.getVehiculo().getMatricula());
            }
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
