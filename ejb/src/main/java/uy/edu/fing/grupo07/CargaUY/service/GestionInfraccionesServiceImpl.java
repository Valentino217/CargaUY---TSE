package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Descargo;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.RegistroAuditoria;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.RegistroAuditoriaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación del servicio de gestión de casos de fiscalización, notificaciones, descargos
 * y trazabilidad append-only de auditoría (Puntos D.5, E.8, E.c y E.d).
 */
@Stateless
public class GestionInfraccionesServiceImpl implements GestionInfraccionesServiceLocal {

    private static final Logger LOGGER = Logger.getLogger(GestionInfraccionesServiceImpl.class.getName());
    private static final int PLAZO_DESCARGO_DIAS = 7;
    private static final String FUNDAMENTO_CIERRE_AUTOMATICO =
            "Sanción confirmada automáticamente tras transcurrir 7 días desde la notificación sin presentación de descargo por parte de la empresa.";

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    @EJB
    private ControlAcceso control;

    public GestionInfraccionesServiceImpl() {
    }

    public GestionInfraccionesServiceImpl(EntityManager em) {
        this.em = em;
    }

    public GestionInfraccionesServiceImpl(EntityManager em, ControlAcceso control) {
        this.em = em;
        this.control = control;
    }

    @Override
    public List<InfraccionDTO> listarInfracciones(String token, EstadoInfraccion estado, TipoInfraccion tipo) {
        exigirControl().exigir(token, TipoUsuario.FUNCIONARIO, TipoUsuario.ADMINISTRADOR, TipoUsuario.AUTORIDAD);
        return listarInfracciones(estado, tipo);
    }

    @Override
    public List<InfraccionDTO> listarInfraccionesParaEmpresa(String token) {
        Acceso actor = exigirControl().exigir(
                token,
                TipoUsuario.CIUDADANO,
                TipoUsuario.FUNCIONARIO,
                TipoUsuario.ADMINISTRADOR,
                TipoUsuario.AUTORIDAD
        );

        if (actor.tiene(TipoUsuario.ADMINISTRADOR)
                || actor.tiene(TipoUsuario.FUNCIONARIO)
                || actor.tiene(TipoUsuario.AUTORIDAD)) {
            List<Infraccion> todasNotificadas = em.createQuery(
                    "SELECT i FROM Infraccion i WHERE i.estado <> :detectada ORDER BY i.fecha DESC, i.idCaso DESC",
                    Infraccion.class
            ).setParameter("detectada", EstadoInfraccion.DETECTADA).getResultList();
            List<InfraccionDTO> dtos = new ArrayList<>(todasNotificadas.size());
            for (Infraccion inf : todasNotificadas) {
                dtos.add(convertirAInfraccionDTO(inf));
            }
            return dtos;
        }

        List<Integer> empresasAutorizadas = em.createQuery(
                "SELECT v.empresa.nroEmpresa FROM VinculoEmpresa v "
                        + "WHERE v.usuario.id = :usuarioId AND v.responsable = true AND v.empresa.activa = true",
                Integer.class
        ).setParameter("usuarioId", actor.id()).getResultList();

        if (empresasAutorizadas == null || empresasAutorizadas.isEmpty()) {
            return List.of();
        }

        List<Infraccion> infraccionesEmpresa = em.createQuery(
                "SELECT i FROM Infraccion i "
                        + "LEFT JOIN i.guia g "
                        + "LEFT JOIN g.empresa ge "
                        + "LEFT JOIN i.vehiculo v "
                        + "LEFT JOIN v.empresa ve "
                        + "WHERE i.estado <> :detectada "
                        + "AND ((ge IS NOT NULL AND ge.nroEmpresa IN :empresas) "
                        + "OR (ge IS NULL AND ve IS NOT NULL AND ve.nroEmpresa IN :empresas)) "
                        + "ORDER BY i.fecha DESC, i.idCaso DESC",
                Infraccion.class
        )
                .setParameter("detectada", EstadoInfraccion.DETECTADA)
                .setParameter("empresas", empresasAutorizadas)
                .getResultList();

        List<InfraccionDTO> resultados = new ArrayList<>(infraccionesEmpresa.size());
        for (Infraccion infraccion : infraccionesEmpresa) {
            resultados.add(convertirAInfraccionDTO(infraccion));
        }
        return resultados;
    }

