import json
import random
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler

class BalanzaHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        now = datetime.now()
        data = {
            "idPesada": random.randint(1000, 9999),
            "fecha": now.strftime("%Y-%m-%d"),
            "hora": now.strftime("%H:%M:%S"),
            "pesoRegistrado": random.randint(20000, 45000),
            "matricula": "123456",
            "estado": "OK"
        }
        
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8080), BalanzaHandler)
    print("Mock Balanza REST Server listening on port 8080...")
    server.serve_forever()
