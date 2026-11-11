"""
CargaUY - Nodo Periférico Descentralizado de Tracking GPS (mock-tracking).

Funciona como un nodo periférico autónomo contenerizable (Docker) que:
  1. Mantiene el grafo geoespacial de la Red Vial Nacional de Uruguay (MTOP) y calcula
     los recorridos sobre las polilíneas de las rutas nacionales (origen -> destino).
  2. Puede activarse, actualizarse, pausarse, reiniciarse o avanzar paso a paso desde
     el nodo central (pestaña de Seguimiento/Monitoreo de Viaje) vía API HTTP REST.
  3. Emite periódicamente en segundo plano puntos de posición GPS para todas las guías
     de viaje activas (EN_CURSO), intercalando la cantidad configurada de episodios de
     desvío y permaneciendo en el lugar de finalización hasta que el chofer cierre el viaje.
  4. Envía los puntos emitidos hacia el backend central (webhook HTTP POST) y mantiene
     un buffer de salida (outbox) idempotente por UUID para sincronización resiliente.
"""

from datetime import datetime, timedelta, timezone
import heapq
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import os
import threading
import time
import unicodedata
from urllib.error import URLError
from urllib.parse import parse_qs, urlparse
import urllib.request
import uuid

PORT = int(os.environ.get("PORT", "8080"))
UY_TZ = timezone(timedelta(hours=-3))
RADIO_TIERRA_KM = 6371.0088

EVENTOS_POR_MINUTO_DEFECTO = int(os.environ.get("TRACKING_EVENTOS_POR_MINUTO", "12"))
MIN_EVENTOS_POR_MINUTO = 1
MAX_EVENTOS_POR_MINUTO = 120
PASOS_RUTA_BASE = 10
MAX_BUFFER_EVENTOS = 500

