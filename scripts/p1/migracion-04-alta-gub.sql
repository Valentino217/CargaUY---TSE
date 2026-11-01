-- Permite el primer ingreso OIDC sin inventar una fecha de nacimiento.
-- Aplicar con WildFly detenido, después de las migraciones anteriores.
BEGIN;
ALTER TABLE usuarios ALTER COLUMN fecha_nac DROP NOT NULL;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'usuarios'::regclass
          AND conname = 'ck_usuario_nacimiento_federado'
    ) THEN
        ALTER TABLE usuarios ADD CONSTRAINT ck_usuario_nacimiento_federado
            CHECK (fecha_nac IS NOT NULL OR
                   (origen_identidad = 'GUB' AND tipo_usuario = 'CIUDADANO'));
    END IF;
END $$;
COMMIT;
