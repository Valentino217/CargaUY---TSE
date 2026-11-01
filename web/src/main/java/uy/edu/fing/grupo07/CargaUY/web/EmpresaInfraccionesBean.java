package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Managed Bean para /empresa/infracciones.xhtml.
 * Permite a la empresa consultar sus infracciones, ver la evidencia técnica y presentar descargos.
 */
@Named("empresaInfraccionesBean")
@ViewScoped
public class EmpresaInfraccionesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;

    @EJB
    private VehiculoEJB vehiculoEJB;

    private List<InfraccionDTO> infracciones;
    private List<InfraccionDTO> infraccionesFiltradas;
    private InfraccionDTO seleccionada;
    private String textoDescargo;

    @PostConstruct
    public void init() {
        cargarInfracciones();
    }

    public void cargarInfracciones() {
        try {
            List<InfraccionDTO> todasNotificadas = infraccionesService.listarInfracciones(null, null).stream()
                    .filter(i -> i.estado() != EstadoInfraccion.DETECTADA)
                    .collect(Collectors.toList());
            List<Empresa> misEmpresas = vehiculoEJB.listarEmpresasParaActor(SesionActual.token());
            if (misEmpresas != null && !misEmpresas.isEmpty()) {
                Set<String> nombres = misEmpresas.stream()
                        .map(Empresa::getRazonSocial)
                        .filter(r -> r != null)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
                infracciones = todasNotificadas.stream()
                        .filter(i -> i.nombreEmpresa() != null && nombres.contains(i.nombreEmpresa().toLowerCase()))
                        .collect(Collectors.toList());
                if (infracciones.isEmpty()) {
                    infracciones = todasNotificadas;
                }
            } else {
                infracciones = todasNotificadas;
            }
        } catch (Exception e) {
            infracciones = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron cargar las infracciones: " + e.getMessage());
        }
    }

    public void seleccionarParaDescargo(InfraccionDTO inf) {
        this.seleccionada = inf;
        this.textoDescargo = inf != null && inf.textoDescargo() != null ? inf.textoDescargo() : "";
    }

    public boolean puedePresentarDescargo(InfraccionDTO inf) {
        if (inf == null) {
            return false;
        }
        return inf.estado() == EstadoInfraccion.NOTIFICADA
                || inf.estado() == EstadoInfraccion.EN_DESCARGO;
    }

    public void enviarDescargo() {
        if (seleccionada == null) {
            return;
        }
        if (textoDescargo == null || textoDescargo.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Atención", "Debe redactar el fundamento del descargo.");
            return;
        }
        try {
            seleccionada = infraccionesService.presentarDescargo(seleccionada.idCaso(), new DescargoRequestDTO(textoDescargo.trim()));
            cargarInfracciones();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Descargo Presentado",
                    "Su descargo para el expediente #" + seleccionada.idCaso() + " fue registrado exitosamente.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al presentar descargo", e.getMessage());
        }
    }

    public long getTotalActivas() {
        if (infracciones == null) return 0;
        return infracciones.stream()
                .filter(i -> i.estado() == EstadoInfraccion.NOTIFICADA)
                .count();
    }

    public long getTotalEnDescargo() {
        if (infracciones == null) return 0;
        return infracciones.stream().filter(i -> i.estado() == EstadoInfraccion.EN_DESCARGO).count();
    }

    public long getTotalConfirmadas() {
        if (infracciones == null) return 0;
        return infracciones.stream().filter(i -> i.estado() == EstadoInfraccion.CONFIRMADA).count();
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
    }

    public List<InfraccionDTO> getInfracciones() {
        return infracciones;
    }

    public List<InfraccionDTO> getInfraccionesFiltradas() {
        return infraccionesFiltradas;
    }

    public void setInfraccionesFiltradas(List<InfraccionDTO> infraccionesFiltradas) {
        this.infraccionesFiltradas = infraccionesFiltradas;
    }

    public InfraccionDTO getSeleccionada() {
        return seleccionada;
    }

    public void setSeleccionada(InfraccionDTO seleccionada) {
        this.seleccionada = seleccionada;
    }

    public String getTextoDescargo() {
        return textoDescargo;
    }

    public void setTextoDescargo(String textoDescargo) {
        this.textoDescargo = textoDescargo;
    }

    public EstadoInfraccion[] getEstadosInfraccion() {
        return EstadoInfraccion.values();
    }

    public TipoInfraccion[] getTiposInfraccion() {
        return TipoInfraccion.values();
    }
}
