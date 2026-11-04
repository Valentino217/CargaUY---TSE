package uy.edu.fing.grupo07.CargaUY.domain.entity;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class CredencialesFirma {
	    private final PrivateKey clavePrivada;
	    private final X509Certificate certificado;

	    public CredencialesFirma(PrivateKey clavePrivada,
	                             X509Certificate certificado) {
	        this.clavePrivada = clavePrivada;
	        this.certificado = certificado;
	    }

	    public PrivateKey getClavePrivada() {
	        return clavePrivada;
	    }

	    public X509Certificate getCertificado() {
	        return certificado;
	    }
}
