package uy.edu.fing.grupo07.CargaUY.audit;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Instala el trigger PostgreSQL que hace append-only a la tabla
 * {@code registro_auditoria} (R005 / AC004).
 *
 * <p>Se ejecuta en cada despliegue, después de que Hibernate genera el esquema.
 * Es necesario porque el script {@code docker/postgres/init/02-audit-immutable.sql}
 * corre cuando la tabla todavía no existe, y además {@code drop-and-create}
 * recrea la tabla (y pierde el trigger) en cada deploy.</p>
 */
@Singleton
@Startup
public class AuditoriaTriggerInstaller {

    private static final Logger LOG = Logger.getLogger(AuditoriaTriggerInstaller.class.getName());

    static final String SQL_FUNCION =
            "CREATE OR REPLACE FUNCTION trg_prevent_audit_modification() "
            + "RETURNS TRIGGER AS $$ "
            + "BEGIN "
            + "  RAISE EXCEPTION 'Operación denegada: la tabla registro_auditoria es inmutable (append-only). "
            + "No se permiten UPDATE ni DELETE.'; "
            + "  RETURN NULL; "
            + "END; "
            + "$$ LANGUAGE plpgsql";

    static final String SQL_DROP_TRIGGER =
            "DROP TRIGGER IF EXISTS trg_audit_immutable ON registro_auditoria";

    static final String SQL_CREATE_TRIGGER =
            "CREATE TRIGGER trg_audit_immutable "
            + "BEFORE UPDATE OR DELETE ON registro_auditoria "
            + "FOR EACH ROW EXECUTE FUNCTION trg_prevent_audit_modification()";

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public AuditoriaTriggerInstaller() {
    }

    /** Constructor para tests unitarios. */
    AuditoriaTriggerInstaller(EntityManager em) {
        this.em = em;
    }

    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void instalar() {
        try {
            em.createNativeQuery(SQL_FUNCION).executeUpdate();
            em.createNativeQuery(SQL_DROP_TRIGGER).executeUpdate();
            em.createNativeQuery(SQL_CREATE_TRIGGER).executeUpdate();
            LOG.info("Trigger de inmutabilidad instalado en registro_auditoria");
        } catch (RuntimeException e) {
            // No se bloquea el despliegue (p. ej. si la BD no es PostgreSQL), pero se deja registro.
            LOG.log(Level.SEVERE, "No se pudo instalar el trigger de inmutabilidad de registro_auditoria", e);
        }
    }
}
