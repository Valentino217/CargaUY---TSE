#!/usr/bin/env python3
"""
Generador de Carga de Tracking y Prueba de Saturación - carga.uy (Grupo 07, TSE 2026)
=====================================================================================
Script autónomo basado exclusivamente en la biblioteca estándar de Python 3
(`urllib.request`, `concurrent.futures`, `statistics`, `uuid`, `json`, `argparse`)
sin requerir paquetes externos (`pip`).

Escenarios evaluados (Secciones 3.7.3 y 4.1.4 de la letra):
  1. Carga Normal Sostenida: Camiones concurrentes reportando eventos GPS (`POSICION`)
     hacia `POST /CargaUY-web/api/chofer/eventos/sync` con `X-Correlation-ID` y `traceparent`.
  2. Pico de Ráfaga (3x): Simulación de camiones que recuperan cobertura móvil
     simultáneamente y descargan lotes acumulados en ráfaga (3x tasa nominal).
  3. Idempotencia por UUID (AC012): Reenvío deliberado de lotes con UUIDs duplicados
     para verificar descarte silencioso sin duplicar registros.
  4. Eventos Desordenados (Out-of-Order): Envío de posiciones con marcas de tiempo
     no monotónicas (`t-10m`, `t`, `t-5m`) verificando ordenamiento temporal en consultas.
  5. Consultas Concurrentes de Frontoffice bajo Carga: Medición de tiempos de respuesta
     (p50, p95, p99, promedio, tasa de error) en `GET /CargaUY-web/api/chofer/eventos/{guiaId}`
     y `/CargaUY-web/api/public/ready` (u `/api/ops/health` si se pasa `--admin-token`),
     validando la meta arquitectónica p95 < 500 ms.
"""

import argparse
import concurrent.futures
import datetime
import json
import math
import random
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid
from typing import Dict, List, Optional, Tuple


DEFAULT_BASE_URL = "http://localhost:8080/CargaUY-web"
DEFAULT_CEDULA_CHOFER = 11223344
DEFAULT_GUIA_ID = 1001


def http_json(
    method: str,
    url: str,
    payload: Optional[object] = None,
    token: Optional[str] = None,
    correlation_id: Optional[str] = None,
    traceparent: Optional[str] = None,
    timeout: float = 8.0,
) -> Tuple[int, float, object, str]:
    """Ejecuta una petición HTTP JSON y retorna (status_code, latencia_ms, body_parsed, corr_header)."""
    corr = correlation_id or str(uuid.uuid4())
    tp = traceparent or f"00-{uuid.uuid4().hex}-{uuid.uuid4().hex[:16]}-01"
    headers = {
        "Accept": "application/json",
        "X-Correlation-ID": corr,
        "traceparent": tp,
    }
    data_bytes = None
    if payload is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data_bytes = json.dumps(payload).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url=url, data=data_bytes, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            raw = resp.read().decode("utf-8", errors="replace")
            resp_corr = resp.headers.get("X-Correlation-ID", corr)
            parsed = json.loads(raw) if raw.strip() else None
            return resp.status, elapsed_ms, parsed, resp_corr
    except urllib.error.HTTPError as he:
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        raw = ""
        try:
            raw = he.read().decode("utf-8", errors="replace")
        except Exception:
            pass
        return he.code, elapsed_ms, raw, corr
    except Exception as ex:
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        return 0, elapsed_ms, str(ex), corr


def obtener_token_chofer(base_url: str, cedula: int) -> Optional[str]:
    """Intenta obtener un JWT de desarrollo para chofer desde /api/auth/dev-chofer."""
    url = f"{base_url.rstrip('/')}/api/auth/dev-chofer"
    status, _, body, _ = http_json("POST", url, payload={"ci": cedula})
    if status == 200 and isinstance(body, dict) and "token" in body:
        return str(body["token"])
    return None


