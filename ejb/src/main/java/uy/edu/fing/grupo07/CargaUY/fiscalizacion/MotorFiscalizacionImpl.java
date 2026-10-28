package uy.edu.fing.grupo07.CargaUY.fiscalizacion;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
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
    private static final double UMBRAL_TRACKING_POR_DEFECTO = 5.0;

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
        evaluarDesvioDeRuta(guia, reglas, infraccionesGeneradas);

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

        boolean tieneItvVigente = false;
        List<ITV> listaItv = vehiculo.getItv();
        if (listaItv != null) {
            for (ITV itv : listaItv) {
                if (itv.isVigente()) {
                    tieneItvVigente = true;
                    break;
                }
            }
        }

        if (!tieneItvVigente) {
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

    private void evaluarDesvioDeRuta(
            GuiaDeViaje guia,
            ReglaFiscalizacion reglas,
            List<TipoInfraccion> infraccionesGeneradas
    ) {
        EventoViaje eventoDesviado = buscarEventoConMayorDesvio(guia);
        if (eventoDesviado == null || eventoDesviado.getDistanciaDesvioKm() == null) {
            return;
        }

        double distanciaKm = eventoDesviado.getDistanciaDesvioKm();
        double umbralKm = reglas.getUmbralTracking();
        boolean superaTolerancia = distanciaKm > umbralKm;

        if (!superaTolerancia) {
            return;
        }

        String origenTexto = "No especificado";
        if (guia.getOrigen() != null && !guia.getOrigen().isBlank()) {
            origenTexto = guia.getOrigen();
        }

        String destinoTexto = "No especificado";
        if (guia.getDestino() != null && !guia.getDestino().isBlank()) {
            destinoTexto = guia.getDestino();
        }

        String evidencia = "El evento de tracking UUID " + eventoDesviado.getUuid()
                + " en las coordenadas (lat: " + eventoDesviado.getLatitud()
                + ", lon: " + eventoDesviado.getLongitud() + ") procesado vía FaaS "
                + "registró un desvío de " + distanciaKm + " km respecto a la ruta asignada ("
                + origenTexto + " -> " + destinoTexto + "), superando la tolerancia máxima permitida de "
                + umbralKm + " km.";

        registrarInfraccionSiNoExiste(
                guia,
                TipoInfraccion.DESVIO_DE_RUTA,
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
        if (query == null) {
            return null;
        }
        query.setParameter("guiaId", guiaId);
        query.setMaxResults(1);

        List<PesadaBalanza> resultados = query.getResultList();
        if (resultados == null || resultados.isEmpty()) {
            return null;
        }

        return resultados.get(0);
    }

    private EventoViaje buscarEventoConMayorDesvio(GuiaDeViaje guia) {
        TypedQuery<EventoViaje> query = em.createQuery(
                "SELECT e FROM EventoViaje e WHERE e.guia.id = :guiaId AND e.distanciaDesvioKm IS NOT NULL ORDER BY e.distanciaDesvioKm DESC",
                EventoViaje.class
        );
        if (query != null) {
            query.setParameter("guiaId", guia.getId());
            query.setMaxResults(1);

            List<EventoViaje> resultados = query.getResultList();
            if (resultados != null && !resultados.isEmpty()) {
                return resultados.get(0);
            }
        }

        List<EventoViaje> eventosEnMemoria = guia.getEventos();
        if (eventosEnMemoria == null || eventosEnMemoria.isEmpty()) {
            return null;
        }

        EventoViaje mayor = null;
        for (EventoViaje ev : eventosEnMemoria) {
            if (ev != null && ev.getDistanciaDesvioKm() != null) {
                if (mayor == null || ev.getDistanciaDesvioKm() > mayor.getDistanciaDesvioKm()) {
                    mayor = ev;
                }
            }
        }

        return mayor;
    }

    private ReglaFiscalizacion obtenerReglasVigentes() {
        ReglaFiscalizacion regla = em.find(ReglaFiscalizacion.class, 1);
        if (regla != null) {
            em.refresh(regla); // Observar la edición administrativa sin conservar una regla vieja en caché.
            return regla;
        }

        return new ReglaFiscalizacion(
                UMBRAL_CARGA_POR_DEFECTO,
                UMBRAL_TRACKING_POR_DEFECTO
        );
    }
}
