package uy.edu.fing.grupo07.CargaUY.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias - ReporteDashboardExporter")
class ReporteDashboardExporterTest {

    private DashboardResumenDTO resumenEjemplo() {
        Map<TipoEstado, Long> guias = new EnumMap<>(TipoEstado.class);
        guias.put(TipoEstado.EN_CURSO, 4L);

        Map<EstadoInfraccion, Long> infEstado = new EnumMap<>(EstadoInfraccion.class);
        infEstado.put(EstadoInfraccion.DETECTADA, 2L);

        Map<TipoInfraccion, Long> infTipo = new EnumMap<>(TipoInfraccion.class);
        infTipo.put(TipoInfraccion.EXCESO_DE_CARGA, 2L);

        return new DashboardResumenDTO(guias, infEstado, infTipo, 15L);
    }

    @Test
    @DisplayName("generarPdf produce un PDF válido e incrusta el logo de CargaUY")
    void generarPdfConLogoYTablas() {
        Map<String, String> filtros = new LinkedHashMap<>();
        filtros.put("Desde", "2026-11-01");
        filtros.put("Hasta", "2026-11-12");
        filtros.put("Rubro", "Forestal");

        byte[] pdf = ReporteDashboardExporter.generarPdf(resumenEjemplo(), filtros);
        String header = new String(pdf, 0, Math.min(8, pdf.length), StandardCharsets.US_ASCII);

        assertTrue(header.startsWith("%PDF-"), "Debe iniciar con cabecera PDF");
        // El logo PNG (66 KB) incrustado hace que el PDF supere ampliamente los 10 KB de un reporte solo texto.
        assertTrue(pdf.length > 10_000, "El PDF debe incluir el logo incrustado");
    }

    @Test
    @DisplayName("generarCsv genera BOM UTF-8 y sanitiza fórmulas")
    void generarCsvSanitizado() {
        Map<String, String> filtros = Map.of("Empresa", "=SUM(A1:A2)");
        byte[] csv = ReporteDashboardExporter.generarCsv(resumenEjemplo(), filtros);
        String contenido = new String(csv, StandardCharsets.UTF_8);

        assertTrue(contenido.startsWith("\uFEFF"));
        assertTrue(contenido.contains("\"'=SUM(A1:A2)\""));
    }
}
