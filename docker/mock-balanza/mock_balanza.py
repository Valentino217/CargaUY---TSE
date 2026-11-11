import json
import os
import random
import re
import sqlite3
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


DB_PATH = os.environ.get("BALANZA_DB_PATH", "/data/balanzas.db")
PORT = int(os.environ.get("PORT", "8080"))
UY_TZ = timezone(timedelta(hours=-3))


def ahora_uy() -> datetime:
    return datetime.now(UY_TZ)

_LOCK = threading.Lock()
_CONFIG = {
    "modoFallo": False,
    "latenciaMs": 0,
}

PEAJES_RUTA = [
    "Peaje Pando - Ruta 8",
    "Peaje Paso de los Toros - Ruta 5",
    "Peaje Santa Lucía - Ruta 11",
    "Peaje Mercedes - Ruta 2",
    "Peaje Queguay - Ruta 3",
    "Peaje Solís - Ruta 9",
    "Peaje Manuel Díaz - Ruta 5",
]


def normalizar_matricula(matricula: str) -> str:
    if not matricula:
        return ""
    return re.sub(r"[\s-]+", "", matricula).upper()


def log_json(evento: str, **campos):
    registro = {
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "service": "nodo-balanza",
        "evento": evento,
    }
    registro.update(campos)
    print(json.dumps(registro, ensure_ascii=False), flush=True)


