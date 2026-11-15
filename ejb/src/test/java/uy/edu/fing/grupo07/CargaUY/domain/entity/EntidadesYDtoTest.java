package uy.edu.fing.grupo07.CargaUY.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.integration.ResultadoPdi;
import uy.edu.fing.grupo07.CargaUY.jms.ColasAplicacion;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - Entidades de Dominio y DTOs")
class EntidadesYDtoTest {

    @Test
    @DisplayName("Entidades de Usuario, Empresa, Rubro, TipoCarga y VinculoEmpresa")
    void testUsuariosYEmpresa() {
        LocalDate nac = LocalDate.of(1990, 5, 15);

        Ciudadano ciu = new Ciudadano();
        ciu.setId(1);
        ciu.setNombre("Ana");
        ciu.setMail("ana@test.com");
        ciu.setFechaNac(nac);
        ciu.setCi(11111111);
        ciu.setContrasena("pass1");
        ciu.setActivo(true);
        ciu.setOrigenIdentidad("GUB");
        ciu.setUidExterno("uid-1");
        ciu.setGuias(new ArrayList<>());
        assertEquals("pass1", ciu.getContrasena());
        assertEquals("pass1", ciu.getPassword());
        assertEquals("GUB", ciu.getOrigenIdentidad());
        assertEquals("uid-1", ciu.getUidExterno());
        assertTrue(ciu.getGuias().isEmpty());
        assertEquals(0L, ciu.getVersion());

        Ciudadano ciu2 = new Ciudadano("Ana", "ana@test.com", nac, 11111111, "pass1");
        assertEquals(ciu, ciu2);
        assertEquals(ciu.hashCode(), ciu2.hashCode());
        assertNotEquals(ciu, null);
        assertNotEquals(ciu, "otro");

        Chofer ch = new Chofer();
        ch.setEmpresas(new ArrayList<>());
        Chofer ch2 = new Chofer("Luis", "luis@test.com", nac, 22222222, "pass2");
        assertEquals("Luis", ch2.getNombre());
        assertTrue(ch.getEmpresas().isEmpty());

        Funcionario func = new Funcionario();
        func.setNroFuncionario(10);
        func.setDepartamento("Contralor");
        assertEquals(10, func.getNroFuncionario());
        assertEquals("Contralor", func.getDepartamento());
        Funcionario func2 = new Funcionario("Marta", "m@test.com", nac, 33333333, "pass3", 20, "Legal");
        assertEquals(20, func2.getNroFuncionario());
        assertEquals("Legal", func2.getDepartamento());

        Autoridad aut = new Autoridad();
        aut.setCargo("Ministro");
        assertEquals("Ministro", aut.getCargo());
        Autoridad aut2 = new Autoridad("Carlos", "c@test.com", nac, 44444444, "pass4", "Director");
        assertEquals("Director", aut2.getCargo());

        Administrador adm = new Administrador();
        Administrador adm2 = new Administrador("Admin", "adm@test.com", nac, 55555555, "pass5");
        assertNotNull(adm);
        assertEquals(55555555, adm2.getCi());

        Empresa emp = new Empresa();
        emp.setNroEmpresa(100);
        emp.setRazonSocial("Razon SA");
        emp.setNombrePublico("Publico");
        emp.setDireccion("Calle 1");
        emp.setActiva(true);
        emp.setCiudadanos(new ArrayList<>(List.of(ciu)));
        emp.setChoferes(new ArrayList<>(List.of(ch2)));
        emp.setRubros(new ArrayList<>());
        emp.setVehiculos(new ArrayList<>());
        emp.setGuias(new ArrayList<>());
        ciu.setEmpresas(new ArrayList<>(List.of(emp)));

        Empresa emp2 = new Empresa(100, "Razon SA", "Publico", "Calle 1");
        assertEquals(emp, emp2);
        assertEquals(emp.hashCode(), emp2.hashCode());
        assertEquals(0L, emp.getVersion());
        assertEquals(1, emp.getCiudadanos().size());
        assertEquals(1, emp.getChoferes().size());
        assertTrue(emp.getRubros().isEmpty());
        assertTrue(emp.getVehiculos().isEmpty());
        assertTrue(emp.getGuias().isEmpty());

        Rubro r1 = new Rubro();
        r1.setCodigo(5);
        r1.setNombre("Agro");
        r1.setDesc("Sector agropecuario");
        r1.setEmpresas(new ArrayList<>(List.of(emp)));
        Rubro r2 = new Rubro("Agro", "Desc");
        r2.setCodigo(5);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertEquals("Agro", r1.getNombre());
        assertEquals("Sector agropecuario", r1.getDesc());
        assertEquals(1, r1.getEmpresas().size());

        TipoCarga tc1 = new TipoCarga();
        tc1.setCodigo(8);
        tc1.setDesc("Refrigerada");
        tc1.setGuias(new ArrayList<>());
        TipoCarga tc2 = new TipoCarga("Refrigerada");
        tc2.setCodigo(8);
        assertEquals(tc1, tc2);
        assertEquals(tc1.hashCode(), tc2.hashCode());
        assertEquals("Refrigerada", tc1.getDesc());
        assertTrue(tc1.getGuias().isEmpty());

        VinculoEmpresa ve = new VinculoEmpresa(ciu, emp);
        ve.setResponsable(true);
        ve.setChofer(false);
        assertEquals(ciu, ve.getUsuario());
        assertEquals(emp, ve.getEmpresa());
        assertTrue(ve.isResponsable());
        assertFalse(ve.isChofer());
        assertEquals("100:1", VinculoEmpresa.clave(100, 1));
        assertNotNull(new VinculoEmpresa());
    }

    @Test
    @DisplayName("Entidades Vehiculo, ITV, PNC, GuiaDeViaje, EventoViaje, IncidenteRuta, PuntoTracking y PesadaBalanza")
    void testVehiculoGuiaEventosTrackingPesada() {
        Empresa emp = new Empresa(10, "Emp", "Emp", "Dir");
        Vehiculo v1 = new Vehiculo();
        v1.setMatricula("STP 1234");
        v1.setMarca("Volvo");
        v1.setModelo("FH");
        v1.setPeso(10000);
        v1.setCapacidadCarga(25000);
        v1.setEstadoHabilitacion(true);
        v1.setEmpresa(emp);
        v1.setGuias(new ArrayList<>());

        Vehiculo v2 = new Vehiculo("STP 1234", "Volvo", "FH", 10000, 25000, true, emp);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertEquals("Volvo", v1.getMarca());
        assertEquals("FH", v1.getModelo());
        assertEquals(10000, v1.getPeso());
        assertEquals(25000, v1.getCapacidadCarga());
        assertTrue(v1.getGuias().isEmpty());

        ITV itv1 = new ITV();
        itv1.setNroCertificado(1);
        itv1.setFechaVencimiento(LocalDate.now().plusDays(10));
        itv1.setVehiculo(v1);
        ITV itv2 = new ITV(LocalDate.now().plusDays(10), v1);
        itv2.setNroCertificado(1);
        assertEquals(itv1, itv2);
        assertEquals(itv1.hashCode(), itv2.hashCode());
        assertTrue(itv1.isVigente());
        assertEquals(v1, itv1.getVehiculo());

        PNC pnc1 = new PNC();
        pnc1.setNro(2);
        pnc1.setFechaVencimiento(LocalDate.now().plusDays(10));
        pnc1.setVehiculo(v1);
        PNC pnc2 = new PNC(LocalDate.now().plusDays(10), v1);
        pnc2.setNro(2);
        assertEquals(pnc1, pnc2);
        assertEquals(pnc1.hashCode(), pnc2.hashCode());
        assertTrue(pnc1.isVigente());
        assertEquals(v1, pnc1.getVehiculo());

        v1.setItv(new ArrayList<>(List.of(itv1)));
        v1.setPncs(new ArrayList<>(List.of(pnc1)));
        assertTrue(v1.verificarHabilitacionVigente());

        GuiaDeViaje g1 = new GuiaDeViaje();
        g1.setId(50L);
        g1.setFecha(LocalDate.now());
        g1.setOrigen("MVD");
        g1.setDestino("SAL");
        g1.setRubroCliente("Agro");
        g1.setVolumenCarga(12000f);
        g1.setEstado(TipoEstado.EN_CURSO);
        g1.setVersion(1);
        g1.setEmpresa(emp);
        g1.setVehiculo(v1);
        g1.setTiposCarga(new ArrayList<>());
        g1.setPuntosTracking(new ArrayList<>());
        g1.setPesadas(new ArrayList<>());
        g1.setEventos(new ArrayList<>());
        g1.setInfracciones(new ArrayList<>());

        GuiaDeViaje g2 = new GuiaDeViaje(LocalDate.now(), "MVD", "SAL", "Agro", 12000f, emp, v1, null);
        g2.setId(50L);
        assertEquals(g1, g2);
        assertEquals(g1.hashCode(), g2.hashCode());
        assertEquals(1, g1.getVersion());
        assertTrue(g1.getInfracciones().isEmpty());

        PuntoTracking pt1 = new PuntoTracking();
        pt1.setId(1L);
        pt1.setLatitud(-34.9);
        pt1.setLongitud(-56.1);
        pt1.setTiempo(LocalDateTime.now());
        pt1.setDistanciaDesvioKm(1.2);
        PuntoTracking pt2 = new PuntoTracking(-34.9, -56.1, LocalDateTime.now(), g1);
        pt2.setId(1L);
        PuntoTracking pt3 = new PuntoTracking(-34.9, -56.1, LocalDateTime.now(), 1.2, g1);
        pt3.setId(1L);
        assertEquals(pt1, pt2);
        assertEquals(pt1.hashCode(), pt3.hashCode());
        g1.agregarPuntoTracking(pt1);
        assertEquals(1, g1.getPuntosTracking().size());
        assertEquals(g1, pt1.getGuia());
        assertEquals(-34.9, pt1.getLatitud());
        assertEquals(-56.1, pt1.getLongitud());
        assertNotNull(pt1.getTiempo());
        assertEquals(1.2, pt1.getDistanciaDesvioKm());

        PesadaBalanza pb1 = new PesadaBalanza();
        pb1.setIdPesada(10);
        pb1.setFecha(LocalDate.now());
        pb1.setHora(LocalTime.NOON);
        pb1.setPesoRegistrado(32000);
        pb1.setVehiculo(v1);
        PesadaBalanza pb2 = new PesadaBalanza(LocalDate.now(), LocalTime.NOON, 32000, g1);
        pb2.setIdPesada(10);
        assertEquals(pb1, pb2);
        assertEquals(pb1.hashCode(), pb2.hashCode());
        g1.agregarPesada(pb1);
        assertEquals(1, g1.getPesadas().size());
        assertEquals(g1, pb1.getGuia());
        assertEquals(32000, pb1.getPesoRegistrado());
        assertEquals(LocalTime.NOON, pb1.getHora());
        assertEquals(LocalDate.now(), pb1.getFecha());
        assertEquals(v1, pb1.getVehiculo());

        EventoViaje ev1 = new EventoViaje();
        ev1.setId(9L);
        ev1.setUuid("uuid-fijo");
        ev1.setTiempo(LocalDateTime.now());
        ev1.setTipo(TipoEvento.INCIDENTE);
        ev1.setLatitud(-34.8);
        ev1.setLongitud(-56.2);
        ev1.setDistanciaDesvioKm(0.5);
        ev1.setCantidadKg(500.0);
        ev1.setMercaderia("Soja");
        ev1.setEmpresaCarga("AgroSA");

        IncidenteRuta inc1 = new IncidenteRuta();
        inc1.setId(3L);
        inc1.setFoto("base64foto");
        inc1.setDescripcion("Pinchadura");
        IncidenteRuta inc2 = new IncidenteRuta("base64foto", "Pinchadura", ev1);
        inc2.setId(3L);
        assertEquals(inc1, inc2);
        assertEquals(inc1.hashCode(), inc2.hashCode());
        assertEquals("base64foto", inc1.getFoto());
        assertEquals("Pinchadura", inc1.getDescripcion());

        ev1.setIncidente(inc1);
        assertEquals(ev1, inc1.getEvento());
        assertEquals(inc1, ev1.getIncidente());
        g1.agregarEvento(ev1);
        assertEquals(1, g1.getEventos().size());
        assertEquals(0.5, ev1.getDistanciaDesvioKm());
        assertEquals(500.0, ev1.getCantidadKg());
        assertEquals("Soja", ev1.getMercaderia());
        assertEquals("AgroSA", ev1.getEmpresaCarga());
    }

