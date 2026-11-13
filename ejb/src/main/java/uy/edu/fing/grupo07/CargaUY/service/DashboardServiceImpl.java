package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Rubro;
import uy.edu.fing.grupo07.CargaUY.domain.entity.TipoCarga;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Implementación del servicio de métricas consolidadas del Dashboard (CU-C4 / Autoridad - Sección 3.6.2).
 */
@Stateless
public class DashboardServiceImpl implements DashboardServiceLocal {

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    @EJB
    private ControlAcceso control;

    public DashboardServiceImpl() {
    }

    public DashboardServiceImpl(EntityManager em) {
        this.em = em;
    }

    private void verificarControl() {
        if (control == null) {
            throw new IllegalStateException("ControlAcceso no disponible");
        }
    }

    @Override
    public DashboardResumenDTO obtenerResumenPublico() {
        Map<TipoEstado, Long> guiasPorEstado = inicializarMapaGuias();
        cargarMetricasGuias(guiasPorEstado);
        long totalPesadas = consultarConteo("SELECT COUNT(p) FROM PesadaBalanza p");
        long totalEmpresasHabilitadas = consultarConteo("SELECT COUNT(e) FROM Empresa e WHERE e.activa = true");
        long totalVehiculosHabilitados = consultarConteo("SELECT COUNT(v) FROM Vehiculo v WHERE v.estadoHabilitacion = true");
        return new DashboardResumenDTO(
                guiasPorEstado,
                Map.of(),
                Map.of(),
                totalPesadas,
                totalEmpresasHabilitadas,
                totalVehiculosHabilitados,
                Map.of(),
                Map.of(),
                0.0
        );
    }

    @Override
    public DashboardResumenDTO obtenerResumenPortal(String token) {
        verificarControl();
        control.exigir(token);
        return obtenerResumen();
    }

