"""
CargaUY - Function as a Service (FaaS) para Procesamiento de Eventos de Tracking y Cálculo de Desvío
sobre Polilíneas Reales de la Red Vial Nacional de Uruguay (MTOP).

Compatible con:
  1. AWS Lambda (Function URL / API Gateway v1 y v2) -> handler: lambda_function.lambda_handler
  2. Google Cloud Run Functions (HTTP trigger)      -> handler: evaluar_tracking_http
"""

import datetime
import heapq
import hmac
import hashlib
import json
import math
import os
import unicodedata
import urllib.error
import urllib.request
import uuid

RADIO_TIERRA_KM = 6371.0088

# Nodos logísticos y capitales departamentales de la Red Vial Nacional de Uruguay (MTOP)
NODOS_VIALES_UY = {
    "montevideo": (-34.9011, -56.1645),
    "canelones": (-34.5228, -56.2778),
    "las_piedras": (-34.7303, -56.2192),
    "pando": (-34.7167, -55.9583),
    "ciudad_de_la_costa": (-34.8167, -55.9500),
    "libertad": (-34.6333, -56.6167),
    "san_jose": (-34.3375, -56.7136),
    "ecilda_paullier": (-34.3580, -57.0480),
    "rosario": (-34.3167, -57.3500),
    "colonia": (-34.4626, -57.8398),
    "tarariras": (-34.2667, -57.6167),
    "carmelo": (-34.0002, -58.2840),
    "nueva_palmira": (-33.8703, -58.4117),
    "cardona": (-33.8667, -57.3667),
    "dolores": (-33.5333, -58.2167),
    "mercedes": (-33.2524, -58.0305),
    "fray_bentos": (-33.1165, -58.3107),
    "young": (-32.6983, -57.6269),
    "paysandu": (-32.3171, -58.0807),
    "salto": (-31.3833, -57.9667),
    "bella_union": (-30.2597, -57.6008),
    "artigas": (-30.4000, -56.4667),
    "florida": (-34.0956, -56.2142),
    "sarandi_grande": (-33.7333, -56.3333),
    "durazno": (-33.3806, -56.5236),
    "paso_de_los_toros": (-32.8167, -56.5167),
    "tacuarembo": (-31.7169, -55.9811),
    "rivera": (-30.9053, -55.5508),
    "trinidad": (-33.5165, -56.8996),
    "minas": (-34.3759, -55.2377),
    "jose_pedro_varela": (-33.4500, -54.5333),
    "treinta_y_tres": (-33.2333, -54.3833),
    "melo": (-32.3703, -54.1675),
    "rio_branco": (-32.5986, -53.3858),
    "soca": (-34.6833, -55.7000),
    "pan_de_azucar": (-34.7833, -55.2333),
    "maldonado": (-34.9000, -54.9500),
    "san_carlos": (-34.7917, -54.9167),
    "rocha": (-34.4833, -54.3333),
    "castillos": (-34.1986, -53.8414),
    "chuy": (-33.6978, -53.4616),
}

