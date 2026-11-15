package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - ITVEJB y PNCEJB")
class ITVPNCEJBTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<ITV> qItv;

    @Mock
    private TypedQuery<PNC> qPnc;

    private ITVEJB itvEJB;
    private PNCEJB pncEJB;
    private Vehiculo vehiculo;

    @BeforeEach
    void setUp() throws Exception {
        itvEJB = new ITVEJB();
        Field emItv = ITVEJB.class.getDeclaredField("em");
        emItv.setAccessible(true);
        emItv.set(itvEJB, em);

        pncEJB = new PNCEJB();
        Field emPnc = PNCEJB.class.getDeclaredField("em");
        emPnc.setAccessible(true);
        emPnc.set(pncEJB, em);

        Empresa emp = new Empresa(10, "Transportes S.A.", "TransSA", "Ruta 1");
        vehiculo = new Vehiculo("STP 1234", "Volvo", "FH", 12000, 30000, true, emp);
    }

    @Test
    @DisplayName("ITVEJB: crear, modificar, eliminar y listarITVs")
    void testITVEJB() {
        ITV itv = new ITV(LocalDate.now().plusDays(90), vehiculo);
        itv.setNroCertificado(101);

        itvEJB.crear(itv);
        verify(em).persist(itv);

        itvEJB.modificar(itv);
        verify(em).merge(itv);

        when(em.merge(itv)).thenReturn(itv);
        itvEJB.eliminar(itv);
        verify(em).remove(itv);

        when(em.createQuery(anyString(), eq(ITV.class))).thenReturn(qItv);
        when(qItv.getResultList()).thenReturn(List.of(itv));
        List<ITV> lista = itvEJB.listarITVs();
        assertEquals(1, lista.size());
        assertEquals(101, lista.get(0).getNroCertificado());
    }

    @Test
    @DisplayName("PNCEJB: crear, modificar, eliminar y listarPNCs")
    void testPNCEJB() {
        PNC pnc = new PNC(LocalDate.now().plusDays(120), vehiculo);
        pnc.setNro(202);

        pncEJB.crear(pnc);
        verify(em).persist(pnc);

        pncEJB.modificar(pnc);
        verify(em).merge(pnc);

        when(em.merge(pnc)).thenReturn(pnc);
        pncEJB.eliminar(pnc);
        verify(em).remove(pnc);

        when(em.createQuery(anyString(), eq(PNC.class))).thenReturn(qPnc);
        when(qPnc.getResultList()).thenReturn(List.of(pnc));
        List<PNC> lista = pncEJB.listarPNCs();
        assertEquals(1, lista.size());
        assertEquals(202, lista.get(0).getNro());
    }

    @Test
    @DisplayName("ITVEJB y PNCEJB fallan cerrados sin ControlAcceso y validan token por rol/empresa")
    void testTokenizadoYFailClosed() throws Exception {
        ITV itv = new ITV(LocalDate.now().plusDays(60), vehiculo);
        itv.setNroCertificado(101);
        PNC pnc = new PNC(LocalDate.now().plusDays(90), "PNC-2026-01", vehiculo);
        pnc.setNro(202);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> itvEJB.crear("tok", itv));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> pncEJB.listarPNCs("tok"));

        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso control =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        Field cItv = ITVEJB.class.getDeclaredField("control");
        cItv.setAccessible(true);
        cItv.set(itvEJB, control);
        Field cPnc = PNCEJB.class.getDeclaredField("control");
        cPnc.setAccessible(true);
        cPnc.set(pncEJB, control);

        when(em.find(ITV.class, 101)).thenReturn(itv);
        when(em.merge(itv)).thenReturn(itv);
        itvEJB.crear("tok-emp", itv);
        itvEJB.modificar("tok-emp", itv);
        itvEJB.eliminar("tok-emp", itv);
        verify(control, org.mockito.Mockito.times(3)).exigir(
                "tok-emp",
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR,
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO
        );

        when(em.find(PNC.class, 202)).thenReturn(pnc);
        when(em.merge(pnc)).thenReturn(pnc);
        pncEJB.crear("tok-emp", pnc);
        pncEJB.modificar("tok-emp", pnc);
        pncEJB.eliminar("tok-emp", pnc);
        verify(control, org.mockito.Mockito.times(6)).exigir(
                "tok-emp",
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR,
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO
        );

        uy.edu.fing.grupo07.CargaUY.security.Acceso ciudadano =
                new uy.edu.fing.grupo07.CargaUY.security.Acceso(
                        7,
                        41234567,
                        "Resp",
                        java.util.Set.of(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CIUDADANO)
                );
        when(control.exigir(
                eq("tok-ciu"),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CIUDADANO)
        )).thenReturn(ciudadano);

        when(em.createQuery(org.mockito.ArgumentMatchers.contains("WHERE t.vehiculo.empresa.activa = true"), eq(ITV.class)))
                .thenReturn(qItv);
        when(qItv.setParameter("id", 7)).thenReturn(qItv);
        when(qItv.getResultList()).thenReturn(List.of(itv));
        assertEquals(1, itvEJB.listarITVs("tok-ciu").size());

        when(em.createQuery(org.mockito.ArgumentMatchers.contains("WHERE p.vehiculo.empresa.activa = true"), eq(PNC.class)))
                .thenReturn(qPnc);
        when(qPnc.setParameter("id", 7)).thenReturn(qPnc);
        when(qPnc.getResultList()).thenReturn(List.of(pnc));
        assertEquals(1, pncEJB.listarPNCs("tok-ciu").size());
    }
}