# Nodos logísticos y capitales departamentales de la Red Vial Nacional de Uruguay (MTOP)
NODOS_VIALES_UY = {
    "montevideo": (-34.9011, -56.1645),
    "las_piedras": (-34.7303, -56.2192),
    "canelones": (-34.5228, -56.2778),
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
    "dolores": (-33.5300, -58.2170),
    "mercedes": (-33.2524, -58.0305),
    "fray_bentos": (-33.1165, -58.3107),
    "young": (-32.6983, -57.6269),
    "paysandu": (-32.3171, -58.0807),
    "salto": (-31.3833, -57.9667),
    "bella_union": (-30.2597, -57.6008),
    "artigas": (-30.4000, -56.4667),
    "rivera": (-30.9053, -55.5508),
    "tacuarembo": (-31.7169, -55.9811),
    "paso_de_los_toros": (-32.8167, -56.5167),
    "durazno": (-33.3806, -56.5236),
    "sarandi_grande": (-33.7333, -56.3333),
    "florida": (-34.0956, -56.2142),
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

ALIAS_LOCALIDADES_UY = {
    "montevideo": "montevideo",
    "puerto de montevideo": "montevideo",
    "puerto de montevideo (montevideo)": "montevideo",
    "mvd": "montevideo",
    "terminal cuenca del plata": "montevideo",
    "las piedras": "las_piedras",
    "ruta 5 km 24": "las_piedras",
    "ruta 5 km 24 - polo logistico (canelones)": "las_piedras",
    "polo logistico canelones": "las_piedras",
    "la paz": "las_piedras",
    "canelones": "canelones",
    "canelones - nodo ruta 5 / ruta 11 (canelones)": "canelones",
    "santa lucia": "canelones",
    "pando": "pando",
    "pando - polo industrial ruta 8 (canelones)": "pando",
    "ciudad de la costa": "pando",
    "barros blancos": "pando",
    "libertad": "libertad",
    "libertad - corredor ruta 1 (san jose)": "libertad",
    "ciudad del plata": "libertad",
    "san jose": "san_jose",
    "san jose de mayo": "san_jose",
    "san jose de mayo - ruta 3 (san jose)": "san_jose",
    "rosario": "rosario",
    "rosario - empalme ruta 1 y ruta 2 (colonia)": "rosario",
    "juan lacaze": "rosario",
    "nueva helvecia": "rosario",
    "colonia": "colonia",
    "colonia del sacramento": "colonia",
    "colonia del sacramento (colonia)": "colonia",
    "carmelo": "carmelo",
    "carmelo - ruta 21 (colonia)": "carmelo",
    "nueva palmira": "nueva_palmira",
    "puerto de nueva palmira": "nueva_palmira",
    "puerto de nueva palmira (colonia)": "nueva_palmira",
    "cardona": "cardona",
    "cardona - empalme ruta 2 y ruta 12 (soriano)": "cardona",
    "florencio sanchez": "cardona",
    "dolores": "dolores",
    "dolores - polo granelero (soriano)": "dolores",
    "polo granelero dolores": "dolores",
    "mercedes": "mercedes",
    "mercedes - ruta 2 (soriano)": "mercedes",
    "soriano": "mercedes",
    "fray bentos": "fray_bentos",
    "fray bentos - planta upm i (rio negro)": "fray_bentos",
    "rio negro": "fray_bentos",
    "planta upm i": "fray_bentos",
    "upm fray bentos": "fray_bentos",
    "young": "young",
    "young - acopio agricola (rio negro)": "young",
    "paysandu": "paysandu",
    "paysandu - zona industrial / puerto (paysandu)": "paysandu",
    "puerto de paysandu": "paysandu",
    "pdu": "paysandu",
    "salto": "salto",
    "salto - central horticola del norte (salto)": "salto",
    "central horticola del norte": "salto",
    "bella union": "bella_union",
    "bella union - polo agroindustrial (artigas)": "bella_union",
    "artigas": "artigas",
    "artigas - frontera quarai (artigas)": "artigas",
    "rivera": "rivera",
    "rivera - frontera seca / puerto seco (rivera)": "rivera",
    "puerto seco rivera": "rivera",
    "santana do livramento": "rivera",
    "tacuarembo": "tacuarembo",
    "tacuarembo - polo forestal (tacuarembo)": "tacuarembo",
    "polo forestal tacuarembo": "tacuarembo",
    "paso de los toros": "paso_de_los_toros",
    "paso de los toros - planta upm ii (tacuarembo)": "paso_de_los_toros",
    "planta upm ii": "paso_de_los_toros",
    "upm 2": "paso_de_los_toros",
    "pueblo centenario": "paso_de_los_toros",
    "durazno": "durazno",
    "durazno - nodo logistico ruta 5 (durazno)": "durazno",
    "nodo logistico ruta 5": "durazno",
    "florida": "florida",
    "florida - cuenca lechera ruta 5 (florida)": "florida",
    "sarandi grande": "florida",
    "trinidad": "trinidad",
    "trinidad - ruta 3 (flores)": "trinidad",
    "flores": "trinidad",
    "minas": "minas",
    "minas - planta cementera ruta 8 (lavalleja)": "minas",
    "lavalleja": "minas",
    "treinta y tres": "treinta_y_tres",
    "treinta y tres - cuenca arrocera (treinta y tres)": "treinta_y_tres",
    "melo": "melo",
    "melo - ruta 8 (cerro largo)": "melo",
    "cerro largo": "melo",
    "rio branco": "rio_branco",
    "rio branco - frontera yaguaron (cerro largo)": "rio_branco",
    "maldonado": "maldonado",
    "maldonado - logistica este (maldonado)": "maldonado",
    "punta del este": "maldonado",
    "san carlos": "maldonado",
    "rocha": "rocha",
    "rocha - ruta 9 (rocha)": "rocha",
    "la paloma": "rocha",
    "chuy": "chuy",
    "chuy - frontera brasil (rocha)": "chuy",
}

TRAMOS_RUTAS_UY = [
    ("montevideo", "las_piedras", "Ruta 5"),
    ("las_piedras", "canelones", "Ruta 5"),
    ("canelones", "florida", "Ruta 5"),
    ("florida", "durazno", "Ruta 5"),
    ("durazno", "paso_de_los_toros", "Ruta 5"),
    ("paso_de_los_toros", "tacuarembo", "Ruta 5"),
    ("tacuarembo", "rivera", "Ruta 5"),
    ("montevideo", "libertad", "Ruta 1"),
    ("libertad", "rosario", "Ruta 1"),
    ("rosario", "colonia", "Ruta 1"),
    ("rosario", "cardona", "Ruta 2"),
    ("cardona", "mercedes", "Ruta 2"),
    ("mercedes", "fray_bentos", "Ruta 2"),
    ("colonia", "carmelo", "Ruta 21"),
    ("carmelo", "nueva_palmira", "Ruta 21"),
    ("nueva_palmira", "dolores", "Ruta 21"),
    ("dolores", "mercedes", "Ruta 21"),
    ("libertad", "san_jose", "Ruta 3"),
    ("san_jose", "trinidad", "Ruta 3"),
    ("trinidad", "young", "Ruta 3"),
    ("young", "paysandu", "Ruta 3"),
    ("paysandu", "salto", "Ruta 3"),
    ("salto", "bella_union", "Ruta 3"),
    ("fray_bentos", "young", "Ruta 24"),
    ("mercedes", "young", "Ruta 2 / Ruta 24"),
    ("montevideo", "pando", "Ruta 8"),
    ("pando", "minas", "Ruta 8"),
    ("minas", "treinta_y_tres", "Ruta 8"),
    ("treinta_y_tres", "melo", "Ruta 8"),
    ("treinta_y_tres", "rio_branco", "Ruta 18"),
    ("melo", "rio_branco", "Ruta 26"),
    ("pando", "maldonado", "Ruta Interbalnearia / Ruta 9"),
    ("maldonado", "rocha", "Ruta 9"),
    ("rocha", "chuy", "Ruta 9"),
    ("san_jose", "canelones", "Ruta 11"),
    ("canelones", "pando", "Ruta 11"),
    ("cardona", "nueva_palmira", "Ruta 12"),
    ("cardona", "trinidad", "Ruta 57"),
    ("mercedes", "trinidad", "Ruta 14"),
    ("trinidad", "durazno", "Ruta 14"),
    ("florida", "minas", "Ruta 12"),
    ("maldonado", "minas", "Ruta 60"),
    ("rocha", "treinta_y_tres", "Ruta 15 / Ruta 19"),
    ("paysandu", "tacuarembo", "Ruta 26"),
    ("tacuarembo", "melo", "Ruta 26"),
    ("salto", "tacuarembo", "Ruta 31"),
    ("bella_union", "artigas", "Ruta 30"),
    ("artigas", "rivera", "Ruta 30 / Ruta 5"),
]


def ahora_uy() -> datetime:
    return datetime.now(UY_TZ)


def log_json(evento: str, **campos):
    registro = {
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "service": "nodo-tracking",
        "evento": evento,
    }
    registro.update(campos)
    print(json.dumps(registro, ensure_ascii=False), flush=True)


def normalizar_texto(texto: str | None) -> str:
    if not texto:
        return ""
    minusculas = str(texto).strip().lower()
    sin_tildes = "".join(
        c for c in unicodedata.normalize("NFD", minusculas)
        if unicodedata.category(c) != "Mn"
    )
    return " ".join(sin_tildes.split())


def distancia_haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
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


def _construir_grafo():
    adyacencia = {nodo: [] for nodo in NODOS_VIALES_UY}
    for orig, dest, ruta in TRAMOS_RUTAS_UY:
        lat_o, lon_o = NODOS_VIALES_UY[orig]
        lat_d, lon_d = NODOS_VIALES_UY[dest]
        dist_km = round(distancia_haversine_km(lat_o, lon_o, lat_d, lon_d) * 1.12, 2)
        adyacencia[orig].append((dest, ruta, dist_km))
        adyacencia[dest].append((orig, ruta, dist_km))
    return adyacencia


GRAFO_VIAL_UY = _construir_grafo()


def resolver_nodo_vial(nombre_lugar: str | None) -> str:
    clave = normalizar_texto(nombre_lugar)
    if not clave:
        return "montevideo"
    if clave in ALIAS_LOCALIDADES_UY:
        return ALIAS_LOCALIDADES_UY[clave]
    if clave in NODOS_VIALES_UY:
        return clave
    if "(" in clave:
        sin_parentesis = clave[:clave.index("(")].strip()
        if sin_parentesis in ALIAS_LOCALIDADES_UY:
            return ALIAS_LOCALIDADES_UY[sin_parentesis]
    if "-" in clave:
        prefijo = clave[:clave.index("-")].strip()
        if prefijo in ALIAS_LOCALIDADES_UY:
            return ALIAS_LOCALIDADES_UY[prefijo]
    for alias_key, nodo_id in ALIAS_LOCALIDADES_UY.items():
        if len(alias_key) >= 4 and (
            clave.startswith(alias_key + " ") or clave.endswith(" " + alias_key)
        ):
            return nodo_id
    return "montevideo"


def calcular_ruta_uruguay(nombre_origen: str | None, nombre_destino: str | None):
    orig = resolver_nodo_vial(nombre_origen)
    dest = resolver_nodo_vial(nombre_destino)
    if orig == dest:
        pt = NODOS_VIALES_UY[orig]
        return "Tramo Urbano Local", [[pt[0], pt[1]], [pt[0], pt[1]]]

    dist = {k: float("inf") for k in NODOS_VIALES_UY}
    prev_nodo = {}
    prev_ruta = {}
    dist[orig] = 0.0
    pq = [(0.0, orig)]

    while pq:
        d_act, u = heapq.heappop(pq)
        if d_act > dist[u]:
            continue
        if u == dest:
            break
        for v, ruta, peso in GRAFO_VIAL_UY.get(u, []):
            nd = d_act + peso
            if nd < dist[v]:
                dist[v] = nd
                prev_nodo[v] = u
                prev_ruta[v] = ruta
                heapq.heappush(pq, (nd, v))

    camino = []
    rutas_inv = []
    cursor = dest
    while cursor is not None:
        camino.append(NODOS_VIALES_UY[cursor])
        r = prev_ruta.get(cursor)
        if r:
            rutas_inv.append(r)
        cursor = prev_nodo.get(cursor)
    camino.reverse()
    rutas_inv.reverse()
    rutas_unicas = []
    for r in rutas_inv:
        if r not in rutas_unicas:
            rutas_unicas.append(r)

    polilinea = [[pt[0], pt[1]] for pt in camino]
    corredor = " -> ".join(rutas_unicas) if rutas_unicas else "Red Vial Nacional"
    return corredor, polilinea


def deserializar_polilinea(serializada: str | None) -> list[list[float]]:
    if not serializada or not str(serializada).strip():
        return []
    puntos = []
    for par in str(serializada).strip().split(";"):
        limpio = par.strip()
        if not limpio:
            continue
        coords = limpio.split(",")
        if len(coords) == 2:
            try:
                puntos.append([float(coords[0].strip()), float(coords[1].strip())])
            except ValueError:
                continue
    return puntos


def calcular_distancia_punto_a_segmento_km(
    lat_p: float, lon_p: float, lat_a: float, lon_a: float, lat_b: float, lon_b: float
) -> float:
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
        return distancia_haversine_km(lat_p, lon_p, lat_a, lon_a)

    t = ((px - ax) * dx + (py - ay) * dy) / norma_cuadrada
    t_acotado = max(0.0, min(1.0, t))
    lat_c = lat_a + t_acotado * (lat_b - lat_a)
    lon_c = lon_a + t_acotado * (lon_b - lon_a)
    return distancia_haversine_km(lat_p, lon_p, lat_c, lon_c)


def calcular_desvio_polilinea_km(lat_p: float, lon_p: float, polilinea: list[list[float]]) -> float:
    if not polilinea:
        return 0.0
    if len(polilinea) == 1:
        return round(distancia_haversine_km(lat_p, lon_p, polilinea[0][0], polilinea[0][1]), 2)
    min_dist = float("inf")
    for i in range(len(polilinea) - 1):
        a = polilinea[i]
        b = polilinea[i + 1]
        d = calcular_distancia_punto_a_segmento_km(lat_p, lon_p, a[0], a[1], b[0], b[1])
        if d < min_dist:
            min_dist = d
    return round(min_dist, 2)


def obtener_punto_en_polilinea(polilinea: list[list[float]]) -> list[float]:
    if not polilinea:
        return [-34.9011, -56.1645]
    if len(polilinea) == 1:
        return [polilinea[0][0], polilinea[0][1]]
    idx = (len(polilinea) - 1) // 2
    a = polilinea[idx]
    b = polilinea[idx + 1]
    return [round((a[0] + b[0]) / 2.0, 4), round((a[1] + b[1]) / 2.0, 4)]


def obtener_punto_desviado_desde_coordenada(base: list[float], polilinea: list[list[float]]) -> list[float]:
    ref = base if (base and len(base) >= 2) else obtener_punto_en_polilinea(polilinea)
    candidatos = [
        [ref[0] - 0.38, ref[1] + 0.48],
        [ref[0] + 0.38, ref[1] - 0.48],
        [ref[0] - 0.42, ref[1] - 0.42],
        [ref[0] + 0.42, ref[1] + 0.42],
    ]
    mejor = candidatos[0]
    max_desvio = -1.0
    for cand in candidatos:
        desvio = calcular_desvio_polilinea_km(cand[0], cand[1], polilinea)
        if desvio > max_desvio:
            max_desvio = desvio
            mejor = cand
    return [round(mejor[0], 4), round(mejor[1], 4)]


def generar_puntos_recorrido_polilinea(polilinea: list[list[float]], cantidad_pasos: int) -> list[list[float]]:
    if not polilinea:
        return [[-34.9011, -56.1645]]
    pasos = max(2, cantidad_pasos)
    if len(polilinea) == 1:
        return [[polilinea[0][0], polilinea[0][1]]]

    num_segmentos = len(polilinea) - 1
    longitudes = []
    longitud_total = 0.0
    for i in range(num_segmentos):
        a = polilinea[i]
        b = polilinea[i + 1]
        d = distancia_haversine_km(a[0], a[1], b[0], b[1])
        longitudes.append(d)
        longitud_total += d

    if longitud_total < 1e-6:
        fin = polilinea[-1]
        return [[fin[0], fin[1]]]

    recorrido = []
    for p in range(pasos):
        if p == 0:
            orig = polilinea[0]
            recorrido.append([round(orig[0], 4), round(orig[1], 4)])
            continue
        if p == pasos - 1:
            dest = polilinea[-1]
            recorrido.append([round(dest[0], 4), round(dest[1], 4)])
            continue

        objetivo_km = (longitud_total * p) / (pasos - 1)
        acumulado = 0.0
        seg_idx = num_segmentos - 1
        fraccion = 1.0
        for i in range(num_segmentos):
            seg_len = longitudes[i]
            if acumulado + seg_len >= objetivo_km or i == num_segmentos - 1:
                seg_idx = i
                fraccion = max(0.0, min(1.0, (objetivo_km - acumulado) / seg_len)) if seg_len > 1e-9 else 0.0
                break
            acumulado += seg_len

        a = polilinea[seg_idx]
        b = polilinea[seg_idx + 1]
        lat = round(a[0] + fraccion * (b[0] - a[0]), 4)
        lon = round(a[1] + fraccion * (b[1] - a[1]), 4)
        recorrido.append([lat, lon])
    return recorrido


class EstadoSimulacionGuia:
    def __init__(self, guia_id: int, desvios_configurados: int, plan_pasos: list[dict]):
        self.guia_id = guia_id
        self.desvios_configurados = desvios_configurados
        self.plan_pasos = plan_pasos
        self.indice_actual = 0

    def siguiente_paso(self) -> dict:
        if not self.plan_pasos:
            return {
                "latitud": -34.9011,
                "longitud": -56.1645,
                "esInicioEpisodioDesvio": False,
                "esDesvio": False,
                "enDestino": True,
                "descripcion": "Mock Tracking GPS - Posición en destino",
            }
        if self.indice_actual < len(self.plan_pasos) - 1:
            paso = self.plan_pasos[self.indice_actual]
            self.indice_actual += 1
            return paso
        return self.plan_pasos[-1]


def construir_estado_simulacion(guia: dict, desvios_deseados: int) -> EstadoSimulacionGuia:
    guia_id = int(guia["id"])
    origen = str(guia.get("origen") or "Montevideo").strip() or "Montevideo"
    destino = str(guia.get("destino") or "Rivera").strip() or "Rivera"

    polilinea = deserializar_polilinea(guia.get("polilineaRuta"))
    if not polilinea:
        _, polilinea = calcular_ruta_uruguay(origen, destino)

    num_desvios = max(0, int(desvios_deseados))
    cantidad_puntos_ruta = max(PASOS_RUTA_BASE, num_desvios + 4)
    puntos_ruta = generar_puntos_recorrido_polilinea(polilinea, cantidad_puntos_ruta)

    plan = []
    ultimo_idx = len(puntos_ruta) - 1
    desvios_insertados = 0

    for i in range(ultimo_idx):
        pt_ruta = puntos_ruta[i]
        plan.append({
            "latitud": pt_ruta[0],
            "longitud": pt_ruta[1],
            "esInicioEpisodioDesvio": False,
            "esDesvio": False,
            "enDestino": False,
            "descripcion": f"Mock Tracking GPS - En ruta ({origen} -> {destino}) tramo {i + 1}/{ultimo_idx}",
        })

        if desvios_insertados < num_desvios:
            desvios_insertados += 1
            desvio1 = obtener_punto_desviado_desde_coordenada(pt_ruta, polilinea)
            desvio2 = [round(desvio1[0] + 0.008, 4), round(desvio1[1] + 0.008, 4)]

            plan.append({
                "latitud": desvio1[0],
                "longitud": desvio1[1],
                "esInicioEpisodioDesvio": True,
                "esDesvio": True,
                "enDestino": False,
                "descripcion": f"Mock Tracking GPS - Desvío #{desvios_insertados}/{num_desvios} fuera de corredor ({origen} -> {destino})",
            })
            plan.append({
                "latitud": desvio2[0],
                "longitud": desvio2[1],
                "esInicioEpisodioDesvio": False,
                "esDesvio": True,
                "enDestino": False,
                "descripcion": f"Mock Tracking GPS - Continúa en desvío #{desvios_insertados}/{num_desvios} (no genera nueva infracción)",
            })

            idx_retorno = min(i + 1, max(0, ultimo_idx - 1))
            pt_retorno = puntos_ruta[idx_retorno]
            plan.append({
                "latitud": pt_retorno[0],
                "longitud": pt_retorno[1],
                "esInicioEpisodioDesvio": False,
                "esDesvio": False,
                "enDestino": False,
                "descripcion": f"Mock Tracking GPS - Reingreso a ruta esperada tras desvío #{desvios_insertados} ({origen} -> {destino})",
            })

    pt_destino = puntos_ruta[ultimo_idx]
    plan.append({
        "latitud": pt_destino[0],
        "longitud": pt_destino[1],
        "esInicioEpisodioDesvio": False,
        "esDesvio": False,
        "enDestino": True,
        "descripcion": f"Mock Tracking GPS - En lugar de finalización ({destino}) aguardando cierre de viaje del chofer",
    })

    return EstadoSimulacionGuia(guia_id, num_desvios, plan)


_LOCK = threading.Lock()
_WAKE_EVENT = threading.Event()

_CONFIG = {
    "modoFallo": False,
    "latenciaMs": 0,
    "backendUrl": os.environ.get("CARGAUY_BACKEND_URL", "").strip(),
    "faasUrl": os.environ.get("FAAS_TRACKING_URL", "").strip(),
    "faasSecret": os.environ.get("FAAS_SHARED_SECRET", "").strip(),
}

_ESTADO = {
    "activo": False,
    "eventosPorMinuto": max(MIN_EVENTOS_POR_MINUTO, min(MAX_EVENTOS_POR_MINUTO, EVENTOS_POR_MINUTO_DEFECTO)),
    "cantidadDesvios": 0,
    "totalEventosEmitidos": 0,
    "totalDesviosEmitidos": 0,
    "ultimoResultado": "Nodo periférico de Tracking detenido.",
}

# Diccionario de guías activas sincronizadas desde el nodo central: {guia_id: dict}
_GUIAS_ACTIVAS: dict[int, dict] = {}
# Estado de avance de ruta por cada guía activa: {guia_id: EstadoSimulacionGuia}
_ESTADOS_POR_GUIA: dict[int, EstadoSimulacionGuia] = {}
# Buffer de salida (outbox) de eventos generados por el scheduler pendientes de drenar por el central
_EVENTOS_PENDIENTES: list[dict] = []


def _invocar_faas_o_local(
    uuid_ev: str,
    tiempo_str: str,
    guia_id: int,
    latitud: float,
    longitud: float,
    origen: str,
    destino: str,
    polilinea: list[list[float]],
    corredor_vial: str,
    faas_url: str,
    faas_secret: str,
) -> float:
    if faas_url:
        try:
            payload = {
                "uuid": uuid_ev,
                "guiaId": guia_id,
                "tiempo": tiempo_str,
                "tipo": "POSICION",
                "latitud": latitud,
                "longitud": longitud,
                "origen": origen,
                "destino": destino,
                "corredorVial": corredor_vial or "",
                "waypoints": polilinea,
            }
            headers = {"Content-Type": "application/json"}
            if faas_secret:
                headers["X-FaaS-Secret"] = faas_secret
            req = urllib.request.Request(
                faas_url,
                data=json.dumps(payload).encode("utf-8"),
                headers=headers,
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=4) as resp:
                if 200 <= resp.getcode() < 300:
                    body = json.loads(resp.read().decode("utf-8"))
                    if isinstance(body, dict) and isinstance(body.get("body"), str):
                        body = json.loads(body["body"])
                    if isinstance(body, dict) and "distanciaDesvioKm" in body:
                        return round(float(body["distanciaDesvioKm"]), 2)
        except Exception as err:
            log_json("tracking.faas.fallback", guiaId=guia_id, error=str(err))

    return calcular_desvio_polilinea_km(latitud, longitud, polilinea)


def _enviar_push_al_backend(evento_dto: dict, backend_url: str, faas_secret: str) -> bool:
    if not backend_url or not faas_secret:
        return False
    url_limpia = backend_url.rstrip("/")
    if not url_limpia.endswith("/api/chofer/eventos/faas"):
        if url_limpia.endswith("/api"):
            url_limpia = url_limpia + "/chofer/eventos/faas"
        else:
            url_limpia = url_limpia + "/api/chofer/eventos/faas"
    try:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-FaaS-Secret": faas_secret,
            "X-Correlation-ID": evento_dto.get("uuid") or str(uuid.uuid4()),
        }
        req = urllib.request.Request(
            url_limpia,
            data=json.dumps(evento_dto).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=4) as resp:
            return 200 <= resp.getcode() < 300
    except URLError:
        return False
    except Exception:
        return False