    @Test
    @DisplayName("Entidades Infraccion, Descargo, RegistroAuditoria, ReglaFiscalizacion, FlujoIdentidad y SesionAcceso")
    void testInfraccionDescargoAuditoriaReglaYSeguridad() throws Exception {
        Empresa emp = new Empresa(10, "Emp", "Emp", "Dir");
        Vehiculo v = new Vehiculo("STP 1234", "V", "M", 1000, 2000, true, emp);
        GuiaDeViaje g = new GuiaDeViaje(LocalDate.now(), "A", "B", "R", 100f, emp, v, null);

        Infraccion inf1 = new Infraccion();
        inf1.setIdCaso(1);
        inf1.setFecha(LocalDate.now());
        inf1.setTipo(TipoInfraccion.EXCESO_DE_CARGA);
        inf1.setEstado(EstadoInfraccion.DETECTADA);
        inf1.setResolucion("CONFIRMADA");
        inf1.setFundamento("Exceso constatado");
        inf1.setFechaResolucion(LocalDate.now());
        inf1.setEvidencia("Balanza 1");
        inf1.setGuia(g);

        Infraccion inf2 = new Infraccion(LocalDate.now(), TipoInfraccion.EXCESO_DE_CARGA, EstadoInfraccion.DETECTADA, g);
        inf2.setIdCaso(1);
        Infraccion inf3 = new Infraccion(LocalDate.now(), TipoInfraccion.VIAJE_SIN_GUIA, EstadoInfraccion.DETECTADA, v, "Sin guia");

        assertEquals(inf1, inf2);
        assertEquals(inf1.hashCode(), inf2.hashCode());
        assertEquals(v, inf1.resolverVehiculo());
        assertEquals(emp, inf1.resolverEmpresa());
        assertEquals(v, inf3.resolverVehiculo());
        assertEquals(emp, inf3.resolverEmpresa());
        assertEquals("CONFIRMADA", inf1.getResolucion());
        assertEquals("Exceso constatado", inf1.getFundamento());
        assertNotNull(inf1.getFechaResolucion());
        assertEquals("Balanza 1", inf1.getEvidencia());

        Descargo d1 = new Descargo();
        d1.setId(7L);
        d1.setFecha(LocalDate.now());
        d1.setDescargo("Fue un error de balanza");
        Descargo d2 = new Descargo(LocalDate.now(), "Texto", inf1);
        d2.setId(7L);
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
        inf1.setDescargo(d1);
        assertEquals(d1, inf1.getDescargo());
        assertEquals(inf1, d1.getInfraccion());
        assertEquals("Fue un error de balanza", d1.getDescargo());
        assertNotNull(d1.getFecha());

        RegistroAuditoria ra1 = new RegistroAuditoria("CONFIRMADA", LocalDate.now(), "func1", "CONFIRMAR", "Fundamento 1");
        Field idField = RegistroAuditoria.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(ra1, 100L);
        RegistroAuditoria ra2 = new RegistroAuditoria(inf1, "CONFIRMADA", LocalDate.now(), "func1", "CONFIRMAR", "Fundamento 1");
        idField.set(ra2, 100L);
        assertEquals(ra1, ra2);
        assertEquals(ra1.hashCode(), ra2.hashCode());
        assertNotNull(new RegistroAuditoria());

        inf1.setRegistroAuditoria(ra1);
        assertEquals(ra1, inf1.getRegistroAuditoria());
        assertEquals(1, inf1.getRegistrosAuditoria().size());
        assertEquals(inf1, ra1.getInfraccion());

        ReglaFiscalizacion rf1 = new ReglaFiscalizacion();
        rf1.setId(1);
        rf1.setUmbralCarga(800);
        rf1.setUmbralTracking(3.5);
        ReglaFiscalizacion rf2 = new ReglaFiscalizacion(800, 3.5);
        assertEquals(rf1, rf2);
        assertEquals(rf1.hashCode(), rf2.hashCode());
        assertEquals(800, rf1.getUmbralCarga());
        assertEquals(3.5, rf1.getUmbralTracking());
        assertEquals(0L, rf1.getVersion());

        Instant vence = Instant.now().plusSeconds(60);
        FlujoIdentidad fi = new FlujoIdentidad("h", "ch", "nc", vence);
        assertEquals("ch", fi.getChallenge());
        assertEquals("nc", fi.getNonce());
        assertEquals(vence, fi.getVence());
        assertNotNull(new FlujoIdentidad());

        Ciudadano ciu = new Ciudadano("N", "m@t.com", LocalDate.now(), 123, "p");
        SesionAcceso sa = new SesionAcceso("h", ciu, vence);
        assertEquals(ciu, sa.getUsuario());
        assertEquals(vence, sa.getVence());
        assertNotNull(new SesionAcceso());
    }

