"""
CargaUY - Function as a Service (FaaS) para Procesamiento de Eventos de Tracking y Cálculo de Desvío.
Compatible con:
  1. AWS Lambda (Function URL / API Gateway v1 y v2) -> handler: lambda_function.lambda_handler
  2. Google Cloud Run Functions (HTTP trigger)      -> handler: evaluar_tracking_http
"""

import datetime
import hmac
import hashlib
import json
import math
import os
import unicodedata
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

RADIO_TIERRA_KM = 6371.0088

# Catálogo geográfico de capitales departamentales y nodos logísticos de Uruguay (latitud, longitud)
CATALOGO_LOCALIDADES_UY = {
    "montevideo": (-34.9011, -56.1645),
    "puerto de montevideo": (-34.9033, -56.2114),
    "canelones": (-34.5228, -56.2778),
    "las piedras": (-34.7303, -56.2192),
    "pando": (-34.7167, -55.9583),
    "ciudad de la costa": (-34.8167, -55.9500),
    "san jose": (-34.3375, -56.7136),
    "san jose de mayo": (-34.3375, -56.7136),
    "libertad": (-34.6333, -56.6167),
    "colonia": (-34.4626, -57.8398),
    "colonia del sacramento": (-34.4626, -57.8398),
    "nueva palmira": (-33.8703, -58.4117),
    "carmelo": (-34.0002, -58.2840),
    "mercedes": (-33.2524, -58.0305),
    "soriano": (-33.2524, -58.0305),
    "fray bentos": (-33.1165, -58.3107),
    "rio negro": (-33.1165, -58.3107),
    "young": (-32.6983, -57.6269),
    "paysandu": (-32.3171, -58.0807),
    "salto": (-31.3833, -57.9667),
    "artigas": (-30.4000, -56.4667),
    "bella union": (-30.2597, -57.6008),
    "rivera": (-30.9053, -55.5508),
    "tacuarembo": (-31.7169, -55.9811),
    "paso de los toros": (-32.8167, -56.5167),
    "durazno": (-33.3806, -56.5236),
    "florida": (-34.0956, -56.2142),
    "flores": (-33.5165, -56.8996),
    "trinidad": (-33.5165, -56.8996),
    "lavalleja": (-34.3759, -55.2377),
    "minas": (-34.3759, -55.2377),
    "maldonado": (-34.9000, -54.9500),
    "punta del este": (-34.9667, -54.9500),
    "rocha": (-34.4833, -54.3333),
    "chuy": (-33.6978, -53.4616),
    "treinta y tres": (-33.2333, -54.3833),
    "cerro largo": (-32.3703, -54.1675),
    "melo": (-32.3703, -54.1675),
    "rio branco": (-32.5986, -53.3858),
}


def normalizar_texto(texto):
    if not texto:
        return ""
    texto_limpio = str(texto).strip().lower()
    sin_tildes = "".join(
        c for c in unicodedata.normalize("NFD", texto_limpio)
        if unicodedata.category(c) != "Mn"
    )
    return sin_tildes


def resolver_coordenadas(nombre_lugar, lat_explicita=None, lon_explicita=None):
    if lat_explicita is not None and lon_explicita is not None:
        return (float(lat_explicita), float(lon_explicita), "COORDENADAS_EXPLICITAS")

    clave = normalizar_texto(nombre_lugar)
    if clave in CATALOGO_LOCALIDADES_UY:
        lat, lon = CATALOGO_LOCALIDADES_UY[clave]
        return (lat, lon, clave)

    for localidad, (lat, lon) in CATALOGO_LOCALIDADES_UY.items():
        if localidad in clave or clave in localidad:
            return (lat, lon, localidad)

    # Fallback al centro logístico de Montevideo si no coincide ninguna localidad
    lat_mvd, lon_mvd = CATALOGO_LOCALIDADES_UY["montevideo"]
    return (lat_mvd, lon_mvd, "montevideo (default)")


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


def procesar_evento_individual(item):
    latitud = float(item.get("latitud", 0.0))
    longitud = float(item.get("longitud", 0.0))
    origen = item.get("origen", "Montevideo")
    destino = item.get("destino", "Rivera")

    lat_orig, lon_orig, orig_resuelto = resolver_coordenadas(
        origen, item.get("origenLatitud"), item.get("origenLongitud")
    )
    lat_dest, lon_dest, dest_resuelto = resolver_coordenadas(
        destino, item.get("destinoLatitud"), item.get("destinoLongitud")
    )

    distancia_km, lat_proy, lon_proy, avance = calcular_desvio_punto_a_segmento_km(
        latitud, longitud, lat_orig, lon_orig, lat_dest, lon_dest
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
        "distanciaDesvioKm": distancia_km,
        "puntoProyeccionRuta": {"latitud": lat_proy, "longitud": lon_proy},
        "fraccionAvanceRuta": avance,
        "procesadoPorFaas": True,
        "faasTimestamp": ahora_iso,
    }

    if item.get("incidente") is not None:
        resultado["incidente"] = item.get("incidente")

    secreto = os.environ.get("FAAS_SHARED_SECRET", "")
    if secreto and resultado.get("uuid") is not None:
        mensaje_firma = f"{resultado['uuid']}:{resultado['guiaId']}:{distancia_km}"
        firma = hmac.new(
            secreto.encode("utf-8"),
            mensaje_firma.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()
        resultado["faasSignature"] = firma

    return resultado


def enviar_webhook_backend(payload_procesado, callback_url=None, auth_header=None):
    url_destino = callback_url or os.environ.get("CARGAUY_BACKEND_URL", "").strip()
    if not url_destino:
        return (False, "CARGAUY_BACKEND_URL no configurada (modo respuesta directa)")

    cuerpo_bytes = json.dumps(payload_procesado).encode("utf-8")
    cabeceras = {"Content-Type": "application/json"}

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
    auth_header = headers_norm.get("authorization")

    if isinstance(cuerpo, list):
        resultados = [procesar_evento_individual(ev) for ev in cuerpo]
        enviado, detalle = enviar_webhook_backend(resultados, auth_header=auth_header)
        for r in resultados:
            r["webhookEnviado"] = enviado
            r["webhookDetalle"] = detalle
        return resultados

    if isinstance(cuerpo, dict):
        callback_override = cuerpo.get("backendCallbackUrl")
        if "eventos" in cuerpo and isinstance(cuerpo["eventos"], list):
            resultados = [procesar_evento_individual(ev) for ev in cuerpo["eventos"]]
            enviado, detalle = enviar_webhook_backend(
                resultados, callback_url=callback_override, auth_header=auth_header
            )
            for r in resultados:
                r["webhookEnviado"] = enviado
                r["webhookDetalle"] = detalle
            return resultados

        resultado = procesar_evento_individual(cuerpo)
        enviado, detalle = enviar_webhook_backend(
            resultado, callback_url=callback_override, auth_header=auth_header
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
        "Access-Control-Allow-Headers": "Content-Type, Authorization, X-FaaS-Secret",
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
            "servicio": "CargaUY FaaS Tracking & Desvío Processor",
            "estado": "ACTIVO",
            "localidadesSoportadas": len(CATALOGO_LOCALIDADES_UY),
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
            json.dumps({"servicio": "CargaUY FaaS Tracking Processor", "estado": "ACTIVO"}),
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
    except Exception as exc:
        return (
            json.dumps({"error": str(exc)}),
            400,
            {"Content-Type": "application/json", "Access-Control-Allow-Origin": "*"},
        )
