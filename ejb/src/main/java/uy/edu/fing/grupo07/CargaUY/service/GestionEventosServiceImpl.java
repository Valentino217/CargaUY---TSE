package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.IncidenteRuta;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.integration.FaasTrackingClient;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Stateless
public class GestionEventosServiceImpl implements GestionEventosServiceLocal {

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    @Inject
    private FaasTrackingClient faasTrackingClient;

    @EJB
    private FiscalizacionProducerLocal fiscalizacionProducer;

    public GestionEventosServiceImpl() {
    }

    // Constructor para tests con mock
    GestionEventosServiceImpl(EntityManager em) {
        this.em = em;
    }

    GestionEventosServiceImpl(EntityManager em, FaasTrackingClient faasTrackingClient) {
        this.em = em;
        this.faasTrackingClient = faasTrackingClient;
    }

    GestionEventosServiceImpl(
            EntityManager em,
            FaasTrackingClient faasTrackingClient,
            FiscalizacionProducerLocal fiscalizacionProducer
    ) {
        this.em = em;
        this.faasTrackingClient = faasTrackingClient;
        this.fiscalizacionProducer = fiscalizacionProducer;
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
    public void validarEventoPreEncolado(EventoViajeDTO dto) {
        GuiaDeViaje guia = validarPrecondicionesYResolverGuia(dto);
        validarEstadoViaje(guia, dto, false);
    }

    @Override
    public EventoViaje guardarEvento(EventoViajeDTO dto) {
        if (dto != null && dto.uuid() != null && !dto.uuid().isBlank() && existeEvento(dto.uuid())) {
            throw new BusinessException("El evento con UUID " + dto.uuid() + " ya fue registrado previamente.");
        }
        GuiaDeViaje guia = validarPrecondicionesYResolverGuia(dto);
        validarEstadoViaje(guia, dto, true);

        LocalDateTime tiempo = dto.tiempo() != null ? dto.tiempo() : LocalDateTime.now();
        EventoViaje evento = new EventoViaje(dto.uuid(), tiempo, dto.tipo(), dto.latitud(), dto.longitud(), guia);

        if (dto.cantidadKg() != null) {
            evento.setCantidadKg(dto.cantidadKg());
        }
        if (dto.mercaderia() != null && !dto.mercaderia().isBlank()) {
            evento.setMercaderia(dto.mercaderia().trim());
        }
        if (dto.empresaCarga() != null && !dto.empresaCarga().isBlank()) {
            evento.setEmpresaCarga(dto.empresaCarga().trim());
        }

        if (dto.distanciaDesvioKm() != null) {
            evento.setDistanciaDesvioKm(dto.distanciaDesvioKm());
        } else if (faasTrackingClient != null) {
            FaasTrackingClient.ResultadoFaas resultadoFaas = faasTrackingClient.procesarEvento(
                    dto,
                    guia.getOrigen(),
                    guia.getDestino()
            );
            evento.setDistanciaDesvioKm(resultadoFaas.distanciaDesvioKm());
        }

        if (dto.incidente() != null) {
            IncidenteRuta incidente = new IncidenteRuta(
                    dto.incidente().foto(),
                    dto.incidente().descripcion(),
                    evento
            );
            evento.setIncidente(incidente);
        }

        em.persist(evento);
        if (fiscalizacionProducer != null && guia.getId() != null) {
            fiscalizacionProducer.solicitarEvaluacion(guia.getId());
        }
        return evento;
    }

    private GuiaDeViaje validarPrecondicionesYResolverGuia(EventoViajeDTO dto) {
        if (dto == null) {
            throw new BusinessException("El evento a registrar no puede ser nulo.");
        }
        if (dto.uuid() == null || dto.uuid().isBlank()) {
            throw new BusinessException("El UUID del evento es obligatorio para garantizar idempotencia.");
        }
        if (dto.tipo() == null) {
            throw new BusinessException("El tipo de evento es obligatorio.");
        }
        if (dto.guiaId() == null && (dto.matricula() == null || dto.matricula().isBlank())) {
            throw new BusinessException("El ID de guía de viaje es obligatorio.");
        }
        if (dto.cantidadKg() != null && dto.cantidadKg() < 0) {
            throw new BusinessException("La cantidad de carga/descarga en kilogramos no puede ser negativa.");
        }

        if (dto.guiaId() != null) {
            GuiaDeViaje guia = em.find(GuiaDeViaje.class, dto.guiaId());
            if (guia == null) {
                if (dto.matricula() != null && !dto.matricula().isBlank()) {
                    notificarActividadVehiculoSinGuiaSiRegistrado(
                            dto.matricula().trim(),
                            "Evento " + dto.tipo() + " (UUID " + dto.uuid() + ") con guía inexistente #" + dto.guiaId()
                    );
                }
                throw new BusinessException("No se encontró la guía de viaje con ID " + dto.guiaId());
            }
            return guia;
        }

        String matriculaRaw = dto.matricula().trim();
        List<GuiaDeViaje> activas = buscarGuiasEnCursoPorMatricula(matriculaRaw);
        if (activas.isEmpty()) {
            Vehiculo vehiculo = buscarVehiculoPorMatricula(matriculaRaw);
            if (vehiculo != null) {
                if (fiscalizacionProducer != null) {
                    fiscalizacionProducer.solicitarEvaluacionSinGuia(
                            vehiculo.getMatricula(),
                            "Evento de monitoreo/tracking tipo " + dto.tipo() + " (UUID " + dto.uuid() + ") sin guía activa"
                    );
                }
                throw new BusinessException(
                        "El vehículo " + vehiculo.getMatricula()
                                + " no tiene ninguna guía de viaje EN_CURSO; se derivó a fiscalización automática por VIAJE_SIN_GUIA."
                );
            }
            throw new BusinessException("No existe vehículo registrado ni guía de viaje EN_CURSO para la matrícula " + matriculaRaw);
        }
        return activas.get(0);
    }

    private void validarEstadoViaje(GuiaDeViaje guia, EventoViajeDTO dto, boolean aplicarTransicion) {
        TipoEstado estadoActual = guia.getEstado() != null ? guia.getEstado() : TipoEstado.SIN_INICIAR;
        TipoEvento tipo = dto.tipo();

        if (estadoActual == TipoEstado.SIN_INICIAR) {
            if (tipo != TipoEvento.INICIO) {
                if (guia.getVehiculo() != null && fiscalizacionProducer != null) {
                    fiscalizacionProducer.solicitarEvaluacionSinGuia(
                            guia.getVehiculo().getMatricula(),
                            "Evento operativo " + tipo + " (UUID " + dto.uuid() + ") sobre guía #" + guia.getId() + " aún SIN_INICIAR"
                    );
                }
                throw new BusinessException(
                        "Transición inválida: la guía #" + guia.getId()
                                + " se encuentra SIN_INICIAR y solo admite el evento INICIO (se recibió " + tipo + ")."
                );
            }
            // Punto E.7: permitir que el viaje inicie y que el motor de fiscalización evalúe SIN_HABILITACION a la fecha de la guía.
            if (aplicarTransicion) {
                guia.setEstado(TipoEstado.EN_CURSO);
            }
            return;
        }

        if (estadoActual == TipoEstado.EN_CURSO) {
            if (tipo == TipoEvento.INICIO) {
                throw new BusinessException(
                        "Transición inválida: la guía #" + guia.getId() + " ya se encuentra EN_CURSO y no admite otro INICIO."
                );
            }
            if (tipo == TipoEvento.FIN && aplicarTransicion) {
                guia.setEstado(TipoEstado.FINALIZADO);
            }
            return;
        }

        if (estadoActual == TipoEstado.FINALIZADO) {
            if (guia.getVehiculo() != null && fiscalizacionProducer != null) {
                fiscalizacionProducer.solicitarEvaluacionSinGuia(
                        guia.getVehiculo().getMatricula(),
                        "Evento operativo " + tipo + " (UUID " + dto.uuid() + ") sobre guía #" + guia.getId() + " ya FINALIZADA"
                );
            }
            throw new BusinessException(
                    "Transición inválida: la guía #" + guia.getId()
                            + " ya se encuentra FINALIZADO y no admite nuevos eventos (" + tipo + ")."
            );
        }
    }

    private List<GuiaDeViaje> buscarGuiasEnCursoPorMatricula(String matricula) {
        TypedQuery<GuiaDeViaje> q = em.createQuery(
                "SELECT g FROM GuiaDeViaje g JOIN g.vehiculo v "
                        + "WHERE UPPER(v.matricula) = :matricula AND g.estado = :enCurso ORDER BY g.id DESC",
                GuiaDeViaje.class
        );
        if (q == null) {
            return List.of();
        }
        q.setParameter("matricula", matricula.toUpperCase());
        q.setParameter("enCurso", TipoEstado.EN_CURSO);
        q.setMaxResults(1);
        List<GuiaDeViaje> lista = q.getResultList();
        return lista != null ? lista : List.of();
    }

    private Vehiculo buscarVehiculoPorMatricula(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return null;
        }
        String limpia = matricula.trim();
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

    private void notificarActividadVehiculoSinGuiaSiRegistrado(String matricula, String detalle) {
        Vehiculo v = buscarVehiculoPorMatricula(matricula);
        if (v != null && fiscalizacionProducer != null) {
            fiscalizacionProducer.solicitarEvaluacionSinGuia(v.getMatricula(), detalle);
        }
    }

    @Override
    public List<SyncResultDTO> guardarEventosLote(List<EventoViajeDTO> eventos) {
        List<SyncResultDTO> resultados = new ArrayList<>();
        if (eventos == null || eventos.isEmpty()) {
            return resultados;
        }

        for (EventoViajeDTO dto : eventos) {
            if (dto == null || dto.uuid() == null || dto.uuid().isBlank()) {
                String uuidError = null;
                if (dto != null) {
                    uuidError = dto.uuid();
                }
                resultados.add(SyncResultDTO.error(uuidError, "UUID no puede ser nulo o vacío"));
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
            Long idGuia = null;
            String matricula = null;
            if (ev.getGuia() != null) {
                idGuia = ev.getGuia().getId();
                if (ev.getGuia().getVehiculo() != null) {
                    matricula = ev.getGuia().getVehiculo().getMatricula();
                }
            }
            dtos.add(new EventoViajeDTO(
                    ev.getUuid(),
                    ev.getTiempo(),
                    ev.getTipo(),
                    ev.getLatitud(),
                    ev.getLongitud(),
                    idGuia,
                    incDto,
                    ev.getDistanciaDesvioKm(),
                    ev.getCantidadKg(),
                    ev.getMercaderia(),
                    ev.getEmpresaCarga(),
                    matricula
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
                "JOIN FETCH g.chofer " +
                "LEFT JOIN FETCH g.empresa " +
                "LEFT JOIN FETCH g.vehiculo " +
                "WHERE (g.estado = :enCurso OR g.estado = :sinIniciar) "
        );

        if (choferId != null) {
            jpql.append("AND g.chofer.id = :choferId ");
        } else {
            jpql.append("AND g.chofer.ci = :ci ");
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

        return mapearAGuiaResumenDTO(resultados.get(0));
    }

    @Override
    public List<GuiaResumenDTO> listarGuiasChofer(Integer choferId, Integer ci, TipoEstado estado) {
        if (choferId == null && ci == null) {
            throw new BusinessException("Debe indicar el choferId o la cédula (ci) del chofer.");
        }

        StringBuilder jpql = new StringBuilder(
                "SELECT g FROM GuiaDeViaje g " +
                "JOIN FETCH g.chofer " +
                "LEFT JOIN FETCH g.empresa " +
                "LEFT JOIN FETCH g.vehiculo " +
                "WHERE 1=1 "
        );

        if (choferId != null) {
            jpql.append("AND g.chofer.id = :choferId ");
        } else {
            jpql.append("AND g.chofer.ci = :ci ");
        }

        if (estado != null) {
            jpql.append("AND g.estado = :estado ");
        }

        jpql.append("ORDER BY g.fecha DESC, g.id DESC");

        TypedQuery<GuiaDeViaje> q = em.createQuery(jpql.toString(), GuiaDeViaje.class);

        if (choferId != null) {
            q.setParameter("choferId", choferId);
        } else {
            q.setParameter("ci", ci);
        }

        if (estado != null) {
            q.setParameter("estado", estado);
        }

        List<GuiaDeViaje> guias = q.getResultList();
        List<GuiaResumenDTO> dtos = new ArrayList<>();
        for (GuiaDeViaje g : guias) {
            dtos.add(mapearAGuiaResumenDTO(g));
        }
        return dtos;
    }

    private GuiaResumenDTO mapearAGuiaResumenDTO(GuiaDeViaje guia) {
        if (guia == null) {
            return null;
        }
        String matricula = null;
        String marcaModelo = null;
        if (guia.getVehiculo() != null) {
            matricula = guia.getVehiculo().getMatricula();
            marcaModelo = guia.getVehiculo().getMarca() + " " + guia.getVehiculo().getModelo();
        }
        Integer nroEmpresa = null;
        String razonSocial = null;
        if (guia.getEmpresa() != null) {
            nroEmpresa = guia.getEmpresa().getNroEmpresa();
            razonSocial = guia.getEmpresa().getRazonSocial();
        }
        Integer idChofer = null;
        String nombreChofer = null;
        if (guia.getChofer() != null) {
            idChofer = guia.getChofer().getId();
            nombreChofer = guia.getChofer().getNombre();
        }
        return new GuiaResumenDTO(
                guia.getId(),
                guia.getFecha(),
                guia.getOrigen(),
                guia.getDestino(),
                guia.getRubroCliente(),
                guia.getVolumenCarga(),
                guia.getEstado(),
                guia.getVersion(),
                matricula,
                marcaModelo,
                nroEmpresa,
                razonSocial,
                idChofer,
                nombreChofer
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

        String uuid = UUID.randomUUID().toString();
        if (dto.uuid() != null && !dto.uuid().isBlank()) {
            uuid = dto.uuid();
        }
        LocalDateTime tiempo = LocalDateTime.now();
        if (dto.tiempo() != null) {
            tiempo = dto.tiempo();
        }

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

        Long idGuiaGuardada = null;
        if (guardado.getGuia() != null) {
            idGuiaGuardada = guardado.getGuia().getId();
        }

        return new EventoViajeDTO(
                guardado.getUuid(),
                guardado.getTiempo(),
                guardado.getTipo(),
                guardado.getLatitud(),
                guardado.getLongitud(),
                idGuiaGuardada,
                incDto,
                guardado.getDistanciaDesvioKm()
        );
    }
}