    @Test
    @DisplayName("DTOs y Excepciones: records de transferencia, constructores y métodos utilitarios")
    void testDtosYExcepciones() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        DatosUsuario du = new DatosUsuario(TipoUsuario.FUNCIONARIO, "Nom", "m@t.com", hoy, 123, "pwd", 1, "Dep", "Cargo");
        assertEquals(TipoUsuario.FUNCIONARIO, du.tipo());

        Empresa emp = new Empresa(10, "Razon", "Pub", "Dir");
        Ciudadano ciu = new Ciudadano("Ciu", "c@t.com", hoy, 111, "p");
        ciu.setId(5);
        emp.getCiudadanos().add(ciu);
        EmpresaResumen er = EmpresaResumen.desde(emp);
        assertEquals(10, er.getNumero());
        assertEquals("Razon", er.getRazonSocial());
        assertEquals("Pub", er.getNombrePublico());
        assertEquals("Dir", er.getDireccion());
        assertTrue(er.isActiva());
        assertEquals(List.of(5), er.getCiudadanos());
        assertEquals(0L, er.getVersion());

        UsuarioResumen urCiu = UsuarioResumen.desde(ciu);
        assertEquals("CIUDADANO", urCiu.getTipo());
        assertEquals(5, urCiu.getId());
        assertEquals("Ciu", urCiu.getNombre());
        assertEquals("c@t.com", urCiu.getMail());
        assertEquals(hoy, urCiu.getFechaNac());
        assertEquals(111, urCiu.getCi());
        assertTrue(urCiu.isActivo());
        assertNull(urCiu.getNroFuncionario());
        assertNull(urCiu.getDepartamento());
        assertNull(urCiu.getCargo());
        assertEquals(0L, urCiu.getVersion());
        assertNotNull(urCiu.getRoles());

