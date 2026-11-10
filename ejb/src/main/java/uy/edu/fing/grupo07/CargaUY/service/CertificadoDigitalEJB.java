package uy.edu.fing.grupo07.CargaUY.service;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CertificadoDigital;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoCertificado;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

/**
 * Session Bean implementation class CertificadoDigitalEJB
 */
@Stateless
@LocalBean
public class CertificadoDigitalEJB {

    /**
     * Default constructor. 
     */
    public CertificadoDigitalEJB() {
        // TODO Auto-generated constructor stub
    }
    
    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public Usuario buscarUsuarioPorCi(int ci) {

        List<Usuario> usuarios = em.createQuery(
            "SELECT DISTINCT u FROM Usuario u " +
            "JOIN u.roles r " +
            "WHERE u.ci = :ci " +
            "AND r IN :roles",
            Usuario.class
        )
        .setParameter("ci", ci)
        .setParameter("roles", List.of(
            TipoUsuario.FUNCIONARIO,
            TipoUsuario.ADMINISTRADOR
        ))
        .getResultList();

        if (usuarios.isEmpty()) {
            return null;
        }

        return usuarios.get(0);
    }
    
    // 2. Consultar certificado activo de un usuario
    public CertificadoDigital buscarCertificadoActivoPorCi(int ci) {

        List<CertificadoDigital> certificados = em.createQuery(
            "SELECT c FROM CertificadoDigital c " +
            "WHERE c.usuario.ci = :ci " +
            "AND c.estado = :estado",
            CertificadoDigital.class
        )
        .setParameter("ci", ci)
        .setParameter("estado", EstadoCertificado.ACTIVO)
        .getResultList();

        if (certificados.isEmpty()) {
            return null;
        }

        return certificados.get(0);
    }
    
    // 3. Registrar certificado
    public CertificadoDigital registrarCertificado(
            int ci,
            byte[] archivoCertificadoCifrado,
            String nombreArchivo,
            LocalDateTime fechaVencimiento) {

        // Buscamos al propietario por su cédula
        Usuario usuario = buscarUsuarioPorCi(ci);

        if (usuario == null) {
            throw new IllegalArgumentException(
                "No existe un usuario con esa cédula."
            );
        }

        // Verificamos que no tenga otro certificado activo
        CertificadoDigital existente =
            buscarCertificadoActivoPorCi(ci);

        if (existente != null) {
            throw new IllegalStateException(
                "El usuario ya tiene un certificado activo."
            );
        }

        // Creamos la entidad
        CertificadoDigital certificado = new CertificadoDigital();

        certificado.setUsuario(usuario);
        certificado.setArchivoCertificado(archivoCertificadoCifrado);
        certificado.setNombreArchivo(nombreArchivo);
        certificado.setFechaRegistro(LocalDateTime.now());
        certificado.setFechaVencimiento(fechaVencimiento);
        certificado.setEstado(EstadoCertificado.ACTIVO);

        // Guardamos en PostgreSQL mediante Hibernate
        em.persist(certificado);

        return certificado;
    }

    // 4. Revocar certificado
    public void revocarCertificado(int idCertificado, String motivo) {

        CertificadoDigital certificado =
            em.find(CertificadoDigital.class, idCertificado);

        if (certificado == null) {
            throw new IllegalArgumentException(
                "No existe ese certificado."
            );
        }

        if (certificado.getEstado() != EstadoCertificado.ACTIVO) {
            throw new IllegalStateException(
                "El certificado no está activo."
            );
        }

        certificado.setEstado(EstadoCertificado.REVOCADO);
        certificado.setFechaRevocacion(LocalDateTime.now());
        certificado.setMotivoRevocacion(motivo);

        // No hace falta llamar a persist:
        // Hibernate detecta los cambios de la entidad administrada.
    }
    
    // Abre y valida un archivo PKCS#12
    public CredencialesFirma abrirCredenciales(
            byte[] archivo,
            String password) {

        if (archivo == null || archivo.length == 0) {
            throw new IllegalArgumentException(
                "Debe seleccionar un archivo de certificado."
            );
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                "Debe ingresar la contraseña del certificado."
            );
        }

        char[] clavePassword = password.toCharArray();

        try {
            // 1. Crear el almacén PKCS#12
            KeyStore ks = KeyStore.getInstance("PKCS12");

            // 2. Abrir el archivo utilizando la contraseña
            try (ByteArrayInputStream entrada =
                    new ByteArrayInputStream(archivo)) {

                ks.load(entrada, clavePassword);
            }

            // 3. Buscar una entrada que contenga una clave privada
            Enumeration<String> aliases = ks.aliases();

            while (aliases.hasMoreElements()) {

                String alias = aliases.nextElement();

                if (ks.isKeyEntry(alias)
                        && ks.getCertificate(alias) instanceof X509Certificate) {

                    // 4. Recuperar la clave privada
                    PrivateKey clavePrivada =
                        (PrivateKey) ks.getKey(alias, clavePassword);

                    if (clavePrivada == null) {
                        continue;
                    }

                    // 5. Recuperar el certificado X.509
                    X509Certificate certificado =
                        (X509Certificate) ks.getCertificate(alias);

                    // 6. Comprobar que esté vigente
                    certificado.checkValidity();

                    // 7. Devolver las credenciales
                    return new CredencialesFirma(
                        clavePrivada,
                        certificado
                    );
                }
            }

            throw new IllegalArgumentException(
                "El archivo no contiene una clave privada y un certificado válido."
            );

        } catch (Exception e) {

            throw new IllegalArgumentException(
                "No se pudo abrir el certificado. Verifique el archivo y la contraseña.",
                e
            );

        } finally {
            // Limpiar el arreglo de caracteres de la contraseña
            Arrays.fill(clavePassword, '\0');
        }
    }
    
    //determina fecha de venciiento del certificado
    
    public LocalDateTime obtenerFechaVencimiento(X509Certificate certificado) {

        return certificado.getNotAfter()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
    
    public List<CertificadoDigital> listarCertificados() {

        return em.createQuery(
            "SELECT c FROM CertificadoDigital c " +
            "JOIN FETCH c.usuario " +
            "ORDER BY c.fechaRegistro DESC",
            CertificadoDigital.class
        ).getResultList();
    }
    
    public CertificadoDigital buscarCertificadoActivoPorCiInspector(int ci) {

        LocalDate hoy = LocalDate.now();

        List<CertificadoDigital> certificados = em.createQuery(
            "SELECT c FROM CertificadoDigital c " +
            "WHERE c.usuario.ci = :ci " +
            "AND c.estado = :estado " +
            "AND c.fechaVencimiento >= :hoy " +
            "ORDER BY c.fechaRegistro DESC",
            CertificadoDigital.class
        )
        .setParameter("ci", ci)
        .setParameter("estado", EstadoCertificado.ACTIVO)
        .setParameter("hoy", hoy)
        .setMaxResults(1)
        .getResultList();

        if (certificados.isEmpty()) {
            return null;
        }

        return certificados.get(0);
    }
}