    @Override
    public List<InfraccionDTO> listarInfracciones(EstadoInfraccion estado, TipoInfraccion tipo) {
        StringBuilder jpql = new StringBuilder("SELECT i FROM Infraccion i WHERE 1=1");

        if (estado != null) {
            jpql.append(" AND i.estado = :estado");
        }
        if (tipo != null) {
            jpql.append(" AND i.tipo = :tipo");
        }
        jpql.append(" ORDER BY i.fecha DESC, i.idCaso DESC");

        TypedQuery<Infraccion> query = em.createQuery(jpql.toString(), Infraccion.class);

        if (estado != null) {
            query.setParameter("estado", estado);
        }
        if (tipo != null) {
            query.setParameter("tipo", tipo);
        }

        List<Infraccion> infracciones = query.getResultList();
        List<InfraccionDTO> resultados = new ArrayList<>(infracciones.size());

        for (Infraccion infraccion : infracciones) {
            resultados.add(convertirAInfraccionDTO(infraccion));
        }

        return resultados;
    }

    @Override
    public InfraccionDTO obtenerInfraccion(String token, int idCaso) {
        Acceso actor = exigirControl().exigir(
                token,
                TipoUsuario.FUNCIONARIO,
                TipoUsuario.ADMINISTRADOR,
                TipoUsuario.AUTORIDAD,
                TipoUsuario.CIUDADANO
        );
        Infraccion infraccion = buscarInfraccionPorId(idCaso);
        if (!actor.tiene(TipoUsuario.ADMINISTRADOR)
                && !actor.tiene(TipoUsuario.FUNCIONARIO)
                && !actor.tiene(TipoUsuario.AUTORIDAD)) {
            Empresa empresa = infraccion.resolverEmpresa();
            if (empresa == null || infraccion.getEstado() == EstadoInfraccion.DETECTADA) {
                throw new ErrorAcceso(403, "Sin acceso a este expediente de infracción");
            }
            exigirControl().empresa(token, empresa.getNroEmpresa(), false);
        }
        return convertirAInfraccionDTO(infraccion);
    }

    @Override
    public InfraccionDTO obtenerInfraccion(int idCaso) {
        Infraccion infraccion = buscarInfraccionPorId(idCaso);
        return convertirAInfraccionDTO(infraccion);
    }

    @Override
    public InfraccionDTO notificarInfraccion(String token, int idCaso) {
        Acceso actor = exigirControl().exigir(token, TipoUsuario.FUNCIONARIO, TipoUsuario.ADMINISTRADOR);
        return ejecutarNotificacion(
                idCaso,
                formatearActor(actor),
                "Notificación oficial cursada a la empresa transportista."
        );
    }

    @Override
    public InfraccionDTO notificarInfraccion(int idCaso) {
        return ejecutarNotificacion(
                idCaso,
                "SISTEMA / FUNCIONARIO",
                "Notificación oficial cursada a la empresa transportista."
        );
    }

    @Override
    public InfraccionDTO procesarNotificacionAutomatica(int idCaso) {
        Infraccion infraccion = em.find(Infraccion.class, idCaso);
        if (infraccion == null) {
            LOGGER.log(Level.WARNING, "Notificación automática omitida: no existe caso con ID {0}", idCaso);
            return null;
        }
        em.lock(infraccion, LockModeType.PESSIMISTIC_WRITE);
        if (infraccion.getEstado() != EstadoInfraccion.DETECTADA) {
            return convertirAInfraccionDTO(infraccion);
        }

        Empresa empresa = infraccion.resolverEmpresa();
        String nombreEmp = resolverNombreEmpresa(empresa);
        String detalle = "Notificación automática enviada a la empresa transportista"
                + (nombreEmp != null ? " (" + nombreEmp + ")" : "")
                + " al generarse el caso de fiscalización.";

        infraccion.setEstado(EstadoInfraccion.NOTIFICADA);
        infraccion.setFechaNotificacion(LocalDate.now());
        registrarEventoAuditoria(
                infraccion,
                EstadoInfraccion.NOTIFICADA.name(),
                "SISTEMA (Notificación Automática)",
                "NOTIFICAR_INFRACCION",
                detalle
        );
        return convertirAInfraccionDTO(infraccion);
    }