        assertEquals("CHOFER", UsuarioResumen.desde(new Chofer("C", "c@t.com", hoy, 222, "p")).getTipo());
        assertEquals("FUNCIONARIO", UsuarioResumen.desde(new Funcionario("F", "f@t.com", hoy, 333, "p", 9, "D")).getTipo());
        assertEquals("AUTORIDAD", UsuarioResumen.desde(new Autoridad("A", "a@t.com", hoy, 444, "p", "Cargo")).getTipo());
        assertEquals("ADMINISTRADOR", UsuarioResumen.desde(new Administrador("Ad", "ad@t.com", hoy, 555, "p")).getTipo());

        GuiaResumenDTO gr = new GuiaResumenDTO(1L, hoy, "O", "D", "R", 10f, TipoEstado.EN_CURSO, 1, "MAT", "Marca", 10, "Razon", 5, "Chof");
        assertEquals(1L, gr.id());
        assertEquals("MAT", gr.vehiculoMatricula());

        RegistroAuditoriaDTO rad = new RegistroAuditoriaDTO(1L, "CONF", hoy, ahora, "u", "ACC", "Fund");
        InfraccionDTO idto1 = new InfraccionDTO(1, hoy, TipoInfraccion.EXCESO_DE_CARGA, EstadoInfraccion.DETECTADA, "Ev", "Fund", hoy, 1L, "MAT", "Emp", "Desc", hoy, rad);
        assertEquals(1, idto1.historialAuditoria().size());
        InfraccionDTO idto2 = new InfraccionDTO(2, hoy, TipoInfraccion.EXCESO_DE_CARGA, EstadoInfraccion.DETECTADA, "Ev", "Fund", hoy, 1L, "MAT", "Emp", "Desc", hoy, null, null);
        assertTrue(idto2.historialAuditoria().isEmpty());
        assertFalse(idto2.isDescargoEditado());
        assertTrue(idto2.versionesAnterioresDescargo().isEmpty());
        assertEquals(0, idto2.historialAuditoriaConsolidado().size());

