package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Descargo;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.RegistroAuditoria;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.RegistroAuditoriaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación del servicio de gestión de casos de fiscalización y descargos (CU-C4).
 */
@Stateless
public class GestionInfraccionesServiceImpl implements GestionInfraccionesServiceLocal {

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public GestionInfraccionesServiceImpl() {
    }

    public GestionInfraccionesServiceImpl(EntityManager em) {
        this.em = em;
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
    public InfraccionDTO obtenerInfraccion(int idCaso) {
        Infraccion infraccion = buscarInfraccionPorId(idCaso);
        return convertirAInfraccionDTO(infraccion);
    }

    @Override
    public InfraccionDTO notificarInfraccion(int idCaso) {
        Infraccion infraccion = buscarInfraccionPorId(idCaso);
        validarEstadoActual(infraccion, EstadoInfraccion.DETECTADA);

        infraccion.setEstado(EstadoInfraccion.NOTIFICADA);
        return convertirAInfraccionDTO(infraccion);
    }

    @Override
    public InfraccionDTO presentarDescargo(int idCaso, DescargoRequestDTO descargoRequest) {
        if (descargoRequest == null) {
            throw new BusinessException("El objeto de descargo no puede ser nulo.");
        }
        if (descargoRequest.texto() == null || descargoRequest.texto().isBlank()) {
            throw new BusinessException("El texto del descargo es obligatorio y no puede estar vacío.");
        }

        Infraccion infraccion = buscarInfraccionPorId(idCaso);
        validarEstadoActual(
                infraccion,
                EstadoInfraccion.NOTIFICADA,
                EstadoInfraccion.EN_DESCARGO
        );

        Descargo descargoActual = infraccion.getDescargo();
        if (descargoActual != null) {
            descargoActual.setDescargo(descargoRequest.texto().trim());
            descargoActual.setFecha(LocalDate.now());
        } else {
            Descargo nuevoDescargo = new Descargo(
                    LocalDate.now(),
                    descargoRequest.texto().trim(),
                    infraccion
            );
            infraccion.setDescargo(nuevoDescargo);
        }

        infraccion.setEstado(EstadoInfraccion.EN_DESCARGO);
        return convertirAInfraccionDTO(infraccion);
    }

    @Override
    public InfraccionDTO confirmarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion) {
        Infraccion infraccion = buscarInfraccionPorId(idCaso);
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
    public InfraccionDTO desestimarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion) {
        Infraccion infraccion = buscarInfraccionPorId(idCaso);
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

    private InfraccionDTO resolverInfraccion(
            Infraccion infraccion,
            ResolucionInfraccionDTO resolucion,
            EstadoInfraccion estadoFinal,
            String accionAuditada
    ) {
        validarResolucion(resolucion);
        Funcionario funcionario = buscarFuncionarioExistente(resolucion.funcionarioId());

        String identificacionFuncionario = funcionario.getNombre() + " (CI: " + funcionario.getCi() + ")";
        RegistroAuditoria registro = new RegistroAuditoria(
                estadoFinal.name(),
                LocalDate.now(),
                identificacionFuncionario,
                accionAuditada,
                resolucion.fundamento().trim()
        );

        em.persist(registro);

        infraccion.setEstado(estadoFinal);
        infraccion.setResolucion(estadoFinal.name());
        infraccion.setFundamento(resolucion.fundamento().trim());
        infraccion.setFechaResolucion(LocalDate.now());
        infraccion.setRegistroAuditoria(registro);

        return convertirAInfraccionDTO(infraccion);
    }

    private Infraccion buscarInfraccionPorId(int idCaso) {
        Infraccion infraccion = em.find(Infraccion.class, idCaso);
        if (infraccion == null) {
            throw new BusinessException("No existe el caso de infracción con ID " + idCaso);
        }
        return infraccion;
    }

    private Funcionario buscarFuncionarioExistente(Integer funcionarioId) {
        // TODO(P1): En sprint posterior, integrar con la identidad de usuario autenticado en sesión.
        if (funcionarioId == null) {
            throw new BusinessException("Debe especificarse el funcionario interviniente.");
        }

        Funcionario funcionario = em.find(Funcionario.class, funcionarioId);
        if (funcionario == null) {
            throw new BusinessException("No existe el funcionario con ID " + funcionarioId);
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

    private InfraccionDTO convertirAInfraccionDTO(Infraccion infraccion) {
        Long guiaId = null;
        String matricula = null;
        String nombreEmpresa = null;

        if (infraccion.getGuia() != null) {
            guiaId = infraccion.getGuia().getId();

            if (infraccion.getGuia().getVehiculo() != null) {
                matricula = String.valueOf(infraccion.getGuia().getVehiculo().getMatricula());
            }

            if (infraccion.getGuia().getEmpresa() != null) {
                nombreEmpresa = infraccion.getGuia().getEmpresa().getNombrePublico();
                if (nombreEmpresa == null || nombreEmpresa.isBlank()) {
                    nombreEmpresa = infraccion.getGuia().getEmpresa().getRazonSocial();
                }
            }
        }

        String textoDescargo = null;
        LocalDate fechaDescargo = null;
        if (infraccion.getDescargo() != null) {
            textoDescargo = infraccion.getDescargo().getDescargo();
            fechaDescargo = infraccion.getDescargo().getFecha();
        }

        RegistroAuditoriaDTO auditoriaDTO = null;
        RegistroAuditoria audit = infraccion.getRegistroAuditoria();
        if (audit != null) {
            auditoriaDTO = new RegistroAuditoriaDTO(
                    audit.getId(),
                    audit.getResolucion(),
                    audit.getFecha(),
                    audit.getTimestampCreacion(),
                    audit.getUsuario(),
                    audit.getAccion(),
                    audit.getFundamento()
            );
        }

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
                auditoriaDTO
        );
    }
}
