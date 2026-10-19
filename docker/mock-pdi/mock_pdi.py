import json, os, time, uuid, ssl
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

S = "http://schemas.xmlsoap.org/soap/envelope/"
A = "http://www.w3.org/2005/08/addressing"
P = "http://pdi.dnic.gub.uy/"
ACTION = "urn:cargauy:pdi:consultarCiudadano"
for prefix, ns in [("soap", S), ("wsa", A), ("pdi", P)]:
    ET.register_namespace(prefix, ns)
CATALOGO = {
    12345678: ("Juan", "Perez", "1985-05-15"),
    80000001: ("Ana", "Prueba", "1990-01-01"),
    80000002: ("Diego", "Chofer", "1988-01-01"),
}
def child(parent, tag, text=None):
    e = ET.SubElement(parent, tag)
    if text is not None: e.text = str(text)
    return e
def respuesta(mid, ci=None, datos=None, error=None):
    e = ET.Element("{" + S + "}Envelope")
    h = child(e, "{" + S + "}Header")
    child(h, "{" + A + "}Action", A + "/fault" if error else ACTION + "Response")
    child(h, "{" + A + "}RelatesTo", mid)
    b = child(e, "{" + S + "}Body")
    if error:
        fault = child(b, "{" + S + "}Fault")
        child(fault, "faultcode", "soap:Client")
        child(fault, "faultstring", error)
        detail = child(fault, "detail")
        d = child(detail, "{" + P + "}" + error)
        child(d, "ci" if error == "CiudadanoNoEncontrado" else "mensaje",
              ci if error == "CiudadanoNoEncontrado" else "Cabeceras o cuerpo inválidos")
    else:
        r = child(b, "{" + P + "}consultarCiudadanoResponse")
        for tag, value in [("ci", ci), ("nombre", datos[0]), ("apellido", datos[1]),
                           ("fechaNacimiento", datos[2]), ("vigente", "true")]:
            child(r, tag, value)
    return ET.tostring(e, encoding="utf-8", xml_declaration=True)

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        if self.path != "/pdi/ws?wsdl":
            self.send_error(404); return
        wsdl = Path(__file__).with_name("pdi.wsdl").read_text(encoding="utf-8")
        wsdl = wsdl.replace("@ENDPOINT@", os.getenv("PDI_PUBLIC_ENDPOINT", "http://localhost:8081/pdi/ws"))
        data = wsdl.encode("utf-8")
        self.send_response(200); self.send_header("Content-Type", "text/xml;charset=utf-8")
        self.end_headers(); self.wfile.write(data)
    def do_POST(self):
        inicio = time.monotonic()
        mid, ci, status = "", None, 500
        corr = self.headers.get("X-Correlation-ID", "")
        try: corr = str(uuid.UUID(corr))
        except ValueError: corr = str(uuid.uuid4())
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if self.path != "/pdi/ws" or length < 1 or length > 65536: raise ValueError()
            raw = self.rfile.read(length)
            if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper(): raise ValueError()
            doc = ET.fromstring(raw)
            if doc.tag != "{" + S + "}Envelope": raise ValueError()
            h, b = doc.find("{" + S + "}Header"), doc.find("{" + S + "}Body")
            def one(parent, tag):
                nodes = parent.findall(tag)
                if len(nodes) != 1: raise ValueError()
                return nodes[0]
            mid = one(h, "{" + A + "}MessageID").text or ""
            uuid.UUID(mid.removeprefix("urn:uuid:"))
            if not mid.startswith("urn:uuid:"): raise ValueError()
            expected = os.getenv("PDI_PUBLIC_ENDPOINT", "http://localhost:8081/pdi/ws")
            if one(h, "{" + A + "}Action").text != ACTION: raise ValueError()
            if one(h, "{" + A + "}To").text != expected: raise ValueError()
            if self.headers.get("SOAPAction", "").strip('"') != ACTION: raise ValueError()
            reply = one(h, "{" + A + "}ReplyTo")
            if one(reply, "{" + A + "}Address").text != A + "/anonymous": raise ValueError()
            operation = one(b, "{" + P + "}consultarCiudadano")
            ci = int(one(operation, "ci").text)
            if ci <= 0: raise ValueError()
            delay = float(os.getenv("PDI_DELAY_SECONDS", "0"))
            if delay > 0: time.sleep(min(delay, 10))
            datos = CATALOGO.get(ci)
            data = respuesta(mid, ci, datos, None if datos else "CiudadanoNoEncontrado")
            status = 200 if datos else 500
        except Exception:
            data = respuesta(mid, error="ContratoInvalido")
        self.send_response(status); self.send_header("Content-Type", "text/xml;charset=utf-8")
        self.send_header("X-Correlation-ID", corr); self.end_headers()
        try: self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError): pass
        print(json.dumps({"evento": "pdi.respuesta", "correlacion": corr,
              "status": status, "duracionMs": round((time.monotonic()-inicio)*1000)}), flush=True)

if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8080), Handler)
    cert, key = os.getenv("PDI_TLS_CERT"), os.getenv("PDI_TLS_KEY")
    if cert:
        if not key: raise RuntimeError("Falta PDI_TLS_KEY")
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert, key)
        server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever()