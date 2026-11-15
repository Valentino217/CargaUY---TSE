package uy.edu.fing.grupo07.CargaUY.service;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;

@Local
public interface ActaServiceEJBLocal {
	byte[] emitirYFirmarActa(String token, int idCaso, String empresa, String matricula, String fecha, String tipo, String evidencia);
	CredencialesFirma abrirCredenciales(String token, byte[] archivo, String password);
	byte[] firmarPdf(String token, byte[] pdfOriginal, PrivateKey clavePrivada, X509Certificate certificado);
}
