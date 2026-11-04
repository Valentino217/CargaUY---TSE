package uy.edu.fing.grupo07.CargaUY.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import java.util.Collections;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;

/**
 * Session Bean implementation class ActaServiceEJB
 */
@Stateless
@LocalBean
public class ActaServiceEJB implements ActaServiceEJBLocal {

    /**
     * Default constructor. 
     */
    public ActaServiceEJB() {
        // TODO Auto-generated constructor stub
    }
    public byte[] emitirYFirmarActa(int idCaso, String empresa, String matricula, String fecha, String tipo, String evidencia) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1. Crear una página
            PDPage pagina = new PDPage();
            doc.addPage(pagina);

            // 2. Escribir los textos con el "lápiz" (ContentStream)
            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                cs.beginText();
                
                // Título
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(50, 720);
                cs.showText("ACTA OFICIAL DE INFRACCIÓN #" + (idCaso));

                // Datos
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(0, -35);
                cs.showText("Empresa: " + (empresa != null ? empresa : "S/D"));

                cs.newLineAtOffset(0, -25);
                cs.showText("Matrícula / Unidad: " + (matricula != null ? matricula : "S/D"));

                cs.newLineAtOffset(0, -25);
                cs.showText("Fecha de Detección: " + (fecha != null ? fecha : "S/D"));

                cs.newLineAtOffset(0, -25);
                cs.showText("Tipo de Infracción: " + (tipo != null ? tipo : "S/D"));

                cs.newLineAtOffset(0, -35);
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("Evidencia Técnica Registrada:");

                cs.newLineAtOffset(0, -20);
                cs.setFont(PDType1Font.COURIER, 10);
                cs.setLeading(15);
                // Limpieza: PDFBox lanza error si el texto contiene saltos de línea (\n)
                String textoEvidencia = (evidencia != null) ? evidencia.replace("\n", " ").replace("\r", " ") : "Sin evidencia registrada";
                
               
                String[] palabras = textoEvidencia.split("\\s+");

                StringBuilder linea = new StringBuilder();

                for (String palabra : palabras) {

                    String prueba = linea.length() == 0
                            ? palabra
                            : linea + " " + palabra;

                    if (prueba.length() > 80 && linea.length() > 0) {

                        cs.showText(linea.toString());
                        cs.newLine();

                        linea.setLength(0);
                        linea.append(palabra);

                    } else {
                        linea.setLength(0);
                        linea.append(prueba);
                    }
                }

