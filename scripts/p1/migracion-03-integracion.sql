-- Ejecutar después de migracion-02.sql, con respaldo y WildFly detenido.
-- No borra datos. Si existen infracciones duplicadas, toda la transacción se revierte.
BEGIN;

CREATE TABLE IF NOT EXISTS empresa_choferes (
    nro_empresa integer NOT NULL REFERENCES empresas(nro_empresa),
    chofer_id integer NOT NULL REFERENCES choferes(usuario_id),
    PRIMARY KEY(nro_empresa,chofer_id)
);

-- Valentino admite matrículas alfanuméricas. Conservar las referencias existentes.
DO $$
DECLARE fk record; columna record;
BEGIN
    CREATE TEMP TABLE referencias_matricula ON COMMIT DROP AS
    SELECT c.conrelid,c.conname,c.conkey,pg_get_constraintdef(c.oid) AS definicion
    FROM pg_constraint c WHERE c.contype='f' AND c.confrelid='vehiculos'::regclass;
    FOR fk IN SELECT * FROM referencias_matricula LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',fk.conrelid::regclass,fk.conname);
    END LOOP;
    ALTER TABLE vehiculos ALTER COLUMN matricula TYPE varchar(255) USING matricula::text;
    FOR columna IN
        SELECT DISTINCT r.conrelid,a.attname FROM referencias_matricula r
        JOIN pg_attribute a ON a.attrelid=r.conrelid AND a.attnum=ANY(r.conkey)
    LOOP
        EXECUTE format('ALTER TABLE %s ALTER COLUMN %I TYPE varchar(255) USING %I::text',
            columna.conrelid::regclass,columna.attname,columna.attname);
    END LOOP;
    FOR fk IN SELECT * FROM referencias_matricula LOOP
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',fk.conrelid::regclass,fk.conname,fk.definicion);
    END LOOP;
END $$;

ALTER TABLE guias_de_viaje ALTER COLUMN vehiculo_matricula DROP NOT NULL;
ALTER TABLE guias_de_viaje ALTER COLUMN chofer_id DROP NOT NULL;
ALTER TABLE infracciones ADD COLUMN IF NOT EXISTS evidencia varchar(1000);

DO $$
DECLARE restriccion record;
BEGIN
    FOR restriccion IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)
        WHERE c.conrelid='itvs'::regclass AND c.contype='u'
          AND cardinality(c.conkey)=1 AND a.attname='vehiculo_matricula'
    LOOP
        EXECUTE format('ALTER TABLE itvs DROP CONSTRAINT %I',restriccion.conname);
    END LOOP;
    IF EXISTS(SELECT 1 FROM infracciones GROUP BY guia_id,tipo HAVING count(*)>1) THEN
        RAISE EXCEPTION 'Hay infracciones duplicadas por guía/tipo: revisar antes de integrar, no se borró ningún caso';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='infracciones'::regclass AND conname='uk_infraccion_guia_tipo') THEN
        ALTER TABLE infracciones ADD CONSTRAINT uk_infraccion_guia_tipo UNIQUE(guia_id,tipo);
    END IF;
END $$;

-- Compatibilidad con los vínculos anteriores; desde ahora los servicios usan VinculoEmpresa.
INSERT INTO usuario_roles(usuario_id,rol)
SELECT u.id,u.tipo_usuario FROM usuarios u
WHERE NOT EXISTS(SELECT 1 FROM usuario_roles r WHERE r.usuario_id=u.id)
  AND u.tipo_usuario IN ('ADMINISTRADOR','AUTORIDAD','CIUDADANO','CHOFER','FUNCIONARIO')
ON CONFLICT DO NOTHING;
INSERT INTO vinculos_empresa(clave,usuario_id,nro_empresa,responsable,chofer,version)
SELECT ec.nro_empresa::text||':'||ec.chofer_id::text,ec.chofer_id,ec.nro_empresa,false,true,0
FROM empresa_choferes ec
ON CONFLICT(clave) DO UPDATE SET chofer=true;
COMMIT;
