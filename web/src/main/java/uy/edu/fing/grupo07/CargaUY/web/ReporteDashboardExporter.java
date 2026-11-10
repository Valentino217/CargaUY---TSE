package uy.edu.fing.grupo07.CargaUY.web;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Genera los reportes exportables del dashboard de la Autoridad (CSV y PDF) con los
 * indicadores agregados y los filtros aplicados.
 */
public final class ReporteDashboardExporter {

    private static final Color AZUL = new Color(0x0f, 0x29, 0x42);
    private static final Color AZUL_CLARO = new Color(0xe0, 0xf2, 0xfe);
    private static final Color GRIS = new Color(0xf8, 0xfa, 0xfc);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReporteDashboardExporter() {
    }

    private static Map<String, String> indicadores(DashboardResumenDTO r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Empresas habilitadas", String.valueOf(r.totalEmpresasHabilitadas()));
        m.put("Vehiculos habilitados", String.valueOf(r.totalVehiculosHabilitados()));
        m.put("Pesajes registrados", String.valueOf(r.totalPesadas()));
        m.put("Tasa de infracciones confirmadas / detectadas", String.format(Locale.US, "%.1f%%", r.tasaConfirmadasSobreDetectadas()));
        return m;
    }

    private static <K, V> Map<String, String> texto(Map<K, V> origen, boolean decimales) {
        Map<String, String> m = new LinkedHashMap<>();
        if (origen != null) {
            origen.forEach((k, v) -> m.put(String.valueOf(k),
                    decimales && v instanceof Number n ? String.format(Locale.US, "%.0f", n.doubleValue()) : String.valueOf(v)));
        }
        return m;
    }

    private static String csv(String s) {
        String v = s == null ? "" : s;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static void seccionCsv(StringBuilder sb, String titulo, String colClave, String colValor, Map<String, String> datos) {
        sb.append(csv(titulo)).append('\n');
        sb.append(csv(colClave)).append(';').append(csv(colValor)).append('\n');
        if (datos.isEmpty()) {
            sb.append(csv("Sin datos")).append(';').append(csv("-")).append('\n');
        }
        datos.forEach((k, v) -> sb.append(csv(k)).append(';').append(csv(v)).append('\n'));
        sb.append('\n');
    }

    public static byte[] generarCsv(DashboardResumenDTO r, Map<String, String> filtros) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(csv("Reporte Dashboard CargaUY")).append('\n');
        sb.append(csv("Generado")).append(';').append(csv(LocalDateTime.now().format(FMT))).append("\n\n");
        seccionCsv(sb, "Filtros aplicados", "Filtro", "Valor", filtros);
        seccionCsv(sb, "Indicadores generales", "Indicador", "Valor", indicadores(r));
        seccionCsv(sb, "Viajes realizados por periodo", "Periodo", "Viajes", texto(r.viajesPorPeriodo(), false));
        seccionCsv(sb, "Volumen de carga por rubro", "Rubro", "Volumen (kg)", texto(r.volumenPorRubro(), true));
        seccionCsv(sb, "Infracciones detectadas por tipo", "Tipo", "Cantidad", texto(r.infraccionesPorTipo(), false));
        seccionCsv(sb, "Infracciones por estado", "Estado", "Cantidad", texto(r.infraccionesPorEstado(), false));
        seccionCsv(sb, "Guias por estado", "Estado", "Cantidad", texto(r.guiasPorEstado(), false));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void tabla(Document doc, String titulo, String colClave, String colValor, Map<String, String> datos) throws Exception {
        Paragraph p = new Paragraph(titulo, new Font(Font.HELVETICA, 12, Font.BOLD, AZUL));
        p.setSpacingBefore(14);
        p.setSpacingAfter(5);
        doc.add(p);
        PdfPTable t = new PdfPTable(new float[]{3f, 1.2f});
        t.setWidthPercentage(100);
        Font fh = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        for (String h : new String[]{colClave, colValor}) {
            PdfPCell c = new PdfPCell(new Phrase(h, fh));
            c.setBackgroundColor(AZUL);
            c.setPadding(6);
            c.setBorderColor(AZUL);
            if (h.equals(colValor)) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(c);
        }
        Font fb = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x1e, 0x29, 0x3b));
        if (datos.isEmpty()) {
            datos = Map.of("Sin datos para los filtros seleccionados", "-");
        }
        int i = 0;
        for (Map.Entry<String, String> e : datos.entrySet()) {
            Color bg = (i++ % 2 == 0) ? Color.WHITE : GRIS;
            PdfPCell k = new PdfPCell(new Phrase(e.getKey(), fb));
            PdfPCell v = new PdfPCell(new Phrase(e.getValue(), fb));
            v.setHorizontalAlignment(Element.ALIGN_RIGHT);
            for (PdfPCell c : new PdfPCell[]{k, v}) {
                c.setBackgroundColor(bg);
                c.setPadding(5);
                c.setBorderColor(new Color(0xe2, 0xe8, 0xf0));
                t.addCell(c);
            }
        }
        doc.add(t);
    }

    public static byte[] generarPdf(DashboardResumenDTO r, Map<String, String> filtros) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell hc = new PdfPCell();
            hc.setBackgroundColor(AZUL);
            hc.setPadding(14);
            hc.setBorder(Rectangle.NO_BORDER);
            hc.addElement(new Paragraph("CargaUY - Reporte de Fiscalizacion", new Font(Font.HELVETICA, 17, Font.BOLD, Color.WHITE)));
            hc.addElement(new Paragraph("Dashboard de Autoridades  |  Generado el " + LocalDateTime.now().format(FMT),
                    new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0xcb, 0xd5, 0xe1))));
            header.addCell(hc);
            doc.add(header);

            Paragraph pf = new Paragraph("Filtros aplicados", new Font(Font.HELVETICA, 11, Font.BOLD, AZUL));
            pf.setSpacingBefore(12);
            doc.add(pf);
            StringBuilder fs = new StringBuilder();
            filtros.forEach((k, v) -> fs.append(k).append(": ").append(v).append("     "));
            doc.add(new Paragraph(fs.toString().trim(), new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x47, 0x55, 0x69))));

            // Tarjetas de indicadores
            PdfPTable kpis = new PdfPTable(4);
            kpis.setWidthPercentage(100);
            kpis.setSpacingBefore(12);
            for (Map.Entry<String, String> e : indicadores(r).entrySet()) {
                PdfPCell c = new PdfPCell();
                c.setBackgroundColor(AZUL_CLARO);
                c.setPadding(9);
                c.setBorderColor(Color.WHITE);
                c.setBorderWidth(3);
                c.addElement(new Paragraph(e.getValue(), new Font(Font.HELVETICA, 17, Font.BOLD, AZUL)));
                c.addElement(new Paragraph(e.getKey(), new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(0x47, 0x55, 0x69))));
                kpis.addCell(c);
            }
            doc.add(kpis);

            tabla(doc, "Viajes realizados por periodo", "Periodo", "Viajes", texto(r.viajesPorPeriodo(), false));
            tabla(doc, "Volumen de carga transportado por rubro", "Rubro", "Volumen (kg)", texto(r.volumenPorRubro(), true));
            tabla(doc, "Infracciones detectadas por tipo", "Tipo", "Cantidad", texto(r.infraccionesPorTipo(), false));
            tabla(doc, "Infracciones por estado", "Estado", "Cantidad", texto(r.infraccionesPorEstado(), false));
            tabla(doc, "Guias de viaje por estado", "Estado", "Cantidad", texto(r.guiasPorEstado(), false));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte", e);
        }
    }
}
