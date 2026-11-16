#!/usr/bin/env python3
"""
Generador de Carga de Tracking y Prueba de Saturación - carga.uy (Grupo 07, TSE 2026)
=====================================================================================
Script autónomo basado exclusivamente en la biblioteca estándar de Python 3
(`urllib.request`, `concurrent.futures`, `statistics`, `uuid`, `json`, `argparse`)
sin requerir paquetes externos (`pip`).

Reproduce el volumen objetivo modelado para la Sección 3.7.3 y evalúa los SLO de la Sección 4.1.4:
  0. Sonda FaaS en vivo: envía eventos POSICION sin `distanciaDesvioKm` para validar
     la invocación real desde TrackingMDB hacia la FaaS configurada (o su fallback local).
  1. Carga Nominal Sostenida (1x): por defecto 50 ev/s (1.500 camiones / 30 s) en lotes
     de 5 eventos (10 req/s) durante una ventana sostenida configurable (`--duracion`).
  2. Pico de Ráfaga (3x) + Frontoffice Concurrente: 150 ev/s (30 req/s en ráfagas de
     recuperación de cobertura) ejecutándose en paralelo con consultas reales al portal
     Frontoffice (`GET /portal/inicio.xhtml`) y API de lectura (`/api/chofer/eventos/{guia}`),
     verificando p95 < 500 ms (Sección 4.1.4.3.a).
  3. Idempotencia estricta por UUID (Sección 3.7.3.c): verifica ACCEPTED y luego DUPLICATE.
  4. Reconstrucción de eventos fuera de orden (Sección 3.7.3.d): envía timestamps [t+10m, t, t+5m]
     y comprueba ordenamiento cronológico real en `GET /api/chofer/eventos/{guia}`.
  5. Escalones de estrés opcionales (`--escalones-estres`): permite buscar el punto de
     saturación/quiebre de ingesta HTTP + cola JMS observando `/api/ops/metrics`.
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
from typing import Dict, List, Optional, Sequence, Tuple


DEFAULT_BASE_URL = "http://localhost:8080/CargaUY-web"
DEFAULT_CEDULA_CHOFER = 11223344
DEFAULT_GUIA_ID = 1001
DEFAULT_ADMIN_UID = "martin.admin"
DEFAULT_ADMIN_PASSWORD = "admin123"


def http_request(
    method: str,
    url: str,
    payload: Optional[object] = None,
    token: Optional[str] = None,
    correlation_id: Optional[str] = None,
    traceparent: Optional[str] = None,
    timeout: float = 10.0,
) -> Tuple[int, float, object, str]:
    """Ejecuta una petición HTTP y retorna (status_code, latencia_ms, body_parsed_o_texto, corr_header)."""
    corr = correlation_id or str(uuid.uuid4())
    tp = traceparent or f"00-{uuid.uuid4().hex}-{uuid.uuid4().hex[:16]}-01"
    headers = {
        "Accept": "application/json, text/html",
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
            parsed: object = raw
            if raw.lstrip().startswith(("{", "[")):
                try:
                    parsed = json.loads(raw)
                except ValueError:
                    pass
            elif not raw.strip():
                parsed = None
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
    """Obtiene un JWT de desarrollo para chofer desde /api/auth/dev-chofer."""
    url = f"{base_url.rstrip('/')}/api/auth/dev-chofer"
    status, _, body, _ = http_request("POST", url, payload={"ci": cedula})
    if status == 200 and isinstance(body, dict) and "token" in body:
        return str(body["token"])
    return None


def obtener_token_admin(base_url: str, uid: str, password: str) -> Optional[str]:
    """Autentica usuario administrador/autoridad en /api/auth/login para leer métricas de observabilidad."""
    if not uid or not password:
        return None
    url = f"{base_url.rstrip('/')}/api/auth/login"
    status, _, body, _ = http_request("POST", url, payload={"uid": uid, "password": password})
    if status == 200 and isinstance(body, dict) and "token" in body:
        return str(body["token"])
    return None


def leer_ops_json(base_url: str, path: str, admin_token: Optional[str]) -> Optional[Dict[str, object]]:
    """Consulta un endpoint /api/ops/* cuando hay token admin disponible."""
    if not admin_token:
        return None
    url = f"{base_url.rstrip('/')}/{path.lstrip('/')}"
    status, _, body, _ = http_request("GET", url, token=admin_token)
    if status == 200 and isinstance(body, dict):
        return body
    return None


def construir_evento_posicion(
    guia_id: int,
    instante: datetime.datetime,
    paso: int,
    event_uuid: Optional[str] = None,
    distancia_desvio_km: Optional[float] = None,
) -> Dict[str, object]:
    """Genera un EventoViajeDTO POSICION sobre Ruta 5 (tramo cercano a Sarandi Grande / Florida)."""
    progreso = (paso % 100) / 100.0
    # Coordenadas sobre corredor Montevideo -> Sarandi Grande -> Durazno (Ruta 5) para no generar desvios espurios
    lat = -33.7333 + (progreso - 0.5) * 0.04 + random.uniform(-0.0005, 0.0005)
    lon = -56.3333 + (progreso - 0.5) * 0.02 + random.uniform(-0.0005, 0.0005)
    evento: Dict[str, object] = {
        "uuid": event_uuid or str(uuid.uuid4()),
        "tiempo": instante.strftime("%Y-%m-%dT%H:%M:%S"),
        "tipo": "POSICION",
        "latitud": round(lat, 6),
        "longitud": round(lon, 6),
        "guiaId": guia_id,
    }
    if distancia_desvio_km is not None:
        evento["distanciaDesvioKm"] = round(distancia_desvio_km, 2)
    return evento


def calcular_percentil(valores_ordenados: List[float], percentil: float) -> float:
    if not valores_ordenados:
        return 0.0
    idx = (len(valores_ordenados) - 1) * (percentil / 100.0)
    f = math.floor(idx)
    c = math.ceil(idx)
    if f == c:
        return valores_ordenados[int(idx)]
    return valores_ordenados[f] * (c - idx) + valores_ordenados[c] * (idx - f)


def resumir_latencias(nombre: str, resultados: Sequence[Tuple[int, float]], meta_p95_ms: float = 500.0) -> Dict[str, float]:
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
    print(f"  Latencia p95        : {p95:.2f} ms  (Meta < {meta_p95_ms:.0f} ms: {'CUMPLE' if p95 < meta_p95_ms else 'EXCEDE'})")
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


def contar_resultados_sync(body: object) -> Tuple[int, int, int]:
    """Cuenta (accepted, duplicate, errors) en la respuesta JSON de /api/chofer/eventos/sync."""
    if not isinstance(body, list):
        return 0, 0, 0
    acc = dup = err = 0
    for item in body:
        if not isinstance(item, dict):
            continue
        est = str(item.get("estado", "")).upper()
        if est == "ACCEPTED":
            acc += 1
        elif est == "DUPLICATE":
            dup += 1
        else:
            err += 1
    return acc, dup, err


def ejecutar_ventana_ingesta(
    sync_url: str,
    token: Optional[str],
    guia_id: int,
    tasa_ev_s: float,
    duracion_s: float,
    eventos_por_lote: int,
    precalcular_desvio: bool,
    instante_base: datetime.datetime,
    max_workers: int,
) -> Tuple[List[Tuple[int, float]], float, int, int]:
    """
    Envía lotes HTTP a ritmo controlado para sostener `tasa_ev_s` durante `duracion_s`.
    Retorna ([(status, latencia_ms), ...], duracion_real_s, eventos_enviados, eventos_accepted).
    """
    req_por_seg = max(0.5, tasa_ev_s / max(1, eventos_por_lote))
    total_lotes = max(1, int(round(req_por_seg * duracion_s)))
    intervalo = 1.0 / req_por_seg
    desvio_val = 0.0 if precalcular_desvio else None

    lotes = [
        [
            construir_evento_posicion(
                guia_id,
                instante_base + datetime.timedelta(seconds=i * 0.2 + j * 0.02),
                i + j,
                distancia_desvio_km=desvio_val,
            )
            for j in range(eventos_por_lote)
        ]
        for i in range(total_lotes)
    ]

    resultados: List[Tuple[int, float]] = []
    eventos_accepted = 0
    futuros: List[concurrent.futures.Future] = []

    t_inicio = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as pool:
        for idx, lote in enumerate(lotes):
            objetivo = t_inicio + idx * intervalo
            espera = objetivo - time.perf_counter()
            if espera > 0.001:
                time.sleep(espera)
            futuros.append(pool.submit(http_request, "POST", sync_url, lote, token))

        for fut in concurrent.futures.as_completed(futuros):
            code, lat, body, _ = fut.result()
            resultados.append((code, lat))
            if 200 <= code < 300:
                acc, dup, _ = contar_resultados_sync(body)
                eventos_accepted += acc + dup

    duracion_real = max(0.001, time.perf_counter() - t_inicio)
    return resultados, duracion_real, total_lotes * eventos_por_lote, eventos_accepted


def esperar_drenaje_cola(
    base_url: str,
    admin_token: Optional[str],
    metrics_base: Optional[Dict[str, object]],
    delta_esperado: int,
    timeout_s: float = 15.0,
) -> Optional[Dict[str, object]]:
    """Espera hasta que TrackingMDB drene los mensajes encolados y devuelve la lectura final de /api/ops/metrics."""
    if not admin_token or not metrics_base or delta_esperado <= 0:
        return None
    proc_ini = int(metrics_base.get("trackingProcesados", 0))
    dup_ini = int(metrics_base.get("trackingDuplicados", 0))
    err_ini = int(metrics_base.get("trackingErrores", 0))
    limite = time.perf_counter() + timeout_s
    ultimo = None
    while time.perf_counter() < limite:
        ultimo = leer_ops_json(base_url, "/api/ops/metrics", admin_token)
        if ultimo is not None:
            proc_delta = int(ultimo.get("trackingProcesados", 0)) - proc_ini
            dup_delta = int(ultimo.get("trackingDuplicados", 0)) - dup_ini
            err_delta = int(ultimo.get("trackingErrores", 0)) - err_ini
            if proc_delta + dup_delta + err_delta >= delta_esperado:
                return ultimo
        time.sleep(0.35)
    return ultimo


def esperar_eventos_en_guia(
    consulta_api_url: str,
    token: Optional[str],
    uuids_objetivo: Sequence[str],
    timeout_s: float = 6.0,
) -> Tuple[int, float, List[Dict[str, object]]]:
    """Consulta periódicamente GET /api/chofer/eventos/{guia} hasta encontrar los UUIDs esperados."""
    objetivos = set(uuids_objetivo)
    cg, lg = 0, 0.0
    filtrados: List[Dict[str, object]] = []
    limite = time.perf_counter() + timeout_s
    while time.perf_counter() < limite:
        cg, lg, bg, _ = http_request("GET", consulta_api_url, token=token)
        if cg == 200 and isinstance(bg, list):
            filtrados = [ev for ev in bg if isinstance(ev, dict) and ev.get("uuid") in objetivos]
            if len(filtrados) >= len(objetivos):
                return cg, lg, filtrados
        time.sleep(0.35)
    return cg, lg, filtrados


def ejecutar_escenarios(args: argparse.Namespace) -> int:
    base_url = args.url.rstrip("/")
    guia_id = args.guia
    cedula = args.ci
    lote_sz = max(1, args.lote)

    # Compatibilidad con uso heredado --camiones N (si no se fija --tasa explicitamente)
    tasa_nominal = args.tasa
    if args.camiones is not None and args.tasa == 50.0:
        tasa_nominal = max(10.0, float(args.camiones * lote_sz))

    duracion = max(1.0, args.duracion)
    precalcular_masivo = args.modo_carga != "faas-completo"

    print("=" * 80)
    print(" CARGA.UY - GENERADOR DE CARGA DE TRACKING Y PRUEBA DE SATURACION (TSE 2026)")
    print("=" * 80)
    print(f"Target URL          : {base_url}")
    print(f"Modelo Sección 3.7.3: {tasa_nominal:.1f} ev/s nominal (1x) -> {tasa_nominal * 3.0:.1f} ev/s pico (3x)")
    print(f"Ventana sostenida   : {duracion:.1f} s/fase | Lote B={lote_sz} eventos POSICION")
    print(f"Modo cálculo FaaS   : {args.modo_carga} | Guía ID={guia_id} | Chofer CI={cedula}")

    ready_url = f"{base_url}/api/public/ready"
    h_status, h_lat, h_body, h_corr = http_request("GET", ready_url)
    print(f"\n[0] Health Check Inicial (/api/public/ready): HTTP {h_status} ({h_lat:.1f} ms) | X-Correlation-ID={h_corr}")
    if h_status == 0:
        print("    [!] El servidor no se encuentra accesible en la URL indicada.")
        print("    [!] Asegúrese de tener levantado el entorno (`docker compose up -d`).")
        return 1
    if isinstance(h_body, dict):
        print(f"    Estado público: {json.dumps(h_body)}")

    token = obtener_token_chofer(base_url, cedula)
    if token:
        print(f"[Auth] Token JWT de chofer obtenido exitosamente (CI {cedula}).")
    else:
        print("[Auth] No se pudo obtener token dev-chofer.")

    admin_token = args.admin_token or obtener_token_admin(base_url, args.admin_uid, args.admin_password)
    if admin_token:
        print("[Auth] Token ADMIN activo: se recolectarán métricas reales de /api/ops/metrics y /api/ops/health.")
    else:
        print("[Auth] Sin token ADMIN (use --admin-uid/--admin-password o --admin-token para métricas internas).")

    ops_health_ini = leer_ops_json(base_url, "/api/ops/health", admin_token)
    metrics_ini = leer_ops_json(base_url, "/api/ops/metrics", admin_token)
    if ops_health_ini:
        print(f"    [Ops Health Inicial] {json.dumps(ops_health_ini)}")

    sync_url = f"{base_url}/api/chofer/eventos/sync"
    consulta_api_url = f"{base_url}/api/chofer/eventos/{guia_id}"
    portal_url = f"{base_url}/portal/inicio.xhtml"
    ahora = datetime.datetime.now()
    total_accepted_esperado = 0

    # 0B. Sonda FaaS en vivo (eventos sin distanciaDesvioKm)
    faas_sonda_ok = True
    if args.modo_carga in ("faas-mixto", "faas-completo"):
        print("\n[0B] Sonda FaaS en vivo (2 eventos POSICION sin distanciaDesvioKm -> TrackingMDB -> Vercel FaaS)...")
        uuid_sonda_1 = f"loadtest-faas-1-{uuid.uuid4()}"
        uuid_sonda_2 = f"loadtest-faas-2-{uuid.uuid4()}"
        lote_sonda = [
            construir_evento_posicion(guia_id, ahora - datetime.timedelta(seconds=2), 1, event_uuid=uuid_sonda_1),
            construir_evento_posicion(guia_id, ahora - datetime.timedelta(seconds=1), 2, event_uuid=uuid_sonda_2),
        ]
        cs, ls, bs, _ = http_request("POST", sync_url, lote_sonda, token)
        acc_s, dup_s, _ = contar_resultados_sync(bs)
        total_accepted_esperado += acc_s + dup_s
        print(f"    Envío sonda FaaS: HTTP {cs} ({ls:.1f} ms) -> ACCEPTED={acc_s}")
        cg, lg, eventos_sonda = esperar_eventos_en_guia(consulta_api_url, token, (uuid_sonda_1, uuid_sonda_2), timeout_s=6.0)
        encontrados = {str(ev["uuid"]): ev.get("distanciaDesvioKm") for ev in eventos_sonda if "uuid" in ev}
        faas_sonda_ok = (cs == 200 and len(encontrados) == 2 and all(v is not None for v in encontrados.values()))
        print(f"    Verificación en GET /api/chofer/eventos/{guia_id}: HTTP {cg} ({lg:.1f} ms) -> desvíos={list(encontrados.values())}")

    # 1. Escenario 1: Carga Nominal Sostenida (1x = 50 ev/s)
    print(f"\n[1] Ejecutando Carga Nominal Sostenida (1x = {tasa_nominal:.1f} ev/s durante {duracion:.1f} s)...")
    res_1x, dur_1x, env_1x, acc_1x = ejecutar_ventana_ingesta(
        sync_url=sync_url,
        token=token,
        guia_id=guia_id,
        tasa_ev_s=tasa_nominal,
        duracion_s=duracion,
        eventos_por_lote=lote_sz,
        precalcular_desvio=precalcular_masivo,
        instante_base=ahora,
        max_workers=min(32, max(8, int(tasa_nominal / lote_sz) + 4)),
    )
    total_accepted_esperado += acc_1x
    print(f"    Eventos enviados: {env_1x} ({acc_1x} ACCEPTED) en {dur_1x:.2f} s -> {env_1x / dur_1x:.1f} ev/s efectivos ({len(res_1x) / dur_1x:.1f} req/s)")
    stats_1x = resumir_latencias("Escenario 1: Ingesta Carga Nominal (1x)", res_1x, meta_p95_ms=500.0)

    # 2. Escenario 2: Pico de Ráfaga 3x + Consultas Frontoffice JSF + API concurrentes
    tasa_pico = tasa_nominal * 3.0
    print(f"\n[2] Ejecutando Pico de Ráfaga (3x = {tasa_pico:.1f} ev/s durante {duracion:.1f} s)")
    print("    + Consultas concurrentes de Frontoffice (GET /portal/inicio.xhtml y GET /api/chofer/eventos/{guia})...")

    res_consultas_portal: List[Tuple[int, float]] = []
    res_consultas_api: List[Tuple[int, float]] = []
    detener_consultas = False

    def loop_consultas_frontoffice(pool_consultas: concurrent.futures.ThreadPoolExecutor) -> Tuple[List[concurrent.futures.Future], List[concurrent.futures.Future]]:
        f_portal = []
        f_api = []
        while not detener_consultas:
            f_portal.append(pool_consultas.submit(http_request, "GET", portal_url))
            f_api.append(pool_consultas.submit(http_request, "GET", consulta_api_url, None, token))
            time.sleep(0.18)
        return f_portal, f_api

    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool_consultas:
        fut_driver = pool_consultas.submit(loop_consultas_frontoffice, pool_consultas)
        res_3x, dur_3x, env_3x, acc_3x = ejecutar_ventana_ingesta(
            sync_url=sync_url,
            token=token,
            guia_id=guia_id,
            tasa_ev_s=tasa_pico,
            duracion_s=duracion,
            eventos_por_lote=lote_sz,
            precalcular_desvio=precalcular_masivo,
            instante_base=ahora + datetime.timedelta(minutes=2),
            max_workers=min(64, max(16, int(tasa_pico / lote_sz) + 8)),
        )
        detener_consultas = True
        f_portal, f_api = fut_driver.result()
        for f in concurrent.futures.as_completed(f_portal):
            code, lat, _, _ = f.result()
            res_consultas_portal.append((code, lat))
        for f in concurrent.futures.as_completed(f_api):
            code, lat, _, _ = f.result()
            res_consultas_api.append((code, lat))

    total_accepted_esperado += acc_3x
    print(f"    Eventos enviados: {env_3x} ({acc_3x} ACCEPTED) en {dur_3x:.2f} s -> {env_3x / dur_3x:.1f} ev/s efectivos ({len(res_3x) / dur_3x:.1f} req/s)")
    stats_3x = resumir_latencias("Escenario 2A: Ingesta Pico de Ráfaga (3x)", res_3x, meta_p95_ms=500.0)
    stats_portal = resumir_latencias("Escenario 2B: Consultas Web Frontoffice (portal/inicio.xhtml) bajo Pico 3x", res_consultas_portal, meta_p95_ms=500.0)
    stats_api = resumir_latencias("Escenario 2C: Consultas API Viaje (/api/chofer/eventos/{guia}) bajo Pico 3x", res_consultas_api, meta_p95_ms=500.0)

    # 3. Idempotencia estricta por UUID (Sección 3.7.3.c / AC012)
    print("\n[3] Verificando Idempotencia Estricta por UUID (Sección 3.7.3.c / AC012)...")
    uuid_fijo = f"loadtest-dup-{uuid.uuid4()}"
    evento_dup = construir_evento_posicion(
        guia_id, ahora + datetime.timedelta(minutes=5), 99, event_uuid=uuid_fijo,
        distancia_desvio_km=(0.0 if precalcular_masivo else None),
    )
    c1, l1, b1, _ = http_request("POST", sync_url, [evento_dup], token)
    esperar_eventos_en_guia(consulta_api_url, token, (uuid_fijo,), timeout_s=4.0)
    c2, l2, b2, _ = http_request("POST", sync_url, [evento_dup], token)
    acc_d1, _, _ = contar_resultados_sync(b1)
    _, dup_d2, _ = contar_resultados_sync(b2)
    total_accepted_esperado += acc_d1
    idempotencia_ok = (c1 == 200 and c2 == 200 and acc_d1 == 1 and dup_d2 == 1)
    print(f"    1er envío UUID={uuid_fijo}: HTTP {c1} ({l1:.1f} ms) -> {b1}")
    print(f"    2do envío (duplicado)    : HTTP {c2} ({l2:.1f} ms) -> {b2} | OK={idempotencia_ok}")

    # 4. Eventos fuera de orden (Out-of-Order Timestamps — Sección 3.7.3.d)
    print("\n[4] Verificando Reconstrucción Cronológica ante Eventos Fuera de Orden (Sección 3.7.3.d)...")
    t_base = ahora + datetime.timedelta(minutes=10)
    uuid_t10 = f"loadtest-ooo-10-{uuid.uuid4()}"
    uuid_t00 = f"loadtest-ooo-00-{uuid.uuid4()}"
    uuid_t05 = f"loadtest-ooo-05-{uuid.uuid4()}"
    lote_desordenado = [
        construir_evento_posicion(guia_id, t_base + datetime.timedelta(minutes=10), 30, event_uuid=uuid_t10, distancia_desvio_km=(0.0 if precalcular_masivo else None)),
        construir_evento_posicion(guia_id, t_base, 10, event_uuid=uuid_t00, distancia_desvio_km=(0.0 if precalcular_masivo else None)),
        construir_evento_posicion(guia_id, t_base + datetime.timedelta(minutes=5), 20, event_uuid=uuid_t05, distancia_desvio_km=(0.0 if precalcular_masivo else None)),
    ]
    c_oo, l_oo, b_oo, _ = http_request("POST", sync_url, lote_desordenado, token)
    acc_oo, _, _ = contar_resultados_sync(b_oo)
    total_accepted_esperado += acc_oo
    c_ord, l_ord, eventos_ooo = esperar_eventos_en_guia(consulta_api_url, token, (uuid_t00, uuid_t05, uuid_t10), timeout_s=6.0)
    orden_recibido = [ev.get("uuid") for ev in eventos_ooo]
    out_of_order_ok = (orden_recibido == [uuid_t00, uuid_t05, uuid_t10])
    print(f"    Envío desordenado [t+10m, t+0m, t+5m]: HTTP {c_oo} ({l_oo:.1f} ms)")
    print(f"    Orden devuelto por API ({l_ord:.1f} ms)  : esperado=[t+0m, t+5m, t+10m] -> OK={out_of_order_ok}")

    # 5. Escalones de estrés / punto de saturación opcionales
    stats_estres: List[Tuple[float, Dict[str, float], float]] = []
    if args.escalones_estres:
        escalones = [float(x.strip()) for x in args.escalones_estres.split(",") if x.strip()]
        dur_est = max(1.0, args.duracion_estres)
        for esc in escalones:
            print(f"\n[5] Escalón de Estrés ({esc:.0f} ev/s durante {dur_est:.1f} s)...")
            res_e, dur_e, env_e, acc_e = ejecutar_ventana_ingesta(
                sync_url=sync_url,
                token=token,
                guia_id=guia_id,
                tasa_ev_s=esc,
                duracion_s=dur_est,
                eventos_por_lote=lote_sz,
                precalcular_desvio=True,
                instante_base=ahora + datetime.timedelta(minutes=25),
                max_workers=min(96, max(16, int(esc / lote_sz) + 12)),
            )
            total_accepted_esperado += acc_e
            eff_e = env_e / dur_e
            print(f"    Throughput efectivo: {eff_e:.1f} ev/s ({env_e} enviados / {acc_e} ACCEPTED en {dur_e:.2f} s)")
            st_e = resumir_latencias(f"Escalón Estrés {esc:.0f} ev/s", res_e, meta_p95_ms=500.0)
            stats_estres.append((esc, st_e, eff_e))

    # 6. Espera de drenaje JMS y lectura final de observabilidad
    print("\n[6] Verificando drenaje de cola JMS y métricas internas de WildFly...")
    metrics_fin = esperar_drenaje_cola(base_url, admin_token, metrics_ini, total_accepted_esperado)
    ops_health_fin = leer_ops_json(base_url, "/api/ops/health", admin_token)
    h_status_f, h_lat_f, h_body_f, _ = http_request("GET", ready_url)
    print(f"    Health Check Final (/api/public/ready): HTTP {h_status_f} ({h_lat_f:.1f} ms) -> {h_body_f}")

    cero_perdidos = True
    if metrics_ini and metrics_fin:
        enc_delta = int(metrics_fin.get("trackingEncolados", 0)) - int(metrics_ini.get("trackingEncolados", 0))
        proc_delta = int(metrics_fin.get("trackingProcesados", 0)) - int(metrics_ini.get("trackingProcesados", 0))
        dup_mdb_delta = int(metrics_fin.get("trackingDuplicados", 0)) - int(metrics_ini.get("trackingDuplicados", 0))
        err_mdb_delta = int(metrics_fin.get("trackingErrores", 0)) - int(metrics_ini.get("trackingErrores", 0))
        lat_mdb_delta = int(metrics_fin.get("trackingLatenciaTotalMs", 0)) - int(metrics_ini.get("trackingLatenciaTotalMs", 0))
        lat_mdb_prom = (lat_mdb_delta / proc_delta) if proc_delta > 0 else 0.0
        cero_perdidos = (enc_delta >= total_accepted_esperado and proc_delta + dup_mdb_delta >= total_accepted_esperado and err_mdb_delta == 0)
        print(f"    [Ops Metrics Delta] encolados={enc_delta}, esperados={total_accepted_esperado}, "
              f"procesadosMDB={proc_delta}, duplicadosMDB={dup_mdb_delta}, erroresMDB={err_mdb_delta}, "
              f"latenciaMediaMDB={lat_mdb_prom:.2f} ms/evento")
    if ops_health_fin:
        print(f"    [Ops Health Final]  {json.dumps(ops_health_fin)}")

    cumple_slo = (
        stats_1x["errores"] == 0
        and stats_3x["errores"] == 0
        and stats_portal["errores"] == 0
        and stats_api["errores"] == 0
        and stats_portal["p95_ms"] < 500.0
        and stats_api["p95_ms"] < 500.0
        and 200 <= h_status < 300
        and 200 <= h_status_f < 300
        and faas_sonda_ok
        and idempotencia_ok
        and out_of_order_ok
        and cero_perdidos
    )

    print("\n" + "=" * 80)
    print(f" RESULTADO GLOBAL: {'APROBADO (SLO p95 < 500 ms, FaaS, Idempotencia, Orden y 0 pérdidas)' if cumple_slo else 'REVISAR LATENCIAS, METRICAS O ERRORES'}")
    print("=" * 80)
    return 0 if cumple_slo else 1


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generador de carga de tracking GPS (Sección 3.7.3 y 4.1.4) para carga.uy"
    )
    parser.add_argument("--url", default=DEFAULT_BASE_URL, help=f"URL base del despliegue web (default: {DEFAULT_BASE_URL})")
    parser.add_argument("--tasa", type=float, default=50.0, help="Tasa nominal objetivo en eventos/seg para 1x (pico ejecuta 3x). Default: 50")
    parser.add_argument("--duracion", type=float, default=4.0, help="Duración en segundos de cada ventana sostenida (1x y 3x). Default: 4")
    parser.add_argument("--lote", type=int, default=5, help="Cantidad de eventos GPS POSICION por petición POST (default: 5)")
    parser.add_argument("--camiones", type=int, default=None, help="Override opcional compatible con uso previo (tasa = camiones * lote)")
    parser.add_argument("--guia", type=int, default=DEFAULT_GUIA_ID, help=f"ID de guía activa (default: {DEFAULT_GUIA_ID})")
    parser.add_argument("--ci", type=int, default=DEFAULT_CEDULA_CHOFER, help=f"Cédula del chofer (default: {DEFAULT_CEDULA_CHOFER})")
    parser.add_argument(
        "--modo-carga",
        choices=["faas-mixto", "faas-completo", "precalculado"],
        default="faas-mixto",
        help="faas-mixto (default: sonda live a FaaS + carga sostenida precalculada), faas-completo (todo pasa por FaaS), precalculado (sin FaaS)",
    )
    parser.add_argument("--escalones-estres", default="", help="Lista CSV de tasas adicionales en ev/s (ej: 250,500,1000) para probar saturación")
    parser.add_argument("--duracion-estres", type=float, default=3.0, help="Duración en segundos de cada escalón de estrés (default: 3)")
    parser.add_argument("--admin-uid", default=DEFAULT_ADMIN_UID, help=f"Usuario admin para /api/ops/metrics (default: {DEFAULT_ADMIN_UID})")
    parser.add_argument("--admin-password", default=DEFAULT_ADMIN_PASSWORD, help="Contraseña admin para /api/ops/metrics")
    parser.add_argument("--admin-token", default=None, help="JWT ADMIN explícito (opcional, reemplaza --admin-uid/--admin-password)")
    args = parser.parse_args()
    sys.exit(ejecutar_escenarios(args))


if __name__ == "__main__":
    main()
