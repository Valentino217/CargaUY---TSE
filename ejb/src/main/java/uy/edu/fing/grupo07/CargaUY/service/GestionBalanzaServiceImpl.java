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
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.NodoPerifericoConfig;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.integration.BalanzaClient;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    @EJB
    private GestionNodosPerifericosServiceLocal nodosPerifericosService;

    @EJB
    private ControlAcceso control;

    private void verificarControl() {
        if (control == null) {
            throw new IllegalStateException("ControlAcceso no disponible");
        }
    }

    private void validarAccesoGuiaLectura(String token, Long guiaId) {
        verificarControl();
        if (guiaId != null && em != null) {
            GuiaDeViaje guia = em.find(GuiaDeViaje.class, guiaId);
            if (guia != null && guia.getEmpresa() != null) {
                control.empresa(token, guia.getEmpresa().getNroEmpresa(), false);
                return;
            }
        }
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO, TipoUsuario.AUTORIDAD);
    }

    @Override
    public PesadaBalanzaDTO registrarPesada(String token, PesadaBalanzaDTO dto) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        return registrarPesada(dto);
    }

    @Override
    public PesadaBalanzaDTO registrarPesadaDesdeMock(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        return registrarPesadaDesdeMock();
    }

    @Override
    public PesadaBalanzaDTO registrarPesadaDesdeMock(String token, Long guiaIdFallback) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        return registrarPesadaDesdeMock(guiaIdFallback);
    }

    @Override
    public List<PesadaBalanzaDTO> sincronizarPesadasDeGuia(String token, Long guiaId, String correlacion) {
        validarAccesoGuiaLectura(token, guiaId);
        return sincronizarPesadasDeGuia(guiaId, correlacion);
    }

    @Override
    public PesadaBalanzaDTO registrarPesadaViaPeriferico(
            String token,
            PesadaBalanzaDTO dto,
            String peaje,
            String correlacion
    ) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        return registrarPesadaViaPeriferico(dto, peaje, correlacion);
    }

    @Override
    public List<PesadaBalanzaDTO> listarPesadasPorGuia(String token, Long guiaId) {
        validarAccesoGuiaLectura(token, guiaId);
        return listarPesadasPorGuia(guiaId);
    }

    @Override
    public PesadaBalanzaDTO registrarLecturaExternaEnNodo(
            String token,
            String codigoNodo,
            String matricula,
            int pesoKg
    ) {
        verificarControl();
        control.admin(token);
        return registrarLecturaExternaEnNodo(codigoNodo, matricula, pesoKg);
    }

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

    public GestionBalanzaServiceImpl(
            EntityManager em,
            BalanzaClient balanzaClient,
            FiscalizacionProducerLocal fiscalizacionProducer,
            GestionNodosPerifericosServiceLocal nodosPerifericosService
    ) {
        this.em = em;
        this.balanzaClient = balanzaClient;
        this.fiscalizacionProducer = fiscalizacionProducer;
        this.nodosPerifericosService = nodosPerifericosService;
    }

    void setNodosPerifericosService(GestionNodosPerifericosServiceLocal nodosPerifericosService) {
        this.nodosPerifericosService = nodosPerifericosService;
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
        return registrarPesadaDesdeMock((Long) null);
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
    public List<PesadaBalanzaDTO> sincronizarPesadasDeGuia(Long guiaId, String correlacion) {
        if (guiaId == null) {
            throw new BusinessException("El ID de guía es obligatorio para consultar balanzas.");
        }
        if (balanzaClient == null) {
            throw new BusinessException("El cliente de balanza no está configurado.");
        }
        GuiaDeViaje guia = em.find(GuiaDeViaje.class, guiaId);
        if (guia == null) {
            throw new BusinessException("No existe la guía de viaje #" + guiaId);
        }
        if (guia.getVehiculo() == null || guia.getVehiculo().getMatricula() == null || guia.getVehiculo().getMatricula().isBlank()) {
            throw new BusinessException("La guía #" + guiaId + " no tiene un vehículo asignado.");
        }

        String matricula = guia.getVehiculo().getMatricula().trim();
        LocalDate fechaDesde = guia.getFecha() != null ? guia.getFecha() : LocalDate.now();
        LocalDate hoy = LocalDate.now();
        LocalDate fechaHasta = fechaDesde.isAfter(hoy) ? fechaDesde : hoy;
        Integer nroEmpresa = guia.getEmpresa() != null ? guia.getEmpresa().getNroEmpresa() : null;

        List<PesadaBalanzaDTO> remotas;
        if (nodosPerifericosService != null) {
            List<NodoPerifericoConfig> activas = nodosPerifericosService.listarBalanzasHabilitadas();
            if (activas.isEmpty()) {
                throw new BusinessException("Todas las balanzas periféricas están apagadas por el administrador.");
            }
            Set<String> endpoints = new LinkedHashSet<>();
            for (NodoPerifericoConfig cfg : activas) {
                endpoints.add(GestionNodosPerifericosServiceImpl.normalizarBalanzaUrl(cfg.endpointUrl()));
            }
            remotas = new ArrayList<>();
            for (String ep : endpoints) {
                remotas.addAll(balanzaClient.consultarPesadasVehiculoEnEndpoint(
                        ep, matricula, fechaDesde, fechaHasta, nroEmpresa, correlacion
                ));
            }
        } else {
            remotas = balanzaClient.consultarPesadasVehiculo(
                    matricula, fechaDesde, fechaHasta, nroEmpresa, correlacion
            );
        }

        List<PesadaBalanzaDTO> importadas = persistirPesadasRemotasIdempotentes(em, guia, remotas, hoy);

        if (!importadas.isEmpty() && fiscalizacionProducer != null && guia.getId() != null) {
            fiscalizacionProducer.solicitarEvaluacion(guia.getId());
        }

        LOGGER.log(Level.INFO, "Sincronización de balanza para guía #{0}: {1} remotas consultadas, {2} nuevas importadas.",
                new Object[]{guiaId, remotas != null ? remotas.size() : 0, importadas.size()});
        return importadas;
    }

    @Override
    public int sondearBalanzasPeriodico() {
        if (balanzaClient == null || em == null || nodosPerifericosService == null) {
            return 0;
        }
        List<NodoPerifericoConfig> balanzasHabilitadas = nodosPerifericosService.listarBalanzasHabilitadas();
        if (balanzasHabilitadas.isEmpty()) {
            return 0;
        }

        Set<String> endpointsActivos = new LinkedHashSet<>();
        for (NodoPerifericoConfig cfg : balanzasHabilitadas) {
            endpointsActivos.add(GestionNodosPerifericosServiceImpl.normalizarBalanzaUrl(cfg.endpointUrl()));
        }

        LocalDate hoy = LocalDate.now();
        String corr = UUID.randomUUID().toString();

        // 1. Inicializar watermark de pesadas previas por endpoint para evitar falsos positivos en lecturas semilla
        for (String endpoint : endpointsActivos) {
            if (!nodosPerifericosService.isWatermarkInicializado(endpoint)) {
                try {
                    List<PesadaBalanzaDTO> existentesHoy = balanzaClient.consultarPesadasPorFechaEnEndpoint(endpoint, hoy, corr);
                    int maxId = existentesHoy.stream()
                            .mapToInt(PesadaBalanzaDTO::idPesada)
                            .max()
                            .orElse(0);
                    nodosPerifericosService.inicializarWatermark(endpoint, maxId);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "No se pudo inicializar watermark de balanza en {0}: {1}",
                            new Object[]{endpoint, e.getMessage()});
                }
            }
        }

        // 2. Obtener guías EN_CURSO
        List<GuiaDeViaje> guiasEnCurso = buscarGuiasEnCurso();
        Set<String> matriculasEnCursoNorm = new HashSet<>();
        for (GuiaDeViaje g : guiasEnCurso) {
            if (g.getVehiculo() != null && g.getVehiculo().getMatricula() != null) {
                matriculasEnCursoNorm.add(normalizarMatriculaClave(g.getVehiculo().getMatricula()));
            }
        }

        // 3. Registrar paso automático de guías EN_CURSO en las balanzas habilitadas
        for (GuiaDeViaje guia : guiasEnCurso) {
            if (guia.getId() == null || guia.getVehiculo() == null || guia.getVehiculo().getMatricula() == null) {
                continue;
            }
            String matricula = guia.getVehiculo().getMatricula().trim();
            Integer nroEmpresa = guia.getEmpresa() != null ? guia.getEmpresa().getNroEmpresa() : null;

            for (NodoPerifericoConfig balanza : balanzasHabilitadas) {
                ModoNodoPeriferico modo = balanza.modoOperacion() != null
                        ? balanza.modoOperacion()
                        : ModoNodoPeriferico.CALIBRADA;
                if (nodosPerifericosService.yaEmitioAutoPesada(guia.getId(), balanza.codigo(), modo)) {
                    continue;
                }
                int peso = calcularPesoAutomaticoParaBalanza(guia, modo);
                String endpointNorm = GestionNodosPerifericosServiceImpl.normalizarBalanzaUrl(balanza.endpointUrl());
                try {
                    PesadaBalanzaDTO reqDto = new PesadaBalanzaDTO(
                            null, hoy, LocalTime.now().withNano(0), peso, matricula, guia.getId()
                    );
                    PesadaBalanzaDTO creada = balanzaClient.registrarPesadaEnPerifericoEnEndpoint(
                            endpointNorm, reqDto, nroEmpresa, balanza.estacionPeaje(), corr
                    );
                    nodosPerifericosService.marcarAutoPesadaEmitida(guia.getId(), balanza.codigo(), modo);
                    if (creada != null && creada.idPesada() > 0) {
                        nodosPerifericosService.actualizarWatermarkIdPesada(endpointNorm, creada.idPesada());
                    }
                    LOGGER.log(Level.INFO,
                            "Paso automático por balanza {0} ({1}): Guía #{2}, Vehículo {3}, Peso {4} kg.",
                            new Object[]{balanza.codigo(), modo, guia.getId(), matricula, peso});
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "No se pudo emitir paso automático de guía #{0} en balanza {1}: {2}",
                            new Object[]{guia.getId(), balanza.codigo(), e.getMessage()});
                }
            }
        }

        // 4. Sincronización periódica (pull) para cada guía EN_CURSO
        int totalImportadas = 0;
        for (GuiaDeViaje guia : guiasEnCurso) {
            if (guia.getId() == null || guia.getVehiculo() == null || guia.getVehiculo().getMatricula() == null) {
                continue;
            }
            String matricula = guia.getVehiculo().getMatricula().trim();
            LocalDate fechaDesde = guia.getFecha() != null ? guia.getFecha() : hoy;
            LocalDate fechaHasta = fechaDesde.isAfter(hoy) ? fechaDesde : hoy;
            Integer nroEmpresa = guia.getEmpresa() != null ? guia.getEmpresa().getNroEmpresa() : null;

            List<PesadaBalanzaDTO> remotas = new ArrayList<>();
            for (String endpoint : endpointsActivos) {
                try {
                    List<PesadaBalanzaDTO> encontradas = balanzaClient.consultarPesadasVehiculoEnEndpoint(
                            endpoint, matricula, fechaDesde, fechaHasta, nroEmpresa, corr
                    );
                    if (encontradas != null) {
                        remotas.addAll(encontradas);
                        for (PesadaBalanzaDTO rem : encontradas) {
                            if (rem != null && rem.idPesada() > 0) {
                                nodosPerifericosService.actualizarWatermarkIdPesada(endpoint, rem.idPesada());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error consultando balanza {0} para guía #{1}: {2}",
                            new Object[]{endpoint, guia.getId(), e.getMessage()});
                }
            }

            List<PesadaBalanzaDTO> importadas = persistirPesadasRemotasIdempotentes(em, guia, remotas, hoy);
            if (!importadas.isEmpty()) {
                totalImportadas += importadas.size();
                if (fiscalizacionProducer != null) {
                    fiscalizacionProducer.solicitarEvaluacion(guia.getId());
                }
            }
        }

        // 5. Detectar lecturas nuevas registradas después del arranque para vehículos sin guía EN_CURSO (VIAJE_SIN_GUIA)
        for (String endpoint : endpointsActivos) {
            if (!nodosPerifericosService.isWatermarkInicializado(endpoint)) {
                continue;
            }
            try {
                int watermarkActual = nodosPerifericosService.getWatermarkIdPesada(endpoint);
                List<PesadaBalanzaDTO> delDia = balanzaClient.consultarPesadasPorFechaEnEndpoint(endpoint, hoy, corr);
                if (delDia == null || delDia.isEmpty()) {
                    continue;
                }
                for (PesadaBalanzaDTO rem : delDia) {
                    if (rem == null || rem.idPesada() <= watermarkActual || rem.pesoRegistrado() <= 0) {
                        continue;
                    }
                    nodosPerifericosService.actualizarWatermarkIdPesada(endpoint, rem.idPesada());
                    String matNorm = normalizarMatriculaClave(rem.matricula());
                    if (matNorm.isEmpty() || matriculasEnCursoNorm.contains(matNorm)) {
                        continue;
                    }
                    Vehiculo vehiculo = buscarVehiculoFlexible(rem.matricula());
                    if (vehiculo == null) {
                        continue;
                    }
                    LocalDate f = rem.fecha() != null ? rem.fecha() : hoy;
                    LocalTime h = rem.hora() != null ? rem.hora().withNano(0) : LocalTime.now().withNano(0);
                    PesadaBalanza sinGuia = new PesadaBalanza(f, h, rem.pesoRegistrado(), null, vehiculo);
                    em.persist(sinGuia);
                    em.flush();
                    totalImportadas++;
                    if (fiscalizacionProducer != null) {
                        fiscalizacionProducer.solicitarEvaluacionSinGuia(
                                vehiculo.getMatricula(),
                                "Lectura periódica de balanza #" + rem.idPesada() + " (" + rem.pesoRegistrado()
                                        + " kg el " + f + " " + h + ") sin guía EN_CURSO"
                        );
                    }
                    LOGGER.log(Level.WARNING,
                            "Sondeo periódico detectó pesada #{0} para vehículo {1} SIN guía EN_CURSO -> VIAJE_SIN_GUIA.",
                            new Object[]{rem.idPesada(), vehiculo.getMatricula()});
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error en detección periódica sin guía contra {0}: {1}",
                        new Object[]{endpoint, e.getMessage()});
            }
        }

        return totalImportadas;
    }

    @Override
    public PesadaBalanzaDTO registrarLecturaExternaEnNodo(String codigoNodo, String matricula, int pesoKg) {
        if (balanzaClient == null || nodosPerifericosService == null) {
            throw new BusinessException("El subsistema de nodos de balanza no está disponible.");
        }
        if (matricula == null || matricula.isBlank()) {
            throw new BusinessException("La matrícula del vehículo es obligatoria.");
        }
        if (pesoKg <= 0) {
            throw new BusinessException("El peso debe ser mayor a 0 kg.");
        }
        Optional<NodoPerifericoConfig> optNodo = nodosPerifericosService.buscarPorCodigo(codigoNodo);
        if (optNodo.isEmpty() || !optNodo.get().esBalanza()) {
            throw new BusinessException("No existe la balanza periférica " + codigoNodo + ".");
        }
        NodoPerifericoConfig balanza = optNodo.get();
        if (!balanza.habilitado()) {
            throw new BusinessException("La balanza " + balanza.codigo() + " está apagada por el administrador.");
        }

        Vehiculo v = buscarVehiculoFlexible(matricula);
        Integer nroEmpresa = (v != null && v.getEmpresa() != null) ? v.getEmpresa().getNroEmpresa() : null;
        String matEfectiva = v != null ? v.getMatricula() : matricula.trim();
        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now().withNano(0);
        String endpointNorm = GestionNodosPerifericosServiceImpl.normalizarBalanzaUrl(balanza.endpointUrl());

        // Asegurar baseline previo para no perder esta lectura si es el primer contacto con el endpoint
        if (!nodosPerifericosService.isWatermarkInicializado(endpointNorm)) {
            try {
                List<PesadaBalanzaDTO> previas = balanzaClient.consultarPesadasPorFechaEnEndpoint(endpointNorm, hoy, UUID.randomUUID().toString());
                int maxId = previas.stream().mapToInt(PesadaBalanzaDTO::idPesada).max().orElse(0);
                nodosPerifericosService.inicializarWatermark(endpointNorm, maxId);
            } catch (Exception ignored) {
            }
        }

        PesadaBalanzaDTO dto = new PesadaBalanzaDTO(null, hoy, ahora, pesoKg, matEfectiva, null);
        return balanzaClient.registrarPesadaEnPerifericoEnEndpoint(
                endpointNorm, dto, nroEmpresa, balanza.estacionPeaje(), UUID.randomUUID().toString()
        );
    }

    static int calcularPesoAutomaticoParaBalanza(GuiaDeViaje guia, ModoNodoPeriferico modo) {
        Vehiculo v = guia.getVehiculo();
        int tara = (v != null && v.getPeso() > 0) ? v.getPeso() : 8500;
        int capacidad = (v != null && v.getCapacidadCarga() > 0) ? v.getCapacidadCarga() : 30000;
        if (modo == ModoNodoPeriferico.SOBRECARGA) {
            return tara + capacidad + 2500;
        }
        int cargaDeclarada = guia.getVolumenCarga() > 0
                ? Math.round(guia.getVolumenCarga())
                : Math.max(1000, (capacidad * 80) / 100);
        int cargaSegura = Math.min(capacidad, Math.max(1000, cargaDeclarada));
        return tara + cargaSegura;
    }

    private List<GuiaDeViaje> buscarGuiasEnCurso() {
        TypedQuery<GuiaDeViaje> q = em.createQuery(
                "SELECT g FROM GuiaDeViaje g LEFT JOIN FETCH g.vehiculo LEFT JOIN FETCH g.empresa WHERE g.estado = :enCurso ORDER BY g.id ASC",
                GuiaDeViaje.class
        );
        if (q == null) {
            return List.of();
        }
        q.setParameter("enCurso", TipoEstado.EN_CURSO);
        List<GuiaDeViaje> res = q.getResultList();
        return res != null ? res : List.of();
    }

    private Vehiculo buscarVehiculoFlexible(String matriculaRaw) {
        if (matriculaRaw == null || matriculaRaw.isBlank()) {
            return null;
        }
        Vehiculo v = buscarVehiculoPorMatricula(matriculaRaw);
        if (v != null) {
            return v;
        }
        String buscadaNorm = normalizarMatriculaClave(matriculaRaw);
        if (buscadaNorm.isEmpty()) {
            return null;
        }
        TypedQuery<Vehiculo> q = em.createQuery("SELECT v FROM Vehiculo v", Vehiculo.class);
        if (q == null) {
            return null;
        }
        List<Vehiculo> todos = q.getResultList();
        if (todos != null) {
            for (Vehiculo cand : todos) {
                if (cand != null && normalizarMatriculaClave(cand.getMatricula()).equals(buscadaNorm)) {
                    return cand;
                }
            }
        }
        return null;
    }

    private static String normalizarMatriculaClave(String matricula) {
        if (matricula == null) {
            return "";
        }
        return matricula.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Persiste de forma idempotente en la base central las pesadas recibidas del nodo periférico
     * para una guía dada, evitando duplicados por (fecha, hora sin nanosegundos, pesoRegistrado).
     */
    static List<PesadaBalanzaDTO> persistirPesadasRemotasIdempotentes(
            EntityManager em,
            GuiaDeViaje guia,
            List<PesadaBalanzaDTO> remotas,
            LocalDate fechaDefecto
    ) {
        if (em == null || guia == null || remotas == null || remotas.isEmpty()) {
            return List.of();
        }
        Set<String> existentes = new HashSet<>();
        TypedQuery<PesadaBalanza> q = em.createQuery(
                "SELECT p FROM PesadaBalanza p WHERE p.guia.id = :guiaId",
                PesadaBalanza.class
        );
        if (q != null) {
            q.setParameter("guiaId", guia.getId());
            List<PesadaBalanza> actuales = q.getResultList();
            if (actuales != null) {
                for (PesadaBalanza p : actuales) {
                    existentes.add(clavePesada(p.getFecha(), p.getHora(), p.getPesoRegistrado()));
                }
            }
        }

        String matricula = guia.getVehiculo() != null ? guia.getVehiculo().getMatricula() : null;
        LocalDate fallbackFecha = fechaDefecto != null ? fechaDefecto : LocalDate.now();
        List<PesadaBalanzaDTO> importadas = new ArrayList<>();
        for (PesadaBalanzaDTO remota : remotas) {
            if (remota == null || remota.pesoRegistrado() <= 0) {
                continue;
            }
            LocalDate f = remota.fecha() != null ? remota.fecha() : fallbackFecha;
            LocalTime h = remota.hora() != null ? remota.hora().withNano(0) : LocalTime.now().withNano(0);
            if (!existentes.add(clavePesada(f, h, remota.pesoRegistrado()))) {
                continue;
            }
            PesadaBalanza pesada = new PesadaBalanza(f, h, remota.pesoRegistrado(), guia);
            em.persist(pesada);
            em.flush();
            importadas.add(new PesadaBalanzaDTO(
                    pesada.getIdPesada(),
                    pesada.getFecha(),
                    pesada.getHora(),
                    pesada.getPesoRegistrado(),
                    matricula != null ? matricula : remota.matricula(),
                    guia.getId()
            ));
        }
        return importadas;
    }

    @Override
    public PesadaBalanzaDTO registrarPesadaViaPeriferico(PesadaBalanzaDTO dto, String peaje, String correlacion) {
        if (dto == null) {
            throw new BusinessException("La información de pesada no puede ser nula.");
        }
        if (dto.pesoRegistrado() <= 0) {
            throw new BusinessException("El peso registrado debe ser mayor a 0.");
        }
        if (balanzaClient == null) {
            throw new BusinessException("El cliente de balanza no está configurado.");
        }

        String matricula = dto.matricula() != null && !dto.matricula().isBlank() ? dto.matricula().trim() : null;
        Integer nroEmpresa = null;

        if (dto.guiaId() != null) {
            GuiaDeViaje guia = em.find(GuiaDeViaje.class, dto.guiaId());
            if (guia == null) {
                throw new BusinessException("No existe la guía de viaje con ID " + dto.guiaId());
            }
            if (guia.getEstado() != TipoEstado.EN_CURSO) {
                throw new BusinessException(
                        "Solo se pueden registrar pesajes en guías con estado EN_CURSO (la guía #"
                                + guia.getId() + " se encuentra en estado " + guia.getEstado() + ")."
                );
            }
            if (matricula == null && guia.getVehiculo() != null) {
                matricula = guia.getVehiculo().getMatricula();
            }
            if (guia.getEmpresa() != null) {
                nroEmpresa = guia.getEmpresa().getNroEmpresa();
            }
        }

        if (matricula == null || matricula.isBlank()) {
            throw new BusinessException("Debe indicar una matrícula o una guía con vehículo asignado.");
        }

        if (nroEmpresa == null) {
            Vehiculo v = buscarVehiculoPorMatricula(matricula);
            if (v != null && v.getEmpresa() != null) {
                nroEmpresa = v.getEmpresa().getNroEmpresa();
            }
        }

        LocalDate fechaEfectiva = dto.fecha() != null ? dto.fecha() : LocalDate.now();
        LocalTime horaEfectiva = dto.hora() != null ? dto.hora().withNano(0) : LocalTime.now().withNano(0);

        PesadaBalanzaDTO paraPeriferico = new PesadaBalanzaDTO(
                null,
                fechaEfectiva,
                horaEfectiva,
                dto.pesoRegistrado(),
                matricula,
                dto.guiaId()
        );
        PesadaBalanzaDTO confirmada = balanzaClient.registrarPesadaEnPeriferico(paraPeriferico, nroEmpresa, peaje, correlacion);
        LOGGER.log(Level.INFO, "Pesada registrada en nodo periférico (idPesada={0}, matrícula={1}, peso={2} kg). Pendiente de sincronización request-response.",
                new Object[]{confirmada.idPesada(), matricula, confirmada.pesoRegistrado()});

        return new PesadaBalanzaDTO(
                confirmada.idPesada(),
                confirmada.fecha() != null ? confirmada.fecha() : fechaEfectiva,
                confirmada.hora() != null ? confirmada.hora() : horaEfectiva,
                confirmada.pesoRegistrado() > 0 ? confirmada.pesoRegistrado() : dto.pesoRegistrado(),
                matricula,
                dto.guiaId()
        );
    }

    private static String clavePesada(LocalDate fecha, LocalTime hora, int peso) {
        LocalDate f = fecha != null ? fecha : LocalDate.MIN;
        LocalTime h = hora != null ? hora.withNano(0) : LocalTime.MIDNIGHT;
        return f + "|" + h + "|" + peso;
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
        if (q == null) {
            return List.of();
        }
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