    private InfraccionDTO ejecutarNotificacion(int idCaso, String usuarioAuditado, String fundamentoAuditoria) {
        Infraccion infraccion = buscarInfraccionConBloqueo(idCaso);
        validarEstadoActual(infraccion, EstadoInfraccion.DETECTADA);

        infraccion.setEstado(EstadoInfraccion.NOTIFICADA);
        infraccion.setFechaNotificacion(LocalDate.now());
        registrarEventoAuditoria(
                infraccion,
                EstadoInfraccion.NOTIFICADA.name(),
                usuarioAuditado,
                "NOTIFICAR_INFRACCION",
                fundamentoAuditoria
        );
        return convertirAInfraccionDTO(infraccion);
    }

    @Override
    public InfraccionDTO presentarDescargo(String token, int idCaso, DescargoRequestDTO descargoRequest) {
        validarDescargoRequest(descargoRequest);
        Infraccion infraccion = buscarInfraccionConBloqueo(idCaso);
        Empresa empresa = infraccion.resolverEmpresa();
        Acceso actor;
        if (empresa != null) {
            actor = exigirControl().empresa(token, empresa.getNroEmpresa(), true);
            if (!actor.tiene(TipoUsuario.ADMINISTRADOR)) {
                uy.edu.fing.grupo07.CargaUY.domain.entity.VinculoEmpresa vinculo = em.find(
                        uy.edu.fing.grupo07.CargaUY.domain.entity.VinculoEmpresa.class,
                        uy.edu.fing.grupo07.CargaUY.domain.entity.VinculoEmpresa.clave(empresa.getNroEmpresa(), actor.id())
                );
                if (!actor.tiene(TipoUsuario.CIUDADANO) || !empresa.isActiva() || vinculo == null || !vinculo.isResponsable()) {
                    throw new ErrorAcceso(403, "Solo un representante responsable de la empresa sancionada puede presentar descargos");
                }
            }
        } else {
            actor = exigirControl().exigir(token, TipoUsuario.ADMINISTRADOR);
        }
        return ejecutarPresentarDescargo(infraccion, descargoRequest, formatearActor(actor));
    }

    @Override
    public InfraccionDTO presentarDescargo(int idCaso, DescargoRequestDTO descargoRequest) {
        validarDescargoRequest(descargoRequest);
        Infraccion infraccion = buscarInfraccionConBloqueo(idCaso);
        String nombreEmp = resolverNombreEmpresa(infraccion.resolverEmpresa());
        String actorEmpresa = nombreEmp != null
                ? "Empresa Transportista (" + nombreEmp + ")"
                : "Empresa Transportista";
        return ejecutarPresentarDescargo(infraccion, descargoRequest, actorEmpresa);
    }