def sincronizar_guias_activas(lista_guias: list[dict] | None):
    if lista_guias is None:
        return
    nuevas: dict[int, dict] = {}
    for g in lista_guias:
        if not isinstance(g, dict) or g.get("id") is None:
            continue
        gid = int(g["id"])
        nuevas[gid] = {
            "id": gid,
            "origen": str(g.get("origen") or "Montevideo").strip() or "Montevideo",
            "destino": str(g.get("destino") or "Rivera").strip() or "Rivera",
            "matricula": g.get("matricula"),
            "corredorVial": g.get("corredorVial") or "",
            "polilineaRuta": g.get("polilineaRuta") or "",
        }
    _GUIAS_ACTIVAS.clear()
    _GUIAS_ACTIVAS.update(nuevas)
    # Limpiar estados de simulación de guías que ya finalizaron
    ids_finalizados = [gid for gid in _ESTADOS_POR_GUIA if gid not in _GUIAS_ACTIVAS]
    for gid in ids_finalizados:
        _ESTADOS_POR_GUIA.pop(gid, None)


def ejecutar_paso_emision(encolar_en_outbox: bool = True) -> list[dict]:
    """
    Ejecuta un paso de emisión de coordenadas GPS para todas las guías activas (EN_CURSO).
    Retorna la lista de eventos emitidos en este paso.
    """
    with _LOCK:
        guias_snapshot = [dict(g) for g in _GUIAS_ACTIVAS.values()]
        cant_desvios = _ESTADO["cantidadDesvios"]
        faas_url = _CONFIG["faasUrl"]
        faas_secret = _CONFIG["faasSecret"]
        backend_url = _CONFIG["backendUrl"]

        if not guias_snapshot:
            _ESTADO["ultimoResultado"] = "No hay guías de viaje en estado EN_CURSO actualmente en el nodo periférico."
            _ESTADOS_POR_GUIA.clear()
            return []

        pasos_a_procesar = []
        for g in guias_snapshot:
            gid = g["id"]
            estado_guia = _ESTADOS_POR_GUIA.get(gid)
            if estado_guia is None or estado_guia.desvios_configurados != cant_desvios:
                estado_guia = construir_estado_simulacion(g, cant_desvios)
                _ESTADOS_POR_GUIA[gid] = estado_guia
            paso = estado_guia.siguiente_paso()
            pasos_a_procesar.append((g, paso))

    eventos_generados = []
    detalle_ultimo = None

    for g, paso in pasos_a_procesar:
        gid = g["id"]
        origen = g["origen"]
        destino = g["destino"]
        matricula = g.get("matricula")
        corredor_vial = g.get("corredorVial") or ""
        polilinea = deserializar_polilinea(g.get("polilineaRuta"))
        if not polilinea:
            corredor_calc, polilinea = calcular_ruta_uruguay(origen, destino)
            if not corredor_vial:
                corredor_vial = corredor_calc

        uuid_ev = str(uuid.uuid4())
        tiempo_iso = ahora_uy().strftime("%Y-%m-%dT%H:%M:%S")

        desvio_km = _invocar_faas_o_local(
            uuid_ev,
            tiempo_iso,
            gid,
            paso["latitud"],
            paso["longitud"],
            origen,
            destino,
            polilinea,
            corredor_vial,
            faas_url,
            faas_secret,
        )

        evento_dto = {
            "uuid": uuid_ev,
            "tiempo": tiempo_iso,
            "tipo": "POSICION",
            "latitud": paso["latitud"],
            "longitud": paso["longitud"],
            "guiaId": gid,
            "incidente": {
                "foto": None,
                "descripcion": paso["descripcion"],
            },
            "distanciaDesvioKm": desvio_km,
            "cantidadKg": None,
            "mercaderia": None,
            "empresaCarga": None,
            "matricula": matricula,
            "esInicioEpisodioDesvio": bool(paso.get("esInicioEpisodioDesvio")),
        }

        _enviar_push_al_backend(evento_dto, backend_url, faas_secret)
        eventos_generados.append(evento_dto)

        estado_tramo = (
            "En destino aguardando finalización"
            if paso["enDestino"]
            else ("EN DESVÍO" if paso["esDesvio"] else "En ruta")
        )
        detalle_ultimo = (
            f"Guía #{gid} ({origen} -> {destino}): "
            f"({paso['latitud']:.4f}, {paso['longitud']:.4f}) | "
            f"Desvío: {desvio_km:.2f} km | {estado_tramo}"
        )

    with _LOCK:
        for ev in eventos_generados:
            _ESTADO["totalEventosEmitidos"] += 1
            if ev.get("esInicioEpisodioDesvio"):
                _ESTADO["totalDesviosEmitidos"] += 1
            if encolar_en_outbox:
                _EVENTOS_PENDIENTES.append(ev)
                if len(_EVENTOS_PENDIENTES) > MAX_BUFFER_EVENTOS:
                    _EVENTOS_PENDIENTES.pop(0)
        if detalle_ultimo:
            _ESTADO["ultimoResultado"] = detalle_ultimo

    log_json(
        "tracking.paso.emitido",
        cantidadEventos=len(eventos_generados),
        totalEmitidos=_ESTADO["totalEventosEmitidos"],
        ultimoResultado=detalle_ultimo,
    )
    return eventos_generados


