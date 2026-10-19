package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
@Stateless @LocalBean
public class EvaluadorToleranciasService {
    @EJB private ReglasLecturaService reglas;
    // Servicio interno del motor: el llamador ya identifica y valida vehículo/viaje.
    public boolean excesoCarga(double medidoKg,double pesoPropioKg,double capacidadKg){
        if(!Double.isFinite(medidoKg)||!Double.isFinite(pesoPropioKg)||!Double.isFinite(capacidadKg)
                ||medidoKg<0||pesoPropioKg<0||capacidadKg<0)throw new ErrorNegocio("Pesos inválidos");
        var r=reglas.actuales();
        return medidoKg>pesoPropioKg+capacidadKg+r.cargaKg();
    }
    public boolean superaDesvio(double distanciaKm){
        if(!Double.isFinite(distanciaKm)||distanciaKm<0)throw new ErrorNegocio("Distancia inválida");
        return distanciaKm>reglas.actuales().desvioKm();
    }
}