# Alias oficiales y departamentales hacia los nodos viales
ALIAS_LOCALIDADES_UY = {
    "montevideo": "montevideo",
    "puerto de montevideo": "montevideo",
    "puerto de montevideo (montevideo)": "montevideo",
    "mvd": "montevideo",
    "canelones": "canelones",
    "ruta 5 km 24 - polo logistico (canelones)": "las_piedras",
    "las piedras": "las_piedras",
    "pando": "pando",
    "ciudad de la costa": "ciudad_de_la_costa",
    "libertad": "libertad",
    "san jose": "san_jose",
    "san jose de mayo": "san_jose",
    "san jose de mayo - ruta 3 (san jose)": "san_jose",
    "ecilda paullier": "ecilda_paullier",
    "rosario": "rosario",
    "colonia": "colonia",
    "colonia del sacramento": "colonia",
    "colonia del sacramento (colonia)": "colonia",
    "tarariras": "tarariras",
    "carmelo": "carmelo",
    "nueva palmira": "nueva_palmira",
    "puerto de nueva palmira": "nueva_palmira",
    "puerto de nueva palmira (colonia)": "nueva_palmira",
    "cardona": "cardona",
    "dolores": "dolores",
    "dolores - polo granelero (soriano)": "dolores",
    "mercedes": "mercedes",
    "mercedes - ruta 2 (soriano)": "mercedes",
    "soriano": "mercedes",
    "fray bentos": "fray_bentos",
    "fray bentos - planta upm i (rio negro)": "fray_bentos",
    "rio negro": "fray_bentos",
    "young": "young",
    "young - acopio agricola (rio negro)": "young",
    "paysandu": "paysandu",
    "paysandu - zona industrial / puerto (paysandu)": "paysandu",
    "pdu": "paysandu",
    "salto": "salto",
    "salto - central horticola del norte (salto)": "salto",
    "bella union": "bella_union",
    "bella union - polo agroindustrial (artigas)": "bella_union",
    "artigas": "artigas",
    "artigas - ruta 30 (artigas)": "artigas",
    "florida": "florida",
    "florida - cuenca lechera ruta 5 (florida)": "florida",
    "sarandi grande": "sarandi_grande",
    "durazno": "durazno",
    "durazno - nodo logistico ruta 5 (durazno)": "durazno",
    "paso de los toros": "paso_de_los_toros",
    "paso de los toros - planta upm ii (tacuarembo)": "paso_de_los_toros",
    "tacuarembo": "tacuarembo",
    "tacuarembo - polo forestal (tacuarembo)": "tacuarembo",
    "rivera": "rivera",
    "rivera - frontera seca / puerto seco (rivera)": "rivera",
    "trinidad": "trinidad",
    "trinidad - ruta 3 (flores)": "trinidad",
    "flores": "trinidad",
    "minas": "minas",
    "minas - planta cementera ruta 8 (lavalleja)": "minas",
    "lavalleja": "minas",
    "jose pedro varela": "jose_pedro_varela",
    "treinta y tres": "treinta_y_tres",
    "treinta y tres - cuenca arrocera (treinta y tres)": "treinta_y_tres",
    "melo": "melo",
    "melo - ruta 8 (cerro largo)": "melo",
    "cerro largo": "melo",
    "rio branco": "rio_branco",
    "rio branco - frontera yaguaron (cerro largo)": "rio_branco",
    "soca": "soca",
    "pan de azucar": "pan_de_azucar",
    "maldonado": "maldonado",
    "maldonado - polo logistico este (maldonado)": "maldonado",
    "punta del este": "maldonado",
    "san carlos": "san_carlos",
    "rocha": "rocha",
    "rocha - ruta 9 (rocha)": "rocha",
    "castillos": "castillos",
    "chuy": "chuy",
    "chuy - frontera brasil (rocha)": "chuy",
}