def init_db() -> sqlite3.Connection:
    db_path = DB_PATH
    if db_path != ":memory:":
        parent = os.path.dirname(db_path)
        if parent:
            try:
                os.makedirs(parent, exist_ok=True)
            except OSError:
                db_path = ":memory:"

    conn = sqlite3.connect(db_path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    with conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS pesadas_peaje (
                id_pesada INTEGER PRIMARY KEY AUTOINCREMENT,
                matricula TEXT NOT NULL,
                matricula_norm TEXT NOT NULL,
                nro_empresa INTEGER,
                fecha TEXT NOT NULL,
                hora TEXT NOT NULL,
                peso_registrado INTEGER NOT NULL,
                peaje TEXT NOT NULL,
                estado TEXT NOT NULL DEFAULT 'OK'
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_pesadas_mat_fecha ON pesadas_peaje (matricula_norm, fecha)"
        )
        count = conn.execute("SELECT COUNT(*) FROM pesadas_peaje").fetchone()[0]
        if count == 0:
            hoy = ahora_uy().date()
            ayer = hoy - timedelta(days=1)
            hace_3_dias = hoy - timedelta(days=3)
            semilla = [
                ("STP 1234", 101, hoy.isoformat(), "08:15:00", 37200, "Peaje Pando - Ruta 8"),
                ("STP 1234", 101, hoy.isoformat(), "11:40:00", 38900, "Peaje Paso de los Toros - Ruta 5"),
                ("STP 1234", 101, hace_3_dias.isoformat(), "14:30:00", 44200, "Peaje Queguay - Ruta 3"),
                ("LOR 2024", 102, hoy.isoformat(), "09:10:00", 36800, "Peaje Santa Lucía - Ruta 11"),
                ("LOR 2024", 102, hoy.isoformat(), "12:25:00", 37500, "Peaje Mercedes - Ruta 2"),
                ("STP 5678", 101, hoy.isoformat(), "10:05:00", 36400, "Peaje Pando - Ruta 8"),
                ("EXP 1111", 103, hoy.isoformat(), "07:50:00", 39800, "Peaje Manuel Díaz - Ruta 5"),
                ("LOR 9999", 102, ayer.isoformat(), "16:00:00", 29500, "Peaje Solís - Ruta 9"),
            ]
            for mat, emp, f, h, peso, peaje in semilla:
                conn.execute(
                    """
                    INSERT INTO pesadas_peaje (matricula, matricula_norm, nro_empresa, fecha, hora, peso_registrado, peaje, estado)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'OK')
                    """,
                    (mat, normalizar_matricula(mat), emp, f, h, peso, peaje),
                )
    return conn


_CONN = init_db()


def row_to_dict(row: sqlite3.Row) -> dict:
    return {
        "idPesada": row["id_pesada"],
        "fecha": row["fecha"],
        "hora": row["hora"],
        "pesoRegistrado": row["peso_registrado"],
        "matricula": row["matricula"],
        "nroEmpresa": row["nro_empresa"],
        "peaje": row["peaje"],
        "estado": row["estado"],
    }


def consultar_pesadas(
    matricula: str | None,
    fecha: str | None,
    fecha_desde: str | None,
    fecha_hasta: str | None,
    nro_empresa: int | None,
    autogenerar: bool,
) -> list[dict]:
    filtros = []
    args = []

    mat_norm = normalizar_matricula(matricula) if matricula else ""
    if mat_norm:
        filtros.append("matricula_norm = ?")
        args.append(mat_norm)
    if fecha:
        filtros.append("fecha = ?")
        args.append(fecha)
    else:
        if fecha_desde:
            filtros.append("fecha >= ?")
            args.append(fecha_desde)
        if fecha_hasta:
            filtros.append("fecha <= ?")
            args.append(fecha_hasta)
    if nro_empresa is not None:
        filtros.append("(nro_empresa = ? OR nro_empresa IS NULL)")
        args.append(nro_empresa)

    where_sql = ("WHERE " + " AND ".join(filtros)) if filtros else ""
    sql = f"SELECT * FROM pesadas_peaje {where_sql} ORDER BY fecha ASC, hora ASC, id_pesada ASC"

    with _LOCK:
        rows = _CONN.execute(sql, args).fetchall()
        if not rows and autogenerar and mat_norm:
            now_uy = ahora_uy()
            fecha_eff = fecha if fecha else now_uy.strftime("%Y-%m-%d")
            hora_eff = now_uy.strftime("%H:%M:%S")
            peso_gen = random.randint(29000, 41500)
            peaje_gen = random.choice(PEAJES_RUTA)
            with _CONN:
                cur = _CONN.execute(
                    """
                    INSERT INTO pesadas_peaje (matricula, matricula_norm, nro_empresa, fecha, hora, peso_registrado, peaje, estado)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'OK')
                    """,
                    (matricula.strip(), mat_norm, nro_empresa, fecha_eff, hora_eff, peso_gen, peaje_gen),
                )
                new_id = cur.lastrowid
                rows = _CONN.execute("SELECT * FROM pesadas_peaje WHERE id_pesada = ?", (new_id,)).fetchall()

    return [row_to_dict(r) for r in rows]


def insertar_pesada(data: dict) -> dict:
    matricula = str(data.get("matricula") or "").strip()
    if not matricula:
        raise ValueError("El campo 'matricula' es obligatorio.")
    peso = int(data.get("pesoRegistrado") or 0)
    if peso <= 0:
        raise ValueError("El campo 'pesoRegistrado' debe ser mayor a 0.")

    now = ahora_uy()
    fecha = str(data.get("fecha") or now.strftime("%Y-%m-%d")).strip()
    hora = str(data.get("hora") or now.strftime("%H:%M:%S")).strip()
    if len(hora) == 5:
        hora += ":00"
    nro_empresa_raw = data.get("nroEmpresa")
    nro_empresa = int(nro_empresa_raw) if nro_empresa_raw not in (None, "") else None
    peaje = str(data.get("peaje") or random.choice(PEAJES_RUTA)).strip()

    with _LOCK:
        with _CONN:
            cur = _CONN.execute(
                """
                INSERT INTO pesadas_peaje (matricula, matricula_norm, nro_empresa, fecha, hora, peso_registrado, peaje, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'OK')
                """,
                (matricula, normalizar_matricula(matricula), nro_empresa, fecha, hora, peso, peaje),
            )
            row = _CONN.execute("SELECT * FROM pesadas_peaje WHERE id_pesada = ?", (cur.lastrowid,)).fetchone()
            return row_to_dict(row)


class BalanzaHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Silenciar log plano de BaseHTTPRequestHandler para usar log estructurado JSON
        pass

    def _correlacion(self) -> str:
        return self.headers.get("X-Correlation-ID") or ""

    def _enviar_json(self, status: int, payload, correlacion: str):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if correlacion:
            self.send_header("X-Correlation-ID", correlacion)
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
                {"status": "DOWN", "error": "Nodo de balanzas temporalmente indisponible (simulación)"},
                correlacion,
            )
            log_json("balanza.http.error", correlacion=correlacion, path=path, status=503)
            return False
        return True

    def do_GET(self):
        inicio = time.monotonic()
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        correlacion = self._correlacion()

        if parsed.path == "/health":
            with _LOCK:
                modo_fallo = _CONFIG["modoFallo"]
                total = _CONN.execute("SELECT COUNT(*) FROM pesadas_peaje").fetchone()[0]
            if modo_fallo:
                self._enviar_json(503, {"status": "DOWN", "service": "nodo-balanza"}, correlacion)
                return
            self._enviar_json(200, {"status": "UP", "service": "nodo-balanza", "totalPesadas": total}, correlacion)
            return

        if parsed.path not in ("/", "/pesadas"):
            self._enviar_json(404, {"error": "Endpoint no encontrado"}, correlacion)
            return

        if not self._aplicar_simulacion_entorno(correlacion, parsed.path):
            return

        matricula_param = params.get("matricula", [None])[0]
        fecha_param = params.get("fecha", [None])[0]
        fecha_desde_param = params.get("fechaDesde", [None])[0]
        fecha_hasta_param = params.get("fechaHasta", [None])[0]
        emp_param = params.get("nroEmpresa", [None])[0]
        autogenerar = (params.get("autogenerar", ["false"])[0] or "false").lower() == "true"

        nro_empresa = None
        if emp_param:
            try:
                nro_empresa = int(emp_param)
            except ValueError:
                nro_empresa = None

        if parsed.path == "/pesadas" or matricula_param or fecha_param or fecha_desde_param or fecha_hasta_param or emp_param:
            resultados = consultar_pesadas(
                matricula_param, fecha_param, fecha_desde_param, fecha_hasta_param, nro_empresa, autogenerar
            )
            duracion_ms = round((time.monotonic() - inicio) * 1000, 2)
            self._enviar_json(200, resultados, correlacion)
            log_json(
                "balanza.consulta",
                correlacion=correlacion,
                metodo="GET",
                path=parsed.path,
                matricula=matricula_param,
                fecha=fecha_param,
                fechaDesde=fecha_desde_param,
                fechaHasta=fecha_hasta_param,
                nroEmpresa=nro_empresa,
                cantidadResultados=len(resultados),
                duracionMs=duracion_ms,
                status=200,
            )
            return

        # Compatibilidad hacia atrás para GET / sin parámetros
        with _LOCK:
            row = _CONN.execute("SELECT * FROM pesadas_peaje ORDER BY id_pesada DESC LIMIT 1").fetchone()
        now_uy = ahora_uy()
        payload = row_to_dict(row) if row else {
            "idPesada": 1,
            "fecha": now_uy.strftime("%Y-%m-%d"),
            "hora": now_uy.strftime("%H:%M:%S"),
            "pesoRegistrado": 32500,
            "matricula": "STP 1234",
            "peaje": "Peaje Pando - Ruta 8",
            "estado": "OK",
        }
        self._enviar_json(200, payload, correlacion)

    def do_POST(self):
        inicio = time.monotonic()
        parsed = urlparse(self.path)
        correlacion = self._correlacion()

        length = int(self.headers.get("Content-Length") or 0)
        raw_body = self.rfile.read(length) if length > 0 else b"{}"
        try:
            data = json.loads(raw_body.decode("utf-8"))
        except Exception:
            self._enviar_json(400, {"error": "JSON inválido"}, correlacion)
            return

        if parsed.path == "/config":
            with _LOCK:
                if "modoFallo" in data:
                    _CONFIG["modoFallo"] = bool(data["modoFallo"])
                if "latenciaMs" in data:
                    _CONFIG["latenciaMs"] = max(0, int(data["latenciaMs"]))
                cfg = dict(_CONFIG)
            self._enviar_json(200, cfg, correlacion)
            log_json("balanza.config", correlacion=correlacion, config=cfg)
            return

        if parsed.path == "/pesadas":
            if not self._aplicar_simulacion_entorno(correlacion, parsed.path):
                return
            try:
                creada = insertar_pesada(data)
            except ValueError as ve:
                self._enviar_json(400, {"error": str(ve)}, correlacion)
                return

            duracion_ms = round((time.monotonic() - inicio) * 1000, 2)
            self._enviar_json(201, creada, correlacion)
            log_json(
                "balanza.pesada.registrada",
                correlacion=correlacion,
                metodo="POST",
                path=parsed.path,
                idPesada=creada["idPesada"],
                matricula=creada["matricula"],
                fecha=creada["fecha"],
                pesoRegistrado=creada["pesoRegistrado"],
                peaje=creada["peaje"],
                duracionMs=duracion_ms,
                status=201,
            )
            return

        self._enviar_json(404, {"error": "Endpoint no encontrado"}, correlacion)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), BalanzaHandler)
    log_json("balanza.startup", port=PORT, dbPath=DB_PATH)
    server.serve_forever()