                // Escribir última línea
                if (linea.length() > 0) {
                    cs.showText(linea.toString());
                }
                cs.endText();
            }

            // 3. Guardar en memoria y retornar los bytes
            doc.save(out);
            return out.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return null; // Si algo falla, devuelve null en vez de romper la aplicación
        }
    }
    
    public CredencialesFirma abrirCredenciales (byte[] archivo, String password) {
    	//este metodo abrira el certificado que se subio y obtendra la clave privada y los datos
    	
    	try {
    		KeyStore ks = KeyStore.getInstance("PKCS12");
    		//KeyStore es una clase de Java para administrar claves y certificados.
    		ks.load(new ByteArrayInputStream(archivo), password.toCharArray());
    		//Convierte el arreglo de bytes en un flujo de entrada para que Java pueda leerlo.
    		//Convierte la contraseña de tipo String a un arreglo de caracteres (char[]), que es el formato que espera el método load
    		String alias = ks.aliases().nextElement();
    		//Es el nombre que identifica una entrada dentro del archivo .p12
    		PrivateKey clavePrivada = (PrivateKey) ks.getKey(alias, password.toCharArray());
    		//Esta línea recupera la clave privada.
    		//PrivateKey: tipo de dato que representa una clave privada.
    		//ks.getKey(...): busca la clave dentro del almacén.
    		//password.toCharArray(): permite acceder a la clave protegida por contraseña.
    		//alias: indica qué entrada queremos recuperar.
    		X509Certificate certificado = (X509Certificate) ks.getCertificate(alias);
    		//Recuperamos el certificado asociado a la entrada.
    		//El certificado contiene información como titulo,emisor,periodo de validez,clave publica
    		//la clave publica sera utilizada para luego verificar la firma
    		certificado.checkValidity();
    		//Comprueba si el certificado está dentro de su período de validez
    		//Si todavía no comenzó su vigencia, genera un error.
    		//Si ya venció, genera un error
    		//Si está vigente, continúa normalmente.
    		//Esta comprobación no verifica la confianza del emisor ni la revocación del certificado

    	    System.out.println("Certificado abierto correctamente");
    	    System.out.println("Titular: " + certificado.getSubjectX500Principal());
    	    
    	    return new CredencialesFirma(clavePrivada,certificado);
    	}
    	catch(Exception e) {
    		 e.printStackTrace();
    		 //muestra el error detallado en consola
    		 return null;
    	}
    	
    	
    	
    }
    
    //firmar el pdf ////////////////////////////////////////////
    public byte[] firmarPdf(byte[] pdfOriginal, PrivateKey clavePrivada,X509Certificate certificado) {
    	try {
    	
    		if (Security.getProvider("BC") == null) {
    	        Security.addProvider(new BouncyCastleProvider());
    	    }
    		
    		PDDocument documento = PDDocument.load(pdfOriginal);
    		//abre el pdf que recibimos
    		
    		ByteArrayOutputStream salida = new ByteArrayOutputStream();
    	    //Crea un espacio en memoria donde vamos a guardar el PDF firmado
    		
    		PDSignature firma = new PDSignature();
    		//Crea un objeto que representa la firma digital que incorporaremos al PDF
    		
    		firma.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
    		//Indica el manejador de firma utilizado para este tipo de firma PDF.

    		//Es parte de la estructura estándar de firmas digitales PDF.
    		
            firma.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            //ndica que utilizaremos una firma PKCS#7/CMS separada del contenido del documento.
            
            firma.setName(certificado.getSubjectX500Principal().getName());
           // Obtiene el nombre del titular del certificado y lo incorpora como información descriptiva de la firma.
            
            firma.setSignDate(Calendar.getInstance());
            
            documento.addSignature(firma, contenido ->
            generarFirmaCMS(contenido, clavePrivada, certificado));

            documento.saveIncremental(salida);

            return salida.toByteArray();
    	
    	}catch(Exception e) {
    		
    		 throw new RuntimeException("Error al firmar el PDF", e);
    	}
    }
    
    private byte[] generarFirmaCMS(
            InputStream contenido,
            PrivateKey clavePrivada,
            X509Certificate certificado) throws IOException {

    	
    	//
    	
    	//Genera la firma criptográfica que permite detectar si el contenido firmado del acta fue modificado.
        try {
            // Leer el contenido que PDFBox necesita firmar
            byte[] datos = contenido.readAllBytes();

            // Crear el generador de firma CMS
            CMSSignedDataGenerator generador =
                    new CMSSignedDataGenerator();

            // Elegir el algoritmo de firma
            ContentSigner firmador =
                    new JcaContentSignerBuilder("SHA256withRSA")
                            .setProvider("BC")
                            .build(clavePrivada);

            // Preparar el cálculo del resumen criptográfico
            DigestCalculatorProvider calculador =
                    new JcaDigestCalculatorProviderBuilder()
                            .setProvider("BC")
                            .build();

            // Agregar la firma y el certificado
            generador.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(calculador)
                            .build(firmador, certificado)
            );

            generador.addCertificates(
                    new JcaCertStore(
                            Collections.singletonList(certificado)
                    )
            );

            // Generar la firma CMS
            CMSSignedData firma = generador.generate(
                    new CMSProcessableByteArray(datos),
                    false
            );

            // Devolver la firma en bytes
            return firma.getEncoded();

        } catch (Exception e) {
            throw new IOException("Error al generar la firma CMS", e);
        }
    }
    

}
