BEGIN;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS origen_identidad varchar(10) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS uid_externo varchar(200);
CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_identidad ON usuarios(origen_identidad,uid_externo)
    WHERE uid_externo IS NOT NULL;
CREATE TABLE IF NOT EXISTS usuario_roles(
    usuario_id integer NOT NULL REFERENCES usuarios(id),
    rol varchar(30) NOT NULL,
    PRIMARY KEY(usuario_id,rol)
);
INSERT INTO usuario_roles(usuario_id,rol)
SELECT id,tipo_usuario FROM usuarios
WHERE tipo_usuario IN ('ADMINISTRADOR','AUTORIDAD','FUNCIONARIO','CIUDADANO','CHOFER')
ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS flujos_identidad(
    hash varchar(64) PRIMARY KEY,
    challenge varchar(43) NOT NULL,
    nonce varchar(43) NOT NULL,
    vence timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_flujos_vence ON flujos_identidad(vence);
CREATE TABLE IF NOT EXISTS sesiones_acceso(
    hash varchar(64) PRIMARY KEY,
    usuario_id integer NOT NULL REFERENCES usuarios(id),
    vence timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_sesiones_vence ON sesiones_acceso(vence);
CREATE TABLE IF NOT EXISTS vinculos_empresa(
    clave varchar(64) PRIMARY KEY,
    usuario_id integer NOT NULL REFERENCES usuarios(id),
    nro_empresa integer NOT NULL REFERENCES empresas(nro_empresa),
    responsable boolean NOT NULL DEFAULT false,
    chofer boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE(nro_empresa,usuario_id)
);
INSERT INTO vinculos_empresa(clave,usuario_id,nro_empresa,responsable,chofer,version)
SELECT ec.nro_empresa::text || ':' || ec.ciudadano_id::text,
       ec.ciudadano_id,ec.nro_empresa,true,false,0
FROM empresa_ciudadanos ec ON CONFLICT DO NOTHING;

-- Canonizar las referencias de guía hacia Usuario para admitir múltiples roles.
-- Detectar la FK existente evita depender de su nombre generado por Hibernate.
DO $$
DECLARE fila record;
BEGIN
 FOR fila IN
    SELECT c.conname FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)
    WHERE c.conrelid='guias_de_viaje'::regclass AND c.contype='f'
      AND a.attname='chofer_id'
 LOOP
    EXECUTE format('ALTER TABLE guias_de_viaje DROP CONSTRAINT %I',fila.conname);
 END LOOP;
END $$;
ALTER TABLE guias_de_viaje ADD CONSTRAINT fk_guia_usuario_chofer
    FOREIGN KEY(chofer_id) REFERENCES usuarios(id);
COMMIT;