# Tramos bidireccionales de Rutas Nacionales del Uruguay (nodo_a, nodo_b, nombre_ruta, waypoints_intermedios)
TRAMOS_RUTAS_UY = [
    # Corredor Ruta 1
    ("montevideo", "libertad", "Ruta 1", [(-34.7750, -56.3950)]),
    ("libertad", "ecilda_paullier", "Ruta 1", [(-34.4800, -56.8500)]),
    ("ecilda_paullier", "rosario", "Ruta 1", []),
    ("rosario", "colonia", "Ruta 1", [(-34.3900, -57.6000)]),
    # Corredor Ruta 2 y Ruta 21
    ("rosario", "cardona", "Ruta 2", [(-34.0900, -57.3600)]),
    ("cardona", "mercedes", "Ruta 2", [(-33.5400, -57.7000)]),
    ("mercedes", "fray_bentos", "Ruta 2", []),
    ("colonia", "tarariras", "Ruta 22", []),
    ("colonia", "carmelo", "Ruta 21", [(-34.2200, -58.0700)]),
    ("carmelo", "nueva_palmira", "Ruta 21", []),
    ("nueva_palmira", "dolores", "Ruta 21", []),
    ("dolores", "mercedes", "Ruta 21", []),
    ("cardona", "nueva_palmira", "Ruta 12", [(-33.8900, -57.8800)]),
    # Corredor Ruta 3 y Ruta 24
    ("libertad", "san_jose", "Ruta 3", []),
    ("san_jose", "trinidad", "Ruta 3", [(-33.9200, -56.8200)]),
    ("trinidad", "young", "Ruta 3", [(-33.1200, -57.1600)]),
    ("young", "paysandu", "Ruta 3", [(-32.5100, -57.8400)]),
    ("fray_bentos", "paysandu", "Ruta 24", [(-32.7200, -58.1200)]),
    ("fray_bentos", "young", "Ruta 25", []),
    ("paysandu", "salto", "Ruta 3", [(-31.8500, -57.9900)]),
    ("salto", "bella_union", "Ruta 3", [(-30.8200, -57.7800)]),
    ("bella_union", "artigas", "Ruta 30", [(-30.3800, -57.0200)]),
    ("salto", "tacuarembo", "Ruta 31", [(-31.5200, -56.9800)]),
    # Corredor Ruta 5
    ("montevideo", "las_piedras", "Ruta 5", []),
    ("las_piedras", "canelones", "Ruta 5", []),
    ("canelones", "florida", "Ruta 5", [(-34.3100, -56.2300)]),
    ("florida", "sarandi_grande", "Ruta 5", []),
    ("sarandi_grande", "durazno", "Ruta 5", []),
    ("durazno", "paso_de_los_toros", "Ruta 5", [(-33.1000, -56.5200)]),
    ("paso_de_los_toros", "tacuarembo", "Ruta 5", [(-32.2600, -56.2400)]),
    ("tacuarembo", "rivera", "Ruta 5", [(-31.3100, -55.7600)]),
    ("rivera", "artigas", "Ruta 30", [(-30.6500, -56.0100)]),
    # Conectores transversales (Rutas 11, 14, 26)
    ("ecilda_paullier", "san_jose", "Ruta 11", []),
    ("san_jose", "canelones", "Ruta 11", []),
    ("canelones", "pando", "Ruta 11", []),
    ("mercedes", "trinidad", "Ruta 14", [(-33.3800, -57.4600)]),
    ("trinidad", "durazno", "Ruta 14", []),
    ("durazno", "jose_pedro_varela", "Ruta 14", [(-33.4100, -55.5200)]),
    ("paysandu", "tacuarembo", "Ruta 26", [(-32.0200, -57.0300)]),
    ("tacuarembo", "melo", "Ruta 26", [(-32.0500, -55.0700)]),
    # Corredor Ruta 8 y Ruta 18
    ("montevideo", "pando", "Ruta 8", []),
    ("pando", "soca", "Ruta 8", []),
    ("soca", "minas", "Ruta 8", []),
    ("minas", "jose_pedro_varela", "Ruta 8", [(-33.9100, -54.8800)]),
    ("jose_pedro_varela", "treinta_y_tres", "Ruta 8", []),
    ("treinta_y_tres", "melo", "Ruta 8", [(-32.8000, -54.2700)]),
    ("treinta_y_tres", "rio_branco", "Ruta 18", [(-32.9100, -53.8800)]),
    ("melo", "rio_branco", "Ruta 26", []),
    # Corredor Ruta 9 / Interbalnearia
    ("montevideo", "ciudad_de_la_costa", "Ruta Interbalnearia", []),
    ("ciudad_de_la_costa", "pan_de_azucar", "Ruta Interbalnearia", [(-34.7800, -55.5600)]),
    ("soca", "pan_de_azucar", "Ruta 9", []),
    ("pan_de_azucar", "maldonado", "Ruta 93", []),
    ("pan_de_azucar", "san_carlos", "Ruta 9", []),
    ("maldonado", "san_carlos", "Ruta 39", []),
    ("minas", "pan_de_azucar", "Ruta 60", []),
    ("san_carlos", "rocha", "Ruta 9", [(-34.6300, -54.6200)]),
    ("rocha", "castillos", "Ruta 9", []),
    ("castillos", "chuy", "Ruta 9", []),
    ("rocha", "treinta_y_tres", "Ruta 15", [(-33.8600, -54.3500)]),
]


def normalizar_texto(texto):
    if not texto:
        return ""
    texto_limpio = str(texto).strip().lower()
    sin_tildes = "".join(
        c for c in unicodedata.normalize("NFD", texto_limpio)
        if unicodedata.category(c) != "Mn"
    )
    return " ".join(sin_tildes.split())


def distancia_haversine_km(lat1, lon1, lat2, lon2):
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)

    a = (
        math.sin(dphi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * (math.sin(dlambda / 2.0) ** 2)
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1.0 - a)))
    return RADIO_TIERRA_KM * c


