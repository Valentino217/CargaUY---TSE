-- Migración 05: Eventos de carga/descarga parcial, foto Base64 de incidente,
-- fiscalización VIAJE_SIN_GUIA y pista de auditoría append-only 1:N.
BEGIN;

ALTER TABLE IF EXISTS eventos_viaje
    ADD COLUMN IF NOT EXISTS cantidad_kg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS mercaderia VARCHAR(255),
    ADD COLUMN IF NOT EXISTS empresa_carga VARCHAR(255);

ALTER TABLE IF EXISTS incidentes_ruta
    ALTER COLUMN foto TYPE TEXT;

ALTER TABLE IF EXISTS pesadas_balanza
    ALTER COLUMN guia_id DROP NOT NULL;

ALTER TABLE IF EXISTS pesadas_balanza
    ADD COLUMN IF NOT EXISTS vehiculo_matricula VARCHAR(255);

ALTER TABLE IF EXISTS infracciones
    ALTER COLUMN guia_id DROP NOT NULL;

ALTER TABLE IF EXISTS infracciones
    ADD COLUMN IF NOT EXISTS vehiculo_matricula VARCHAR(255);

DO $$
BEGIN
    IF to_regclass('infracciones') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_infraccion_vehiculo_sin_guia_fecha
            ON infracciones (vehiculo_matricula, fecha, tipo)
            WHERE guia_id IS NULL AND vehiculo_matricula IS NOT NULL;
    END IF;
END $$;

ALTER TABLE IF EXISTS registro_auditoria
    ADD COLUMN IF NOT EXISTS infraccion_id INTEGER;

DO $$
DECLARE
    restriccion record;
BEGIN
    IF to_regclass('registro_auditoria') IS NOT NULL THEN
        FOR restriccion IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
            WHERE c.conrelid = 'registro_auditoria'::regclass
              AND c.contype = 'u'
              AND cardinality(c.conkey) = 1
              AND a.attname = 'infraccion_id'
        LOOP
            EXECUTE format('ALTER TABLE registro_auditoria DROP CONSTRAINT %I', restriccion.conname);
        END LOOP;
        CREATE INDEX IF NOT EXISTS idx_registro_auditoria_infraccion
            ON registro_auditoria (infraccion_id);
        DROP TRIGGER IF EXISTS trg_audit_immutable ON registro_auditoria;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'infracciones'
          AND column_name = 'registro_auditoria_id'
    ) THEN
        EXECUTE 'UPDATE registro_auditoria r
                 SET infraccion_id = i.id_caso
                 FROM infracciones i
                 WHERE i.registro_auditoria_id = r.id
                   AND r.infraccion_id IS NULL';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION trg_prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'Operación denegada: la tabla registro_auditoria es inmutable (append-only). No se permiten UPDATE ni DELETE.';
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
BEFORE UPDATE OR DELETE ON registro_auditoria
FOR EACH ROW EXECUTE FUNCTION trg_prevent_audit_modification();

COMMIT;
