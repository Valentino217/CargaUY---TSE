package uy.edu.fing.grupo07.CargaUY.fiscalizacion;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación del motor de fiscalización automática de viajes y cargas (CU-C3).
 */
@Stateless
public class MotorFiscalizacionImpl implements MotorFiscalizacionLocal {

    private static final Logger LOGGER = Logger.getLogger(MotorFiscalizacionImpl.class.getName());

    private static final int UMBRAL_CARGA_POR_DEFECTO = 500;
    private static final int UMBRAL_TRACKING_POR_DEFECTO = 3;

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public MotorFiscalizacionImpl() {
    }

    public MotorFiscalizacionImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<TipoInfraccion> evaluarGuia(Long guiaId) {
        if (guiaId == null) {
            throw new BusinessException("El identificador de la guía de viaje es obligatorio.");
        }

        GuiaDeViaje guia = em.find(GuiaDeViaje.class, guiaId);
        if (guia == null) {
            LOGGER.log(Level.WARNING, "No fue posible fiscalizar: la guía con ID {0} no existe.", guiaId);
            return Collections.emptyList();
        }

        ReglaFiscalizacion reglas = obtenerReglasVigentes();
        List<TipoInfraccion> infraccionesGeneradas = new ArrayList<>();

        evaluarExcesoDeCarga(guia, reglas, infraccionesGeneradas);
        evaluarHabilitacionDelVehiculo(guia, infraccionesGeneradas);

        return infraccionesGeneradas;
    }

    private void evaluarExcesoDeCarga(
            GuiaDeViaje guia,
            ReglaFiscalizacion reglas,
            List<TipoInfraccion> infraccionesGeneradas
    ) {
        Vehiculo vehiculo = guia.getVehiculo();
        if (vehiculo == null) {
            return;
        }

        PesadaBalanza pesadaMasAlta = buscarPesadaMasAlta(guia.getId());
        if (pesadaMasAlta == null) {
            return;
        }

        int pesoMaximoPermitido = vehiculo.getPeso() + vehiculo.getCapacidadCarga();
        int excesoEnKilos = pesadaMasAlta.getPesoRegistrado() - pesoMaximoPermitido;
        boolean superaTolerancia = excesoEnKilos > reglas.getUmbralCarga();

        if (!superaTolerancia) {
            return;
        }

        String evidencia = "La pesada #" + pesadaMasAlta.getIdPesada()
                + " registró " + pesadaMasAlta.getPesoRegistrado() + " kg. "
                + "El peso máximo legal permitido para el vehículo es " + pesoMaximoPermitido + " kg "
                + "(tara: " + vehiculo.getPeso() + " kg, capacidad: " + vehiculo.getCapacidadCarga() + " kg). "
                + "Se constató un exceso de " + excesoEnKilos + " kg, superando la tolerancia permitida de "
                + reglas.getUmbralCarga() + " kg.";

        registrarInfraccionSiNoExiste(
                guia,
                TipoInfraccion.EXCESO_DE_CARGA,
                evidencia,
                infraccionesGeneradas
        );
    }

    private void evaluarHabilitacionDelVehiculo(
            GuiaDeViaje guia,
            List<TipoInfraccion> infraccionesGeneradas
    ) {
        Vehiculo vehiculo = guia.getVehiculo();
        if (vehiculo == null) {
            return;
        }

        boolean habilitacionVigente = vehiculo.verificarHabilitacionVigente();
        if (habilitacionVigente) {
            return;
        }

        StringBuilder detalleFaltantes = new StringBuilder();

        if (!vehiculo.isEstadoHabilitacion()) {
            detalleFaltantes.append("El vehículo está administrativamente inhabilitado. ");
        }

        ITV itv = vehiculo.getItv();
        if (itv == null || !itv.isVigente()) {
            detalleFaltantes.append("Inspección Técnica Vehicular (ITV) ausente o vencida. ");
        }

        boolean tienePncVigente = false;
        List<PNC> listaPnc = vehiculo.getPncs();
        if (listaPnc != null) {
            for (PNC pnc : listaPnc) {
                if (pnc.isVigente()) {
                    tienePncVigente = true;
                    break;
                }
            }
        }

        if (!tienePncVigente) {
            detalleFaltantes.append("Permiso Nacional de Circulación (PNC) ausente o vencido. ");
        }

        String evidencia = "El vehículo matrícula " + vehiculo.getMatricula()
                + " no cuenta con habilitación vigente para circular. Detalle: "
                + detalleFaltantes.toString().trim();

        registrarInfraccionSiNoExiste(
                guia,
                TipoInfraccion.SIN_HABILITACION,
                evidencia,
                infraccionesGeneradas
        );
    }

    private void registrarInfraccionSiNoExiste(
            GuiaDeViaje guia,
            TipoInfraccion tipo,
            String evidencia,
            List<TipoInfraccion> infraccionesGeneradas
    ) {
        TypedQuery<Long> queryExistentes = em.createQuery(
                "SELECT COUNT(i) FROM Infraccion i WHERE i.guia.id = :guiaId AND i.tipo = :tipo",
                Long.class
        );
        queryExistentes.setParameter("guiaId", guia.getId());
        queryExistentes.setParameter("tipo", tipo);

        Long cantidadExistente = queryExistentes.getSingleResult();
        if (cantidadExistente != null && cantidadExistente > 0L) {
            return;
        }

        Infraccion nuevaInfraccion = new Infraccion(
                LocalDate.now(),
                tipo,
                EstadoInfraccion.DETECTADA,
                guia,
                evidencia
        );

        em.persist(nuevaInfraccion);
        infraccionesGeneradas.add(tipo);

        LOGGER.log(
                Level.INFO,
                "Infracción detectada y registrada con éxito. Guía: {0}, Tipo: {1}",
                new Object[]{guia.getId(), tipo}
        );
    }

    private PesadaBalanza buscarPesadaMasAlta(Long guiaId) {
        TypedQuery<PesadaBalanza> query = em.createQuery(
                "SELECT p FROM PesadaBalanza p WHERE p.guia.id = :guiaId ORDER BY p.pesoRegistrado DESC",
                PesadaBalanza.class
        );
        query.setParameter("guiaId", guiaId);
        query.setMaxResults(1);

        List<PesadaBalanza> resultados = query.getResultList();
        if (resultados.isEmpty()) {
            return null;
        }

        return resultados.get(0);
    }

    private ReglaFiscalizacion obtenerReglasVigentes() {
        ReglaFiscalizacion regla = em.find(ReglaFiscalizacion.class, 1);
        if (regla != null) {
            return regla;
        }

        return new ReglaFiscalizacion(
                UMBRAL_CARGA_POR_DEFECTO,
                UMBRAL_TRACKING_POR_DEFECTO
        );
    }
}