def resolver_nodo_vial(nombre_lugar):
    """
    Resuelve estrictamente un nombre de localidad a su clave de nodo en NODOS_VIALES_UY.
    Rechaza lugares desconocidos con ValueError en lugar de caer silenciosamente en Montevideo.
    """
    clave = normalizar_texto(nombre_lugar)
    if not clave:
        raise ValueError("El nombre de la localidad no puede ser vacío.")

    if clave in ALIAS_LOCALIDADES_UY:
        return ALIAS_LOCALIDADES_UY[clave]

    if "(" in clave and ")" in clave:
        sin_depto = clave[:clave.index("(")].strip()
        if sin_depto in ALIAS_LOCALIDADES_UY:
            return ALIAS_LOCALIDADES_UY[sin_depto]

    if " - " in clave:
        prefijo = clave[:clave.index(" - ")].strip()
        if prefijo in ALIAS_LOCALIDADES_UY:
            return ALIAS_LOCALIDADES_UY[prefijo]

    for alias_key, nodo_id in ALIAS_LOCALIDADES_UY.items():
        if len(alias_key) >= 4 and (
            clave.startswith(alias_key + " ")
            or clave.startswith(alias_key + " (")
            or clave.startswith(alias_key + " -")
        ):
            return nodo_id

    raise ValueError(
        f"Localidad desconocida o no registrada en la Red Vial Nacional de Uruguay: '{nombre_lugar}'."
    )


def _construir_grafo():
    adyacencia = {nodo: [] for nodo in NODOS_VIALES_UY}
    for orig, dest, ruta, intermedios in TRAMOS_RUTAS_UY:
        lat_o, lon_o = NODOS_VIALES_UY[orig]
        lat_d, lon_d = NODOS_VIALES_UY[dest]
        pts_ida = [(lat_o, lon_o)] + list(intermedios) + [(lat_d, lon_d)]
        dist_km = 0.0
        for i in range(len(pts_ida) - 1):
            dist_km += distancia_haversine_km(
                pts_ida[i][0], pts_ida[i][1], pts_ida[i + 1][0], pts_ida[i + 1][1]
            )
        adyacencia[orig].append((dest, ruta, list(intermedios), dist_km))
        adyacencia[dest].append((orig, ruta, list(reversed(intermedios)), dist_km))
    return adyacencia


GRAFO_VIAL_UY = _construir_grafo()


def calcular_polilinea_ruta_uruguay(nombre_origen, nombre_destino):
    """
    Calcula mediante Dijkstra el camino vial real más corto sobre las Rutas Nacionales de Uruguay.
    Retorna (corredor_vial, polilinea_waypoints, nodo_origen, nodo_destino).
    """
    nodo_orig = resolver_nodo_vial(nombre_origen)
    nodo_dest = resolver_nodo_vial(nombre_destino)

    if nodo_orig == nodo_dest:
        lat, lon = NODOS_VIALES_UY[nodo_orig]
        return ("Tramo Urbano Local", [(lat, lon)], nodo_orig, nodo_dest)

    dist = {k: float("inf") for k in NODOS_VIALES_UY}
    prev_nodo = {}
    prev_tramo = {}
    dist[nodo_orig] = 0.0
    cola = [(0.0, nodo_orig)]

    while cola:
        d_actual, u = heapq.heappop(cola)
        if d_actual > dist[u]:
            continue
        if u == nodo_dest:
            break
        for v, nombre_ruta, intermedios, peso in GRAFO_VIAL_UY.get(u, []):
            nueva_dist = d_actual + peso
            if nueva_dist < dist[v]:
                dist[v] = nueva_dist
                prev_nodo[v] = u
                prev_tramo[v] = (u, v, nombre_ruta, intermedios)
                heapq.heappush(cola, (nueva_dist, v))

    tramos = []
    paso = nodo_dest
    while paso in prev_tramo:
        tramos.append(prev_tramo[paso])
        paso = prev_nodo[paso]
    tramos.reverse()

    polilinea = [NODOS_VIALES_UY[nodo_orig]]
    rutas_ordenadas = []
    for u, v, nombre_ruta, intermedios in tramos:
        if nombre_ruta not in rutas_ordenadas:
            rutas_ordenadas.append(nombre_ruta)
        for wp in intermedios:
            polilinea.append(wp)
        polilinea.append(NODOS_VIALES_UY[v])

    corredor = " -> ".join(rutas_ordenadas) if rutas_ordenadas else "Red Vial Nacional"
    return (corredor, polilinea, nodo_orig, nodo_dest)


