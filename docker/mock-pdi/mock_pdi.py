from http.server import HTTPServer, BaseHTTPRequestHandler

WSDL_CONTENT = """<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
             xmlns:tns="http://pdi.dnic.gub.uy/"
             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
             targetNamespace="http://pdi.dnic.gub.uy/"
             name="PdiService">
  <types>
    <xsd:schema targetNamespace="http://pdi.dnic.gub.uy/">
      <xsd:element name="consultarCiudadano">
        <xsd:complexType>
          <xsd:sequence>
            <xsd:element name="ci" type="xsd:int"/>
          </xsd:sequence>
        </xsd:complexType>
      </xsd:element>
      <xsd:element name="consultarCiudadanoResponse">
        <xsd:complexType>
          <xsd:sequence>
            <xsd:element name="ci" type="xsd:int"/>
            <xsd:element name="nombre" type="xsd:string"/>
            <xsd:element name="apellido" type="xsd:string"/>
            <xsd:element name="fechaNacimiento" type="xsd:string"/>
            <xsd:element name="vigente" type="xsd:boolean"/>
          </xsd:sequence>
        </xsd:complexType>
      </xsd:element>
    </xsd:schema>
  </types>
  <message name="consultarCiudadanoInput">
    <part name="parameters" element="tns:consultarCiudadano"/>
  </message>
  <message name="consultarCiudadanoOutput">
    <part name="parameters" element="tns:consultarCiudadanoResponse"/>
  </message>
  <portType name="PdiPortType">
    <operation name="consultarCiudadano">
      <input message="tns:consultarCiudadanoInput"/>
      <output message="tns:consultarCiudadanoOutput"/>
    </operation>
  </portType>
  <binding name="PdiBinding" type="tns:PdiPortType">
    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
    <operation name="consultarCiudadano">
      <soap:operation soapAction=""/>
      <input><soap:body use="literal"/></input>
      <output><soap:body use="literal"/></output>
    </operation>
  </binding>
  <service name="PdiService">
    <port name="PdiPort" binding="tns:PdiBinding">
      <soap:address location="http://mock-pdi:8080/pdi/ws"/>
    </port>
  </service>
</definitions>
"""

SOAP_RESPONSE_TEMPLATE = """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:pdi="http://pdi.dnic.gub.uy/">
  <soap:Body>
    <pdi:consultarCiudadanoResponse>
      <ci>{ci}</ci>
      <nombre>Juan</nombre>
      <apellido>Perez</apellido>
      <fechaNacimiento>1985-05-15</fechaNacimiento>
      <vigente>true</vigente>
    </pdi:consultarCiudadanoResponse>
  </soap:Body>
</soap:Envelope>"""

class PdiHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if "wsdl" in self.path.lower():
            self.send_response(200)
            self.send_header("Content-Type", "text/xml; charset=utf-8")
            self.end_headers()
            self.wfile.write(WSDL_CONTENT.encode("utf-8"))
        else:
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"Mock PDI SOAP Service running. Access /pdi/ws?wsdl for WSDL.")

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8")
        
        # Extraer CI si es posible, default 12345678
        ci = "12345678"
        if "<ci>" in body and "</ci>" in body:
            ci = body.split("<ci>")[1].split("</ci>")[0].strip()

        resp = SOAP_RESPONSE_TEMPLATE.format(ci=ci)
        self.send_response(200)
        self.send_header("Content-Type", "text/xml; charset=utf-8")
        self.end_headers()
        self.wfile.write(resp.encode("utf-8"))

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8080), PdiHandler)
    print("Mock PDI SOAP Server listening on port 8080...")
    server.serve_forever()
