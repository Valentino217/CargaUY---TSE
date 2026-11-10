package uy.edu.fing.grupo07.CargaUY.audit;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aplica de forma idempotente las migraciones incrementales de esquema requeridas
 * cuando {@code jakarta.persistence.schema-generation.database.action = none}.
 *
 * <p>Se ejecuta antes de {@link AuditoriaTriggerInstaller} para permitir la migración
 * de la relación 1:N {@code registro_auditoria.infraccion_id} antes de reinstalar el
 * trigger de inmutabilidad append-only.</p>
 */
@Singleton
@Startup
public class EsquemaMigracionStartupBean {

    private static final Logger LOG = Logger.getLogger(EsquemaMigracionStartupBean.class.getName());

    static final List<String> SENTENCIAS_DDL = List.of(
            "ALTER TABLE IF EXISTS guias_de_viaje ADD COLUMN IF NOT EXISTS corredor_vial VARCHAR(250)",
            "ALTER TABLE IF EXISTS guias_de_viaje ADD COLUMN IF NOT EXISTS polilinea_ruta VARCHAR(4000)",
            "ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS distancia_desvio_km DOUBLE PRECISION",
            "ALTER TABLE IF EXISTS puntos_tracking ADD COLUMN IF NOT EXISTS distancia_desvio_km DOUBLE PRECISION",
            "ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS cantidad_kg DOUBLE PRECISION",
            "ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS mercaderia VARCHAR(255)",
            "ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS empresa_carga VARCHAR(255)",
            "ALTER TABLE IF EXISTS incidentes_ruta ALTER COLUMN foto TYPE TEXT",
            "ALTER TABLE IF EXISTS pesadas_balanza ALTER COLUMN guia_id DROP NOT NULL",
            "ALTER TABLE IF EXISTS pesadas_balanza ADD COLUMN IF NOT EXISTS vehiculo_matricula VARCHAR(255)",
            "ALTER TABLE IF EXISTS infracciones ALTER COLUMN guia_id DROP NOT NULL",
            "ALTER TABLE IF EXISTS infracciones ADD COLUMN IF NOT EXISTS vehiculo_matricula VARCHAR(255)",
            "ALTER TABLE IF EXISTS infracciones DROP CONSTRAINT IF EXISTS uk_infraccion_guia_tipo",
            "DO $$ BEGIN IF to_regclass('infracciones') IS NOT NULL THEN "
                    + "CREATE UNIQUE INDEX IF NOT EXISTS uk_infraccion_vehiculo_sin_guia_fecha "
                    + "ON infracciones (vehiculo_matricula, fecha, tipo) "
                    + "WHERE guia_id IS NULL AND vehiculo_matricula IS NOT NULL; "
                    + "END IF; END $$",
            "ALTER TABLE IF EXISTS registro_auditoria ADD COLUMN IF NOT EXISTS infraccion_id INTEGER",
            "DO $$ DECLARE restriccion record; BEGIN "
                    + "IF to_regclass('registro_auditoria') IS NOT NULL THEN "
                    + "FOR restriccion IN SELECT c.conname FROM pg_constraint c "
                    + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey) "
                    + "WHERE c.conrelid = 'registro_auditoria'::regclass AND c.contype = 'u' "
                    + "AND cardinality(c.conkey) = 1 AND a.attname = 'infraccion_id' LOOP "
                    + "EXECUTE format('ALTER TABLE registro_auditoria DROP CONSTRAINT %I', restriccion.conname); "
                    + "END LOOP; END IF; END $$",
            "DO $$ BEGIN IF to_regclass('registro_auditoria') IS NOT NULL THEN "
                    + "CREATE INDEX IF NOT EXISTS idx_registro_auditoria_infraccion ON registro_auditoria (infraccion_id); "
                    + "END IF; END $$",
            "DO $$ BEGIN IF to_regclass('registro_auditoria') IS NOT NULL THEN "
                    + "DROP TRIGGER IF EXISTS trg_audit_immutable ON registro_auditoria; "
                    + "END IF; END $$",
            "DO $$ BEGIN "
                    + "IF EXISTS (SELECT 1 FROM information_schema.columns "
                    + "WHERE table_name = 'infracciones' AND column_name = 'registro_auditoria_id') THEN "
                    + "EXECUTE 'UPDATE registro_auditoria r SET infraccion_id = i.id_caso "
                    + "FROM infracciones i WHERE i.registro_auditoria_id = r.id AND r.infraccion_id IS NULL'; "
                    + "END IF; END $$",
            "ALTER TABLE IF EXISTS usuario_roles DROP CONSTRAINT IF EXISTS usuario_roles_rol_check",
            "ALTER TABLE IF EXISTS usuario_roles ADD CONSTRAINT usuario_roles_rol_check "
                    + "CHECK ((rol)::text = ANY (ARRAY['CIUDADANO_COMUN', 'CIUDADANO', 'CHOFER', 'FUNCIONARIO', 'AUTORIDAD', 'ADMINISTRADOR']::text[]))"
    );

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public EsquemaMigracionStartupBean() {
    }

    public EsquemaMigracionStartupBean(EntityManager em) {
        this.em = em;
    }

    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void migrarEsquema() {
        try {
            for (String sql : SENTENCIAS_DDL) {
                em.createNativeQuery(sql).executeUpdate();
            }
            LOG.info("Migración incremental de esquema (rutas reales, eventos, VIAJE_SIN_GUIA y auditoría 1:N) aplicada correctamente.");
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo ejecutar la migración incremental automática de esquema PostgreSQL", e);
        }
    }
}