    private InfraccionDTO ejecutarPresentarDescargo(
            Infraccion infraccion,
            DescargoRequestDTO descargoRequest,
            String usuarioAuditado
    ) {
        validarEstadoActual(
                infraccion,
                EstadoInfraccion.NOTIFICADA,
                EstadoInfraccion.EN_DESCARGO
        );
        if (infraccion.getEstado() == EstadoInfraccion.NOTIFICADA && infraccion.getDescargo() == null) {
            LocalDate fechaNotif = resolverFechaNotificacion(infraccion);
            LocalDate fechaLimite = LocalDate.now().minusDays(PLAZO_DESCARGO_DIAS);
            if (fechaNotif != null && !fechaNotif.isAfter(fechaLimite)) {
                aplicarResolucion(
                        infraccion,
                        EstadoInfraccion.CONFIRMADA,
                        "SISTEMA (Cierre Automático)",
                        "CONFIRMAR_INFRACCION",
                        FUNDAMENTO_CIERRE_AUTOMATICO
                );
                throw new BusinessException(
                        "El plazo de " + PLAZO_DESCARGO_DIAS
                                + " días para presentar descargo ha vencido; la infracción fue confirmada automáticamente."
                );
            }
        }

        String textoLimpio = descargoRequest.texto().trim();
        Descargo descargoActual = infraccion.getDescargo();
        boolean esEdicion = descargoActual != null;
        if (esEdicion && textoLimpio.equals(descargoActual.getDescargo())) {
            return convertirAInfraccionDTO(infraccion);
        }
        if (esEdicion) {
            descargoActual.setDescargo(textoLimpio);
            descargoActual.setFecha(LocalDate.now());
        } else {
            Descargo nuevoDescargo = new Descargo(
                    LocalDate.now(),
                    textoLimpio,
                    infraccion
            );
            infraccion.setDescargo(nuevoDescargo);
        }

        infraccion.setEstado(EstadoInfraccion.EN_DESCARGO);
        registrarEventoAuditoria(
                infraccion,
                EstadoInfraccion.EN_DESCARGO.name(),
                usuarioAuditado,
                esEdicion ? "EDITAR_DESCARGO" : "PRESENTAR_DESCARGO",
                textoLimpio
        );
        return convertirAInfraccionDTO(infraccion);
    }

    @Override
    public InfraccionDTO confirmarInfraccion(String token, int idCaso, String fundamento) {
        Acceso actor = exigirControl().exigir(token, TipoUsuario.FUNCIONARIO, TipoUsuario.ADMINISTRADOR);
        return confirmarInfraccion(idCaso, new ResolucionInfraccionDTO(actor.id(), fundamento));
    }

