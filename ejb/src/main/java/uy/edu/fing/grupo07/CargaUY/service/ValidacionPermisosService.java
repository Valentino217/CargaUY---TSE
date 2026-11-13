package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.dto.ValidacionEmpresaDTO;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;

@Stateless
@LocalBean
public class ValidacionPermisosService {
    @EJB private ControlAcceso control;

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public ValidacionEmpresaDTO consultar(String token, int numero) {
        control.exigir(token);
        if (numero <= 0) throw new ErrorAcceso(400, "Número de empresa inválido");

        Empresa empresa = em.find(Empresa.class, numero);
        if (empresa == null) throw new ErrorAcceso(404, "Empresa inexistente");

        LocalDate hoy = LocalDate.now();
        List<Vehiculo> vehiculos = em.createQuery(
                "select v from Vehiculo v where v.empresa.nroEmpresa=:numero order by v.matricula",
                Vehiculo.class).setParameter("numero", numero).getResultList();

        var unidades = vehiculos.stream().map(v -> resumir(v, hoy)).toList();
        return new ValidacionEmpresaDTO(empresa.getNroEmpresa(), empresa.getRazonSocial(),
                empresa.isActiva(), hoy, unidades);
    }

    private ValidacionEmpresaDTO.Unidad resumir(Vehiculo v, LocalDate hoy) {
        List<LocalDate> pncs = v.getPncs() == null ? List.of()
                : v.getPncs().stream().map(p -> p.getFechaVencimiento()).toList();
        List<LocalDate> itvs = v.getItv() == null ? List.of()
                : v.getItv().stream().map(i -> i.getFechaVencimiento()).toList();

        LocalDate pnc = ultimo(pncs), itv = ultimo(itvs);
        String epnc = estado(pncs, pnc, hoy), eitv = estado(itvs, itv, hoy);
        String resultado;
        if (!v.isEstadoHabilitacion()) resultado = "INHABILITADA";
        else if ("VENCIDO".equals(epnc) || "VENCIDO".equals(eitv)) resultado = "NO_VIGENTE";
        else if (!"VIGENTE".equals(epnc) || !"VIGENTE".equals(eitv)) resultado = "INFORMACION_INCOMPLETA";
        else resultado = "VIGENTE";

        return new ValidacionEmpresaDTO.Unidad(v.getMatricula(), v.isEstadoHabilitacion(),
                pnc, epnc, itv, eitv, resultado);
    }

    private LocalDate ultimo(List<LocalDate> fechas) {
        return fechas.stream().filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    private String estado(List<LocalDate> historial, LocalDate ultima, LocalDate hoy) {
        if (ultima != null && !ultima.isBefore(hoy)) return "VIGENTE";
        if (historial.isEmpty() || historial.stream().anyMatch(Objects::isNull))
            return "SIN_INFORMACION";
        return "VENCIDO";
    }
}