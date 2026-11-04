import json
import random
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs


class BalanzaHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        now = datetime.now()

        matricula_param = params.get("matricula", [None])[0]
        fecha_param = params.get("fecha", [None])[0]
        correlacion = self.headers.get("X-Correlation-ID")

        fecha_str = fecha_param if fecha_param else now.strftime("%Y-%m-%d")
        matricula_str = matricula_param if matricula_param else "123456"

        if matricula_param or fecha_param:
            # Retorna lista de pesadas asociadas al vehículo y fecha solicitada (Sección 4.3.5)
            payload = [
                {
                    "idPesada": random.randint(1000, 4999),
                    "fecha": fecha_str,
                    "hora": "08:30:00",
                    "pesoRegistrado": random.randint(24000, 34000),
                    "matricula": matricula_str,
                    "estado": "OK",
                },
                {
                    "idPesada": random.randint(5000, 9999),
                    "fecha": fecha_str,
                    "hora": now.strftime("%H:%M:%S"),
                    "pesoRegistrado": random.randint(34001, 44500),
                    "matricula": matricula_str,
                    "estado": "OK",
                },
            ]
        else:
            # Compatibilidad hacia atrás para GET / sin parámetros
            payload = {
                "idPesada": random.randint(1000, 9999),
                "fecha": fecha_str,
                "hora": now.strftime("%H:%M:%S"),
                "pesoRegistrado": random.randint(20000, 45000),
                "matricula": matricula_str,
                "estado": "OK",
            }

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        if correlacion:
            self.send_header("X-Correlation-ID", correlacion)
        self.end_headers()
        self.wfile.write(json.dumps(payload).encode("utf-8"))


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8080), BalanzaHandler)
    print("Mock Balanza REST Server listening on port 8080...")
    server.serve_forever()