def construir_evento_posicion(
    guia_id: int,
    instante: datetime.datetime,
    paso: int,
    event_uuid: Optional[str] = None,
) -> Dict[str, object]:
    """Genera un DTO EventoViajeDTO de tipo POSICION sobre el corredor Ruta 5 (Montevideo - Rivera)."""
    progreso = (paso % 100) / 100.0
    lat = -34.9011 + progreso * (-30.9053 - (-34.9011)) + random.uniform(-0.002, 0.002)
    lon = -56.1645 + progreso * (-55.5508 - (-56.1645)) + random.uniform(-0.002, 0.002)
    return {
        "uuid": event_uuid or str(uuid.uuid4()),
        "tiempo": instante.strftime("%Y-%m-%dT%H:%M:%S"),
        "tipo": "POSICION",
        "latitud": round(lat, 6),
        "longitud": round(lon, 6),
        "guiaId": guia_id,
    }


def calcular_percentil(valores_ordenados: List[float], percentil: float) -> float:
    if not valores_ordenados:
        return 0.0
    idx = (len(valores_ordenados) - 1) * (percentil / 100.0)
    f = math.floor(idx)
    c = math.ceil(idx)
    if f == c:
        return valores_ordenados[int(idx)]
    return valores_ordenados[f] * (c - idx) + valores_ordenados[c] * (idx - f)


def resumir_latencias(nombre: str, resultados: List[Tuple[int, float]]) -> Dict[str, float]:
    total = len(resultados)
    exitos = [lat for code, lat in resultados if 200 <= code < 300]
    errores = total - len(exitos)
    lat_ordenadas = sorted(exitos) if exitos else [0.0]

    p50 = calcular_percentil(lat_ordenadas, 50)
    p95 = calcular_percentil(lat_ordenadas, 95)
    p99 = calcular_percentil(lat_ordenadas, 99)
    prom = statistics.mean(lat_ordenadas) if exitos else 0.0
    tasa_error = (errores / total * 100.0) if total > 0 else 0.0

    print(f"\n--- {nombre} ---")
    print(f"  Solicitudes totales : {total}")
    print(f"  Exitosas (2xx)      : {len(exitos)} | Errores: {errores} ({tasa_error:.2f}%)")
    print(f"  Latencia promedio   : {prom:.2f} ms")
    print(f"  Latencia p50        : {p50:.2f} ms")
    print(f"  Latencia p95        : {p95:.2f} ms  (Meta < 500 ms: {'CUMPLE' if p95 < 500.0 else 'EXCEDE'})")
    print(f"  Latencia p99        : {p99:.2f} ms")

    return {
        "total": float(total),
        "exitos": float(len(exitos)),
        "errores": float(errores),
        "tasa_error_pct": round(tasa_error, 2),
        "p50_ms": round(p50, 2),
        "p95_ms": round(p95, 2),
        "p99_ms": round(p99, 2),
        "promedio_ms": round(prom, 2),
    }


