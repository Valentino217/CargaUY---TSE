package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Rubro;
import uy.edu.fing.grupo07.CargaUY.domain.entity.TipoCarga;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.integration.RedVialUruguay;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJB;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

@Named("GuiaDeViajeBean")
@ViewScoped
public class GuiaDeViajeBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final List<String> LOCALIDADES_CARGA_URUGUAY =
            RedVialUruguay.listarNombresLocalidadesOficiales();

    @EJB
    private VehiculoEJB vehiculoEJB;

    @EJB
    private GuiaDeViajeEJB GuiaDeViajeEJB;

    private List<Vehiculo> vehiculos;
    private List<Empresa> empresas;
    private List<GuiaDeViaje> guias;
    private List<Usuario> choferes;
    private List<Rubro> rubrosCatalogo = new ArrayList<>();
    private List<TipoCarga> tiposCargaCatalogo = new ArrayList<>();

    private GuiaDeViaje guia;
    private String matriculaVehiculo;
    private int choferId;
    private Integer tipoCargaCodigo;
    private boolean editando;
    private int empresaId;

    public GuiaDeViajeBean() {
    }

    @PostConstruct
    public void init() {
        String token = SesionActual.token();
        cargarCatalogos();
        try {
            empresas = vehiculoEJB.listarEmpresasParaActor(token);
        } catch (Exception excepcion) {
            empresas = new ArrayList<>();
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudieron cargar las empresas: " + extraerMensaje(excepcion)
            );
        }
        try {
            guias = GuiaDeViajeEJB.listarParaActor(token);
        } catch (Exception excepcion) {
            guias = new ArrayList<>();
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo inicializar los datos de Guía de Viaje: " + extraerMensaje(excepcion)
            );
        }

        nuevo();
    }

    private void cargarCatalogos() {
        try {
            List<Rubro> r = GuiaDeViajeEJB.listarRubrosCatalogo();
            rubrosCatalogo = r != null ? r : new ArrayList<>();
        } catch (Exception e) {
            rubrosCatalogo = new ArrayList<>();
        }
        try {
            List<TipoCarga> tc = GuiaDeViajeEJB.listarTiposCargaCatalogo();
            tiposCargaCatalogo = tc != null ? tc : new ArrayList<>();
        } catch (Exception e) {
            tiposCargaCatalogo = new ArrayList<>();
        }
    }

    public List<String> getLocalidadesUruguay() {
        return LOCALIDADES_CARGA_URUGUAY;
    }

    public List<String> getRubrosDisponibles() {
        cargarCatalogos();
        List<String> nombres = new ArrayList<>();
        for (Rubro r : rubrosCatalogo) {
            if (r.getNombre() != null && !r.getNombre().isBlank()) {
                nombres.add(r.getNombre());
            }
        }
        return nombres;
    }

    public List<TipoCarga> getTiposCargaDisponibles() {
        cargarCatalogos();
        return tiposCargaCatalogo;
    }

    public String obtenerDescripcionTiposCarga(GuiaDeViaje g) {
        if (g == null || g.getTiposCarga() == null || g.getTiposCarga().isEmpty()) {
            return "Sin especificar";
        }
        StringBuilder sb = new StringBuilder();
        for (TipoCarga tc : g.getTiposCarga()) {
            if (tc != null && tc.getDesc() != null && !tc.getDesc().isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(tc.getDesc());
            }
        }
        return sb.length() > 0 ? sb.toString() : "Sin especificar";
    }

    private List<Vehiculo> obtenerVehiculosHabilitados(int empId) {
        if (empId <= 0) {
            return Collections.emptyList();
        }
        try {
            List<Vehiculo> todos = vehiculoEJB.listarVehiculosPorEmpresa(SesionActual.token(), empId);
            return todos != null ? todos : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void nuevo() {
        cargarCatalogos();
        guia = new GuiaDeViaje();
        guia.setFecha(java.time.LocalDate.now());
        guia.setOrigen(LOCALIDADES_CARGA_URUGUAY.get(2)); // Nueva Palmira por defecto
        guia.setDestino(LOCALIDADES_CARGA_URUGUAY.get(0)); // Puerto de Montevideo por defecto
        guia.actualizarRutaPlanificada();
        if (!rubrosCatalogo.isEmpty()) {
            guia.setRubroCliente(rubrosCatalogo.get(0).getNombre());
        } else {
            guia.setRubroCliente(null);
        }
        if (!tiposCargaCatalogo.isEmpty()) {
            this.tipoCargaCodigo = tiposCargaCatalogo.get(0).getCodigo();
        } else {
            this.tipoCargaCodigo = null;
        }
        guia.setVolumenCarga(28000.0f);
        editando = false;

        if (empresas != null && !empresas.isEmpty()) {
            Empresa empresaPorDefecto = empresas.get(0);
            this.empresaId = empresaPorDefecto.getNroEmpresa();
            this.vehiculos = obtenerVehiculosHabilitados(empresaId);
            this.choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(SesionActual.token(), empresaId);

            if (vehiculos != null && !vehiculos.isEmpty()) {
                this.matriculaVehiculo = vehiculos.get(0).getMatricula();
            } else {
                this.matriculaVehiculo = null;
            }

            if (choferes != null && !choferes.isEmpty()) {
                this.choferId = choferes.get(0).getId();
            } else {
                this.choferId = 0;
            }
        } else {
            this.empresaId = 0;
            this.choferId = 0;
            this.matriculaVehiculo = null;
            this.vehiculos = new ArrayList<>();
            this.choferes = new ArrayList<>();
        }
    }

    public void guardar() {
        try {
            if (guia.getOrigen() != null && guia.getOrigen().equals(guia.getDestino())) {
                throw new IllegalArgumentException("La localidad de origen y la localidad de destino deben ser distintas.");
            }

            Empresa empresaSeleccionada = null;
            for (Empresa empresa : empresas) {
                if (empresa.getNroEmpresa() == empresaId) {
                    empresaSeleccionada = empresa;
                    break;
                }
            }

            if (empresaSeleccionada == null) {
                throw new IllegalArgumentException("Debe seleccionar una empresa válida.");
            }

            guia.setEmpresa(empresaSeleccionada);

            Vehiculo vehiculoSeleccionado = null;
            for (Vehiculo vehiculo : vehiculos) {
                if (vehiculo.getMatricula().equals(matriculaVehiculo)) {
                    vehiculoSeleccionado = vehiculo;
                    break;
                }
            }

            if (vehiculoSeleccionado == null) {
                throw new IllegalArgumentException("Debe seleccionar un vehículo válido.");
            }

            boolean apto = vehiculoEJB.estaAptoParaOperar(vehiculoSeleccionado.getMatricula(), guia.getFecha());

            guia.setVehiculo(vehiculoSeleccionado);

            Usuario choferSeleccionado = null;
            for (Usuario chofer : choferes) {
                if (chofer.getId() == choferId) {
                    choferSeleccionado = chofer;
                    break;
                }
            }

            if (choferSeleccionado == null) {
                throw new IllegalArgumentException("Debe seleccionar un chofer válido.");
            }

            guia.setChofer(choferSeleccionado);
            List<TipoCarga> tiposSeleccionados = new ArrayList<>();
            if (tipoCargaCodigo != null) {
                for (TipoCarga tc : tiposCargaCatalogo) {
                    if (tc.getCodigo() == tipoCargaCodigo) {
                        tiposSeleccionados.add(tc);
                        break;
                    }
                }
            }
            guia.setTiposCarga(tiposSeleccionados);
            guia.actualizarRutaPlanificada();

            if (editando) {
                GuiaDeViajeEJB.modificar(SesionActual.token(), guia);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Guía de viaje modificada correctamente.");
            } else {
                GuiaDeViajeEJB.crear(SesionActual.token(), guia);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Guía de viaje registrada correctamente.");
            }

            if (!apto) {
                mostrarMensaje(
                        FacesMessage.SEVERITY_WARN,
                        "Advertencia de Habilitación",
                        "El vehículo " + vehiculoSeleccionado.getMatricula()
                                + " presenta permisos vencidos o inhabilitación. Si inicia el viaje sin regularizar, el motor de fiscalización generará un caso SIN_HABILITACION."
                );
            }

            guias = GuiaDeViajeEJB.listarParaActor(SesionActual.token());
            nuevo();

        } catch (Exception excepcion) {
            FacesContext.getCurrentInstance().validationFailed();
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo guardar la guía de viaje: " + extraerMensaje(excepcion)
            );
        }
    }

    private String extraerMensaje(Throwable t) {
        Throwable curr = t;
        while (curr.getCause() != null && curr.getCause() != curr) {
            curr = curr.getCause();
        }
        return curr.getMessage() != null ? curr.getMessage() : t.getMessage();
    }

    private void mostrarMensaje(FacesMessage.Severity severity, String resumen, String detalle) {
        FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(severity, resumen, detalle)
        );
    }

    public void editar(GuiaDeViaje guiaSeleccionada) {
        cargarCatalogos();
        this.guia = guiaSeleccionada;
        this.editando = true;

        if (guiaSeleccionada.getEmpresa() != null) {
            this.empresaId = guiaSeleccionada.getEmpresa().getNroEmpresa();
        }

        if (guiaSeleccionada.getVehiculo() != null) {
            this.matriculaVehiculo = guiaSeleccionada.getVehiculo().getMatricula();
        }

        if (guiaSeleccionada.getChofer() != null) {
            this.choferId = guiaSeleccionada.getChofer().getId();
        }

        if (guiaSeleccionada.getTiposCarga() != null && !guiaSeleccionada.getTiposCarga().isEmpty()) {
            this.tipoCargaCodigo = guiaSeleccionada.getTiposCarga().get(0).getCodigo();
        } else if (!tiposCargaCatalogo.isEmpty()) {
            this.tipoCargaCodigo = tiposCargaCatalogo.get(0).getCodigo();
        } else {
            this.tipoCargaCodigo = null;
        }

        this.vehiculos = obtenerVehiculosHabilitados(empresaId);
        this.choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(SesionActual.token(), empresaId);
    }

    public void eliminar(GuiaDeViaje guiaAEliminar) {
        try {
            GuiaDeViajeEJB.eliminar(SesionActual.token(), guiaAEliminar);
            guias = GuiaDeViajeEJB.listarParaActor(SesionActual.token());

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Éxito",
                    "Guía de viaje eliminada correctamente."
            );

            nuevo();

        } catch (Exception excepcion) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo eliminar la guía de viaje: " + extraerMensaje(excepcion)
            );
        }
    }

    public void avanzarEstado(GuiaDeViaje g) {
        if (g == null) return;
        try {
            GuiaDeViajeEJB.avanzarEstadoAdmin(SesionActual.token(), g.getId());
            guias = GuiaDeViajeEJB.listarParaActor(SesionActual.token());
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Estado Actualizado",
                    "La guía #" + g.getId() + " avanzó de estado exitosamente."
            );
        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "No se pudo avanzar el estado",
                    extraerMensaje(e)
            );
        }
    }

    public void seleccionarEmpresa() {
        this.vehiculos = new ArrayList<>();
        this.choferes = new ArrayList<>();
        this.matriculaVehiculo = null;
        this.choferId = 0;

        if (this.empresaId == 0) {
            return;
        }

        try {
            this.vehiculos = obtenerVehiculosHabilitados(empresaId);
            this.choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(SesionActual.token(), empresaId);

            if (vehiculos != null && !vehiculos.isEmpty()) {
                this.matriculaVehiculo = vehiculos.get(0).getMatricula();
            }

            if (choferes != null && !choferes.isEmpty()) {
                this.choferId = choferes.get(0).getId();
            }

        } catch (Exception excepcion) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudieron cargar los vehículos y choferes: " + extraerMensaje(excepcion)
            );
        }
    }

    public GuiaDeViaje getGuia() {
        return guia;
    }

    public void setGuia(GuiaDeViaje guia) {
        this.guia = guia;
    }

    public String getMatriculaVehiculo() {
        return matriculaVehiculo;
    }

    public void setMatriculaVehiculo(String matriculaVehiculo) {
        this.matriculaVehiculo = matriculaVehiculo;
    }

    public int getChoferId() {
        return choferId;
    }

    public void setChoferId(int choferId) {
        this.choferId = choferId;
    }

    public int getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(int empresaId) {
        this.empresaId = empresaId;
    }

    public List<Vehiculo> getVehiculos() {
        return vehiculos;
    }

    public List<Empresa> getEmpresas() {
        return empresas;
    }

    public List<Usuario> getChoferes() {
        return choferes;
    }

    public List<GuiaDeViaje> getGuiaDeViajes() {
        return guias;
    }

    public GuiaDeViaje getGuiaDeViaje() {
        return guia;
    }

    public void setGuiaDeViaje(GuiaDeViaje guia) {
        this.guia = guia;
    }

    public boolean isEditando() {
        return editando;
    }

    public Integer getTipoCargaCodigo() {
        return tipoCargaCodigo;
    }

    public void setTipoCargaCodigo(Integer tipoCargaCodigo) {
        this.tipoCargaCodigo = tipoCargaCodigo;
    }
}