def calcular_desvio_punto_a_segmento_km(lat_p, lon_p, lat_a, lon_a, lat_b, lon_b):
    """
    Calcula la distancia mínima en kilómetros desde el punto P(lat_p, lon_p)
    hacia el segmento de ruta A(lat_a, lon_a) -> B(lat_b, lon_b).
    Retorna (distancia_km, lat_proyeccion, lon_proyeccion, fraccion_avance).
    """
    lat_media_rad = math.radians((lat_a + lat_b + lat_p) / 3.0)
    escala_lon = math.cos(lat_media_rad)

    ax = lon_a * escala_lon
    ay = lat_a
    bx = lon_b * escala_lon
    by = lat_b
    px = lon_p * escala_lon
    py = lat_p

    dx = bx - ax
    dy = by - ay
    norma_cuadrada = dx * dx + dy * dy

    if norma_cuadrada < 1e-12:
        dist = distancia_haversine_km(lat_p, lon_p, lat_a, lon_a)
        return (round(dist, 2), lat_a, lon_a, 0.0)

    t = ((px - ax) * dx + (py - ay) * dy) / norma_cuadrada
    t_acotado = max(0.0, min(1.0, t))

    lat_c = lat_a + t_acotado * (lat_b - lat_a)
    lon_c = lon_a + t_acotado * (lon_b - lon_a)

    dist = distancia_haversine_km(lat_p, lon_p, lat_c, lon_c)
    return (round(dist, 2), round(lat_c, 6), round(lon_c, 6), round(t_acotado, 4))


def calcular_desvio_a_polilinea_km(lat_p, lon_p, polilinea):
    """
    Calcula la distancia mínima desde (lat_p, lon_p) hacia todos los segmentos
    consecutivos de la polilínea vial planificada.
    """
    if not polilinea:
        raise ValueError("La polilínea de la ruta no puede ser vacía.")
    if len(polilinea) == 1:
        lat_a, lon_a = polilinea[0]
        dist = round(distancia_haversine_km(lat_p, lon_p, lat_a, lon_a), 2)
        return (dist, round(lat_a, 6), round(lon_a, 6), 0.0)

    mejor_dist = float("inf")
    mejor_proy = (polilinea[0][0], polilinea[0][1])
    mejor_avance = 0.0
    total_tramos = len(polilinea) - 1

    for idx in range(total_tramos):
        lat_a, lon_a = polilinea[idx]
        lat_b, lon_b = polilinea[idx + 1]
        dist, lat_c, lon_c, t_seg = calcular_desvio_punto_a_segmento_km(
            lat_p, lon_p, lat_a, lon_a, lat_b, lon_b
        )
        if dist < mejor_dist:
            mejor_dist = dist
            mejor_proy = (lat_c, lon_c)
            mejor_avance = round((idx + t_seg) / total_tramos, 4)

    return (round(mejor_dist, 2), mejor_proy[0], mejor_proy[1], mejor_avance)


