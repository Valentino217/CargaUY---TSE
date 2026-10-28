package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.dto.EmpresaResumen;
import uy.edu.fing.grupo07.CargaUY.integration.ResultadoPdi;
import uy.edu.fing.grupo07.CargaUY.security.IdentidadesService;
import uy.edu.fing.grupo07.CargaUY.service.ConsultaCiudadanoService;
import uy.edu.fing.grupo07.CargaUY.service.GestionPerfilesService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Managed Bean para /funcionario/empresas.xhtml.
 * Gestión integral de empresas, consulta PDI y vinculación de responsables/choferes.
 */
@Named("funcionarioEmpresasBean")
@ViewScoped
public class FuncionarioEmpresasBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionPerfilesService perfilesService;

    @EJB
    private IdentidadesService identidadesService;

    @EJB
    private ConsultaCiudadanoService pdiService;

    private List<EmpresaResumen> empresas;
    private List<EmpresaResumen> empresasFiltradas;

    private boolean modoEdicion;
    private int numeroForm;
    private String razonSocialForm;
    private String nombrePublicoForm;
    private String direccionForm;
    private long versionForm;

    private EmpresaResumen empresaSeleccionada;
    private List<IdentidadesService.VinculoVista> vinculosEmpresa;
    private int usuarioIdVinculo;
    private String funcionVinculo = "RESPONSABLE";

    private Integer ciConsultaPdi;
    private ResultadoPdi resultadoPdi;

    @PostConstruct
    public void init() {
        cargarEmpresas();
    }

    public void cargarEmpresas() {
        try {
            empresas = perfilesService.listarEmpresas(SesionActual.token(), 0, 100);
        } catch (Exception e) {
            empresas = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron listar las empresas: " + e.getMessage());
        }
    }

    public void prepararNuevaEmpresa() {
        this.modoEdicion = false;
        this.numeroForm = 0;
        this.razonSocialForm = "";
        this.nombrePublicoForm = "";
        this.direccionForm = "";
        this.versionForm = 0;
    }

    public void prepararEdicionEmpresa(EmpresaResumen emp) {
        if (emp == null) return;
        this.modoEdicion = true;
        this.numeroForm = emp.numero();
        this.razonSocialForm = emp.razonSocial();
        this.nombrePublicoForm = emp.nombrePublico();
        this.direccionForm = emp.direccion();
        this.versionForm = emp.version();
    }

    public void guardarEmpresa() {
        try {
            String token = SesionActual.token();
            if (modoEdicion) {
                perfilesService.modificarEmpresa(token, numeroForm, versionForm, razonSocialForm, nombrePublicoForm, direccionForm);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Empresa Actualizada", "Se guardaron los cambios de la empresa #" + numeroForm);
            } else {
                perfilesService.crearEmpresa(token, numeroForm, razonSocialForm, nombrePublicoForm, direccionForm);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Empresa Registrada", "Se dio de alta la empresa #" + numeroForm);
            }
            cargarEmpresas();
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al guardar empresa", e.getMessage());
        }
    }

    public void cambiarEstado(EmpresaResumen emp, boolean activar) {
        if (emp == null) return;
        try {
            perfilesService.cambiarEstadoEmpresa(SesionActual.token(), emp.numero(), activar);
            cargarEmpresas();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Estado Actualizado",
                    "La empresa #" + emp.numero() + " fue " + (activar ? "reactivada" : "dada de baja") + ".");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
        }
    }

    public void abrirVinculos(EmpresaResumen emp) {
        this.empresaSeleccionada = emp;
        this.usuarioIdVinculo = 0;
        this.funcionVinculo = "RESPONSABLE";
        cargarVinculos();
    }

    public void cargarVinculos() {
        if (empresaSeleccionada == null) {
            vinculosEmpresa = new ArrayList<>();
            return;
        }
        try {
            vinculosEmpresa = identidadesService.listar(SesionActual.token(), empresaSeleccionada.numero());
        } catch (Exception e) {
            vinculosEmpresa = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Aviso", e.getMessage());
        }
    }

    public void guardarVinculo() {
        if (empresaSeleccionada == null || usuarioIdVinculo <= 0) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Indique un ID de usuario válido.");
            return;
        }
        boolean esResponsable = "RESPONSABLE".equals(funcionVinculo);
        boolean esChofer = "CHOFER".equals(funcionVinculo);
        try {
            identidadesService.vinculo(SesionActual.token(), empresaSeleccionada.numero(), usuarioIdVinculo, esResponsable, esChofer);
            cargarVinculos();
            cargarEmpresas();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Vínculo Guardado",
                    "Se actualizó el vínculo del usuario #" + usuarioIdVinculo + " como "
                            + (esResponsable ? "Responsable" : (esChofer ? "Chofer" : "Desvinculado"))
                            + " en la empresa #" + empresaSeleccionada.numero());
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al vincular", e.getMessage());
        }
    }

    public void consultarEnPdi() {
        if (ciConsultaPdi == null || ciConsultaPdi <= 0) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Ingrese una Cédula de Identidad válida.");
            return;
        }
        try {
            resultadoPdi = pdiService.consultar(SesionActual.token(), ciConsultaPdi, UUID.randomUUID().toString());
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "PDI Consultado", "Estado respuesta PDI: " + resultadoPdi.estado());
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error PDI", e.getMessage());
        }
    }

    public long getTotalActivas() {
        if (empresas == null) return 0;
        return empresas.stream().filter(EmpresaResumen::activa).count();
    }

    public long getTotalInactivas() {
        if (empresas == null) return 0;
        return empresas.stream().filter(e -> !e.activa()).count();
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
    }

    public List<EmpresaResumen> getEmpresas() {
        return empresas;
    }

    public List<EmpresaResumen> getEmpresasFiltradas() {
        return empresasFiltradas;
    }

    public void setEmpresasFiltradas(List<EmpresaResumen> empresasFiltradas) {
        this.empresasFiltradas = empresasFiltradas;
    }

    public boolean isModoEdicion() {
        return modoEdicion;
    }

    public int getNumeroForm() {
        return numeroForm;
    }

    public void setNumeroForm(int numeroForm) {
        this.numeroForm = numeroForm;
    }

    public String getRazonSocialForm() {
        return razonSocialForm;
    }

    public void setRazonSocialForm(String razonSocialForm) {
        this.razonSocialForm = razonSocialForm;
    }

    public String getNombrePublicoForm() {
        return nombrePublicoForm;
    }

    public void setNombrePublicoForm(String nombrePublicoForm) {
        this.nombrePublicoForm = nombrePublicoForm;
    }

    public String getDireccionForm() {
        return direccionForm;
    }

    public void setDireccionForm(String direccionForm) {
        this.direccionForm = direccionForm;
    }

    public EmpresaResumen getEmpresaSeleccionada() {
        return empresaSeleccionada;
    }

    public List<IdentidadesService.VinculoVista> getVinculosEmpresa() {
        return vinculosEmpresa;
    }

    public int getUsuarioIdVinculo() {
        return usuarioIdVinculo;
    }

    public void setUsuarioIdVinculo(int usuarioIdVinculo) {
        this.usuarioIdVinculo = usuarioIdVinculo;
    }

    public String getFuncionVinculo() {
        return funcionVinculo;
    }

    public void setFuncionVinculo(String funcionVinculo) {
        this.funcionVinculo = funcionVinculo;
    }

    public Integer getCiConsultaPdi() {
        return ciConsultaPdi;
    }

    public void setCiConsultaPdi(Integer ciConsultaPdi) {
        this.ciConsultaPdi = ciConsultaPdi;
    }

    public ResultadoPdi getResultadoPdi() {
        return resultadoPdi;
    }
}
