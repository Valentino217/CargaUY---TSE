package uy.edu.fing.grupo07.CargaUY.service;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;

@Local
public interface ActaServiceEJBLocal {
	 public byte[] emitirYFirmarActa(int idCaso, String empresa, String matricula, String fecha, String tipo, String evidencia);
	 default byte[] emitirYFirmarActa(String token, int idCaso, String empresa, String matricula, String fecha, String tipo, String evidencia) {
	     return emitirYFirmarActa(idCaso, empresa, matricula, fecha, tipo, evidencia);
	 }
	 default CredencialesFirma abrirCredenciales(String token, byte[] archivo, String password) {
	     return null;
	 }
	 default byte[] firmarPdf(String token, byte[] pdfOriginal, PrivateKey clavePrivada, X509Certificate certificado) {
	     return null;
	 }
}
