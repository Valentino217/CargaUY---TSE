-- Trigger PostgreSQL para forzar inmutabilidad (append-only) en registro_auditoria (R005 / AC004)

CREATE OR REPLACE FUNCTION trg_prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Operación denegada: La tabla registro_auditoria es estrictamente inmutable (append-only). No se permiten sentencias UPDATE ni DELETE.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Crear el trigger condicionalmente tras la creación de la tabla
DO $$
BEGIN
    IF EXISTS (
        SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'registro_auditoria'
    ) THEN
        DROP TRIGGER IF EXISTS trg_audit_immutable ON registro_auditoria;
        CREATE TRIGGER trg_audit_immutable
        BEFORE UPDATE OR DELETE ON registro_auditoria
        FOR EACH ROW
        EXECUTE FUNCTION trg_prevent_audit_modification();
    END IF;
END $$;