    @Override
    public InfraccionDTO confirmarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion) {
        Infraccion infraccion = buscarInfraccionConBloqueo(idCaso);
        validarEstadoActual(
                infraccion,
                EstadoInfraccion.NOTIFICADA,
                EstadoInfraccion.EN_DESCARGO
        );

        return resolverInfraccion(
                infraccion,
                resolucion,
                EstadoInfraccion.CONFIRMADA,
                "CONFIRMAR_INFRACCION"
        );
    }

    @Override
    public InfraccionDTO desestimarInfraccion(String token, int idCaso, String fundamento) {
        Acceso actor = exigirControl().exigir(token, TipoUsuario.FUNCIONARIO, TipoUsuario.ADMINISTRADOR);
        return desestimarInfraccion(idCaso, new ResolucionInfraccionDTO(actor.id(), fundamento));
    }

    @Override
    public InfraccionDTO desestimarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion) {
        Infraccion infraccion = buscarInfraccionConBloqueo(idCaso);
        validarEstadoActual(
                infraccion,
                EstadoInfraccion.DETECTADA,
                EstadoInfraccion.NOTIFICADA,
                EstadoInfraccion.EN_DESCARGO
        );

        return resolverInfraccion(
                infraccion,
                resolucion,
                EstadoInfraccion.DESESTIMADA,
                "DESESTIMAR_INFRACCION"
        );
    }

    /**
     * Job periódico que verifica casos notificados vencidos sin descargo para confirmar la sanción.
     */
    @Schedule(hour = "*", minute = "0", persistent = false)
    public void ejecutarCierreAutomaticoSinDescargo() {
        try {
            int cerrados = cerrarInfraccionesNotificadasVencidas();
            if (cerrados > 0) {
                LOGGER.log(
                        Level.INFO,
                        "Cierre automático de fiscalización: {0} infracción(es) confirmada(s) tras vencer el plazo de {1} días sin descargo.",
                        new Object[]{cerrados, PLAZO_DESCARGO_DIAS}
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error ejecutando cierre automático de infracciones notificadas vencidas", e);
        }
    }

    @Override
    public int cerrarInfraccionesNotificadasVencidas() {
        TypedQuery<Infraccion> query = em.createQuery(
                "SELECT i FROM Infraccion i WHERE i.estado = :estado",
                Infraccion.class
        );
        query.setParameter("estado", EstadoInfraccion.NOTIFICADA);
        List<Infraccion> notificadas = query.getResultList();
        if (notificadas == null || notificadas.isEmpty()) {
            return 0;
        }

        LocalDate fechaLimite = LocalDate.now().minusDays(PLAZO_DESCARGO_DIAS);
        int cerrados = 0;

        for (Infraccion infraccion : notificadas) {
            try {
                em.lock(infraccion, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            } catch (Exception ignored) {
                // En tests unitarios con mocks que no soportan lock no interrumpir
            }
            if (infraccion.getEstado() != EstadoInfraccion.NOTIFICADA || infraccion.getDescargo() != null) {
                continue;
            }
            LocalDate fechaNotif = resolverFechaNotificacion(infraccion);
            if (fechaNotif != null && !fechaNotif.isAfter(fechaLimite)) {
                aplicarResolucion(
                        infraccion,
                        EstadoInfraccion.CONFIRMADA,
                        "SISTEMA (Cierre Automático)",
                        "CONFIRMAR_INFRACCION",
                        FUNDAMENTO_CIERRE_AUTOMATICO
                );
                cerrados++;
            }
        }
        return cerrados;
    }

    private LocalDate resolverFechaNotificacion(Infraccion infraccion) {
        if (infraccion.getFechaNotificacion() != null) {
            return infraccion.getFechaNotificacion();
        }
        List<RegistroAuditoria> registros = infraccion.getRegistrosAuditoria();
        if (registros != null) {
            for (RegistroAuditoria reg : registros) {
                if ("NOTIFICAR_INFRACCION".equals(reg.getAccion()) && reg.getFecha() != null) {
                    return reg.getFecha();
                }
            }
        }
        return infraccion.getFecha();
    }

    private InfraccionDTO resolverInfraccion(
            Infraccion infraccion,
            ResolucionInfraccionDTO resolucion,
            EstadoInfraccion estadoFinal,
            String accionAuditada
    ) {
        validarResolucion(resolucion);
        Usuario funcionario = buscarFuncionarioExistente(resolucion.funcionarioId());

        String identificacionFuncionario = funcionario.getNombre() + " (CI: " + funcionario.getCi() + ")";
        String fundamentoLimpio = resolucion.fundamento().trim();

        aplicarResolucion(infraccion, estadoFinal, identificacionFuncionario, accionAuditada, fundamentoLimpio);
        return convertirAInfraccionDTO(infraccion);
    }

    private void aplicarResolucion(
            Infraccion infraccion,
            EstadoInfraccion estadoFinal,
            String usuarioAuditado,
            String accionAuditada,
            String fundamentoLimpio
    ) {
        infraccion.setEstado(estadoFinal);
        infraccion.setResolucion(estadoFinal.name());
        infraccion.setFundamento(fundamentoLimpio);
        infraccion.setFechaResolucion(LocalDate.now());

        registrarEventoAuditoria(
                infraccion,
                estadoFinal.name(),
                usuarioAuditado,
                accionAuditada,
                fundamentoLimpio
        );
    }

    private void registrarEventoAuditoria(
            Infraccion infraccion,
            String resolucionEstado,
            String usuario,
            String accion,
            String fundamento
    ) {
        RegistroAuditoria registro = new RegistroAuditoria(
                infraccion,
                resolucionEstado,
                LocalDate.now(),
                usuario,
                accion,
                fundamento
        );
        em.persist(registro);
        infraccion.agregarRegistroAuditoria(registro);
    }

    private ControlAcceso exigirControl() {
        if (control == null) {
            throw new ErrorAcceso(500, "Servicio de control de acceso no disponible");
        }
        return control;
    }

    private String formatearActor(Acceso actor) {
        return actor.nombre() + " (CI: " + actor.ci() + ")";
    }

    private void validarDescargoRequest(DescargoRequestDTO descargoRequest) {
        if (descargoRequest == null) {
            throw new BusinessException("El objeto de descargo no puede ser nulo.");
        }
        if (descargoRequest.texto() == null || descargoRequest.texto().isBlank()) {
            throw new BusinessException("El texto del descargo es obligatorio y no puede estar vacío.");
        }
    }

    private Infraccion buscarInfraccionPorId(int idCaso) {
        Infraccion infraccion = em.find(Infraccion.class, idCaso);
        if (infraccion == null) {
            throw new BusinessException("No existe el caso de infracción con ID " + idCaso);
        }
        return infraccion;
    }

    private Infraccion buscarInfraccionConBloqueo(int idCaso) {
        Infraccion infraccion = em.find(Infraccion.class, idCaso);
        if (infraccion == null) {
            throw new BusinessException("No existe el caso de infracción con ID " + idCaso);
        }
        em.lock(infraccion, LockModeType.PESSIMISTIC_WRITE);
        return infraccion;
    }

    private Usuario buscarFuncionarioExistente(Integer funcionarioId) {
        if (funcionarioId == null) {
            throw new BusinessException("Debe especificarse el funcionario interviniente.");
        }

        Usuario funcionario = em.find(Usuario.class, funcionarioId);
        if (funcionario == null) {
            throw new BusinessException("No existe el funcionario con ID " + funcionarioId);
        }

        if (!funcionario.isActivo()
                || (!funcionario.getRoles().contains(TipoUsuario.FUNCIONARIO)
                && !funcionario.getRoles().contains(TipoUsuario.ADMINISTRADOR))) {
            throw new BusinessException("Usuario sin permisos para resolver infracciones");
        }
        return funcionario;
    }

    private void validarEstadoActual(Infraccion infraccion, EstadoInfraccion... estadosPermitidos) {
        boolean estadoValido = false;
        for (EstadoInfraccion permitido : estadosPermitidos) {
            if (infraccion.getEstado() == permitido) {
                estadoValido = true;
                break;
            }
        }

        if (!estadoValido) {
            throw new BusinessException(
                    "Operación no permitida: la infracción se encuentra en estado "
                            + infraccion.getEstado()
            );
        }
    }

    private void validarResolucion(ResolucionInfraccionDTO resolucion) {
        if (resolucion == null) {
            throw new BusinessException("La información de resolución no puede ser nula.");
        }
        if (resolucion.fundamento() == null || resolucion.fundamento().isBlank()) {
            throw new BusinessException("El fundamento de la resolución es obligatorio.");
        }
    }

    private String resolverNombreEmpresa(Empresa empresa) {
        if (empresa == null) {
            return null;
        }
        String nombre = empresa.getNombrePublico();
        if (nombre == null || nombre.isBlank()) {
            nombre = empresa.getRazonSocial();
        }
        return nombre;
    }

    private InfraccionDTO convertirAInfraccionDTO(Infraccion infraccion) {
        Long guiaId = infraccion.getGuia() != null ? infraccion.getGuia().getId() : null;

        Vehiculo vehiculo = infraccion.resolverVehiculo();
        String matricula = vehiculo != null && vehiculo.getMatricula() != null
                ? String.valueOf(vehiculo.getMatricula())
                : null;

        String nombreEmpresa = resolverNombreEmpresa(infraccion.resolverEmpresa());

        String textoDescargo = null;
        LocalDate fechaDescargo = null;
        if (infraccion.getDescargo() != null) {
            textoDescargo = infraccion.getDescargo().getDescargo();
            fechaDescargo = infraccion.getDescargo().getFecha();
        }

        List<RegistroAuditoria> registros = infraccion.getRegistrosAuditoria();
        List<RegistroAuditoriaDTO> historial = new ArrayList<>(registros.size());
        for (RegistroAuditoria audit : registros) {
            historial.add(new RegistroAuditoriaDTO(
                    audit.getId(),
                    audit.getResolucion(),
                    audit.getFecha(),
                    audit.getTimestampCreacion(),
                    audit.getUsuario(),
                    audit.getAccion(),
                    audit.getFundamento()
            ));
        }

        RegistroAuditoriaDTO auditoriaDTO = historial.isEmpty() ? null : historial.get(historial.size() - 1);

        return new InfraccionDTO(
                infraccion.getIdCaso(),
                infraccion.getFecha(),
                infraccion.getTipo(),
                infraccion.getEstado(),
                infraccion.getEvidencia(),
                infraccion.getFundamento(),
                infraccion.getFechaResolucion(),
                guiaId,
                matricula,
                nombreEmpresa,
                textoDescargo,
                fechaDescargo,
                auditoriaDTO,
                historial
        );
    }
}
