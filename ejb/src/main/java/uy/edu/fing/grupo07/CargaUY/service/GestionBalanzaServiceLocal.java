package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;

import java.util.List;

/**
 * Interfaz local para el servicio de ingesta y gestión de pesadas de balanza.
 */
@Local
public interface GestionBalanzaServiceLocal {

    /**
     * Registra una pesada asociándola a la guía de viaje activa del vehículo.
     */
    PesadaBalanzaDTO registrarPesada(PesadaBalanzaDTO dto);

    /**
     * Consulta el mock de balanza periférica y registra automáticamente la pesada resultante.
     */
    PesadaBalanzaDTO registrarPesadaDesdeMock();

    /**
     * Retorna todas las pesadas asociadas a una guía de viaje ordenadas cronológicamente.
     */
    List<PesadaBalanzaDTO> listarPesadasPorGuia(Long guiaId);
}
