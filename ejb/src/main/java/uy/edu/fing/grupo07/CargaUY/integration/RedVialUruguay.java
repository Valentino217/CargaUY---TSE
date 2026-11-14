package uy.edu.fing.grupo07.CargaUY.integration;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Motor geoespacial y grafo de la Red Vial Nacional de Uruguay (MTOP).
 * <p>
 * Modela los nodos logísticos oficiales (puertos, plantas industriales, capitales departamentales
 * y pasos de frontera) y los corredores reales de las Rutas Nacionales (Rutas 1, 2, 3, 5, 8, 9,
 * 11, 12, 14, 18, 21, 24, 26, 30, 31, 57, 60).
 * </p>
 * <p>
 * Permite:
 * <ul>
 *   <li>Validar estrictamente localidades de origen y destino (rechazando lugares desconocidos sin caer en Montevideo).</li>
 *   <li>Obtener la ruta real planificada mediante algoritmo de camino mínimo (Dijkstra) como una polilínea de tramos viales.</li>
 *   <li>Calcular el desvío perpendicular mínimo en kilómetros desde cualquier coordenada GPS hacia la polilínea de la ruta.</li>
 * </ul>
 * </p>
 */
public final class RedVialUruguay implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final double RADIO_TIERRA_KM = 6371.0088;

    /**
     * Representa una localidad o nodo de la red vial uruguaya.
     */
    public record NodoVial(
            String id,
            String nombreOficial,
            String departamento,
            double latitud,
            double longitud,
            boolean seleccionableEnGuia
    ) implements Serializable {
        public double[] coordenadas() {
            return new double[]{latitud, longitud};
        }
    }

    /**
     * Representa un tramo directo entre dos nodos sobre una Ruta Nacional.
     */
    public record TramoVial(
            String nodoOrigenId,
            String nodoDestinoId,
            String nombreRuta,
            double distanciaKm,
            List<double[]> puntosIntermedios
    ) implements Serializable {
        public TramoVial {
            puntosIntermedios = puntosIntermedios == null ? List.of() : List.copyOf(puntosIntermedios);
        }

        public TramoVial(String nodoOrigenId, String nodoDestinoId, String nombreRuta, double distanciaKm) {
            this(nodoOrigenId, nodoDestinoId, nombreRuta, distanciaKm, List.of());
        }
    }

    /**
     * Resultado del cálculo de una ruta planificada sobre la red vial.
     */
    public record RutaPlanificada(
            NodoVial origen,
            NodoVial destino,
            List<NodoVial> nodosRecorridos,
            List<double[]> polilinea,
            String corredorVial,
            double distanciaTotalKm
    ) implements Serializable {

        public String serializarPolilinea() {
            return RedVialUruguay.serializarPolilinea(polilinea);
        }
    }

    private static final Map<String, NodoVial> NODOS_POR_ID = new LinkedHashMap<>();
    private static final Map<String, String> ALIAS_A_NODO_ID = new HashMap<>();
    private static final Map<String, List<TramoVial>> ADYACENCIA = new HashMap<>();
    private static final List<String> NOMBRES_OFICIALES_SELECCIONABLES;

    static {
        inicializarRedVial();
        List<String> seleccionables = new ArrayList<>();
        for (NodoVial nodo : NODOS_POR_ID.values()) {
            if (nodo.seleccionableEnGuia()) {
                seleccionables.add(nodo.nombreOficial());
            }
        }
        NOMBRES_OFICIALES_SELECCIONABLES = Collections.unmodifiableList(seleccionables);
    }

    private RedVialUruguay() {
    }

    private static void inicializarRedVial() {
        // 1. Nodos logísticos y capitales departamentales de Uruguay
        registrarNodo("montevideo", "Puerto de Montevideo (Montevideo)", "Montevideo", -34.9011, -56.1645, true,
                "montevideo", "puerto de montevideo", "mvd", "terminal cuenca del plata");
        registrarNodo("las_piedras", "Ruta 5 Km 24 - Polo Logístico (Canelones)", "Canelones", -34.7303, -56.2192, true,
                "las piedras", "ruta 5 km 24", "polo logistico canelones", "la paz");
        registrarNodo("canelones", "Canelones - Nodo Ruta 5 / Ruta 11 (Canelones)", "Canelones", -34.5228, -56.2778, true,
                "canelones", "santa lucia");
        registrarNodo("pando", "Pando - Polo Industrial Ruta 8 (Canelones)", "Canelones", -34.7167, -55.9583, true,
                "pando", "ciudad de la costa", "barros blancos");
        registrarNodo("libertad", "Libertad - Corredor Ruta 1 (San José)", "San José", -34.6333, -56.6167, true,
                "libertad", "ciudad del plata");
        registrarNodo("san_jose", "San José de Mayo - Ruta 3 (San José)", "San José", -34.3375, -56.7136, true,
                "san jose", "san jose de mayo");
        registrarNodo("rosario", "Rosario - Empalme Ruta 1 y Ruta 2 (Colonia)", "Colonia", -34.3167, -57.3500, false,
                "rosario", "juan lacaze", "nueva helvecia");
        registrarNodo("colonia", "Colonia del Sacramento (Colonia)", "Colonia", -34.4626, -57.8398, true,
                "colonia", "colonia del sacramento");
        registrarNodo("carmelo", "Carmelo - Ruta 21 (Colonia)", "Colonia", -34.0002, -58.2840, true,
                "carmelo");
        registrarNodo("nueva_palmira", "Puerto de Nueva Palmira (Colonia)", "Colonia", -33.8703, -58.4117, true,
                "nueva palmira", "puerto de nueva palmira");
        registrarNodo("cardona", "Cardona - Empalme Ruta 2 y Ruta 12 (Soriano)", "Soriano", -33.8667, -57.3667, false,
                "cardona", "florencio sanchez");
        registrarNodo("dolores", "Dolores - Polo Granelero (Soriano)", "Soriano", -33.5300, -58.2170, true,
                "dolores", "polo granelero dolores");
        registrarNodo("mercedes", "Mercedes - Ruta 2 (Soriano)", "Soriano", -33.2524, -58.0305, true,
                "mercedes", "soriano");
        registrarNodo("fray_bentos", "Fray Bentos - Planta UPM I (Río Negro)", "Río Negro", -33.1165, -58.3107, true,
                "fray bentos", "rio negro", "planta upm i", "upm fray bentos");
        registrarNodo("young", "Young - Acopio Agrícola (Río Negro)", "Río Negro", -32.6983, -57.6269, true,
                "young");
        registrarNodo("paysandu", "Paysandú - Zona Industrial / Puerto (Paysandú)", "Paysandú", -32.3171, -58.0807, true,
                "paysandu", "puerto de paysandu", "pdu");
        registrarNodo("salto", "Salto - Central Hortícola del Norte (Salto)", "Salto", -31.3833, -57.9667, true,
                "salto", "central horticola del norte");
        registrarNodo("bella_union", "Bella Unión - Polo Agroindustrial (Artigas)", "Artigas", -30.2597, -57.6008, true,
                "bella union");
        registrarNodo("artigas", "Artigas - Frontera Quaraí (Artigas)", "Artigas", -30.4000, -56.4667, true,
                "artigas");
        registrarNodo("rivera", "Rivera - Frontera Seca / Puerto Seco (Rivera)", "Rivera", -30.9053, -55.5508, true,
                "rivera", "puerto seco rivera", "santana do livramento");
        registrarNodo("tacuarembo", "Tacuarembó - Polo Forestal (Tacuarembó)", "Tacuarembó", -31.7169, -55.9811, true,
                "tacuarembo", "polo forestal tacuarembo");
        registrarNodo("paso_de_los_toros", "Paso de los Toros - Planta UPM II (Tacuarembó)", "Tacuarembó", -32.8167, -56.5167, true,
                "paso de los toros", "planta upm ii", "upm 2", "pueblo centenario");
        registrarNodo("durazno", "Durazno - Nodo Logístico Ruta 5 (Durazno)", "Durazno", -33.3806, -56.5236, true,
                "durazno", "nodo logistico ruta 5");
        registrarNodo("florida", "Florida - Cuenca Lechera Ruta 5 (Florida)", "Florida", -34.0956, -56.2142, true,
                "florida", "sarandi grande");
        registrarNodo("trinidad", "Trinidad - Ruta 3 (Flores)", "Flores", -33.5165, -56.8996, true,
                "trinidad", "flores");
        registrarNodo("minas", "Minas - Planta Cementera Ruta 8 (Lavalleja)", "Lavalleja", -34.3759, -55.2377, true,
                "minas", "lavalleja");
        registrarNodo("treinta_y_tres", "Treinta y Tres - Cuenca Arrocera (Treinta y Tres)", "Treinta y Tres", -33.2333, -54.3833, true,
                "treinta y tres");
        registrarNodo("melo", "Melo - Ruta 8 (Cerro Largo)", "Cerro Largo", -32.3703, -54.1675, true,
                "melo", "cerro largo");
        registrarNodo("rio_branco", "Río Branco - Frontera Yaguarón (Cerro Largo)", "Cerro Largo", -32.5986, -53.3858, true,
                "rio branco");
        registrarNodo("maldonado", "Maldonado - Logística Este (Maldonado)", "Maldonado", -34.9000, -54.9500, true,
                "maldonado", "punta del este", "san carlos");
        registrarNodo("rocha", "Rocha - Ruta 9 (Rocha)", "Rocha", -34.4833, -54.3333, true,
                "rocha", "la paloma");
        registrarNodo("chuy", "Chuy - Frontera Brasil (Rocha)", "Rocha", -33.6978, -53.4616, true,
                "chuy");

        // 2. Tramos Reales de Rutas Nacionales del MTOP (con vértices intermedios sobre trazado vial)
        // Corredor Ruta 5 (Montevideo - Rivera)
        registrarTramo("montevideo", "las_piedras", "Ruta 5",
                new double[]{-34.8650, -56.2280}, new double[]{-34.8050, -56.2250}, new double[]{-34.7620, -56.2250});
        registrarTramo("las_piedras", "canelones", "Ruta 5",
                new double[]{-34.6880, -56.2230}, new double[]{-34.6250, -56.2450});
        registrarTramo("canelones", "florida", "Ruta 5",
                new double[]{-34.4360, -56.2648}, new double[]{-34.3092, -56.2460}, new double[]{-34.1890, -56.2280});
        registrarTramo("florida", "durazno", "Ruta 5",
                new double[]{-33.9400, -56.2650}, new double[]{-33.7333, -56.3333}, new double[]{-33.5350, -56.4120});
        registrarTramo("durazno", "paso_de_los_toros", "Ruta 5",
                new double[]{-33.1850, -56.5050}, new double[]{-33.0550, -56.4820}, new double[]{-32.9200, -56.4980});
        registrarTramo("paso_de_los_toros", "tacuarembo", "Ruta 5",
                new double[]{-32.6425, -56.4536}, new double[]{-32.4250, -56.3150},
                new double[]{-32.2389, -56.1917}, new double[]{-32.0439, -56.1139}, new double[]{-31.8650, -56.0420});
        registrarTramo("tacuarembo", "rivera", "Ruta 5",
                new double[]{-31.5450, -55.9120}, new double[]{-31.3850, -55.8250},
                new double[]{-31.1833, -55.7611}, new double[]{-31.0180, -55.6230});

        // Corredor Ruta 1 (Montevideo - Colonia)
        registrarTramo("montevideo", "libertad", "Ruta 1",
                new double[]{-34.8420, -56.2850}, new double[]{-34.7750, -56.3980}, new double[]{-34.6820, -56.5120});
        registrarTramo("libertad", "rosario", "Ruta 1",
                new double[]{-34.5320, -56.7850}, new double[]{-34.4150, -56.9800}, new double[]{-34.3380, -57.1850});
        registrarTramo("rosario", "colonia", "Ruta 1",
                new double[]{-34.3650, -57.5200}, new double[]{-34.4180, -57.6850});

        // Corredor Ruta 2 (Rosario - Fray Bentos)
        registrarTramo("rosario", "cardona", "Ruta 2",
                new double[]{-34.1450, -57.3750}, new double[]{-33.9850, -57.3950});
        registrarTramo("cardona", "mercedes", "Ruta 2",
                new double[]{-33.7450, -57.5850}, new double[]{-33.5950, -57.7250},
                new double[]{-33.5086, -57.8008}, new double[]{-33.3850, -57.9150});
        registrarTramo("mercedes", "fray_bentos", "Ruta 2",
                new double[]{-33.1950, -58.0150}, new double[]{-33.1280, -58.0050}, new double[]{-33.1220, -58.1550});

        // Corredor Ruta 21 (Colonia - Nueva Palmira - Mercedes)
        registrarTramo("colonia", "carmelo", "Ruta 21",
                new double[]{-34.3050, -57.9550}, new double[]{-34.1550, -58.0650});
        registrarTramo("carmelo", "nueva_palmira", "Ruta 21",
                new double[]{-33.9350, -58.3520});
        registrarTramo("nueva_palmira", "dolores", "Ruta 21",
                new double[]{-33.7350, -58.3250}, new double[]{-33.6250, -58.2550});
        registrarTramo("dolores", "mercedes", "Ruta 21",
                new double[]{-33.4250, -58.1450}, new double[]{-33.3350, -58.0850});

        // Corredor Ruta 3 (Libertad - San José - Trinidad - Young - Paysandú - Salto - Bella Unión)
        registrarTramo("libertad", "san_jose", "Ruta 3",
                new double[]{-34.5150, -56.6620}, new double[]{-34.4220, -56.6950}, new double[]{-34.3550, -56.7145});
        registrarTramo("san_jose", "trinidad", "Ruta 3",
                new double[]{-34.1650, -56.7580}, new double[]{-33.9450, -56.8320}, new double[]{-33.7250, -56.8780});
        registrarTramo("trinidad", "young", "Ruta 3",
                new double[]{-33.2450, -57.0950}, new double[]{-33.0120, -57.2850}, new double[]{-32.8450, -57.4850});
        registrarTramo("young", "paysandu", "Ruta 3",
                new double[]{-32.5150, -57.7650}, new double[]{-32.3950, -57.9650});
        registrarTramo("paysandu", "salto", "Ruta 3",
                new double[]{-32.0450, -57.9250}, new double[]{-31.7850, -57.8850}, new double[]{-31.5650, -57.9150});
        registrarTramo("salto", "bella_union", "Ruta 3",
                new double[]{-31.0850, -57.7850}, new double[]{-30.7250, -57.6450}, new double[]{-30.4850, -57.6150});

        // Corredor Ruta 24 (Fray Bentos - Young - Paysandú)
        registrarTramo("fray_bentos", "young", "Ruta 24",
                new double[]{-33.1220, -58.1550}, new double[]{-33.1280, -58.0050},
                new double[]{-32.9850, -57.8850}, new double[]{-32.8150, -57.7450});
        registrarTramo("mercedes", "young", "Ruta 2 / Ruta 24",
                new double[]{-33.1950, -58.0150}, new double[]{-33.1280, -58.0050},
                new double[]{-32.9850, -57.8850}, new double[]{-32.8150, -57.7450});

        // Corredor Ruta 8 y 18 (Montevideo - Pando - Minas - Treinta y Tres - Melo - Río Branco)
        registrarTramo("montevideo", "pando", "Ruta 8",
                new double[]{-34.8350, -56.0750}, new double[]{-34.7750, -56.0050});
        registrarTramo("pando", "minas", "Ruta 8",
                new double[]{-34.6450, -55.6850}, new double[]{-34.5150, -55.4350});
        registrarTramo("minas", "treinta_y_tres", "Ruta 8",
                new double[]{-34.0850, -54.9650}, new double[]{-33.7650, -54.7250}, new double[]{-33.4850, -54.5450});
        registrarTramo("treinta_y_tres", "melo", "Ruta 8",
                new double[]{-32.9850, -54.3450}, new double[]{-32.6850, -54.2450});
        registrarTramo("treinta_y_tres", "rio_branco", "Ruta 18",
                new double[]{-33.0150, -54.0150}, new double[]{-32.7850, -53.6850});
        registrarTramo("melo", "rio_branco", "Ruta 26",
                new double[]{-32.4850, -53.7850});

        // Corredor Ruta 9 / Interbalnearia (Montevideo - Pando - Maldonado - Rocha - Chuy)
        registrarTramo("pando", "maldonado", "Ruta Interbalnearia / Ruta 9",
                new double[]{-34.7650, -55.6550}, new double[]{-34.7950, -55.3850}, new double[]{-34.8250, -55.1250});
        registrarTramo("maldonado", "rocha", "Ruta 9",
                new double[]{-34.7850, -54.9150}, new double[]{-34.6350, -54.6150});
        registrarTramo("rocha", "chuy", "Ruta 9",
                new double[]{-34.2550, -54.0550}, new double[]{-33.9950, -53.6850});

        // Corredores Transversales (Rutas 11, 12, 14, 26, 30, 31, 57, 60)
        registrarTramo("san_jose", "canelones", "Ruta 11",
                new double[]{-34.3550, -56.7145}, new double[]{-34.3830, -56.5440}, new double[]{-34.4530, -56.3910});
        registrarTramo("canelones", "pando", "Ruta 11",
                new double[]{-34.5850, -56.1150}, new double[]{-34.6450, -55.9850});
        registrarTramo("cardona", "nueva_palmira", "Ruta 12",
                new double[]{-33.8850, -57.6850}, new double[]{-33.9150, -58.0250});
        registrarTramo("cardona", "trinidad", "Ruta 57",
                new double[]{-33.7150, -57.1550});
        registrarTramo("mercedes", "trinidad", "Ruta 14",
                new double[]{-33.3850, -57.9150}, new double[]{-33.5086, -57.8008},
                new double[]{-33.5180, -57.5250}, new double[]{-33.5250, -57.2250});
        registrarTramo("trinidad", "durazno", "Ruta 14",
                new double[]{-33.4550, -56.7150});
        registrarTramo("florida", "minas", "Ruta 12",
                new double[]{-34.2150, -55.8250}, new double[]{-34.2950, -55.5450});
        registrarTramo("maldonado", "minas", "Ruta 60",
                new double[]{-34.7250, -54.9950}, new double[]{-34.5650, -55.1250});
        registrarTramo("rocha", "treinta_y_tres", "Ruta 15 / Ruta 19",
                new double[]{-34.0450, -54.2250}, new double[]{-33.6250, -54.3450});
        registrarTramo("paysandu", "tacuarembo", "Ruta 26",
                new double[]{-32.2250, -57.3250}, new double[]{-32.0450, -56.7450}, new double[]{-31.8650, -56.2850});
        registrarTramo("tacuarembo", "melo", "Ruta 26",
                new double[]{-31.9250, -55.3850}, new double[]{-32.1450, -54.7850});
        registrarTramo("salto", "tacuarembo", "Ruta 31",
                new double[]{-31.4850, -57.1250}, new double[]{-31.5850, -56.4850});
        registrarTramo("bella_union", "artigas", "Ruta 30",
                new double[]{-30.4150, -57.1250}, new double[]{-30.4350, -56.7850});
        registrarTramo("artigas", "rivera", "Ruta 30 / Ruta 5",
                new double[]{-30.7250, -56.2150}, new double[]{-31.0250, -55.9250},
                new double[]{-31.1833, -55.7611}, new double[]{-31.0180, -55.6230});
    }

    private static void registrarNodo(
            String id,
            String nombreOficial,
            String departamento,
            double lat,
            double lon,
            boolean seleccionable,
            String... aliases
    ) {
        NodoVial nodo = new NodoVial(id, nombreOficial, departamento, lat, lon, seleccionable);
        NODOS_POR_ID.put(id, nodo);
        ADYACENCIA.putIfAbsent(id, new ArrayList<>());

        ALIAS_A_NODO_ID.put(normalizarTexto(id), id);
        ALIAS_A_NODO_ID.put(normalizarTexto(nombreOficial), id);
        for (String alias : aliases) {
            ALIAS_A_NODO_ID.put(normalizarTexto(alias), id);
        }
    }

    private static void registrarTramo(String idA, String idB, String nombreRuta, double[]... puntosIntermedios) {
        NodoVial a = NODOS_POR_ID.get(idA);
        NodoVial b = NODOS_POR_ID.get(idB);
        if (a == null || b == null) {
            throw new IllegalStateException("Nodos inexistentes para tramo: " + idA + " <-> " + idB);
        }
        // Factor de sinuosidad vial de 1.12 sobre distancia geodésica entre vértices consecutivos
        double distKm = Math.round(distanciaHaversineKm(a.latitud(), a.longitud(), b.latitud(), b.longitud()) * 112.0) / 100.0;
        List<double[]> ida = new ArrayList<>(puntosIntermedios.length);
        for (double[] p : puntosIntermedios) {
            ida.add(new double[]{p[0], p[1]});
        }
        List<double[]> vuelta = new ArrayList<>(puntosIntermedios.length);
        for (int i = puntosIntermedios.length - 1; i >= 0; i--) {
            double[] p = puntosIntermedios[i];
            vuelta.add(new double[]{p[0], p[1]});
        }
        ADYACENCIA.get(idA).add(new TramoVial(idA, idB, nombreRuta, distKm, ida));
        ADYACENCIA.get(idB).add(new TramoVial(idB, idA, nombreRuta, distKm, vuelta));
    }

    public static List<String> listarNombresLocalidadesOficiales() {
        return NOMBRES_OFICIALES_SELECCIONABLES;
    }

    /**
     * Resuelve de forma estricta una localidad del catálogo nacional.
     * Lanza {@link IllegalArgumentException} si la localidad es nula, vacía o desconocida
     * (nunca hace fallback silencioso a Montevideo).
     */
    public static NodoVial resolverNodo(String nombreLugar) {
        if (nombreLugar == null || nombreLugar.isBlank()) {
            throw new IllegalArgumentException("El nombre de la localidad no puede ser nulo ni vacío.");
        }
        String clave = normalizarTexto(nombreLugar);

        // 1. Coincidencia exacta por clave, nombre oficial o alias
        String idExacto = ALIAS_A_NODO_ID.get(clave);
        if (idExacto != null) {
            return NODOS_POR_ID.get(idExacto);
        }

        // 2. Si viene con formato "Nombre - Detalle (Departamento)", extraer prefijo antes de '(' o '-'
        int idxParentesis = clave.indexOf('(');
        if (idxParentesis > 0) {
            String sinParentesis = clave.substring(0, idxParentesis).trim();
            String idSinParentesis = ALIAS_A_NODO_ID.get(sinParentesis);
            if (idSinParentesis != null) {
                return NODOS_POR_ID.get(idSinParentesis);
            }
        }
        int idxGuion = clave.indexOf('-');
        if (idxGuion > 0) {
            String prefijoGuion = clave.substring(0, idxGuion).trim();
            String idPrefijo = ALIAS_A_NODO_ID.get(prefijoGuion);
            if (idPrefijo != null) {
                return NODOS_POR_ID.get(idPrefijo);
            }
        }

        // 3. Coincidencia por prefijo de palabra completa en alias registrados (mínimo 4 caracteres)
        if (clave.length() >= 4) {
            for (Map.Entry<String, String> entry : ALIAS_A_NODO_ID.entrySet()) {
                String alias = entry.getKey();
                if (alias.length() >= 4 && (clave.startsWith(alias + " ") || clave.endsWith(" " + alias))) {
                    return NODOS_POR_ID.get(entry.getValue());
                }
            }
        }

        throw new IllegalArgumentException(
                "Localidad no reconocida en el catálogo vial nacional de Uruguay: '" + nombreLugar + "'."
        );
    }

    public static boolean esLocalidadValida(String nombreLugar) {
        if (nombreLugar == null || nombreLugar.isBlank()) {
            return false;
        }
        try {
            resolverNodo(nombreLugar);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Calcula la ruta real sobre la red de Rutas Nacionales entre {@code nombreOrigen} y {@code nombreDestino}
     * utilizando el algoritmo de Dijkstra sobre el grafo vial del MTOP.
     */
    public static RutaPlanificada calcularRuta(String nombreOrigen, String nombreDestino) {
        NodoVial origen = resolverNodo(nombreOrigen);
        NodoVial destino = resolverNodo(nombreDestino);

        if (origen.id().equals(destino.id())) {
            List<double[]> poliUnica = List.of(origen.coordenadas(), destino.coordenadas());
            return new RutaPlanificada(
                    origen,
                    destino,
                    List.of(origen),
                    poliUnica,
                    "Tramo Urbano Local (" + origen.departamento() + ")",
                    0.0
            );
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> previoNodo = new HashMap<>();
        Map<String, String> previoRuta = new HashMap<>();
        Map<String, TramoVial> previoTramo = new HashMap<>();
        for (String id : NODOS_POR_ID.keySet()) {
            dist.put(id, Double.POSITIVE_INFINITY);
        }
        dist.put(origen.id(), 0.0);

         record EstadoDijkstra(String nodoId, double acumulado) {}
        PriorityQueue<EstadoDijkstra> pq = new PriorityQueue<>(Comparator.comparingDouble(EstadoDijkstra::acumulado));
        pq.add(new EstadoDijkstra(origen.id(), 0.0));

        while (!pq.isEmpty()) {
            EstadoDijkstra actual = pq.poll();
            if (actual.acumulado() > dist.get(actual.nodoId())) {
                continue;
            }
            if (actual.nodoId().equals(destino.id())) {
                break;
            }
            for (TramoVial tramo : ADYACENCIA.getOrDefault(actual.nodoId(), Collections.emptyList())) {
                double nuevaDist = actual.acumulado() + tramo.distanciaKm();
                if (nuevaDist < dist.get(tramo.nodoDestinoId())) {
                    dist.put(tramo.nodoDestinoId(), nuevaDist);
                    previoNodo.put(tramo.nodoDestinoId(), actual.nodoId());
                    previoRuta.put(tramo.nodoDestinoId(), tramo.nombreRuta());
                    previoTramo.put(tramo.nodoDestinoId(), tramo);
                    pq.add(new EstadoDijkstra(tramo.nodoDestinoId(), nuevaDist));
                }
            }
        }

        List<NodoVial> caminoNodos = new ArrayList<>();
        Set<String> rutasUsadas = new LinkedHashSet<>();
        List<String> rutasInvertidas = new ArrayList<>();

        String cursor = destino.id();
        while (cursor != null) {
            caminoNodos.add(NODOS_POR_ID.get(cursor));
            String rutaTramo = previoRuta.get(cursor);
            if (rutaTramo != null) {
                rutasInvertidas.add(rutaTramo);
            }
            cursor = previoNodo.get(cursor);
        }
        Collections.reverse(caminoNodos);
        Collections.reverse(rutasInvertidas);
        rutasUsadas.addAll(rutasInvertidas);

        List<double[]> polilinea = new ArrayList<>(caminoNodos.size());
        if (!caminoNodos.isEmpty()) {
            polilinea.add(caminoNodos.get(0).coordenadas());
            for (int i = 0; i < caminoNodos.size() - 1; i++) {
                NodoVial siguiente = caminoNodos.get(i + 1);
                TramoVial tramo = previoTramo.get(siguiente.id());
                if (tramo != null && tramo.puntosIntermedios() != null) {
                    for (double[] punto : tramo.puntosIntermedios()) {
                        polilinea.add(new double[]{punto[0], punto[1]});
                    }
                }
                polilinea.add(siguiente.coordenadas());
            }
        }

        String corredor = rutasUsadas.isEmpty() ? "Red Vial Nacional" : String.join(" -> ", rutasUsadas);
        double totalKm = Math.round(dist.get(destino.id()) * 100.0) / 100.0;

        return new RutaPlanificada(origen, destino, caminoNodos, polilinea, corredor, totalKm);
    }

    /**
     * Calcula la distancia mínima en kilómetros desde el punto P(latP, lonP) hacia cualquiera de los
     * segmentos consecutivos de la polilínea de la ruta planificada.
     */
    public static double calcularDesvioPolilineaKm(double latP, double lonP, List<double[]> polilinea) {
        if (polilinea == null || polilinea.isEmpty()) {
            throw new IllegalArgumentException("La polilínea de la ruta no puede estar vacía.");
        }
        if (polilinea.size() == 1) {
            double[] p0 = polilinea.get(0);
            return Math.round(distanciaHaversineKm(latP, lonP, p0[0], p0[1]) * 100.0) / 100.0;
        }

        double minDistanciaKm = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polilinea.size() - 1; i++) {
            double[] a = polilinea.get(i);
            double[] b = polilinea.get(i + 1);
            double distTramo = calcularDistanciaPuntoASegmentoKm(latP, lonP, a[0], a[1], b[0], b[1]);
            if (distTramo < minDistanciaKm) {
                minDistanciaKm = distTramo;
            }
        }
        return Math.round(minDistanciaKm * 100.0) / 100.0;
    }

    public static double calcularDistanciaPuntoASegmentoKm(
            double latP,
            double lonP,
            double latA,
            double lonA,
            double latB,
            double lonB
    ) {
        double latMediaRad = Math.toRadians((latA + latB + latP) / 3.0);
        double escalaLon = Math.cos(latMediaRad);

        double ax = lonA * escalaLon;
        double ay = latA;
        double bx = lonB * escalaLon;
        double by = latB;
        double px = lonP * escalaLon;
        double py = latP;

        double dx = bx - ax;
        double dy = by - ay;
        double normaCuadrada = (dx * dx) + (dy * dy);

        if (normaCuadrada < 1e-12) {
            return distanciaHaversineKm(latP, lonP, latA, lonA);
        }

        double t = (((px - ax) * dx) + ((py - ay) * dy)) / normaCuadrada;
        double tAcotado = Math.max(0.0, Math.min(1.0, t));

        double latC = latA + (tAcotado * (latB - latA));
        double lonC = lonA + (tAcotado * (lonB - lonA));

        return distanciaHaversineKm(latP, lonP, latC, lonC);
    }

    /**
     * Obtiene un punto intermedio real sobre un segmento de la polilínea de la ruta
     * (garantiza que el desvío con respecto a la polilínea sea 0.0 km).
     */
    public static double[] obtenerPuntoEnPolilinea(List<double[]> polilinea) {
        if (polilinea == null || polilinea.isEmpty()) {
            throw new IllegalArgumentException("La polilínea no puede ser nula ni vacía.");
        }
        if (polilinea.size() == 1) {
            return new double[]{polilinea.get(0)[0], polilinea.get(0)[1]};
        }
        int indiceSegmento = (polilinea.size() - 1) / 2;
        double[] a = polilinea.get(indiceSegmento);
        double[] b = polilinea.get(indiceSegmento + 1);
        double latMedia = Math.round(((a[0] + b[0]) / 2.0) * 10000.0) / 10000.0;
        double lonMedia = Math.round(((a[1] + b[1]) / 2.0) * 10000.0) / 10000.0;
        return new double[]{latMedia, lonMedia};
    }

    /**
     * Obtiene un punto claramente desviado (> 25 km de cualquier tramo de la polilínea)
     * para pruebas de simulación de fiscalización.
     */
    public static double[] obtenerPuntoDesviadoDePolilinea(List<double[]> polilinea) {
        double[] base = obtenerPuntoEnPolilinea(polilinea);
        return obtenerPuntoDesviadoDesdeCoordenada(base, polilinea);
    }

    /**
     * Obtiene un punto claramente desviado de la polilínea tomando como referencia una coordenada base.
     */
    public static double[] obtenerPuntoDesviadoDesdeCoordenada(double[] base, List<double[]> polilinea) {
        double[] ref = (base != null && base.length >= 2) ? base : obtenerPuntoEnPolilinea(polilinea);
        double[][] candidatos = new double[][]{
                {ref[0] - 0.38, ref[1] + 0.48},
                {ref[0] + 0.38, ref[1] - 0.48},
                {ref[0] - 0.42, ref[1] - 0.42},
                {ref[0] + 0.42, ref[1] + 0.42}
        };
        double[] mejor = candidatos[0];
        double maxDesvio = -1.0;
        for (double[] cand : candidatos) {
            double desvio = calcularDesvioPolilineaKm(cand[0], cand[1], polilinea);
            if (desvio > maxDesvio) {
                maxDesvio = desvio;
                mejor = cand;
            }
        }
        return new double[]{
                Math.round(mejor[0] * 10000.0) / 10000.0,
                Math.round(mejor[1] * 10000.0) / 10000.0
        };
    }

    /**
     * Genera una secuencia de coordenadas (lat, lon) interpoladas linealmente a lo largo de los tramos
     * de la polilínea desde el origen hasta el destino. El último punto coincide exactamente con el destino.
     */
    public static List<double[]> generarPuntosRecorridoPolilinea(List<double[]> polilinea, int cantidadPasos) {
        if (polilinea == null || polilinea.isEmpty()) {
            throw new IllegalArgumentException("La polilínea no puede ser nula ni vacía.");
        }
        int pasos = Math.max(2, cantidadPasos);
        if (polilinea.size() == 1) {
            double[] unico = polilinea.get(0);
            return List.of(new double[]{unico[0], unico[1]});
        }

        int numSegmentos = polilinea.size() - 1;
        double[] longitudes = new double[numSegmentos];
        double longitudTotal = 0.0;
        for (int i = 0; i < numSegmentos; i++) {
            double[] a = polilinea.get(i);
            double[] b = polilinea.get(i + 1);
            double dist = distanciaHaversineKm(a[0], a[1], b[0], b[1]);
            longitudes[i] = dist;
            longitudTotal += dist;
        }

        if (longitudTotal < 1e-6) {
            double[] fin = polilinea.get(polilinea.size() - 1);
            return List.of(new double[]{fin[0], fin[1]});
        }

        List<double[]> recorrido = new ArrayList<>(pasos);
        for (int p = 0; p < pasos; p++) {
            if (p == 0) {
                double[] orig = polilinea.get(0);
                recorrido.add(new double[]{
                        Math.round(orig[0] * 10000.0) / 10000.0,
                        Math.round(orig[1] * 10000.0) / 10000.0
                });
                continue;
            }
            if (p == pasos - 1) {
                double[] dest = polilinea.get(polilinea.size() - 1);
                recorrido.add(new double[]{
                        Math.round(dest[0] * 10000.0) / 10000.0,
                        Math.round(dest[1] * 10000.0) / 10000.0
                });
                continue;
            }

            double objetivoKm = (longitudTotal * p) / (pasos - 1);
            double acumulado = 0.0;
            int segIdx = numSegmentos - 1;
            double fraccion = 1.0;

            for (int i = 0; i < numSegmentos; i++) {
                double segLen = longitudes[i];
                if (acumulado + segLen >= objetivoKm || i == numSegmentos - 1) {
                    segIdx = i;
                    fraccion = segLen > 1e-9 ? Math.max(0.0, Math.min(1.0, (objetivoKm - acumulado) / segLen)) : 0.0;
                    break;
                }
                acumulado += segLen;
            }

            double[] a = polilinea.get(segIdx);
            double[] b = polilinea.get(segIdx + 1);
            double lat = Math.round((a[0] + fraccion * (b[0] - a[0])) * 10000.0) / 10000.0;
            double lon = Math.round((a[1] + fraccion * (b[1] - a[1])) * 10000.0) / 10000.0;
            recorrido.add(new double[]{lat, lon});
        }
        return recorrido;
    }

    /**
     * Recorta la polilínea planificada para que comience un paso adelante de la última posición
     * conocida (latP, lonP) sobre la ruta hacia el destino, evitando que rutas EN_CURSO que ya
     * tienen eventos registrados retrocedan al origen al iniciar la simulación GPS.
     */
    public static List<double[]> recortarPolilineaDesdePosicion(
            List<double[]> polilinea,
            double latP,
            double lonP,
            int cantidadPasos
    ) {
        if (polilinea == null || polilinea.size() < 2) {
            return polilinea;
        }
        int numSegmentos = polilinea.size() - 1;
        double[] longitudes = new double[numSegmentos];
        double longitudTotal = 0.0;
        int mejorSeg = 0;
        double mejorT = 0.0;
        double minDistKm = Double.POSITIVE_INFINITY;

        for (int i = 0; i < numSegmentos; i++) {
            double[] a = polilinea.get(i);
            double[] b = polilinea.get(i + 1);
            longitudes[i] = distanciaHaversineKm(a[0], a[1], b[0], b[1]);
            longitudTotal += longitudes[i];

            double t = proyectarEnSegmento(latP, lonP, a, b);
            double latC = a[0] + (t * (b[0] - a[0]));
            double lonC = a[1] + (t * (b[1] - a[1]));
            double dist = distanciaHaversineKm(latP, lonP, latC, lonC);
            if (dist < minDistKm) {
                minDistKm = dist;
                mejorSeg = i;
                mejorT = t;
            }
        }

        if (longitudTotal < 1e-6 || minDistKm > 15.0) {
            return polilinea;
        }

        double distanciaRecorridaKm = mejorT * longitudes[mejorSeg];
        for (int i = 0; i < mejorSeg; i++) {
            distanciaRecorridaKm += longitudes[i];
        }
        if (distanciaRecorridaKm <= 1.0) {
            return polilinea;
        }

        double restanteKm = longitudTotal - distanciaRecorridaKm;
        int pasos = Math.max(2, cantidadPasos);
        double avancePrimerPasoKm = Math.min(restanteKm * 0.85, restanteKm / pasos);
        double objetivoInicioKm = Math.min(longitudTotal, distanciaRecorridaKm + avancePrimerPasoKm);

        double acumulado = 0.0;
        int segInicio = numSegmentos - 1;
        double fracInicio = 1.0;
        for (int i = 0; i < numSegmentos; i++) {
            double segLen = longitudes[i];
            if (acumulado + segLen >= objetivoInicioKm || i == numSegmentos - 1) {
                segInicio = i;
                fracInicio = segLen > 1e-9 ? Math.max(0.0, Math.min(1.0, (objetivoInicioKm - acumulado) / segLen)) : 0.0;
                break;
            }
            acumulado += segLen;
        }

        double[] a = polilinea.get(segInicio);
        double[] b = polilinea.get(segInicio + 1);
        double latInicio = Math.round((a[0] + fracInicio * (b[0] - a[0])) * 10000.0) / 10000.0;
        double lonInicio = Math.round((a[1] + fracInicio * (b[1] - a[1])) * 10000.0) / 10000.0;

        List<double[]> recortada = new ArrayList<>();
        recortada.add(new double[]{latInicio, lonInicio});
        for (int i = segInicio + 1; i <= numSegmentos; i++) {
            double[] v = polilinea.get(i);
            if (recortada.size() == 1 && distanciaHaversineKm(latInicio, lonInicio, v[0], v[1]) < 0.05 && i < numSegmentos) {
                continue;
            }
            recortada.add(new double[]{v[0], v[1]});
        }
        return recortada;
    }

    private static double proyectarEnSegmento(double latP, double lonP, double[] a, double[] b) {
        double latMediaRad = Math.toRadians((a[0] + b[0] + latP) / 3.0);
        double escalaLon = Math.cos(latMediaRad);
        double dx = (b[1] - a[1]) * escalaLon;
        double dy = b[0] - a[0];
        double normaCuadrada = (dx * dx) + (dy * dy);
        if (normaCuadrada < 1e-12) {
            return 0.0;
        }
        double px = (lonP - a[1]) * escalaLon;
        double py = latP - a[0];
        return Math.max(0.0, Math.min(1.0, ((px * dx) + (py * dy)) / normaCuadrada));
    }

    public static String serializarPolilinea(List<double[]> polilinea) {
        if (polilinea == null || polilinea.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < polilinea.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            double[] p = polilinea.get(i);
            sb.append(String.format(Locale.ROOT, "%.4f,%.4f", p[0], p[1]));
        }
        return sb.toString();
    }

    public static List<double[]> deserializarPolilinea(String serializada) {
        if (serializada == null || serializada.isBlank()) {
            return Collections.emptyList();
        }
        String[] pares = serializada.trim().split(";");
        List<double[]> puntos = new ArrayList<>(pares.length);
        for (String par : pares) {
            String limpio = par.trim();
            if (limpio.isEmpty()) {
                continue;
            }
            String[] coords = limpio.split(",");
            if (coords.length != 2) {
                throw new IllegalArgumentException("Formato de coordenada inválido en polilínea: '" + par + "'");
            }
            double lat = Double.parseDouble(coords[0].trim());
            double lon = Double.parseDouble(coords[1].trim());
            puntos.add(new double[]{lat, lon});
        }
        return puntos;
    }

    public static double distanciaHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double senoPhi = Math.sin(dPhi / 2.0);
        double senoLambda = Math.sin(dLambda / 2.0);
        double a = (senoPhi * senoPhi)
                + (Math.cos(phi1) * Math.cos(phi2) * (senoLambda * senoLambda));
        double resto = Math.max(0.0, 1.0 - a);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(resto));
        return RADIO_TIERRA_KM * c;
    }

    public static String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        String minusculas = texto.trim().toLowerCase(Locale.ROOT);
        String descompuesto = Normalizer.normalize(minusculas, Normalizer.Form.NFD);
        return descompuesto.replaceAll("\\p{M}", "");
    }
}