def _scheduler_loop():
    ultimo_tick = 0.0
    while True:
        _WAKE_EVENT.wait(timeout=0.5)
        _WAKE_EVENT.clear()

        with _LOCK:
            activo = _ESTADO["activo"]
            epm = _ESTADO["eventosPorMinuto"]
            modo_fallo = _CONFIG["modoFallo"]

        if not activo or modo_fallo:
            continue

        intervalo_seg = max(0.5, 60.0 / max(1, epm))
        ahora = time.monotonic()
        if ahora - ultimo_tick >= intervalo_seg:
            ultimo_tick = ahora
            try:
                ejecutar_paso_emision(encolar_en_outbox=True)
            except Exception as exc:
                log_json("tracking.scheduler.error", error=str(exc))


def construir_respuesta_estado(drenar: bool = False, eventos_paso: list[dict] | None = None) -> dict:
    with _LOCK:
        eventos_out = []
        if eventos_paso is not None:
            eventos_out.extend(eventos_paso)
        if drenar and _EVENTOS_PENDIENTES:
            vistos = {e["uuid"] for e in eventos_out if "uuid" in e}
            for ev in _EVENTOS_PENDIENTES:
                if ev.get("uuid") not in vistos:
                    eventos_out.append(ev)
            _EVENTOS_PENDIENTES.clear()

        return {
            "status": "DOWN" if _CONFIG["modoFallo"] else "UP",
            "service": "nodo-tracking",
            "activo": _ESTADO["activo"],
            "eventosPorMinuto": _ESTADO["eventosPorMinuto"],
            "cantidadDesvios": _ESTADO["cantidadDesvios"],
            "totalEventosEmitidos": _ESTADO["totalEventosEmitidos"],
            "totalDesviosEmitidos": _ESTADO["totalDesviosEmitidos"],
            "ultimoResultado": _ESTADO["ultimoResultado"],
            "guiasActivas": len(_GUIAS_ACTIVAS),
            "eventosPendientes": len(_EVENTOS_PENDIENTES),
            "eventosEmitidos": eventos_out,
        }


class TrackingHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def _correlacion(self) -> str:
        return self.headers.get("X-Correlation-ID") or str(uuid.uuid4())

    def _enviar_headers_cors(self, correlacion: str = ""):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Correlation-ID, X-FaaS-Secret")
        if correlacion:
            self.send_header("X-Correlation-ID", correlacion)

    def _enviar_json(self, status: int, payload: dict | list, correlacion: str):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._enviar_headers_cors(correlacion)
        self.end_headers()
        self.wfile.write(body)

    def _aplicar_simulacion_entorno(self, correlacion: str, path: str) -> bool:
        with _LOCK:
            modo_fallo = _CONFIG["modoFallo"]
            latencia_ms = _CONFIG["latenciaMs"]
        if latencia_ms > 0:
            time.sleep(latencia_ms / 1000.0)
        if modo_fallo:
            self._enviar_json(
                503,
                {"status": "DOWN", "error": "Nodo periférico de tracking temporalmente indisponible (simulación)"},
                correlacion,
            )
            log_json("tracking.http.error", correlacion=correlacion, path=path, status=503)
            return False
        return True

    def do_OPTIONS(self):
        self.send_response(204)
        self._enviar_headers_cors(self._correlacion())
        self.end_headers()

    def do_HEAD(self):
        parsed = urlparse(self.path)
        correlacion = self._correlacion()
        if parsed.path in ("/", "/health", "/estado"):
            with _LOCK:
                modo_fallo = _CONFIG["modoFallo"]
            self.send_response(503 if modo_fallo else 200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self._enviar_headers_cors(correlacion)
            self.end_headers()
            return
        self.send_response(404)
        self._enviar_headers_cors(correlacion)
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        correlacion = self._correlacion()

        if parsed.path == "/health":
            with _LOCK:
                modo_fallo = _CONFIG["modoFallo"]
            if modo_fallo:
                self._enviar_json(503, {"status": "DOWN", "service": "nodo-tracking"}, correlacion)
                return
            self._enviar_json(200, construir_respuesta_estado(drenar=False), correlacion)
            return

        if parsed.path in ("/", "/estado"):
            if not self._aplicar_simulacion_entorno(correlacion, parsed.path):
                return
            drenar = (params.get("drenar", ["false"])[0] or "false").lower() == "true"
            self._enviar_json(200, construir_respuesta_estado(drenar=drenar), correlacion)
            return

        self._enviar_json(404, {"error": "Endpoint no encontrado en nodo-tracking"}, correlacion)

    def do_POST(self):
        parsed = urlparse(self.path)
        correlacion = self._correlacion()

        length = int(self.headers.get("Content-Length") or 0)
        raw_body = self.rfile.read(length) if length > 0 else b"{}"
        try:
            data = json.loads(raw_body.decode("utf-8")) if raw_body else {}
        except Exception:
            self._enviar_json(400, {"error": "JSON inválido"}, correlacion)
            return

        if parsed.path == "/config":
            with _LOCK:
                if "modoFallo" in data:
                    _CONFIG["modoFallo"] = bool(data["modoFallo"])
                if "latenciaMs" in data:
                    _CONFIG["latenciaMs"] = max(0, int(data["latenciaMs"]))
                if "backendUrl" in data and data["backendUrl"] is not None:
                    _CONFIG["backendUrl"] = str(data["backendUrl"]).strip()
                if "faasUrl" in data and data["faasUrl"] is not None:
                    _CONFIG["faasUrl"] = str(data["faasUrl"]).strip()
                cfg = dict(_CONFIG)
            self._enviar_json(200, cfg, correlacion)
            log_json("tracking.config", correlacion=correlacion, config=cfg)
            return

        if not self._aplicar_simulacion_entorno(correlacion, parsed.path):
            return

        if parsed.path == "/iniciar":
            epm_raw = int(data.get("eventosPorMinuto") or EVENTOS_POR_MINUTO_DEFECTO)
            desvios_raw = int(data.get("cantidadDesvios") or 0)
            epm = max(MIN_EVENTOS_POR_MINUTO, min(MAX_EVENTOS_POR_MINUTO, epm_raw))
            desvios = max(0, desvios_raw)

            with _LOCK:
                cambio_desvios = _ESTADO["cantidadDesvios"] != desvios
                _ESTADO["eventosPorMinuto"] = epm
                _ESTADO["cantidadDesvios"] = desvios
                _ESTADO["activo"] = True
                if "faasUrl" in data and data["faasUrl"] is not None:
                    _CONFIG["faasUrl"] = str(data["faasUrl"]).strip()
                if "backendUrl" in data and data["backendUrl"] is not None:
                    _CONFIG["backendUrl"] = str(data["backendUrl"]).strip()
                if "guiasActivas" in data and isinstance(data["guiasActivas"], list):
                    sincronizar_guias_activas(data["guiasActivas"])
                if cambio_desvios:
                    _ESTADOS_POR_GUIA.clear()
                _ESTADO["ultimoResultado"] = (
                    f"Nodo periférico activo ({epm} eventos/min, {desvios} desvío(s) configurado(s) por viaje)."
                )

            # Emitir primer paso inmediato al activar y despertar al scheduler
            emitidos_iniciales = ejecutar_paso_emision(encolar_en_outbox=False)
            _WAKE_EVENT.set()
            resp = construir_respuesta_estado(drenar=True, eventos_paso=emitidos_iniciales)
            self._enviar_json(200, resp, correlacion)
            log_json(
                "tracking.iniciado",
                correlacion=correlacion,
                eventosPorMinuto=epm,
                cantidadDesvios=desvios,
                guiasActivas=resp["guiasActivas"],
            )
            return

        if parsed.path == "/detener":
            with _LOCK:
                _ESTADO["activo"] = False
                _EVENTOS_PENDIENTES.clear()
                _ESTADO["ultimoResultado"] = (
                    f"Nodo periférico detenido por el usuario. Total eventos emitidos: {_ESTADO['totalEventosEmitidos']}"
                )
            _WAKE_EVENT.set()
            resp = construir_respuesta_estado(drenar=False)
            self._enviar_json(200, resp, correlacion)
            log_json("tracking.detenido", correlacion=correlacion, totalEmitidos=resp["totalEventosEmitidos"])
            return

        if parsed.path == "/reiniciar":
            with _LOCK:
                if "guiasActivas" in data and isinstance(data["guiasActivas"], list):
                    sincronizar_guias_activas(data["guiasActivas"])
                _ESTADOS_POR_GUIA.clear()
                _EVENTOS_PENDIENTES.clear()
                _ESTADO["ultimoResultado"] = "Recorridos del nodo periférico de Tracking reiniciados al punto de origen."
            resp = construir_respuesta_estado(drenar=False)
            self._enviar_json(200, resp, correlacion)
            log_json("tracking.reiniciado", correlacion=correlacion)
            return

        if parsed.path == "/paso":
            with _LOCK:
                if "eventosPorMinuto" in data and data["eventosPorMinuto"] is not None:
                    _ESTADO["eventosPorMinuto"] = max(
                        MIN_EVENTOS_POR_MINUTO, min(MAX_EVENTOS_POR_MINUTO, int(data["eventosPorMinuto"]))
                    )
                if "cantidadDesvios" in data and data["cantidadDesvios"] is not None:
                    nuevos_desvios = max(0, int(data["cantidadDesvios"]))
                    if nuevos_desvios != _ESTADO["cantidadDesvios"]:
                        _ESTADO["cantidadDesvios"] = nuevos_desvios
                        _ESTADOS_POR_GUIA.clear()
                if "faasUrl" in data and data["faasUrl"] is not None:
                    _CONFIG["faasUrl"] = str(data["faasUrl"]).strip()
                if "guiasActivas" in data and isinstance(data["guiasActivas"], list):
                    sincronizar_guias_activas(data["guiasActivas"])

            emitidos = ejecutar_paso_emision(encolar_en_outbox=False)
            resp = construir_respuesta_estado(drenar=True, eventos_paso=emitidos)
            self._enviar_json(200, resp, correlacion)
            return

        if parsed.path == "/guias":
            drenar = bool(data.get("drenar", True))
            with _LOCK:
                if "guiasActivas" in data and isinstance(data["guiasActivas"], list):
                    sincronizar_guias_activas(data["guiasActivas"])
            resp = construir_respuesta_estado(drenar=drenar)
            self._enviar_json(200, resp, correlacion)
            return

        self._enviar_json(404, {"error": "Endpoint no encontrado en nodo-tracking"}, correlacion)


if __name__ == "__main__":
    scheduler_thread = threading.Thread(target=_scheduler_loop, name="nodo-tracking-scheduler", daemon=True)
    scheduler_thread.start()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), TrackingHandler)
    log_json("tracking.startup", port=PORT, backendUrl=_CONFIG["backendUrl"])
    server.serve_forever()
