<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO" %>
<%@ page import="uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO" %>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CargaUY - Detalle y Tracking de Viaje</title>
    <style>
        :root {
            --primary: #1e3a8a;
            --primary-light: #3b82f6;
            --bg: #f8fafc;
            --card-bg: #ffffff;
            --text: #1e293b;
            --text-muted: #64748b;
            --border: #e2e8f0;
            --danger: #ef4444;
            --warning: #f59e0b;
            --success: #10b981;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            margin: 0;
            padding: 24px;
        }
        .container {
            max-width: 1000px;
            margin: 0 auto;
        }
        header {
            margin-bottom: 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 2px solid var(--border);
            padding-bottom: 16px;
        }
        h1 { margin: 0; color: var(--primary); font-size: 1.8rem; }
        .card {
            background: var(--card-bg);
            border-radius: 8px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            padding: 20px;
            margin-bottom: 24px;
            border: 1px solid var(--border);
        }
        .form-inline {
            display: flex;
            gap: 12px;
            align-items: center;
        }
        input[type="number"] {
            padding: 10px 14px;
            border: 1px solid var(--border);
            border-radius: 6px;
            font-size: 1rem;
            width: 220px;
        }
        button {
            background-color: var(--primary);
            color: white;
            border: none;
            padding: 10px 18px;
            font-size: 1rem;
            font-weight: 500;
            border-radius: 6px;
            cursor: pointer;
            transition: background-color 0.2s;
        }
        button:hover { background-color: var(--primary-light); }
        .alert {
            padding: 12px 16px;
            border-radius: 6px;
            margin-bottom: 20px;
            font-weight: 500;
        }
        .alert-error {
            background-color: #fee2e2;
            color: var(--danger);
            border: 1px solid #fca5a5;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 12px;
        }
        th, td {
            text-align: left;
            padding: 12px;
            border-bottom: 1px solid var(--border);
            font-size: 0.95rem;
        }
        th {
            background-color: #f1f5f9;
            color: var(--text-muted);
            text-transform: uppercase;
            font-size: 0.8rem;
            letter-spacing: 0.05em;
        }
        .badge {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 9999px;
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
        }
        .badge-inicio { background: #dbeafe; color: #1e40af; }
        .badge-carga { background: #dcfce7; color: #166534; }
        .badge-descarga { background: #fef3c7; color: #92400e; }
        .badge-incidente { background: #fee2e2; color: #991b1b; }
        .badge-otro { background: #f1f5f9; color: #475569; }
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 20px;
        }
        .stat-card {
            background: #f8fafc;
            border: 1px solid var(--border);
            border-radius: 6px;
            padding: 12px 16px;
        }
        .stat-val { font-size: 1.4rem; font-weight: 700; color: var(--primary); }
        .stat-lbl { font-size: 0.85rem; color: var(--text-muted); }
        .muted-text { color: var(--text-muted); font-style: italic; }
    </style>
</head>
<body>

<div class="container">
    <header>
        <div>
            <h1>CargaUY &bull; Detalle y Tracking de Viaje</h1>
            <p style="margin: 4px 0 0 0; color: var(--text-muted);">Módulo de seguimiento en tiempo real y pesaje en balanza (P3)</p>
        </div>
        <a href="index.jsp" style="color: var(--primary-light); text-decoration: none; font-size: 0.9rem;">&larr; Volver al Inicio</a>
    </header>

    <!-- Formulario de búsqueda -->
    <div class="card">
        <form action="DetalleViajeServlet" method="get" class="form-inline">
            <label for="guiaId" style="font-weight: 600;">ID de Guía de Viaje:</label>
            <input type="number" id="guiaId" name="guiaId" placeholder="Ej. 1" value="<%= request.getAttribute("guiaId") != null ? request.getAttribute("guiaId") : "" %>" required>
            <button type="submit">Consultar Tracking</button>
        </form>
    </div>

    <!-- Alertas -->
    <% String error = (String) request.getAttribute("error"); %>
    <% if (error != null) { %>
        <div class="alert alert-error">
            <%= error %>
        </div>
    <% } %>

    <%
        Long guiaId = (Long) request.getAttribute("guiaId");
        @SuppressWarnings("unchecked")
        List<EventoViajeDTO> eventos = (List<EventoViajeDTO>) request.getAttribute("eventos");
        @SuppressWarnings("unchecked")
        List<PesadaBalanzaDTO> pesadas = (List<PesadaBalanzaDTO>) request.getAttribute("pesadas");
    %>

    <% if (guiaId != null) { %>
        <!-- Resumen numérico -->
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-lbl">Guía Consultada</div>
                <div class="stat-val">#<%= guiaId %></div>
            </div>
            <div class="stat-card">
                <div class="stat-lbl">Eventos Registrados</div>
                <div class="stat-val"><%= eventos != null ? eventos.size() : 0 %></div>
            </div>
            <div class="stat-card">
                <div class="stat-lbl">Pesajes en Balanza</div>
                <div class="stat-val"><%= pesadas != null ? pesadas.size() : 0 %></div>
            </div>
        </div>

        <!-- Sección 1: Línea cronológica de eventos (AC013) -->
        <div class="card">
            <h2 style="margin-top: 0; font-size: 1.25rem; color: var(--primary);">Línea de Tiempo de Eventos (Cronológico ASC)</h2>
            <% if (eventos != null && !eventos.isEmpty()) { %>
                <table>
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Tipo</th>
                            <th>Fecha y Hora</th>
                            <th>Coordenadas</th>
                            <th>UUID</th>
                            <th>Detalle / Incidente</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% for (int i = 0; i < eventos.size(); i++) {
                            EventoViajeDTO ev = eventos.get(i);
                            String badgeClass = "badge-otro";
                            if (ev.tipo() != null) {
                                switch (ev.tipo()) {
                                    case INICIO -> badgeClass = "badge-inicio";
                                    case CARGA -> badgeClass = "badge-carga";
                                    case DESCARGA -> badgeClass = "badge-descarga";
                                    case INCIDENTE -> badgeClass = "badge-incidente";
                                    default -> badgeClass = "badge-otro";
                                }
                            }
                        %>
                            <tr>
                                <td><%= (i + 1) %></td>
                                <td><span class="badge <%= badgeClass %>"><%= ev.tipo() %></span></td>
                                <td><%= ev.tiempo() != null ? ev.tiempo() : "-" %></td>
                                <td><%= ev.latitud() %>, <%= ev.longitud() %></td>
                                <td style="font-family: monospace; font-size: 0.85rem;"><%= ev.uuid() %></td>
                                <td>
                                    <% if (ev.incidente() != null) { %>
                                        <strong style="color: var(--danger);"><%= ev.incidente().descripcion() %></strong>
                                        <% if (ev.incidente().foto() != null && !ev.incidente().foto().isBlank()) { %>
                                            <br><small class="muted-text">Foto: <%= ev.incidente().foto() %></small>
                                        <% } %>
                                    <% } else { %>
                                        <span class="muted-text">-</span>
                                    <% } %>
                                </td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            <% } else { %>
                <p class="muted-text">No se registran eventos asociados a esta guía de viaje aún.</p>
            <% } %>
        </div>

        <!-- Sección 2: Control de Pesaje en Balanzas -->
        <div class="card">
            <h2 style="margin-top: 0; font-size: 1.25rem; color: var(--primary);">Control de Balanzas Periféricas</h2>
            <% if (pesadas != null && !pesadas.isEmpty()) { %>
                <table>
                    <thead>
                        <tr>
                            <th>ID Pesada</th>
                            <th>Fecha</th>
                            <th>Hora</th>
                            <th>Peso Registrado</th>
                            <th>Matrícula Vehículo</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% for (PesadaBalanzaDTO p : pesadas) { %>
                            <tr>
                                <td><strong>#<%= p.idPesada() != null ? p.idPesada() : "-" %></strong></td>
                                <td><%= p.fecha() != null ? p.fecha() : "-" %></td>
                                <td><%= p.hora() != null ? p.hora() : "-" %></td>
                                <td><%= String.format("%,d kg", p.pesoRegistrado()) %></td>
                                <td><%= p.matricula() != null ? p.matricula() : "-" %></td>
                            </tr>
                        <% } %>
                    </tbody>
                </table>
            <% } else { %>
                <p class="muted-text">No se registran pesadas de balanza para esta guía de viaje.</p>
            <% } %>
        </div>
    <% } %>

</div>

</body>
</html>