    @Override
    public DashboardResumenDTO obtenerResumen(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.AUTORIDAD, TipoUsuario.FUNCIONARIO);
        return obtenerResumen();
    }

    @Override
    public List<String> listarRubrosDisponibles(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.AUTORIDAD, TipoUsuario.FUNCIONARIO);
        return listarRubrosDisponibles();
    }

    @Override
    public List<String> listarTiposCargaDisponibles(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.AUTORIDAD, TipoUsuario.FUNCIONARIO);
        return listarTiposCargaDisponibles();
    }

    @Override
    public List<String> listarUbicacionesDisponibles(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.AUTORIDAD, TipoUsuario.FUNCIONARIO);
        return listarUbicacionesDisponibles();
    }

    @Override
    public DashboardResumenDTO obtenerResumenFiltrado(
            String token,
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.AUTORIDAD, TipoUsuario.FUNCIONARIO);
        return obtenerResumenFiltrado(rubro, tipoCarga, ubicacion, fechaDesde, fechaHasta);
    }

    @Override
    public List<String> listarRubrosDisponibles() {
        List<String> rubros = new ArrayList<>();
        try {
            List<String> deRubros = em.createQuery("SELECT DISTINCT r.nombre FROM Rubro r ORDER BY r.nombre", String.class).getResultList();
            if (deRubros != null) {
                for (String r : deRubros) {
                    if (r != null && !r.isBlank() && !rubros.contains(r.trim())) {
                        rubros.add(r.trim());
                    }
                }
            }
            List<String> deGuias = em.createQuery("SELECT DISTINCT g.rubroCliente FROM GuiaDeViaje g WHERE g.rubroCliente IS NOT NULL", String.class).getResultList();
            if (deGuias != null) {
                for (String r : deGuias) {
                    if (r != null && !r.isBlank() && !r.contains("?")) {
                        String normalizado = r.trim();
                        if (!rubros.contains(normalizado)) {
                            rubros.add(normalizado);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return rubros;
    }

    @Override
    public List<String> listarTiposCargaDisponibles() {
        try {
            return em.createQuery("SELECT DISTINCT t.desc FROM TipoCarga t ORDER BY t.desc", String.class).getResultList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public List<String> listarUbicacionesDisponibles() {
        try {
            TreeSet<String> ubicaciones = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            agregarUbicaciones(ubicaciones, em.createQuery(
                    "SELECT DISTINCT g.origen FROM GuiaDeViaje g WHERE g.origen IS NOT NULL", String.class).getResultList());
            agregarUbicaciones(ubicaciones, em.createQuery(
                    "SELECT DISTINCT g.destino FROM GuiaDeViaje g WHERE g.destino IS NOT NULL", String.class).getResultList());
            agregarUbicaciones(ubicaciones, em.createQuery(
                    "SELECT DISTINCT g.corredorVial FROM GuiaDeViaje g WHERE g.corredorVial IS NOT NULL", String.class).getResultList());
            return new ArrayList<>(ubicaciones);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void agregarUbicaciones(Set<String> ubicaciones, List<String> valores) {
        if (valores == null) {
            return;
        }
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                ubicaciones.add(valor.trim());
            }
        }
    }

    @Override
    public DashboardResumenDTO obtenerResumen() {
        Map<TipoEstado, Long> guiasPorEstado = inicializarMapaGuias();
        Map<EstadoInfraccion, Long> infraccionesPorEstado = inicializarMapaInfraccionesPorEstado();
        Map<TipoInfraccion, Long> infraccionesPorTipo = inicializarMapaInfraccionesPorTipo();

        cargarMetricasGuias(guiasPorEstado);
        cargarMetricasInfraccionesPorEstado(infraccionesPorEstado);
        cargarMetricasInfraccionesPorTipo(infraccionesPorTipo);

        long totalPesadas = consultarConteo("SELECT COUNT(p) FROM PesadaBalanza p");
        long totalEmpresasHabilitadas = consultarConteo("SELECT COUNT(e) FROM Empresa e WHERE e.activa = true");
        long totalVehiculosHabilitados = consultarConteo("SELECT COUNT(v) FROM Vehiculo v WHERE v.estadoHabilitacion = true");

        Map<String, Long> viajesPorPeriodo = new TreeMap<>();
        Map<String, Double> volumenPorRubro = new LinkedHashMap<>();
        cargarViajesYVolumenDesdeGuias(null, null, null, null, null, viajesPorPeriodo, volumenPorRubro, null, null, null);

        double tasaConfirmadasSobreDetectadas = calcularTasaConfirmadas(infraccionesPorEstado);

        return new DashboardResumenDTO(
                guiasPorEstado,
                infraccionesPorEstado,
                infraccionesPorTipo,
                totalPesadas,
                totalEmpresasHabilitadas,
                totalVehiculosHabilitados,
                viajesPorPeriodo,
                volumenPorRubro,
                tasaConfirmadasSobreDetectadas
        );
    }

    @Override
    public DashboardResumenDTO obtenerResumenFiltrado(
            String rubro,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        return obtenerResumenFiltrado(rubro, null, ubicacion, fechaDesde, fechaHasta);
    }

    @Override
    public DashboardResumenDTO obtenerResumenFiltrado(
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        boolean sinFiltros = (rubro == null || rubro.isBlank())
                && (tipoCarga == null || tipoCarga.isBlank())
                && (ubicacion == null || ubicacion.isBlank())
                && fechaDesde == null
                && fechaHasta == null;
        if (sinFiltros) {
            return obtenerResumen();
        }

        Map<TipoEstado, Long> guiasPorEstado = inicializarMapaGuias();
        Map<EstadoInfraccion, Long> infraccionesPorEstado = inicializarMapaInfraccionesPorEstado();
        Map<TipoInfraccion, Long> infraccionesPorTipo = inicializarMapaInfraccionesPorTipo();
        Map<String, Long> viajesPorPeriodo = new TreeMap<>();
        Map<String, Double> volumenPorRubro = new LinkedHashMap<>();
        Set<Integer> empresasActivasFiltradas = new HashSet<>();
        Set<String> vehiculosHabilitadosFiltrados = new HashSet<>();

        cargarViajesYVolumenDesdeGuias(
                rubro,
                tipoCarga,
                ubicacion,
                fechaDesde,
                fechaHasta,
                viajesPorPeriodo,
                volumenPorRubro,
                guiasPorEstado,
                empresasActivasFiltradas,
                vehiculosHabilitadosFiltrados
        );

        cargarInfraccionesFiltradas(
                rubro,
                tipoCarga,
                ubicacion,
                fechaDesde,
                fechaHasta,
                infraccionesPorEstado,
                infraccionesPorTipo,
                empresasActivasFiltradas,
                vehiculosHabilitadosFiltrados
        );

        long totalPesadas = contarPesadasFiltradas(
                rubro,
                tipoCarga,
                ubicacion,
                fechaDesde,
                fechaHasta,
                empresasActivasFiltradas,
                vehiculosHabilitadosFiltrados
        );
        agregarCatalogoEmpresasYVehiculosFiltrados(
                rubro,
                tipoCarga,
                ubicacion,
                fechaDesde,
                fechaHasta,
                empresasActivasFiltradas,
                vehiculosHabilitadosFiltrados
        );
        long totalEmpresasHabilitadas = empresasActivasFiltradas.size();
        long totalVehiculosHabilitados = vehiculosHabilitadosFiltrados.size();
        double tasaConfirmadasSobreDetectadas = calcularTasaConfirmadas(infraccionesPorEstado);

        return new DashboardResumenDTO(
                guiasPorEstado,
                infraccionesPorEstado,
                infraccionesPorTipo,
                totalPesadas,
                totalEmpresasHabilitadas,
                totalVehiculosHabilitados,
                viajesPorPeriodo,
                volumenPorRubro,
                tasaConfirmadasSobreDetectadas
        );
    }

    private long consultarConteo(String jpql) {
        TypedQuery<Long> query = em.createQuery(jpql, Long.class);
        if (query == null) {
            return 0L;
        }
        Long resultado = query.getSingleResult();
        return resultado != null ? resultado : 0L;
    }

    private double calcularTasaConfirmadas(Map<EstadoInfraccion, Long> infraccionesPorEstado) {
        long totalInfracciones = 0L;
        for (Long cantidad : infraccionesPorEstado.values()) {
            if (cantidad != null) {
                totalInfracciones += cantidad;
            }
        }
        if (totalInfracciones <= 0L) {
            return 0.0;
        }
        long confirmadas = infraccionesPorEstado.getOrDefault(EstadoInfraccion.CONFIRMADA, 0L);
        return Math.round((confirmadas * 10000.0) / totalInfracciones) / 100.0;
    }

    private void cargarViajesYVolumenDesdeGuias(
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Map<String, Long> viajesPorPeriodo,
            Map<String, Double> volumenPorRubro,
            Map<TipoEstado, Long> guiasPorEstadoFiltrado,
            Set<Integer> empresasActivasFiltradas,
            Set<String> vehiculosHabilitadosFiltrados
    ) {
        TypedQuery<GuiaDeViaje> queryGuias = em.createQuery(
                "SELECT g FROM GuiaDeViaje g ORDER BY g.fecha ASC",
                GuiaDeViaje.class
        );
        if (queryGuias == null) {
            return;
        }
        List<GuiaDeViaje> guias = queryGuias.getResultList();
        if (guias == null) {
            return;
        }
        for (GuiaDeViaje g : guias) {
            if (g == null || !cumpleFiltroGuia(g, rubro, tipoCarga, ubicacion, fechaDesde, fechaHasta)) {
                continue;
            }
            if (guiasPorEstadoFiltrado != null && g.getEstado() != null) {
                guiasPorEstadoFiltrado.merge(g.getEstado(), 1L, Long::sum);
            }
            if (viajesPorPeriodo != null && g.getFecha() != null) {
                String periodo = YearMonth.from(g.getFecha()).toString();
                viajesPorPeriodo.merge(periodo, 1L, Long::sum);
            }
            if (volumenPorRubro != null) {
                String rubroGuia = (g.getRubroCliente() != null && !g.getRubroCliente().isBlank())
                        ? g.getRubroCliente().trim()
                        : "Sin clasificar";
                volumenPorRubro.merge(rubroGuia, (double) g.getVolumenCarga(), Double::sum);
            }
            registrarEmpresaYVehiculoActivos(
                    g.getEmpresa(),
                    g.getVehiculo(),
                    empresasActivasFiltradas,
                    vehiculosHabilitadosFiltrados
            );
        }
    }

    private void cargarInfraccionesFiltradas(
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Map<EstadoInfraccion, Long> infraccionesPorEstado,
            Map<TipoInfraccion, Long> infraccionesPorTipo,
            Set<Integer> empresasActivasFiltradas,
            Set<String> vehiculosHabilitadosFiltrados
    ) {
        TypedQuery<Infraccion> queryInf = em.createQuery(
                "SELECT i FROM Infraccion i",
                Infraccion.class
        );
        if (queryInf == null) {
            return;
        }
        List<Infraccion> infracciones = queryInf.getResultList();
        if (infracciones == null) {
            return;
        }
        boolean filtraGuia = (rubro != null && !rubro.isBlank())
                || (tipoCarga != null && !tipoCarga.isBlank())
                || (ubicacion != null && !ubicacion.isBlank());
        for (Infraccion i : infracciones) {
            if (i == null) {
                continue;
            }
            if (fechaDesde != null && (i.getFecha() == null || i.getFecha().isBefore(fechaDesde))) {
                continue;
            }
            if (fechaHasta != null && (i.getFecha() == null || i.getFecha().isAfter(fechaHasta))) {
                continue;
            }
            GuiaDeViaje g = i.getGuia();
            if (filtraGuia) {
                if (g != null) {
                    if (!cumpleFiltroGuia(g, rubro, tipoCarga, ubicacion, null, null)) {
                        continue;
                    }
                } else if (!cumpleFiltroSinGuia(
                        i.resolverVehiculo(),
                        i.resolverEmpresa(),
                        rubro,
                        tipoCarga,
                        ubicacion
                )) {
                    continue;
                }
            }
            if (i.getEstado() != null) {
                infraccionesPorEstado.merge(i.getEstado(), 1L, Long::sum);
            }
            if (i.getTipo() != null) {
                infraccionesPorTipo.merge(i.getTipo(), 1L, Long::sum);
            }
            registrarEmpresaYVehiculoActivos(
                    i.resolverEmpresa(),
                    i.resolverVehiculo(),
                    empresasActivasFiltradas,
                    vehiculosHabilitadosFiltrados
            );
        }
    }

    private long contarPesadasFiltradas(
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Set<Integer> empresasActivasFiltradas,
            Set<String> vehiculosHabilitadosFiltrados
    ) {
        TypedQuery<PesadaBalanza> queryPesadas = em.createQuery(
                "SELECT p FROM PesadaBalanza p",
                PesadaBalanza.class
        );
        if (queryPesadas == null) {
            return consultarConteo("SELECT COUNT(p) FROM PesadaBalanza p");
        }
        List<PesadaBalanza> pesadas = queryPesadas.getResultList();
        if (pesadas == null) {
            return 0L;
        }
        long conteo = 0L;
        for (PesadaBalanza p : pesadas) {
            if (p == null) {
                continue;
            }
            GuiaDeViaje g = p.getGuia();
            if (g != null && cumpleFiltroGuia(g, rubro, tipoCarga, ubicacion, fechaDesde, fechaHasta)) {
                conteo++;
                registrarEmpresaYVehiculoActivos(
                        g.getEmpresa(),
                        g.getVehiculo(),
                        empresasActivasFiltradas,
                        vehiculosHabilitadosFiltrados
                );
            } else if (g == null) {
                LocalDate fechaPesada = p.getFecha();
                if (fechaDesde != null && (fechaPesada == null || fechaPesada.isBefore(fechaDesde))) {
                    continue;
                }
                if (fechaHasta != null && (fechaPesada == null || fechaPesada.isAfter(fechaHasta))) {
                    continue;
                }
                Vehiculo vehiculo = p.getVehiculo();
                Empresa empresa = vehiculo != null ? vehiculo.getEmpresa() : null;
                if (!cumpleFiltroSinGuia(vehiculo, empresa, rubro, tipoCarga, ubicacion)) {
                    continue;
                }
                conteo++;
                registrarEmpresaYVehiculoActivos(
                        empresa,
                        vehiculo,
                        empresasActivasFiltradas,
                        vehiculosHabilitadosFiltrados
                );
            }
        }
        return conteo;
    }

    private void agregarCatalogoEmpresasYVehiculosFiltrados(
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Set<Integer> empresasActivasFiltradas,
            Set<String> vehiculosHabilitadosFiltrados
    ) {
        if (fechaDesde != null || fechaHasta != null || (tipoCarga != null && !tipoCarga.isBlank())) {
            return;
        }
        TypedQuery<Empresa> queryEmp = em.createQuery("SELECT e FROM Empresa e WHERE e.activa = true", Empresa.class);
        if (queryEmp != null) {
            List<Empresa> empresas = queryEmp.getResultList();
            if (empresas != null) {
                for (Empresa e : empresas) {
                    if (e != null && cumpleFiltroSinGuia(null, e, rubro, null, ubicacion)) {
                        empresasActivasFiltradas.add(e.getNroEmpresa());
                    }
                }
            }
        }
        TypedQuery<Vehiculo> queryVeh = em.createQuery("SELECT v FROM Vehiculo v WHERE v.estadoHabilitacion = true", Vehiculo.class);
        if (queryVeh != null) {
            List<Vehiculo> vehiculos = queryVeh.getResultList();
            if (vehiculos != null) {
                for (Vehiculo v : vehiculos) {
                    Empresa emp = v != null ? v.getEmpresa() : null;
                    if (v != null && cumpleFiltroSinGuia(v, emp, rubro, null, ubicacion)) {
                        if (v.getMatricula() != null) {
                            vehiculosHabilitadosFiltrados.add(v.getMatricula());
                        }
                    }
                }
            }
        }
    }

    private boolean cumpleFiltroSinGuia(
            Vehiculo vehiculo,
            Empresa empresa,
            String rubro,
            String tipoCarga,
            String ubicacion
    ) {
        if (tipoCarga != null && !tipoCarga.isBlank()) {
            return false;
        }
        Empresa emp = empresa != null ? empresa : (vehiculo != null ? vehiculo.getEmpresa() : null);
        if (rubro != null && !rubro.isBlank()) {
            if (emp == null || !empresaTieneRubro(emp, rubro.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (ubicacion != null && !ubicacion.isBlank()) {
            String uFiltro = ubicacion.trim().toLowerCase(Locale.ROOT);
            String dir = (emp != null && emp.getDireccion() != null) ? emp.getDireccion().toLowerCase(Locale.ROOT) : "";
            if (!dir.contains(uFiltro)) {
                return false;
            }
        }
        return true;
    }

    private boolean empresaTieneRubro(Empresa emp, String rubroFiltroLower) {
        try {
            List<Rubro> rubros = emp.getRubros();
            if (rubros == null) {
                return false;
            }
            for (Rubro r : rubros) {
                if (r == null) {
                    continue;
                }
                if (r.getNombre() != null && r.getNombre().toLowerCase(Locale.ROOT).contains(rubroFiltroLower)) {
                    return true;
                }
                if (r.getDesc() != null && r.getDesc().toLowerCase(Locale.ROOT).contains(rubroFiltroLower)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // En caso de relación perezosa no inicializada fuera de sesión
        }
        return false;
    }

    private void registrarEmpresaYVehiculoActivos(
            Empresa empresa,
            Vehiculo vehiculo,
            Set<Integer> empresasActivasFiltradas,
            Set<String> vehiculosHabilitadosFiltrados
    ) {
        if (empresasActivasFiltradas != null && empresa != null && empresa.isActiva()) {
            empresasActivasFiltradas.add(empresa.getNroEmpresa());
        }
        if (vehiculosHabilitadosFiltrados != null && vehiculo != null && vehiculo.isEstadoHabilitacion()
                && vehiculo.getMatricula() != null) {
            vehiculosHabilitadosFiltrados.add(vehiculo.getMatricula());
        }
    }

    private boolean cumpleFiltroGuia(
            GuiaDeViaje g,
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        if (rubro != null && !rubro.isBlank()) {
            String rFiltro = rubro.trim().toLowerCase(Locale.ROOT);
            String rGuia = g.getRubroCliente() != null ? g.getRubroCliente().trim().toLowerCase(Locale.ROOT) : "";
            if (!rGuia.contains(rFiltro)) {
                return false;
            }
        }
        if (tipoCarga != null && !tipoCarga.isBlank()) {
            String tFiltro = tipoCarga.trim().toLowerCase(Locale.ROOT);
            boolean match = false;
            if (g.getTiposCarga() != null) {
                for (TipoCarga tc : g.getTiposCarga()) {
                    if (tc != null && tc.getDesc() != null && tc.getDesc().trim().toLowerCase(Locale.ROOT).contains(tFiltro)) {
                        match = true;
                        break;
                    }
                }
            }
            if (!match) {
                return false;
            }
        }
        if (ubicacion != null && !ubicacion.isBlank()) {
            String uFiltro = ubicacion.trim().toLowerCase(Locale.ROOT);
            String origen = g.getOrigen() != null ? g.getOrigen().toLowerCase(Locale.ROOT) : "";
            String destino = g.getDestino() != null ? g.getDestino().toLowerCase(Locale.ROOT) : "";
            String corredor = g.getCorredorVial() != null ? g.getCorredorVial().toLowerCase(Locale.ROOT) : "";
            if (!origen.contains(uFiltro) && !destino.contains(uFiltro) && !corredor.contains(uFiltro)) {
                return false;
            }
        }
        if (fechaDesde != null) {
            if (g.getFecha() == null || g.getFecha().isBefore(fechaDesde)) {
                return false;
            }
        }
        if (fechaHasta != null) {
            if (g.getFecha() == null || g.getFecha().isAfter(fechaHasta)) {
                return false;
            }
        }
        return true;
    }

    private Map<TipoEstado, Long> inicializarMapaGuias() {
        Map<TipoEstado, Long> mapa = new LinkedHashMap<>();
        for (TipoEstado estado : TipoEstado.values()) {
            mapa.put(estado, 0L);
        }
        return mapa;
    }

    private Map<EstadoInfraccion, Long> inicializarMapaInfraccionesPorEstado() {
        Map<EstadoInfraccion, Long> mapa = new LinkedHashMap<>();
        for (EstadoInfraccion estado : EstadoInfraccion.values()) {
            mapa.put(estado, 0L);
        }
        return mapa;
    }

    private Map<TipoInfraccion, Long> inicializarMapaInfraccionesPorTipo() {
        Map<TipoInfraccion, Long> mapa = new LinkedHashMap<>();
        for (TipoInfraccion tipo : TipoInfraccion.values()) {
            mapa.put(tipo, 0L);
        }
        return mapa;
    }

    private void cargarMetricasGuias(Map<TipoEstado, Long> guiasPorEstado) {
        TypedQuery<Object[]> query = em.createQuery(
                "SELECT g.estado, COUNT(g) FROM GuiaDeViaje g GROUP BY g.estado",
                Object[].class
        );
        if (query == null) {
            return;
        }
        List<Object[]> filas = query.getResultList();
        if (filas == null) {
            return;
        }

        for (Object[] fila : filas) {
            TipoEstado estado = (TipoEstado) fila[0];
            Long cantidad = (Long) fila[1];
            if (estado != null && cantidad != null) {
                guiasPorEstado.put(estado, cantidad);
            }
        }
    }

    private void cargarMetricasInfraccionesPorEstado(Map<EstadoInfraccion, Long> infraccionesPorEstado) {
        TypedQuery<Object[]> query = em.createQuery(
                "SELECT i.estado, COUNT(i) FROM Infraccion i GROUP BY i.estado",
                Object[].class
        );
        if (query == null) {
            return;
        }
        List<Object[]> filas = query.getResultList();
        if (filas == null) {
            return;
        }

        for (Object[] fila : filas) {
            EstadoInfraccion estado = (EstadoInfraccion) fila[0];
            Long cantidad = (Long) fila[1];
            if (estado != null && cantidad != null) {
                infraccionesPorEstado.put(estado, cantidad);
            }
        }
    }

    private void cargarMetricasInfraccionesPorTipo(Map<TipoInfraccion, Long> infraccionesPorTipo) {
        TypedQuery<Object[]> query = em.createQuery(
                "SELECT i.tipo, COUNT(i) FROM Infraccion i GROUP BY i.tipo",
                Object[].class
        );
        if (query == null) {
            return;
        }
        List<Object[]> filas = query.getResultList();
        if (filas == null) {
            return;
        }

        for (Object[] fila : filas) {
            TipoInfraccion tipo = (TipoInfraccion) fila[0];
            Long cantidad = (Long) fila[1];
            if (tipo != null && cantidad != null) {
                infraccionesPorTipo.put(tipo, cantidad);
            }
        }
    }
}
