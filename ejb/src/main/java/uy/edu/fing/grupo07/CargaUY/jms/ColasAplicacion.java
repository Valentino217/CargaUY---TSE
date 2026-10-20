package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.ejb.Singleton;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSDestinationDefinitions;

/** Las colas de P3 se despliegan junto al EAR, sin configuración manual por integrante. */
@Singleton
@JMSDestinationDefinitions({
    @JMSDestinationDefinition(name="java:/jms/queue/TrackingQueue",interfaceName="jakarta.jms.Queue",destinationName="TrackingQueue"),
    @JMSDestinationDefinition(name="java:/jms/queue/FiscalizacionQueue",interfaceName="jakarta.jms.Queue",destinationName="FiscalizacionQueue")
})
public class ColasAplicacion {}
