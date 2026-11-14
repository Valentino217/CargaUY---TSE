(function (global) {
    'use strict';

    var URUGUAY_CENTER = [-32.8, -56.0];
    var URUGUAY_ZOOM = 7;

    function obtenerEstadoMapa() {
        if (!global.__cargaUyMapaState) {
            global.__cargaUyMapaState = {
                map: null,
                layerGroup: null,
                lastGuiaId: null,
                lastBounds: null
            };
        }
        return global.__cargaUyMapaState;
    }

    function escaparHtml(valor) {
        if (valor === null || valor === undefined) {
            return '';
        }
        return String(valor)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatearFechaHora(iso) {
        if (!iso) {
            return '-';
        }
        var limpio = String(iso).replace('T', ' ');
        var punto = limpio.indexOf('.');
        if (punto > 0) {
            limpio = limpio.substring(0, punto);
        }
        return limpio;
    }

    function leerDatosMapaJson() {
        var el = document.getElementById('monitoreoForm:datosMapaJson')
            || document.querySelector('[id$="datosMapaJson"]');
        if (!el) {
            return null;
        }
        var raw = (el.textContent || el.innerText || '').trim();
        if (!raw) {
            return null;
        }
        try {
            return JSON.parse(raw);
        } catch (err) {
            if (global.console && console.warn) {
                console.warn('No se pudo parsear datosMapaJson:', err);
            }
            return null;
        }
    }

    function inicializarMapaSiNecesario(canvasEl, state) {
        if (state.map && state.map.getContainer() !== canvasEl) {
            try {
                state.map.remove();
            } catch (ignored) {
                // Contenedor anterior ya removido por JSF
            }
            state.map = null;
            state.layerGroup = null;
            state.lastGuiaId = null;
        }

        if (!state.map) {
            state.map = global.L.map(canvasEl, {
                center: URUGUAY_CENTER,
                zoom: URUGUAY_ZOOM,
                scrollWheelZoom: true
            });

            global.L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
            }).addTo(state.map);

            global.L.control.scale({ metric: true, imperial: false }).addTo(state.map);
            state.layerGroup = global.L.layerGroup().addTo(state.map);
        }

        return state.layerGroup;
    }

    function determinarColorEvento(ev) {
        if (ev.desviado || ev.tipo === 'INCIDENTE') {
            return '#dc2626';
        }
        if (ev.tipo === 'INICIO') {
            return '#16a34a';
        }
        if (ev.tipo === 'FIN') {
            return '#0f2942';
        }
        if (ev.tipo && (ev.tipo.indexOf('CARGA') >= 0 || ev.tipo.indexOf('DESCARGA') >= 0)) {
            return '#d97706';
        }
        return '#0284c7';
    }

    function construirPopupEvento(ev, indice, totalEventos) {
        var esUltimo = indice === totalEventos - 1;
        var titulo = '#' + (indice + 1) + ' — ' + escaparHtml(ev.tipo || 'POSICION');
        var badgeUltimo = esUltimo
            ? '<span class="cuy-map-popup-badge-latest">ÚLTIMA POSICIÓN</span>'
            : '';

        var lineas = [];
        lineas.push('<div class="cuy-map-popup">');
        lineas.push('<div class="cuy-map-popup-header"><strong>' + titulo + '</strong>' + badgeUltimo + '</div>');
        lineas.push('<div><strong>Hora:</strong> ' + escaparHtml(formatearFechaHora(ev.tiempo)) + '</div>');
        lineas.push('<div><strong>Coord.:</strong> <span class="cuy-mono">' + Number(ev.lat).toFixed(5) + ', ' + Number(ev.lng).toFixed(5) + '</span></div>');

        if (ev.desvioKm !== null && ev.desvioKm !== undefined) {
            var claseDesvio = ev.desviado ? 'cuy-map-popup-alert' : 'cuy-map-popup-ok';
            var textoDesvio = Number(ev.desvioKm).toFixed(2) + ' km';
            lineas.push('<div><strong>Desvío de ruta:</strong> <span class="' + claseDesvio + '">' + textoDesvio + (ev.desviado ? ' (ALERTA)' : '') + '</span></div>');
        }

        if (ev.cantidadKg !== null && ev.cantidadKg !== undefined) {
            lineas.push('<div><strong>Carga:</strong> ' + escaparHtml(ev.cantidadKg) + ' kg</div>');
        }
        if (ev.mercaderia) {
            lineas.push('<div><strong>Mercadería:</strong> ' + escaparHtml(ev.mercaderia) + '</div>');
        }
        if (ev.empresaCarga) {
            lineas.push('<div><strong>Empresa:</strong> ' + escaparHtml(ev.empresaCarga) + '</div>');
        }
        if (ev.incidente) {
            lineas.push('<div class="cuy-map-popup-alert" style="margin-top:4px;"><strong>Incidente:</strong> ' + escaparHtml(ev.incidente) + '</div>');
        }

        lineas.push('</div>');
        return lineas.join('');
    }

    function actualizarEncabezadoMapa(data) {
        var subtituloEl = document.getElementById('mapaSubtituloGuia');
        var corredorEl = document.getElementById('mapaCorredorBadge');
        var contadorEl = document.getElementById('mapaContadorPuntosBadge');
        var avisoEl = document.getElementById('mapaEstadoAviso');

        if (subtituloEl) {
            var matriculaTxt = data.matricula && data.matricula !== 'No asignado'
                ? ' (' + data.matricula + ')'
                : '';
            subtituloEl.textContent = 'Guía #' + data.guiaId + matriculaTxt + ': ' + data.origen + ' → ' + data.destino;
        }
        if (corredorEl) {
            corredorEl.textContent = 'Corredor: ' + (data.corredorVial || 'MTOP Nacional');
        }

        var totalGps = Array.isArray(data.eventos) ? data.eventos.length : 0;
        var desvios = 0;
        if (totalGps > 0) {
            for (var i = 0; i < data.eventos.length; i++) {
                if (data.eventos[i].desviado) {
                    desvios++;
                }
            }
        }
        if (contadorEl) {
            contadorEl.textContent = totalGps + ' punto(s) GPS' + (desvios > 0 ? ' • ' + desvios + ' desvío(s)' : '');
            contadorEl.className = desvios > 0 ? 'cuy-map-pill cuy-map-pill-danger' : 'cuy-map-pill';
        }

        if (avisoEl) {
            var tieneRuta = Array.isArray(data.rutaPlanificada) && data.rutaPlanificada.length >= 2;
            if (totalGps === 0 && tieneRuta) {
                avisoEl.textContent = 'Mostrando ruta planificada MTOP. Aún no hay reportes GPS registrados para este viaje.';
                avisoEl.style.display = 'block';
            } else if (totalGps === 0 && !tieneRuta) {
                avisoEl.textContent = 'Sin coordenadas GPS ni ruta geográfica disponible para esta guía.';
                avisoEl.style.display = 'block';
            } else {
                avisoEl.style.display = 'none';
            }
        }
    }

    function actualizarMapaMonitoreo(forzarCentrado) {
        var cardEl = document.getElementById('mapaMonitoreoCard');
        var canvasEl = document.getElementById('mapaMonitoreoCanvas');
        var data = leerDatosMapaJson();

        if (!cardEl || !canvasEl) {
            return;
        }

        if (!data || !data.guiaSeleccionada) {
            cardEl.style.display = 'none';
            return;
        }

        cardEl.style.display = '';
        actualizarEncabezadoMapa(data);

        if (typeof global.L === 'undefined') {
            canvasEl.innerHTML = '<div class="cuy-map-fallback">No se pudo cargar Leaflet.js para visualizar el mapa OpenStreetMap (verifique conectividad a unpkg.com).</div>';
            return;
        }

        var state = obtenerEstadoMapa();
        var layerGroup = inicializarMapaSiNecesario(canvasEl, state);
        layerGroup.clearLayers();

        var bounds = global.L.latLngBounds([]);

        // 1. Ruta planificada MTOP (línea azul punteada + marcadores de Origen y Destino)
        var rutaPts = Array.isArray(data.rutaPlanificada) ? data.rutaPlanificada : [];
        if (rutaPts.length >= 2) {
            for (var r = 0; r < rutaPts.length; r++) {
                bounds.extend(rutaPts[r]);
            }

            global.L.polyline(rutaPts, {
                color: '#ffffff',
                weight: 7,
                opacity: 0.75,
                interactive: false
            }).addTo(layerGroup);

            global.L.polyline(rutaPts, {
                color: '#2563eb',
                weight: 4,
                opacity: 0.88,
                dashArray: '8, 8'
            }).bindPopup(
                '<div class="cuy-map-popup"><strong>Ruta Planificada (MTOP)</strong><br/>Corredor: '
                + escaparHtml(data.corredorVial || '-') + '<br/>'
                + escaparHtml(data.origen) + ' → ' + escaparHtml(data.destino) + '</div>'
            ).addTo(layerGroup);

            global.L.circleMarker(rutaPts[0], {
                radius: 7,
                fillColor: '#1d4ed8',
                color: '#ffffff',
                weight: 2.5,
                fillOpacity: 1
            }).bindPopup(
                '<div class="cuy-map-popup"><strong>Origen planificado</strong><br/>' + escaparHtml(data.origen) + '</div>'
            ).bindTooltip('Origen: ' + (data.origen || ''), { direction: 'top' }).addTo(layerGroup);

            global.L.circleMarker(rutaPts[rutaPts.length - 1], {
                radius: 7,
                fillColor: '#4338ca',
                color: '#ffffff',
                weight: 2.5,
                fillOpacity: 1
            }).bindPopup(
                '<div class="cuy-map-popup"><strong>Destino planificado</strong><br/>' + escaparHtml(data.destino) + '</div>'
            ).bindTooltip('Destino: ' + (data.destino || ''), { direction: 'top' }).addTo(layerGroup);
        }

        // 2. Recorrido real del camión (polilínea verde esmeralda + puntos cronológicos)
        var eventos = Array.isArray(data.eventos) ? data.eventos : [];
        if (eventos.length > 0) {
            var gpsPts = [];
            for (var i = 0; i < eventos.length; i++) {
                var p = [eventos[i].lat, eventos[i].lng];
                gpsPts.push(p);
                bounds.extend(p);
            }

            if (gpsPts.length >= 2) {
                global.L.polyline(gpsPts, {
                    color: '#ffffff',
                    weight: 7,
                    opacity: 0.7,
                    interactive: false
                }).addTo(layerGroup);

                global.L.polyline(gpsPts, {
                    color: '#059669',
                    weight: 4,
                    opacity: 0.95
                }).bindPopup(
                    '<div class="cuy-map-popup"><strong>Recorrido GPS del camión</strong><br/>Vehículo: '
                    + escaparHtml(data.matricula || '-') + '<br/>Puntos reportados: ' + gpsPts.length + '</div>'
                ).addTo(layerGroup);
            }

            for (var idx = 0; idx < eventos.length; idx++) {
                var ev = eventos[idx];
                var esUltimo = idx === eventos.length - 1;
                var color = determinarColorEvento(ev);
                var coord = [ev.lat, ev.lng];

                if (esUltimo) {
                    global.L.circleMarker(coord, {
                        radius: 15,
                        fillColor: color,
                        color: color,
                        weight: 1.5,
                        opacity: 0.45,
                        fillOpacity: 0.18,
                        interactive: false
                    }).addTo(layerGroup);
                }

                var marker = global.L.circleMarker(coord, {
                    radius: esUltimo ? 9 : 6,
                    fillColor: color,
                    color: '#ffffff',
                    weight: esUltimo ? 3 : 2,
                    fillOpacity: 0.96
                }).bindPopup(construirPopupEvento(ev, idx, eventos.length));

                if (esUltimo) {
                    marker.bindTooltip(
                        'Última posición (' + (ev.tipo || 'POSICION') + ')',
                        { direction: 'top', offset: [0, -8] }
                    );
                }
                marker.addTo(layerGroup);
            }
        }

        state.lastBounds = bounds.isValid() ? bounds : null;
        var cambioDeGuia = state.lastGuiaId !== data.guiaId;
        state.lastGuiaId = data.guiaId;

        global.requestAnimationFrame(function () {
            if (!state.map) {
                return;
            }
            state.map.invalidateSize();
            if (forzarCentrado === true || cambioDeGuia) {
                if (state.lastBounds && state.lastBounds.isValid()) {
                    state.map.fitBounds(state.lastBounds, { padding: [36, 36], maxZoom: 14 });
                } else {
                    state.map.setView(URUGUAY_CENTER, URUGUAY_ZOOM);
                }
            }
        });
    }

    function centrarMapaMonitoreo() {
        actualizarMapaMonitoreo(true);
    }

    global.actualizarMapaMonitoreo = actualizarMapaMonitoreo;
    global.centrarMapaMonitoreo = centrarMapaMonitoreo;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            actualizarMapaMonitoreo(true);
        });
    } else {
        setTimeout(function () {
            actualizarMapaMonitoreo(true);
        }, 50);
    }
})(window);