def procesar_evento_individual(item):
    if "latitud" not in item or "longitud" not in item:
        raise ValueError("Los campos 'latitud' y 'longitud' son obligatorios.")

    latitud = float(item["latitud"])
    longitud = float(item["longitud"])
    if latitud < -90.0 or latitud > 90.0 or longitud < -180.0 or longitud > 180.0:
        raise ValueError(f"Coordenadas GPS fuera de rango válido: ({latitud}, {longitud}).")

    origen = item.get("origen")
    destino = item.get("destino")
    if not origen or not str(origen).strip():
        raise ValueError("El campo 'origen' es obligatorio.")
    if not destino or not str(destino).strip():
        raise ValueError("El campo 'destino' es obligatorio.")

    corredor_vial, polilinea_calculada, orig_resuelto, dest_resuelto = (
        calcular_polilinea_ruta_uruguay(origen, destino)
    )

    waypoints_payload = item.get("waypoints")
    polilinea_efectiva = polilinea_calculada
    if isinstance(waypoints_payload, list) and len(waypoints_payload) >= 2:
        pts_custom = []
        for wp in waypoints_payload:
            if isinstance(wp, (list, tuple)) and len(wp) >= 2:
                pts_custom.append((float(wp[0]), float(wp[1])))
            elif isinstance(wp, dict) and "latitud" in wp and "longitud" in wp:
                pts_custom.append((float(wp["latitud"]), float(wp["longitud"])))
        if len(pts_custom) >= 2:
            polilinea_efectiva = pts_custom

    distancia_km, lat_proy, lon_proy, avance = calcular_desvio_a_polilinea_km(
        latitud, longitud, polilinea_efectiva
    )

    ahora_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    resultado = {
        "uuid": item.get("uuid"),
        "guiaId": item.get("guiaId"),
        "tiempo": item.get("tiempo"),
        "tipo": item.get("tipo", "INCIDENTE"),
        "latitud": latitud,
        "longitud": longitud,
        "origen": origen,
        "destino": destino,
        "origenResuelto": orig_resuelto,
        "destinoResuelto": dest_resuelto,
        "corredorVial": item.get("corredorVial") or corredor_vial,
        "distanciaDesvioKm": distancia_km,
        "puntoProyeccionRuta": {"latitud": lat_proy, "longitud": lon_proy},
        "fraccionAvanceRuta": avance,
        "procesadoPorFaas": True,
        "faasTimestamp": ahora_iso,
    }

    if item.get("incidente") is not None:
        resultado["incidente"] = item.get("incidente")

    secreto = os.environ.get("FAAS_SHARED_SECRET", "").strip()
    if secreto and resultado.get("uuid") is not None:
        mensaje_firma = f"{resultado['uuid']}:{resultado['guiaId']}:{distancia_km}"
        firma = hmac.new(
            secreto.encode("utf-8"),
            mensaje_firma.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()
        resultado["faasSignature"] = firma

    return resultado


def verificar_autenticacion_faas(headers_norm):
    """
    Verifica de forma constante (hmac.compare_digest) la cabecera X-FaaS-Secret
    contra FAAS_SHARED_SECRET. Falla cerrado salvo que FAAS_ALLOW_INSECURE_LOCAL=true
    esté configurado explícitamente para desarrollo local.
    """
    secreto_esperado = os.environ.get("FAAS_SHARED_SECRET", "").strip()
    if not secreto_esperado:
        if os.environ.get("FAAS_ALLOW_INSECURE_LOCAL", "").strip().lower() == "true":
            return
        raise PermissionError(
            "Acceso denegado: FAAS_SHARED_SECRET no está configurado en el entorno."
        )
    secreto_recibido = str(headers_norm.get("x-faas-secret", "")).strip()
    if not secreto_recibido or not hmac.compare_digest(secreto_esperado, secreto_recibido):
        raise PermissionError("Acceso denegado: cabecera X-FaaS-Secret inválida o ausente.")


def enviar_webhook_backend(
    payload_procesado,
    auth_header=None,
    correlation_id=None,
    traceparent=None,
):
    """
    Envía el webhook únicamente a la URL configurada en la variable de entorno
    CARGAUY_BACKEND_URL (se eliminó backendCallbackUrl del body para prevenir SSRF)
    propagando X-Correlation-ID y traceparent.
    """
    url_destino = os.environ.get("CARGAUY_BACKEND_URL", "").strip()
    if not url_destino:
        return (False, "CARGAUY_BACKEND_URL no configurada (modo respuesta directa)")

    corr = str(correlation_id).strip() if correlation_id and str(correlation_id).strip() else str(uuid.uuid4())
    tp = (
        str(traceparent).strip()
        if traceparent and str(traceparent).strip()
        else f"00-{uuid.uuid4().hex}-{uuid.uuid4().hex[:16]}-01"
    )
    cuerpo_bytes = json.dumps(payload_procesado).encode("utf-8")
    cabeceras = {
        "Content-Type": "application/json",
        "X-Correlation-ID": corr,
        "traceparent": tp,
    }

    secreto = os.environ.get("FAAS_SHARED_SECRET", "").strip()
    if secreto:
        cabeceras["X-FaaS-Secret"] = secreto
    if auth_header:
        cabeceras["Authorization"] = auth_header

    peticion = urllib.request.Request(
        url_destino, data=cuerpo_bytes, headers=cabeceras, method="POST"
    )
    try:
        with urllib.request.urlopen(peticion, timeout=5) as respuesta:
            codigo = respuesta.getcode()
            return (200 <= codigo < 300, f"HTTP {codigo}")
    except urllib.error.URLError as err:
        return (False, str(err))


def procesar_payload(cuerpo, headers=None):
    headers_norm = {str(k).lower(): v for k, v in (headers or {}).items()}
    verificar_autenticacion_faas(headers_norm)
    auth_header = headers_norm.get("authorization")
    corr = headers_norm.get("x-correlation-id")
    tp = headers_norm.get("traceparent")

    if isinstance(cuerpo, list):
        resultados = [procesar_evento_individual(ev) for ev in cuerpo]
        enviado, detalle = enviar_webhook_backend(
            resultados, auth_header=auth_header, correlation_id=corr, traceparent=tp
        )
        for r in resultados:
            r["webhookEnviado"] = enviado
            r["webhookDetalle"] = detalle
        return resultados

    if isinstance(cuerpo, dict):
        if "eventos" in cuerpo and isinstance(cuerpo["eventos"], list):
            resultados = [procesar_evento_individual(ev) for ev in cuerpo["eventos"]]
            enviado, detalle = enviar_webhook_backend(
                resultados, auth_header=auth_header, correlation_id=corr, traceparent=tp
            )
            for r in resultados:
                r["webhookEnviado"] = enviado
                r["webhookDetalle"] = detalle
            return resultados

        resultado = procesar_evento_individual(cuerpo)
        enviado, detalle = enviar_webhook_backend(
            resultado, auth_header=auth_header, correlation_id=corr, traceparent=tp
        )
        resultado["webhookEnviado"] = enviado
        resultado["webhookDetalle"] = detalle
        return resultado

    raise ValueError("El cuerpo de la solicitud debe ser un objeto JSON o una lista de eventos.")


def lambda_handler(event, context):
    """
    Entrypoint estándar para AWS Lambda (Function URL y API Gateway).
    """
    headers_cors = {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type, Authorization, X-FaaS-Secret, X-Correlation-ID, traceparent",
    }

    metodo = (
        event.get("requestContext", {}).get("http", {}).get("method")
        or event.get("httpMethod")
        or "POST"
    )
    if metodo.upper() == "OPTIONS":
        return {"statusCode": 204, "headers": headers_cors, "body": ""}

    if metodo.upper() == "GET":
        info = {
            "servicio": "CargaUY FaaS Tracking & Desvío Processor (Red Vial Nacional MTOP)",
            "estado": "ACTIVO",
            "nodosVialesSoportados": len(NODOS_VIALES_UY),
            "tramosRutasNacionales": len(TRAMOS_RUTAS_UY),
        }
        return {"statusCode": 200, "headers": headers_cors, "body": json.dumps(info)}

    try:
        raw_body = event.get("body", event)
        if isinstance(raw_body, str):
            cuerpo = json.loads(raw_body)
        else:
            cuerpo = raw_body

        resultado = procesar_payload(cuerpo, event.get("headers"))
        return {
            "statusCode": 200,
            "headers": headers_cors,
            "body": json.dumps(resultado),
        }
    except PermissionError as pexc:
        return {
            "statusCode": 401,
            "headers": headers_cors,
            "body": json.dumps({"error": str(pexc)}),
        }
    except Exception as exc:
        return {
            "statusCode": 400,
            "headers": headers_cors,
            "body": json.dumps({"error": str(exc)}),
        }


def evaluar_tracking_http(request):
    """
    Entrypoint estándar para Google Cloud Run Functions (HTTP Trigger).
    """
    if request.method == "OPTIONS":
        return ("", 204, {"Access-Control-Allow-Origin": "*"})

    if request.method == "GET":
        return (
            json.dumps({
                "servicio": "CargaUY FaaS Tracking Processor (Red Vial Nacional MTOP)",
                "estado": "ACTIVO",
                "nodosVialesSoportados": len(NODOS_VIALES_UY),
            }),
            200,
            {"Content-Type": "application/json"},
        )

    try:
        cuerpo = request.get_json(silent=True)
        if cuerpo is None:
            raise ValueError("JSON inválido o vacío")
        resultado = procesar_payload(cuerpo, dict(request.headers))
        return (
            json.dumps(resultado),
            200,
            {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        )
    except PermissionError as pexc:
        return (
            json.dumps({"error": str(pexc)}),
            401,
            {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        )
    except Exception as exc:
        return (
            json.dumps({"error": str(exc)}),
            400,
            {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        )
