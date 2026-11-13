package uy.edu.fing.grupo07.CargaUY.web;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.faces.context.FacesContext;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Color AZUL_ACENTO = new Color(0x02, 0x84, 0xc7);
    private static final Color AZUL_CLARO = new Color(0xf0, 0xf9, 0xff);
    private static final Color GRIS = new Color(0xf8, 0xfa, 0xfc);
    private static final Color GRIS_FILTROS = new Color(0xf1, 0xf5, 0xf9);
    private static final Color BORDE = new Color(0xe2, 0xe8, 0xf0);
    private static final Color TEXTO = new Color(0x1e, 0x29, 0x3b);
    private static final Color TEXTO_SUAVE = new Color(0x47, 0x55, 0x69);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String RUTA_LOGO_WEB = "/img/logo-cargauy.png";

    private ReporteDashboardExporter() {
    }

    private static Map<String, String> indicadores(DashboardResumenDTO r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Empresas habilitadas", String.valueOf(r.totalEmpresasHabilitadas()));
        m.put("Vehículos habilitados", String.valueOf(r.totalVehiculosHabilitados()));
        m.put("Pesajes registrados", String.valueOf(r.totalPesadas()));
        m.put("Confirmadas / detectadas", String.format(Locale.US, "%.1f%%", r.tasaConfirmadasSobreDetectadas()));
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
        seccionCsv(sb, "Viajes realizados por período", "Período", "Viajes", texto(r.viajesPorPeriodo(), false));
        seccionCsv(sb, "Volumen de carga por rubro", "Rubro", "Volumen (kg)", texto(r.volumenPorRubro(), true));
        seccionCsv(sb, "Infracciones detectadas por tipo", "Tipo", "Cantidad", texto(r.infraccionesPorTipo(), false));
        seccionCsv(sb, "Infracciones por estado", "Estado", "Cantidad", texto(r.infraccionesPorEstado(), false));
        seccionCsv(sb, "Guías por estado", "Estado", "Cantidad", texto(r.guiasPorEstado(), false));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Image cargarLogoCamion() {
        try {
            try {
                FacesContext fc = FacesContext.getCurrentInstance();
                if (fc != null && fc.getExternalContext() != null) {
                    try (InputStream in = fc.getExternalContext().getResourceAsStream(RUTA_LOGO_WEB)) {
                        if (in != null) {
                            return Image.getInstance(in.readAllBytes());
                        }
                    }
                }
            } catch (Throwable ignored) {
                // En tests unitarios sin implementación JSF en classpath, continuar con fallback local/classpath.
            }
            try (InputStream in = ReporteDashboardExporter.class.getResourceAsStream(RUTA_LOGO_WEB)) {
                if (in != null) {
                    return Image.getInstance(in.readAllBytes());
                }
            }
            Path local = Path.of("src/main/webapp/img/logo-cargauy.png");
            if (Files.isRegularFile(local)) {
                return Image.getInstance(Files.readAllBytes(local));
            }
        } catch (Exception ignored) {
            // Si no se encuentra o no se puede decodificar la imagen, el reporte se genera igual sin logo.
        }
        return null;
    }

    private static PdfPTable construirEncabezado(String fechaGenerado) {
        Image logo = cargarLogoCamion();
        PdfPTable header = logo != null ? new PdfPTable(new float[]{1.05f, 4.95f}) : new PdfPTable(1);
        header.setWidthPercentage(100);
        header.setSpacingAfter(12);

        if (logo != null) {
            logo.scaleToFit(54f, 54f);
            PdfPCell lc = new PdfPCell(logo, false);
            lc.setBackgroundColor(AZUL);
            lc.setBorder(Rectangle.NO_BORDER);
            lc.setPaddingTop(10);
            lc.setPaddingBottom(10);
            lc.setPaddingLeft(12);
            lc.setPaddingRight(4);
            lc.setHorizontalAlignment(Element.ALIGN_CENTER);
            lc.setVerticalAlignment(Element.ALIGN_MIDDLE);
            header.addCell(lc);
        }

        PdfPCell hc = new PdfPCell();
        hc.setBackgroundColor(AZUL);
        hc.setBorder(Rectangle.NO_BORDER);
        hc.setPaddingTop(14);
        hc.setPaddingBottom(14);
        hc.setPaddingLeft(logo != null ? 8 : 16);
        hc.setPaddingRight(16);
        hc.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph titulo = new Paragraph("CargaUY - Reporte",
                new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE));
        titulo.setSpacingAfter(3);
        hc.addElement(titulo);

        Paragraph subtitulo = new Paragraph("Generado el " + fechaGenerado,
                new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0xcb, 0xd5, 0xe1)));
        hc.addElement(subtitulo);
        header.addCell(hc);
        return header;
    }

    private static PdfPTable construirFiltros(Map<String, String> filtros) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(12);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(GRIS_FILTROS);
        cell.setBorderColor(BORDE);
        cell.setBorderWidth(1f);
        cell.setPaddingTop(8);
        cell.setPaddingBottom(8);
        cell.setPaddingLeft(12);
        cell.setPaddingRight(12);

        Font fTitulo = new Font(Font.HELVETICA, 9, Font.BOLD, AZUL);
        Font fClave = new Font(Font.HELVETICA, 8.5f, Font.BOLD, TEXTO_SUAVE);
        Font fValor = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXTO);

        Paragraph p = new Paragraph();
        p.setLeading(13);
        p.add(new Chunk("Filtros aplicados:  ", fTitulo));
        if (filtros == null || filtros.isEmpty()) {
            p.add(new Chunk("Sin filtros activos", fValor));
        } else {
            int idx = 0;
            for (Map.Entry<String, String> e : filtros.entrySet()) {
                if (idx++ > 0) {
                    p.add(new Chunk("   ·   ", fValor));
                }
                p.add(new Chunk(e.getKey() + ": ", fClave));
                p.add(new Chunk(e.getValue(), fValor));
            }
        }
        cell.addElement(p);
        box.addCell(cell);
        return box;
    }

    private static PdfPTable construirKpis(DashboardResumenDTO r) {
        PdfPTable kpis = new PdfPTable(4);
        kpis.setWidthPercentage(100);
        kpis.setSpacingAfter(6);

        Font fNum = new Font(Font.HELVETICA, 17, Font.BOLD, AZUL);
        Font fLbl = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXTO_SUAVE);

        for (Map.Entry<String, String> e : indicadores(r).entrySet()) {
            PdfPCell c = new PdfPCell();
            c.setBackgroundColor(AZUL_CLARO);
            c.setPaddingTop(10);
            c.setPaddingBottom(10);
            c.setPaddingLeft(8);
            c.setPaddingRight(8);
            c.setBorderColor(Color.WHITE);
            c.setBorderWidth(3f);
            c.setBorderColorTop(AZUL_ACENTO);
            c.setBorderWidthTop(2.5f);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);

            Paragraph num = new Paragraph(e.getValue(), fNum);
            num.setAlignment(Element.ALIGN_CENTER);
            num.setSpacingAfter(3);
            Paragraph lbl = new Paragraph(e.getKey(), fLbl);
            lbl.setAlignment(Element.ALIGN_CENTER);

            c.addElement(num);
            c.addElement(lbl);
            kpis.addCell(c);
        }
        return kpis;
    }

    private static void tabla(Document doc, String titulo, String colClave, String colValor, Map<String, String> datos) throws Exception {
        Paragraph p = new Paragraph(titulo, new Font(Font.HELVETICA, 11.5f, Font.BOLD, AZUL));
        p.setSpacingBefore(12);
        p.setSpacingAfter(5);
        doc.add(p);

        PdfPTable t = new PdfPTable(new float[]{3.4f, 1.1f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setKeepTogether(true);
        t.setSpacingAfter(4);

        Font fh = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        for (String h : new String[]{colClave, colValor}) {
            PdfPCell c = new PdfPCell(new Phrase(h, fh));
            c.setBackgroundColor(AZUL);
            c.setPaddingTop(6);
            c.setPaddingBottom(6);
            c.setPaddingLeft(8);
            c.setPaddingRight(8);
            c.setBorderColor(AZUL);
            if (h.equals(colValor)) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(c);
        }

        Font fb = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXTO);
        Font fEmpty = new Font(Font.HELVETICA, 9, Font.ITALIC, TEXTO_SUAVE);
        Map<String, String> filas = datos.isEmpty()
                ? Map.of("Sin datos para los filtros seleccionados", "-")
                : datos;
        Font fontFila = datos.isEmpty() ? fEmpty : fb;

        int i = 0;
        for (Map.Entry<String, String> e : filas.entrySet()) {
            Color bg = (i++ % 2 == 0) ? Color.WHITE : GRIS;
            PdfPCell k = new PdfPCell(new Phrase(e.getKey(), fontFila));
            PdfPCell v = new PdfPCell(new Phrase(e.getValue(), fontFila));
            v.setHorizontalAlignment(Element.ALIGN_RIGHT);
            for (PdfPCell c : new PdfPCell[]{k, v}) {
                c.setBackgroundColor(bg);
                c.setPaddingTop(5);
                c.setPaddingBottom(5);
                c.setPaddingLeft(8);
                c.setPaddingRight(8);
                c.setBorderColor(BORDE);
                t.addCell(c);
            }
        }
        doc.add(t);
    }

    public static byte[] generarPdf(DashboardResumenDTO r, Map<String, String> filtros) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 44);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            String fechaGenerado = LocalDateTime.now().format(FMT);
            writer.setPageEvent(new PdfPageEventHelper() {
                private final Font fPie = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXTO_SUAVE);

                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    PdfContentByte cb = w.getDirectContent();
                    Phrase pie = new Phrase("CargaUY · Sistema Nacional de Transporte de Carga   |   Página " + w.getPageNumber(), fPie);
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, pie,
                            (d.left() + d.right()) / 2f, d.bottom() - 20f, 0);
                }
            });

            doc.open();
            doc.add(construirEncabezado(fechaGenerado));
            doc.add(construirFiltros(filtros));
            doc.add(construirKpis(r));

            tabla(doc, "Viajes realizados por período", "Período", "Viajes", texto(r.viajesPorPeriodo(), false));
            tabla(doc, "Volumen de carga transportado por rubro", "Rubro", "Volumen (kg)", texto(r.volumenPorRubro(), true));
            tabla(doc, "Infracciones detectadas por tipo", "Tipo", "Cantidad", texto(r.infraccionesPorTipo(), false));
            tabla(doc, "Infracciones por estado", "Estado", "Cantidad", texto(r.infraccionesPorEstado(), false));
            tabla(doc, "Guías de viaje por estado", "Estado", "Cantidad", texto(r.guiasPorEstado(), false));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte", e);
        }
    }
}
