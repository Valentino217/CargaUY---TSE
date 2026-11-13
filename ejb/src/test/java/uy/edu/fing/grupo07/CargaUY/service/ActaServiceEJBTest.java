package uy.edu.fing.grupo07.CargaUY.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias - ActaServiceEJB")
class ActaServiceEJBTest {

    private final ActaServiceEJB service = new ActaServiceEJB();

    @BeforeAll
    static void registrarBC() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    static byte[] generarPkcs12Prueba(String password) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        long ahora = System.currentTimeMillis();
        Date desde = new Date(ahora - 60_000L);
        Date hasta = new Date(ahora + 86_400_000L);
        X500Name dn = new X500Name("CN=Inspector Prueba CargaUY");

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn,
                BigInteger.valueOf(ahora),
                desde,
                hasta,
                dn,
                kp.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(builder.build(signer));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("firma", kp.getPrivate(), password.toCharArray(), new Certificate[]{cert});
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ks.store(out, password.toCharArray());
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("emitirYFirmarActa genera PDF válido incluso con evidencia larga y campos nulos")
    void emitirActaGeneraPdf() {
        String evidenciaLarga = "Exceso de peso detectado en balanza oficial ruta 5 km 120.\n"
                + "Se registra diferencia superior a la tolerancia normativa vigente para la configuracion de ejes declarada.";
        byte[] pdf = service.emitirYFirmarActa(101, "TransUruguay S.A.", "STP-1234", "2026-11-12", "EXCESO_DE_CARGA", evidenciaLarga);
        assertNotNull(pdf);
        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));

        byte[] pdfMinimo = service.emitirYFirmarActa(102, null, null, null, null, null);
        assertNotNull(pdfMinimo);
    }

    @Test
    @DisplayName("abrirCredenciales y firmarPdf firman el acta con certificado PKCS#12")
    void abrirCredencialesYFirmarPdf() throws Exception {
        byte[] p12 = generarPkcs12Prueba("clave123");
        CredencialesFirma creds = service.abrirCredenciales(p12, "clave123");
        assertNotNull(creds);
        assertNotNull(creds.getClavePrivada());
        assertNotNull(creds.getCertificado());

        assertNull(service.abrirCredenciales(p12, "incorrecta"));

        byte[] pdfBase = service.emitirYFirmarActa(200, "Logistica Sur", "ABC-9999", "2026-11-12", "DESVIO_DE_RUTA", "Desvio 4.5 km");
        byte[] pdfFirmado = service.firmarPdf(pdfBase, creds.getClavePrivada(), creds.getCertificado());
        assertNotNull(pdfFirmado);
        assertTrue(pdfFirmado.length > pdfBase.length);

        assertThrows(RuntimeException.class,
                () -> service.firmarPdf("no-es-pdf".getBytes(StandardCharsets.UTF_8), creds.getClavePrivada(), creds.getCertificado()));
    }
}