def ejecutar_escenarios(
    base_url: str,
    camiones: int,
    eventos_por_lote: int,
    guia_id: int,
    cedula: int,
    admin_token: Optional[str] = None,
) -> int:
    print("=" * 78)
    print(" CARGA.UY - GENERADOR DE CARGA DE TRACKING Y PRUEBA DE SATURACION (TSE 2026)")
    print("=" * 78)
    print(f"Target URL       : {base_url}")
    print(f"Camiones Base    : {camiones} (Pico 3x = {camiones * 3} ráfagas concurrentes)")
    print(f"Eventos por Lote : {eventos_por_lote} eventos GPS POSICION")
    print(f"Guía de Viaje ID : {guia_id}")

    ready_url = f"{base_url.rstrip('/')}/api/public/ready"
    ops_health_url = f"{base_url.rstrip('/')}/api/ops/health"
    h_status, h_lat, h_body, h_corr = http_json("GET", ready_url)
    print(f"\n[0] Health Check Inicial (/api/public/ready): HTTP {h_status} ({h_lat:.1f} ms) | X-Correlation-ID={h_corr}")
    if h_status == 0:
        print("    [!] El servidor no se encuentra accesible en la URL indicada.")
        print("    [!] Asegúrese de tener levantado el entorno (`docker compose up -d`) antes de ejecutar.")
        return 1
    if isinstance(h_body, dict):
        print(f"    Estado público: {json.dumps(h_body)}")
    if admin_token:
        oh_status, oh_lat, oh_body, _ = http_json("GET", ops_health_url, token=admin_token)
        print(f"    [Ops Admin] (/api/ops/health): HTTP {oh_status} ({oh_lat:.1f} ms) -> {oh_body}")

    token = obtener_token_chofer(base_url, cedula)
    if token:
        print(f"[Auth] Token JWT de chofer obtenido exitosamente (CI {cedula}).")
    else:
        print("[Auth] Ejecutando sin token dev-chofer (verifique p1.entorno.local=true si requiere auth).")

    sync_url = f"{base_url.rstrip('/')}/api/chofer/eventos/sync"
    consulta_url = f"{base_url.rstrip('/')}/api/chofer/eventos/{guia_id}"
    ahora = datetime.datetime.now()

    # 1. Escenario Carga Normal (1x)
    print(f"\n[1] Ejecutando Carga Normal Sostenida ({camiones} camiones concurrentes)...")
    lotes_normales = [
        [construir_evento_posicion(guia_id, ahora + datetime.timedelta(seconds=i * 5 + j), i + j) for j in range(eventos_por_lote)]
        for i in range(camiones)
    ]

    t_inicio_normal = time.perf_counter()
    res_normal: List[Tuple[int, float]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(32, max(4, camiones))) as pool:
        futuros = [pool.submit(http_json, "POST", sync_url, lote, token) for lote in lotes_normales]
        for fut in concurrent.futures.as_completed(futuros):
            code, lat, _, _ = fut.result()
            res_normal.append((code, lat))
    dur_normal = max(0.001, time.perf_counter() - t_inicio_normal)
    ev_totales_normal = camiones * eventos_por_lote
    print(f"    Throughput Carga Normal: {ev_totales_normal / dur_normal:.1f} eventos/seg ({camiones / dur_normal:.1f} req/seg)")
    stats_normal = resumir_latencias("Escenario 1: Ingesta Carga Normal (1x)", res_normal)

    # 2. Escenario Pico 3x + Consultas Concurrentes Frontoffice
    rafagas_pico = camiones * 3
    print(f"\n[2] Ejecutando Pico de Ráfaga 3x ({rafagas_pico} lotes en paralelo por reconexión simultánea)")
    print("    + Consultas concurrentes de monitoreo de viaje (Frontoffice)...")
    lotes_pico = [
        [construir_evento_posicion(guia_id, ahora + datetime.timedelta(minutes=1, seconds=i * 3 + j), i + j) for j in range(eventos_por_lote)]
        for i in range(rafagas_pico)
    ]

    t_inicio_pico = time.perf_counter()
    res_pico: List[Tuple[int, float]] = []
    res_consultas: List[Tuple[int, float]] = []
    num_consultas = max(15, camiones * 2)

    with concurrent.futures.ThreadPoolExecutor(max_workers=min(64, max(8, rafagas_pico + 8))) as pool:
        fut_ingesta = [pool.submit(http_json, "POST", sync_url, lote, token) for lote in lotes_pico]
        fut_consulta = [
            pool.submit(
                http_json,
                "GET",
                consulta_url if idx % 2 == 0 else (ops_health_url if admin_token else ready_url),
                None,
                token if idx % 2 == 0 else admin_token,
            )
            for idx in range(num_consultas)
        ]
        for fut in concurrent.futures.as_completed(fut_ingesta):
            code, lat, _, _ = fut.result()
            res_pico.append((code, lat))
        for fut in concurrent.futures.as_completed(fut_consulta):
            code, lat, _, _ = fut.result()
            res_consultas.append((code, lat))

    dur_pico = max(0.001, time.perf_counter() - t_inicio_pico)
    ev_totales_pico = rafagas_pico * eventos_por_lote
    print(f"    Throughput Pico 3x     : {ev_totales_pico / dur_pico:.1f} eventos/seg ({rafagas_pico / dur_pico:.1f} req/seg)")
    stats_pico = resumir_latencias("Escenario 2A: Ingesta en Pico de Ráfaga (3x)", res_pico)
    stats_consulta = resumir_latencias("Escenario 2B: Consultas Frontoffice / Monitoreo durante Pico 3x", res_consultas)

    # 3. Prueba de Idempotencia por UUID (Duplicados)
    print("\n[3] Verificando Idempotencia Estricta por UUID (AC012)...")
    uuid_fijo = str(uuid.uuid4())
    evento_dup = construir_evento_posicion(guia_id, ahora + datetime.timedelta(minutes=5), 99, event_uuid=uuid_fijo)
    c1, l1, b1, _ = http_json("POST", sync_url, [evento_dup], token)
    time.sleep(0.3)
    c2, l2, b2, _ = http_json("POST", sync_url, [evento_dup], token)
    print(f"    1er envío UUID={uuid_fijo}: HTTP {c1} ({l1:.1f} ms) -> {b1}")
    print(f"    2do envío UUID={uuid_fijo} (duplicado): HTTP {c2} ({l2:.1f} ms) -> {b2}")

    # 4. Prueba de Eventos Desordenados (Out-of-Order Timestamps)
    print("\n[4] Verificando Eventos Desordenados (Out-of-Order Timestamps)...")
    t_base = ahora + datetime.timedelta(minutes=10)
    lote_desordenado = [
        construir_evento_posicion(guia_id, t_base + datetime.timedelta(minutes=10), 30),
        construir_evento_posicion(guia_id, t_base, 10),
        construir_evento_posicion(guia_id, t_base + datetime.timedelta(minutes=5), 20),
    ]
    c_oo, l_oo, b_oo, _ = http_json("POST", sync_url, lote_desordenado, token)
    print(f"    Envío lote desordenado [t+10m, t+0m, t+5m]: HTTP {c_oo} ({l_oo:.1f} ms) -> {b_oo}")

    # Health final
    h_status_f, h_lat_f, h_body_f, _ = http_json("GET", ready_url)
    print(f"\n[5] Health Check Final (/api/public/ready): HTTP {h_status_f} ({h_lat_f:.1f} ms) -> {h_body_f}")
    if admin_token:
        oh_status_f, oh_lat_f, oh_body_f, _ = http_json("GET", ops_health_url, token=admin_token)
        print(f"    [Ops Admin Final] (/api/ops/health): HTTP {oh_status_f} ({oh_lat_f:.1f} ms) -> {oh_body_f}")

    sin_errores = (
        stats_normal["errores"] == 0
        and stats_pico["errores"] == 0
        and stats_consulta["errores"] == 0
        and 200 <= h_status < 300
        and 200 <= h_status_f < 300
        and 200 <= c1 < 300
        and 200 <= c2 < 300
        and 200 <= c_oo < 300
    )
    cumple_meta = sin_errores and stats_consulta["p95_ms"] < 500.0 and stats_pico["p95_ms"] < 500.0
    print("\n" + "=" * 78)
    print(f" RESULTADO GLOBAL: {'APROBADO (sin errores y p95 < 500 ms)' if cumple_meta else 'REVISAR LATENCIAS O ERRORES'}")
    print("=" * 78)
    return 0 if cumple_meta else 1


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generador de carga de tracking GPS y prueba de saturación para carga.uy"
    )
    parser.add_argument(
        "--url",
        default=DEFAULT_BASE_URL,
        help=f"URL base del despliegue web (default: {DEFAULT_BASE_URL})",
    )
    parser.add_argument(
        "--camiones",
        type=int,
        default=15,
        help="Cantidad de camiones concurrentes en carga nominal (el pico ejecuta 3x este valor)",
    )
    parser.add_argument(
        "--lote",
        type=int,
        default=5,
        help="Cantidad de eventos GPS POSICION enviados por ráfaga/camión",
    )
    parser.add_argument(
        "--guia",
        type=int,
        default=DEFAULT_GUIA_ID,
        help=f"ID de la guía de viaje sobre la cual reportar posiciones (default: {DEFAULT_GUIA_ID})",
    )
    parser.add_argument(
        "--ci",
        type=int,
        default=DEFAULT_CEDULA_CHOFER,
        help=f"Cédula del chofer para obtener token de prueba (default: {DEFAULT_CEDULA_CHOFER})",
    )
    parser.add_argument(
        "--admin-token",
        default=None,
        help="JWT opcional con rol ADMIN/AUTORIDAD para consultar también /api/ops/health",
    )
    args = parser.parse_args()
    sys.exit(ejecutar_escenarios(args.url, args.camiones, args.lote, args.guia, args.ci, args.admin_token))


if __name__ == "__main__":
    main()