        RegistroAuditoriaDTO h0 = new RegistroAuditoriaDTO(10L, "DETECTADA", hoy, ahora, "SIS", "CREAR_INFRACCION", "Det");
        RegistroAuditoriaDTO h1 = new RegistroAuditoriaDTO(11L, "EN_DESCARGO", hoy, ahora, "Emp", "PRESENTAR_DESCARGO", "V1");
        RegistroAuditoriaDTO h2 = new RegistroAuditoriaDTO(12L, "EN_DESCARGO", hoy, ahora.plusMinutes(2), "Emp", "EDITAR_DESCARGO", "V2");
        InfraccionDTO idtoEdit = new InfraccionDTO(3, hoy, TipoInfraccion.EXCESO_DE_CARGA, EstadoInfraccion.EN_DESCARGO, "Ev", null, null, 1L, "MAT", "Emp", "V2", hoy, h2, List.of(h0, h1, h2));
        assertTrue(idtoEdit.isDescargoEditado());
        assertEquals(2, idtoEdit.historialAuditoriaConsolidado().size());
        assertEquals("PRESENTAR_DESCARGO", idtoEdit.historialAuditoriaConsolidado().get(1).accion());
        assertEquals("V2", idtoEdit.historialAuditoriaConsolidado().get(1).fundamento());
        assertTrue(idtoEdit.esDescargoEditado(idtoEdit.historialAuditoriaConsolidado().get(1)));
        assertFalse(idtoEdit.esDescargoEditado(h0));
        assertFalse(idtoEdit.esDescargoEditado(null));
        assertEquals(1, idtoEdit.versionesAnterioresDescargo().size());
        assertEquals("V1", idtoEdit.versionesAnterioresDescargo().get(0).fundamento());

        IncidenteDTO incDto = new IncidenteDTO("foto", "desc");
        assertEquals("foto", incDto.foto());
        EventoViajeDTO ev1 = new EventoViajeDTO("u1", ahora, TipoEvento.INICIO, -34.0, -56.0, 1L, incDto);
        EventoViajeDTO ev2 = new EventoViajeDTO("u2", ahora, TipoEvento.INICIO, -34.0, -56.0, 1L, incDto, 1.5);
        EventoViajeDTO ev3 = new EventoViajeDTO("u3", ahora, TipoEvento.CARGA, -34.0, -56.0, 1L, null, 0.0, 1000.0, "Soja", "Emp");
        EventoViajeDTO ev4 = new EventoViajeDTO("u4", ahora, TipoEvento.POSICION, -34.0, -56.0, 1L, null, 0.0, 1000.0, "Soja", "Emp", "STP 1234");
        assertEquals("u1", ev1.uuid());
        assertEquals(1.5, ev2.distanciaDesvioKm());
        assertEquals("Soja", ev3.mercaderia());
        assertEquals("STP 1234", ev4.matricula());

        ValidacionEmpresaDTO.Unidad uVal = new ValidacionEmpresaDTO.Unidad("MAT", true, hoy, "VIGENTE", hoy, "VIGENTE", "VIGENTE");
        ValidacionEmpresaDTO valEmp = new ValidacionEmpresaDTO(10, "Razon", true, hoy, List.of(uVal));
        assertEquals(1L, valEmp.habilitadas());

        assertEquals("ACCEPTED", SyncResultDTO.accepted("u").estado());
        assertEquals("DUPLICATE", SyncResultDTO.duplicate("u").estado());
        assertEquals("ERROR", SyncResultDTO.error("u", "msg").estado());

        ResolucionInfraccionDTO resDto = new ResolucionInfraccionDTO(1, "Fundamento");
        assertEquals(1, resDto.funcionarioId());

        ReportarIncidenteDTO repDto = new ReportarIncidenteDTO("u", 1L, ahora, -34.0, -56.0, "f", "d");
        assertEquals("d", repDto.descripcion());

        DashboardResumenDTO dash = new DashboardResumenDTO(Map.of(), Map.of(), Map.of(), 5L);
        assertEquals(5L, dash.totalPesadas());

        DescargoRequestDTO descReq = new DescargoRequestDTO("texto");
        assertEquals("texto", descReq.texto());

        PesadaBalanzaDTO pesDto = new PesadaBalanzaDTO(1, hoy, LocalTime.NOON, 15000, "MAT", 1L);
        assertEquals(15000, pesDto.pesoRegistrado());

        BusinessException be1 = new BusinessException("err");
        BusinessException be2 = new BusinessException("err2", new RuntimeException("causa"));
        assertEquals("err", be1.getMessage());
        assertNotNull(be2.getCause());

        ResultadoPdi pdiInd = ResultadoPdi.indisponible();
        assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE, pdiInd.estado());

        assertNotNull(new ColasAplicacion());
    }
}
