package uy.edu.fing.grupo07.CargaUY.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Administrador;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Autoridad;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Chofer;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Ciudadano;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Descargo;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.IncidenteRuta;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PuntoTracking;
import uy.edu.fing.grupo07.CargaUY.domain.entity.RegistroAuditoria;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Rubro;
import uy.edu.fing.grupo07.CargaUY.domain.entity.TipoCarga;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

/**
 * EJB Singleton de inicio que inicializa datos de prueba en la base de datos
 * tras el despliegue cuando el esquema se encuentra vacío.
 *
 * <p>Se ejecuta inmediatamente después de {@code AuditoriaTriggerInstaller}
 * para asegurar que la auditoría inmutable esté debidamente configurada.</p>
 */
@Singleton
@Startup
@DependsOn("AuditoriaTriggerInstaller")
public class DatosPruebaStartupBean {

    private static final Logger LOGGER = Logger.getLogger(DatosPruebaStartupBean.class.getName());

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager entityManager;

    public DatosPruebaStartupBean() {
    }

    /**
     * Constructor para inyección de dependencias en pruebas unitarias.
     *
     * @param entityManager el gestor de entidades JPA a utilizar.
     */
    public DatosPruebaStartupBean(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Punto de entrada de inicialización invocado al desplegar la aplicación.
     */
    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void inicializar() {
        if (!Boolean.getBoolean("p3.datos.prueba")) {
            LOGGER.info("Carga automática de datos de prueba deshabilitada; se conserva la base existente.");
            return;
        }
        long usuarios = entityManager.createQuery("select count(u) from Usuario u", Long.class).getSingleResult();
        if (usuarios > 0) {
            LOGGER.info("La base ya contiene usuarios; no se reemplazan sus datos.");
            return;
        }
        try {
            cargarDatosDePrueba();
            LOGGER.info("Datos de prueba inicializados correctamente.");
        } catch (RuntimeException excepcion) {
            LOGGER.log(Level.SEVERE, "No fue posible inicializar los datos de prueba iniciales", excepcion);
        }
    }

    /**
     * Inserta el conjunto completo de datos de prueba relacionados.
     */
    private void cargarDatosDePrueba() {
        inicializarReglasFiscalizacion();

        // 1. Usuarios con distintos roles del sistema
        // Persistir Ciudadano primero para que su ID generado sea 1 (coincidiendo con ID_CIUDADANO_SIMULADO)
        Ciudadano ciudadano = new Ciudadano(
                "Pedro Empresario",
                "pedro.empresario@transporte.uy",
                LocalDate.of(1978, 3, 10),
                87654321,
                "ciudadano123"
        );
        entityManager.persist(ciudadano);

        Chofer chofer = new Chofer(
                "Carlos Chofer",
                "carlos.chofer@transporte.uy",
                LocalDate.of(1990, 8, 15),
                11223344,
                "chofer123"
        );
        entityManager.persist(chofer);

        Chofer choferJuan = new Chofer(
                "Juan Chofer",
                "juan.chofer@transporte.uy",
                LocalDate.of(1992, 4, 12),
                55667788,
                "chofer123"
        );
        entityManager.persist(choferJuan);

        Funcionario funcionario = new Funcionario(
                "Juan Funcionario",
                "funcionario@cargauy.gub.uy",
                LocalDate.of(1985, 5, 20),
                12345678,
                "funcionario123",
                101,
                "Montevideo"
        );
        entityManager.persist(funcionario);

        Autoridad autoridad = new Autoridad(
                "Director Nacional",
                "autoridad@cargauy.gub.uy",
                LocalDate.of(1975, 6, 20),
                20000002,
                "autoridad123",
                "Dirección Nacional de Transporte"
        );
        entityManager.persist(autoridad);

        Administrador administrador = new Administrador(
                "Admin",
                "admin@cargauy.gub.uy",
                LocalDate.of(1980, 1, 15),
                10000001,
                "admin123"
        );
        entityManager.persist(administrador);

        // 2. Rubros y tipos de carga
        Rubro rubroForestal = new Rubro("Madera", "Transporte de madera ");
        entityManager.persist(rubroForestal);

        Rubro rubroGranos = new Rubro("Granos", "Transporte de cereales");
        entityManager.persist(rubroGranos);

        TipoCarga tipoCargaMadera = new TipoCarga("Eucaliptus");
        entityManager.persist(tipoCargaMadera);

        TipoCarga tipoCargaGranos = new TipoCarga("Soja");
        entityManager.persist(tipoCargaGranos);

        // 3. Empresa titular y flota de vehículos
        Empresa empresaTransporte = new Empresa(
                101,
                "Transportes del Plata S.A.",
                "TransPlata",
                "Ruta 1 km 28, San Jose"
        );
        empresaTransporte.getCiudadanos().add(ciudadano);
        empresaTransporte.getChoferes().add(chofer);
        empresaTransporte.getChoferes().add(choferJuan);
        chofer.getEmpresas().add(empresaTransporte);
        choferJuan.getEmpresas().add(empresaTransporte);
        empresaTransporte.getRubros().add(rubroForestal);
        empresaTransporte.getRubros().add(rubroGranos);

        // Vehículo 1: Habilitado con ITV y PNC vigentes
        Vehiculo vehiculoHabilitado = new Vehiculo(
                "STP 1234",
                "camion",
                "123",
                9000,
                31000,
                true,
                empresaTransporte
        );
        ITV itvValido = new ITV(LocalDate.now().plusMonths(6), vehiculoHabilitado);
        PNC pncValido = new PNC(LocalDate.now().plusYears(1), vehiculoHabilitado);
        vehiculoHabilitado.getItv().add(itvValido);
        vehiculoHabilitado.getPncs().add(pncValido);

        // Vehículo 2: Inhabilitado con documentos vencidos para fiscalización
        Vehiculo vehiculoInhabilitado = new Vehiculo(
                "ABC 9876",
                "camion",
                "456",
                10000,
                30000,
                false,
                empresaTransporte
        );
        ITV itvVencido = new ITV(LocalDate.now().minusMonths(3), vehiculoInhabilitado);
        PNC pncVencido = new PNC(LocalDate.now().minusMonths(2), vehiculoInhabilitado);
        vehiculoInhabilitado.getItv().add(itvVencido);
        vehiculoInhabilitado.getPncs().add(pncVencido);

        empresaTransporte.getVehiculos().add(vehiculoHabilitado);
        empresaTransporte.getVehiculos().add(vehiculoInhabilitado);
        entityManager.persist(empresaTransporte);

        // 4. Guía #1: En curso con eventos, pesadas e infracción por exceso de carga detectada
        GuiaDeViaje guiaEnCurso = new GuiaDeViaje(
                LocalDate.now(),
                "Montevideo",
                "Rivera",
                "Madera",
                45.0f,
                empresaTransporte,
                vehiculoHabilitado,
                chofer
        );
        guiaEnCurso.setEstado(TipoEstado.EN_CURSO);
        guiaEnCurso.getTiposCarga().add(tipoCargaMadera);

        EventoViaje eventoInicioGuia1 = new EventoViaje(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusHours(4),
                TipoEvento.INICIO,
                -34.9011,
                -56.1645,
                guiaEnCurso
        );
        guiaEnCurso.agregarEvento(eventoInicioGuia1);

        EventoViaje eventoCargaGuia1 = new EventoViaje(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusHours(3),
                TipoEvento.CARGA,
                -34.8500,
                -56.1500,
                guiaEnCurso
        );
        guiaEnCurso.agregarEvento(eventoCargaGuia1);

        EventoViaje eventoIncidenteGuia1 = new EventoViaje(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusHours(1),
                TipoEvento.INCIDENTE,
                -33.2500,
                -56.0200,
                guiaEnCurso
        );
        IncidenteRuta incidenteGuia1 = new IncidenteRuta(
                "pinchadura.jpg",
                "Pinchadura de neumático trasero derecho en Ruta 5 km 98",
                eventoIncidenteGuia1
        );
        eventoIncidenteGuia1.setIncidente(incidenteGuia1);
        guiaEnCurso.agregarEvento(eventoIncidenteGuia1);

        guiaEnCurso.agregarPuntoTracking(new PuntoTracking(-34.9011, -56.1645, LocalDateTime.now().minusHours(4), guiaEnCurso));
        guiaEnCurso.agregarPuntoTracking(new PuntoTracking(-34.6000, -56.1000, LocalDateTime.now().minusHours(3), guiaEnCurso));
        guiaEnCurso.agregarPuntoTracking(new PuntoTracking(-33.8000, -56.0500, LocalDateTime.now().minusHours(2), guiaEnCurso));
        guiaEnCurso.agregarPuntoTracking(new PuntoTracking(-33.2500, -56.0200, LocalDateTime.now().minusHours(1), guiaEnCurso));

        PesadaBalanza pesadaNormalGuia1 = new PesadaBalanza(
                LocalDate.now(),
                LocalTime.now().minusHours(2),
                38500,
                guiaEnCurso
        );
        guiaEnCurso.agregarPesada(pesadaNormalGuia1);

        PesadaBalanza pesadaExcesoGuia1 = new PesadaBalanza(
                LocalDate.now(),
                LocalTime.now().minusHours(1),
                43500,
                guiaEnCurso
        );
        guiaEnCurso.agregarPesada(pesadaExcesoGuia1);

        Infraccion infraccionDetectada = new Infraccion(
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.DETECTADA,
                guiaEnCurso,
                "La pesada  registro 43.500 kg superando el peso maximo permitido de 40.000 kg (tolerancia 500 kg)."
        );
        guiaEnCurso.getInfracciones().add(infraccionDetectada);
        entityManager.persist(guiaEnCurso);

        // 5. Guia #2: Sin iniciar (Carlos Chofer)
        GuiaDeViaje guiaParaFiscalizar = new GuiaDeViaje(
                LocalDate.now(),
                "Nueva Palmira",
                "Montevideo",
                "Granos",
                32.0f,
                empresaTransporte,
                vehiculoInhabilitado,
                chofer
        );
        guiaParaFiscalizar.setEstado(TipoEstado.SIN_INICIAR);
        guiaParaFiscalizar.getTiposCarga().add(tipoCargaGranos);

        entityManager.persist(guiaParaFiscalizar);

        // 6. Guía #3: Finalizada con infracción confirmada, descargo y registro de auditoría
        GuiaDeViaje guiaFinalizada = new GuiaDeViaje(
                LocalDate.now().minusDays(3),
                "Salto",
                "Montevideo",
                "Granos",
                38.0f,
                empresaTransporte,
                vehiculoHabilitado,
                chofer
        );
        guiaFinalizada.setEstado(TipoEstado.FINALIZADO);
        guiaFinalizada.getTiposCarga().add(tipoCargaGranos);

        EventoViaje eventoInicioGuia3 = new EventoViaje(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusDays(3).withHour(8).withMinute(0),
                TipoEvento.INICIO,
                -31.3833,
                -57.9667,
                guiaFinalizada
        );
        guiaFinalizada.agregarEvento(eventoInicioGuia3);

        EventoViaje eventoFinGuia3 = new EventoViaje(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusDays(2).withHour(18).withMinute(0),
                TipoEvento.FIN,
                -34.9011,
                -56.1645,
                guiaFinalizada
        );
        guiaFinalizada.agregarEvento(eventoFinGuia3);

        PesadaBalanza pesadaGuia3 = new PesadaBalanza(
                LocalDate.now().minusDays(3),
                LocalTime.of(14, 30),
                44200,
                guiaFinalizada
        );
        guiaFinalizada.agregarPesada(pesadaGuia3);

        Infraccion infraccionConfirmada = new Infraccion(
                LocalDate.now().minusDays(3),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.CONFIRMADA,
                guiaFinalizada,
                "La pesada registrada en balanza fue de 44.200 kg excediendo el límite legal permitido de 40.000 kg."
        );
        infraccionConfirmada.setResolucion("CONFIRMADA");
        infraccionConfirmada.setFechaResolucion(LocalDate.now().minusDays(1));
        infraccionConfirmada.setFundamento("Se desestima el descargo presentado.");

        Descargo descargoGuia3 = new Descargo(
                LocalDate.now().minusDays(2),
                "Se solicita la anulacion. Algo debe estar mal",
                infraccionConfirmada
        );
        infraccionConfirmada.setDescargo(descargoGuia3);

        RegistroAuditoria auditoriaGuia3 = new RegistroAuditoria(
                "CONFIRMADA",
                LocalDate.now().minusDays(1),
                "funcionario@cargauy.gub.uy",
                "RESOLVER_INFRACCION",
                "Se desestima el descargo presentado. La balanza de pesaje contaba con verificación ."
        );
        infraccionConfirmada.setRegistroAuditoria(auditoriaGuia3);

        guiaFinalizada.getInfracciones().add(infraccionConfirmada);
        entityManager.persist(guiaFinalizada);

        // 7. Guia #4: Sin empezar para Juan Chofer (CI 55667788)
        GuiaDeViaje guiaSinIniciarJuan = new GuiaDeViaje(
                LocalDate.now(),
                "Colonia",
                "Montevideo",
                "Madera",
                28.0f,
                empresaTransporte,
                vehiculoHabilitado,
                choferJuan
        );
        guiaSinIniciarJuan.setEstado(TipoEstado.SIN_INICIAR);
        guiaSinIniciarJuan.getTiposCarga().add(tipoCargaMadera);
        entityManager.persist(guiaSinIniciarJuan);
    }

    /**
     * Asegura la existencia de la configuración de reglas de fiscalización con umbrales por defecto.
     */
    private void inicializarReglasFiscalizacion() {
        ReglaFiscalizacion reglaExistente = entityManager.find(ReglaFiscalizacion.class, 1);
        if (reglaExistente == null) {
            ReglaFiscalizacion reglaInicial = new ReglaFiscalizacion(500, 5);
            entityManager.persist(reglaInicial);
        }
    }
}
