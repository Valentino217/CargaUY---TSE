-- ============================================================================
-- CargaUY - Script Integral de Datos de Prueba (Seed Data)
-- Idempotente y no destructivo (preserva vínculos, roles y cambios en runtime)
-- ============================================================================
SET client_encoding = 'UTF8';

BEGIN;

-- 0. Asegurar esquema e índices actualizados antes de insertar datos
ALTER TABLE IF EXISTS guias_de_viaje ADD COLUMN IF NOT EXISTS corredor_vial VARCHAR(250);
ALTER TABLE IF EXISTS guias_de_viaje ADD COLUMN IF NOT EXISTS polilinea_ruta VARCHAR(4000);
ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS distancia_desvio_km DOUBLE PRECISION;
ALTER TABLE IF EXISTS puntos_tracking ADD COLUMN IF NOT EXISTS distancia_desvio_km DOUBLE PRECISION;
ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS cantidad_kg DOUBLE PRECISION;
ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS mercaderia VARCHAR(255);
ALTER TABLE IF EXISTS eventos_viaje ADD COLUMN IF NOT EXISTS empresa_carga VARCHAR(255);
ALTER TABLE IF EXISTS incidentes_ruta ALTER COLUMN foto TYPE TEXT;
ALTER TABLE IF EXISTS pesadas_balanza ALTER COLUMN guia_id DROP NOT NULL;
ALTER TABLE IF EXISTS pesadas_balanza ADD COLUMN IF NOT EXISTS vehiculo_matricula VARCHAR(255);
ALTER TABLE IF EXISTS infracciones ALTER COLUMN guia_id DROP NOT NULL;
ALTER TABLE IF EXISTS infracciones ADD COLUMN IF NOT EXISTS vehiculo_matricula VARCHAR(255);
ALTER TABLE IF EXISTS infracciones DROP CONSTRAINT IF EXISTS uk_infraccion_guia_tipo;
ALTER TABLE IF EXISTS pncs ADD COLUMN IF NOT EXISTS numero_permiso VARCHAR(100);
ALTER TABLE IF EXISTS registro_auditoria ADD COLUMN IF NOT EXISTS infraccion_id INTEGER;

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
    END IF;
END $$;

ALTER TABLE IF EXISTS usuario_roles DROP CONSTRAINT IF EXISTS usuario_roles_rol_check;
ALTER TABLE IF EXISTS usuario_roles ADD CONSTRAINT usuario_roles_rol_check
    CHECK (rol::text = ANY (ARRAY['CIUDADANO'::character varying, 'CIUDADANO_COMUN'::character varying, 'CHOFER'::character varying, 'FUNCIONARIO'::character varying, 'AUTORIDAD'::character varying, 'ADMINISTRADOR'::character varying]::text[]));

-- 1. Reglas de Fiscalización
INSERT INTO reglas_fiscalizacion (id, umbral_carga, umbral_tracking, version)
VALUES (1, 500, 5.0, 1)
ON CONFLICT (id) DO NOTHING;

-- 2. Rubros del Catálogo
INSERT INTO rubros (codigo, nombre, descripcion) VALUES
(1, 'Forestal', 'Transporte de madera en rolos, chips y subproductos forestales'),
(2, 'Granos y Cereales', 'Transporte de soja, trigo, maíz, arroz y cereales a granel'),
(3, 'Ganadería', 'Transporte de hacienda vacuna, ovina y equina'),
(4, 'Combustibles y Peligrosas', 'Transporte de hidrocarburos, sustancias inflamables y químicas'),
(5, 'Contenedores y Comercio Exterior', 'Transporte de contenedores marítimos de exportación e importación'),
(6, 'Construcción y Cemento', 'Transporte de cemento, áridos, hierro y materiales de obra')
ON CONFLICT (codigo) DO NOTHING;

-- 3. Tipos de Carga del Catálogo
INSERT INTO tipos_carga (codigo, descripcion) VALUES
(1, 'Troncos de Pino'),
(2, 'Eucaliptus'),
(3, 'Chips de Celulosa'),
(4, 'Soja'),
(5, 'Trigo'),
(6, 'Ganado Vacuno'),
(7, 'Gasoil'),
(8, 'Contenedor 40 Pies'),
(9, 'Cemento Portland')
ON CONFLICT (codigo) DO NOTHING;

-- 4. Usuarios (Inserción inicial compatible con cuenta BootstrapAdmin previa, sin sobrescribir usuarios existentes)
CREATE OR REPLACE FUNCTION pg_temp.upsert_usuario(
    p_id INT, p_tipo VARCHAR, p_ci INT, p_nombre VARCHAR, p_mail VARCHAR,
    p_fecha_nac DATE, p_password VARCHAR, p_origen VARCHAR, p_uid VARCHAR, p_rol VARCHAR,
    p_cargo VARCHAR DEFAULT NULL, p_nro_func INT DEFAULT NULL, p_depto VARCHAR DEFAULT NULL
) RETURNS INT AS $$
DECLARE
    v_id INT;
BEGIN
    SELECT id INTO v_id FROM usuarios WHERE ci = p_ci OR (uid_externo = p_uid AND p_uid IS NOT NULL) LIMIT 1;

    IF v_id IS NULL THEN
        -- Si el p_id ya está ocupado por otra CI (ej. BootstrapAdmin tomó id=1), usar p_id libre
        IF EXISTS (SELECT 1 FROM usuarios WHERE id = p_id) THEN
            INSERT INTO usuarios (tipo_usuario, ci, nombre, mail, fecha_nac, password, activo, origen_identidad, uid_externo, version)
            VALUES (p_tipo, p_ci, p_nombre, p_mail, p_fecha_nac, p_password, true, p_origen, p_uid, 1)
            RETURNING id INTO v_id;
        ELSE
            INSERT INTO usuarios (id, tipo_usuario, ci, nombre, mail, fecha_nac, password, activo, origen_identidad, uid_externo, version)
            VALUES (p_id, p_tipo, p_ci, p_nombre, p_mail, p_fecha_nac, p_password, true, p_origen, p_uid, 1)
            RETURNING id INTO v_id;
        END IF;

        IF p_tipo = 'ADMINISTRADOR' THEN
            INSERT INTO administradores (usuario_id) VALUES (v_id) ON CONFLICT (usuario_id) DO NOTHING;
        ELSIF p_tipo = 'AUTORIDAD' THEN
            INSERT INTO autoridades (usuario_id, cargo) VALUES (v_id, COALESCE(p_cargo, 'Autoridad Nacional'))
            ON CONFLICT (usuario_id) DO NOTHING;
        ELSIF p_tipo = 'FUNCIONARIO' THEN
            INSERT INTO funcionarios (usuario_id, nro_funcionario, departamento)
            VALUES (v_id, COALESCE(p_nro_func, 100), COALESCE(p_depto, 'Montevideo'))
            ON CONFLICT (usuario_id) DO NOTHING;
        ELSIF p_tipo = 'CIUDADANO' THEN
            INSERT INTO ciudadanos (usuario_id) VALUES (v_id) ON CONFLICT (usuario_id) DO NOTHING;
        ELSIF p_tipo = 'CHOFER' THEN
            INSERT INTO choferes (usuario_id) VALUES (v_id) ON CONFLICT (usuario_id) DO NOTHING;
        END IF;

        INSERT INTO usuario_roles (usuario_id, rol) VALUES (v_id, p_rol)
        ON CONFLICT (usuario_id, rol) DO NOTHING;
    ELSE
        -- Usuario ya existente: no sobrescribir perfil, estado ni rol asignado en runtime
        IF NOT EXISTS (SELECT 1 FROM usuario_roles WHERE usuario_id = v_id) THEN
            INSERT INTO usuario_roles (usuario_id, rol) VALUES (v_id, p_rol)
            ON CONFLICT (usuario_id, rol) DO NOTHING;
        END IF;
    END IF;

    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    id_admin_central INT;
    id_martin_admin  INT;
    id_autoridad     INT;
    id_func_juan     INT;
    id_func_laura    INT;
    id_emp_pedro     INT;
    id_emp_mariana   INT;
    id_emp_gonzalo   INT;
    id_ciud_lucia    INT;
    id_ciud_ana      INT;
    id_chof_carlos   INT;
    id_chof_juan     INT;
    id_chof_roberto  INT;
    id_chof_diego    INT;
BEGIN
    -- 4.1 Administradores (BCrypt cost=12)
    id_admin_central := pg_temp.upsert_usuario(10, 'ADMINISTRADOR', 10000001, 'Admin Central', 'admin@cargauy.gub.uy', '1980-01-15', '$2a$12$DY/Bfsus.do8e6ue9dz66eBPP/tY6v7T.ASnNRuZ65uTBAEHGoOBy', 'LDAP', 'admin.central', 'ADMINISTRADOR');
    id_martin_admin  := pg_temp.upsert_usuario(11, 'ADMINISTRADOR', 90000001, 'Martin Admin', 'admin@example.invalid', '1990-01-01', '$2a$12$DY/Bfsus.do8e6ue9dz66eBPP/tY6v7T.ASnNRuZ65uTBAEHGoOBy', 'LDAP', 'martin.admin', 'ADMINISTRADOR');

    -- 4.2 Autoridad MTOP
    id_autoridad := pg_temp.upsert_usuario(20, 'AUTORIDAD', 20000002, 'Dr. Alberto Director', 'autoridad@cargauy.gub.uy', '1975-06-20', '$2a$12$JxbgZqu1/dPp0l.pmfL/Qe/c/3zmNm9xR7qHex36O5Ugr1m.MY/r6', 'LDAP', 'alberto.autoridad', 'AUTORIDAD', 'Director Nacional de Transporte');

    -- 4.3 Funcionarios Fiscalizadores (gub.uy)
    id_func_juan  := pg_temp.upsert_usuario(30, 'FUNCIONARIO', 12345678, 'Juan Funcionario', 'funcionario@cargauy.gub.uy', '1985-05-20', '$2a$12$IJzDsFbw93Vcjp6dNQdtvuddNBiPBDzWI2gPVIvHUvwl.jI5mVBEm', 'GUB', '30303030-3030-4030-8030-303030303030', 'FUNCIONARIO', NULL, 101, 'Montevideo');
    id_func_laura := pg_temp.upsert_usuario(31, 'FUNCIONARIO', 12345679, 'Laura Fiscalizadora', 'laura.fiscal@cargauy.gub.uy', '1988-11-14', '$2a$12$IJzDsFbw93Vcjp6dNQdtvuddNBiPBDzWI2gPVIvHUvwl.jI5mVBEm', 'GUB', '31313131-3131-4131-8131-313131313131', 'FUNCIONARIO', NULL, 102, 'Rutas Nacionales');

    -- 4.4 Ciudadanos Empresa y Ciudadanos Comunes (gub.uy)
    id_emp_pedro   := pg_temp.upsert_usuario(40, 'CIUDADANO', 87654321, 'Pedro Empresario', 'pedro.empresario@transporte.uy', '1978-03-10', '$2a$12$zhR8dDKWaRxrNbObjjHpqel7X.4ecsYyO/hHxSR7WAUWx7xFG0FMC', 'GUB', '40404040-4040-4040-8040-404040404040', 'CIUDADANO');
    id_emp_mariana := pg_temp.upsert_usuario(41, 'CIUDADANO', 23456789, 'Mariana Logística', 'mariana.logistica@oriental.uy', '1982-07-25', '$2a$12$zhR8dDKWaRxrNbObjjHpqel7X.4ecsYyO/hHxSR7WAUWx7xFG0FMC', 'GUB', '41414141-4141-4141-8141-414141414141', 'CIUDADANO');
    id_emp_gonzalo := pg_temp.upsert_usuario(42, 'CIUDADANO', 24681357, 'Gonzalo Norte', 'gonzalo.norte@expreso.uy', '1979-12-05', '$2a$12$zhR8dDKWaRxrNbObjjHpqel7X.4ecsYyO/hHxSR7WAUWx7xFG0FMC', 'GUB', '42424242-4242-4242-8242-424242424242', 'CIUDADANO');
    id_ciud_lucia  := pg_temp.upsert_usuario(43, 'CIUDADANO', 45678901, 'Lucía Ciudadana', 'lucia.ciudadana@correo.uy', '1995-09-30', '$2a$12$zhR8dDKWaRxrNbObjjHpqel7X.4ecsYyO/hHxSR7WAUWx7xFG0FMC', 'GUB', '43434343-4343-4343-8343-434343434343', 'CIUDADANO_COMUN');
    id_ciud_ana    := pg_temp.upsert_usuario(44, 'CIUDADANO', 80000001, 'Ana Prueba', 'ana@example.invalid', '1990-01-01', '$2a$12$zhR8dDKWaRxrNbObjjHpqel7X.4ecsYyO/hHxSR7WAUWx7xFG0FMC', 'GUB', '11111111-1111-4111-8111-111111111111', 'CIUDADANO_COMUN');

    -- 4.5 Choferes (gub.uy)
    id_chof_carlos  := pg_temp.upsert_usuario(50, 'CHOFER', 11223344, 'Carlos Chofer', 'carlos.chofer@transporte.uy', '1990-08-15', '$2a$12$D9uyMi9KzNRTutQxItWvvOMOmXZCc0p8BEdkvVF6/F5.cK9yM9Nim', 'GUB', '50505050-5050-4050-8050-505050505050', 'CHOFER');
    id_chof_juan    := pg_temp.upsert_usuario(51, 'CHOFER', 55667788, 'Juan Chofer', 'juan.chofer@transporte.uy', '1992-04-12', '$2a$12$D9uyMi9KzNRTutQxItWvvOMOmXZCc0p8BEdkvVF6/F5.cK9yM9Nim', 'GUB', '51515151-5151-4151-8151-515151515151', 'CHOFER');
    id_chof_roberto := pg_temp.upsert_usuario(52, 'CHOFER', 34567890, 'Roberto Ruta', 'roberto.ruta@oriental.uy', '1987-10-02', '$2a$12$D9uyMi9KzNRTutQxItWvvOMOmXZCc0p8BEdkvVF6/F5.cK9yM9Nim', 'GUB', '52525252-5252-4252-8252-525252525252', 'CHOFER');
    id_chof_diego   := pg_temp.upsert_usuario(53, 'CHOFER', 33445566, 'Diego Tacuarembó', 'diego.chofer@expreso.uy', '1993-01-18', '$2a$12$D9uyMi9KzNRTutQxItWvvOMOmXZCc0p8BEdkvVF6/F5.cK9yM9Nim', 'GUB', '53535353-5353-4353-8353-535353535353', 'CHOFER');

    -- 5. Empresas y Vínculos Iniciales (solo se siembran si la empresa aún no existe; nunca borra vínculos existentes)
    IF NOT EXISTS (SELECT 1 FROM empresas WHERE nro_empresa = 101) THEN
        INSERT INTO empresas (nro_empresa, razon_social, nombre_publico, direccion, activa, version)
        VALUES (101, 'Transportes del Plata S.A.', 'TransPlata', 'Ruta 1 km 28, San José', true, 1);

        INSERT INTO empresa_rubros (nro_empresa, rubro_codigo) VALUES (101, 1), (101, 2);
        INSERT INTO empresa_ciudadanos (nro_empresa, ciudadano_id) VALUES (101, id_emp_pedro);
        INSERT INTO empresa_choferes (nro_empresa, chofer_id) VALUES (101, id_chof_carlos), (101, id_chof_juan);
        INSERT INTO vinculos_empresa (clave, nro_empresa, usuario_id, responsable, chofer, version) VALUES
        ('101:' || id_emp_pedro,   101, id_emp_pedro,   true,  false, 1),
        ('101:' || id_chof_carlos, 101, id_chof_carlos, false, true,  1),
        ('101:' || id_chof_juan,   101, id_chof_juan,   false, true,  1)
        ON CONFLICT (clave) DO NOTHING;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM empresas WHERE nro_empresa = 102) THEN
        INSERT INTO empresas (nro_empresa, razon_social, nombre_publico, direccion, activa, version)
        VALUES (102, 'Logística Oriental S.R.L.', 'LogiOriental', 'Av. Giannattasio km 22, Canelones', true, 1);

        INSERT INTO empresa_rubros (nro_empresa, rubro_codigo) VALUES (102, 2), (102, 3);
        INSERT INTO empresa_ciudadanos (nro_empresa, ciudadano_id) VALUES (102, id_emp_mariana);
        INSERT INTO empresa_choferes (nro_empresa, chofer_id) VALUES (102, id_chof_carlos), (102, id_chof_roberto);
        INSERT INTO vinculos_empresa (clave, nro_empresa, usuario_id, responsable, chofer, version) VALUES
        ('102:' || id_emp_mariana, 102, id_emp_mariana, true,  false, 1),
        ('102:' || id_chof_carlos, 102, id_chof_carlos, false, true,  1),
        ('102:' || id_chof_roberto,102, id_chof_roberto,false, true,  1)
        ON CONFLICT (clave) DO NOTHING;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM empresas WHERE nro_empresa = 103) THEN
        INSERT INTO empresas (nro_empresa, razon_social, nombre_publico, direccion, activa, version)
        VALUES (103, 'Expreso del Norte S.A.', 'ExpresoNorte', 'Ruta 5 km 390, Tacuarembó', true, 1);

        INSERT INTO empresa_rubros (nro_empresa, rubro_codigo) VALUES (103, 1), (103, 4);
        INSERT INTO empresa_ciudadanos (nro_empresa, ciudadano_id) VALUES (103, id_emp_gonzalo);
        INSERT INTO empresa_choferes (nro_empresa, chofer_id) VALUES (103, id_chof_diego);
        INSERT INTO vinculos_empresa (clave, nro_empresa, usuario_id, responsable, chofer, version) VALUES
        ('103:' || id_emp_gonzalo, 103, id_emp_gonzalo, true,  false, 1),
        ('103:' || id_chof_diego,  103, id_chof_diego,  false, true,  1)
        ON CONFLICT (clave) DO NOTHING;
    END IF;

    -- 6. Vehículos y Certificados (ITV / PNC)
    INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidad_carga, estado_habilitacion, nro_empresa) VALUES
    ('STP 1234', 'Volvo', 'FH 540', 9000, 31000, true, 101),
    ('ABC 9876', 'Scania', 'R450', 10000, 30000, false, 101),
    ('STP 5678', 'Mercedes-Benz', 'Actros 2645', 8500, 33000, false, 101),
    ('LOR 2024', 'Iveco', 'Stralis 460', 8000, 32000, true, 102),
    ('LOR 9999', 'Volkswagen', 'Constellation 19.320', 7500, 24000, true, 102),
    ('EXP 1111', 'Scania', 'G410', 9500, 35000, true, 103)
    ON CONFLICT (matricula) DO NOTHING;

    -- 6.2 ITVs (Inspección Técnica Vehicular)
    INSERT INTO itvs (nrocertificado, fecha_vencimiento, vehiculo_matricula) VALUES
    (1001, CURRENT_DATE + INTERVAL '180 days', 'STP 1234'),
    (1002, CURRENT_DATE - INTERVAL '90 days',  'ABC 9876'),
    (1003, CURRENT_DATE + INTERVAL '120 days', 'STP 5678'),
    (1004, CURRENT_DATE + INTERVAL '240 days', 'LOR 2024'),
    (1005, CURRENT_DATE + INTERVAL '150 days', 'LOR 9999'),
    (1006, CURRENT_DATE + INTERVAL '300 days', 'EXP 1111')
    ON CONFLICT (nrocertificado) DO NOTHING;

    -- 6.3 PNCs (Permiso Nacional de Circulación)
    INSERT INTO pncs (nro, fecha_vencimiento, numero_permiso, vehiculo_matricula) VALUES
    (2001, CURRENT_DATE + INTERVAL '365 days', 'PNC-2026-2001', 'STP 1234'),
    (2002, CURRENT_DATE - INTERVAL '60 days',  'PNC-2025-2002', 'ABC 9876'),
    (2003, CURRENT_DATE - INTERVAL '30 days',  'PNC-2025-2003', 'STP 5678'),
    (2004, CURRENT_DATE + INTERVAL '240 days', 'PNC-2026-2004', 'LOR 2024'),
    (2005, CURRENT_DATE + INTERVAL '180 days', 'PNC-2026-2005', 'LOR 9999'),
    (2006, CURRENT_DATE + INTERVAL '300 days', 'PNC-2026-2006', 'EXP 1111')
    ON CONFLICT (nro) DO NOTHING;

    -- 7. Guías de Viaje con Pesos Reales en Kilogramos y Rubros / Tipos de Carga del Catálogo
    -- 7.1 Guía 1001: EN_CURSO (con eventos, tracking, pesadas con sobrepeso e infracción DETECTADA)
    IF NOT EXISTS (SELECT 1 FROM guias_de_viaje WHERE id = 1001) THEN
        INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
        VALUES (1001, CURRENT_DATE, 'Puerto de Montevideo', 'Rivera (Frontera)', 'Forestal', 30000.0, 'EN_CURSO', id_chof_carlos, 101, 'STP 1234', 1);
        INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1001, 2);
    END IF;

    -- 7.2 Guía 1002: SIN_INICIAR (lista para iniciar)
    IF NOT EXISTS (SELECT 1 FROM guias_de_viaje WHERE id = 1002) THEN
        INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
        VALUES (1002, CURRENT_DATE, 'Colonia del Sacramento', 'Puerto de Montevideo', 'Forestal', 28000.0, 'SIN_INICIAR', id_chof_juan, 101, 'STP 1234', 1);
        INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1002, 1);
    END IF;

    -- 7.3 Guía 1003: SIN_INICIAR con vehículo inhabilitado (para probar fiscalización preventiva)
    IF NOT EXISTS (SELECT 1 FROM guias_de_viaje WHERE id = 1003) THEN
        INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
        VALUES (1003, CURRENT_DATE, 'Nueva Palmira (Puerto)', 'Puerto de Montevideo', 'Granos y Cereales', 29500.0, 'SIN_INICIAR', id_chof_carlos, 101, 'ABC 9876', 1);
        INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1003, 4);
    END IF;

    -- 7.4 Guía 1004: FINALIZADO con Infracción CONFIRMADA, Descargo y Registro de Auditoría
    IF NOT EXISTS (SELECT 1 FROM guias_de_viaje WHERE id = 1004) THEN
        INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
        VALUES (1004, CURRENT_DATE - INTERVAL '3 days', 'Salto', 'Puerto de Montevideo', 'Granos y Cereales', 31000.0, 'FINALIZADO', id_chof_carlos, 101, 'STP 1234', 1);
        INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1004, 4);
    END IF;

    -- 7.5 Guía 1005: EN_CURSO en Empresa 102 (Viaje normal sin excesos)
    IF NOT EXISTS (SELECT 1 FROM guias_de_viaje WHERE id = 1005) THEN
        INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
        VALUES (1005, CURRENT_DATE, 'Canelones', 'Fray Bentos (UPM)', 'Granos y Cereales', 30000.0, 'EN_CURSO', id_chof_roberto, 102, 'LOR 2024', 1);
        INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1005, 5);
    END IF;

    -- 7.6 Guía 1006: FINALIZADO en Empresa 102 (Viaje limpio exitoso)
    IF NOT EXISTS (SELECT 1 FROM guias_de_viaje WHERE id = 1006) THEN
        INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
        VALUES (1006, CURRENT_DATE - INTERVAL '1 day', 'Maldonado', 'Puerto de Montevideo', 'Ganadería', 22000.0, 'FINALIZADO', id_chof_roberto, 102, 'LOR 9999', 1);
        INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1006, 6);
    END IF;

    -- 7.7 Guía 1007: SIN_INICIAR en Empresa 103 (Expreso del Norte)
    IF NOT EXISTS (SELECT 1 FROM guias_de_viaje WHERE id = 1007) THEN
        INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
        VALUES (1007, CURRENT_DATE, 'Tacuarembó', 'Punta Pereira (Montes del Plata)', 'Forestal', 33000.0, 'SIN_INICIAR', id_chof_diego, 103, 'EXP 1111', 1);
        INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1007, 3);
    END IF;
END $$;

-- 8. Eventos de Viaje e Incidentes (con imagen Base64 de ejemplo para previsualización en clic)
INSERT INTO eventos_viaje (id, uuid, tiempo, tipo, latitud, longitud, guia_id, distancia_desvio_km, cantidad_kg, mercaderia, empresa_carga) VALUES
(2001, 'a1111111-1111-4111-8111-111111111001', CURRENT_TIMESTAMP - INTERVAL '4 hours', 'INICIO',    -34.9011, -56.1645, 1001, 0.0, 30000, 'Eucaliptus', 'TransPlata'),
(2002, 'a1111111-1111-4111-8111-111111111002', CURRENT_TIMESTAMP - INTERVAL '3 hours', 'CARGA',     -34.8500, -56.1500, 1001, 0.2, 30000, 'Eucaliptus', 'TransPlata'),
(2003, 'a1111111-1111-4111-8111-111111111003', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'POSICION',  -33.8000, -56.0500, 1001, 0.4, 30000, 'Eucaliptus', 'TransPlata'),
(2004, 'a1111111-1111-4111-8111-111111111004', CURRENT_TIMESTAMP - INTERVAL '1 hour',  'INCIDENTE', -33.2500, -56.0200, 1001, 0.5, 30000, 'Eucaliptus', 'TransPlata')
ON CONFLICT (id) DO NOTHING;

INSERT INTO incidentes_ruta (id, descripcion, foto, evento_id) VALUES
(3001, 'Pinchadura de neumático trasero derecho en Ruta 5 km 98', 'pinchadura_rueda.jpg', 2004)
ON CONFLICT (id) DO NOTHING;

INSERT INTO eventos_viaje (id, uuid, tiempo, tipo, latitud, longitud, guia_id, distancia_desvio_km, cantidad_kg, mercaderia, empresa_carga) VALUES
(2005, 'a1111111-1111-4111-8111-111111111005', CURRENT_TIMESTAMP - INTERVAL '3 days', 'INICIO', -31.3833, -57.9667, 1004, 0.0, 31000, 'Soja', 'TransPlata'),
(2006, 'a1111111-1111-4111-8111-111111111006', CURRENT_TIMESTAMP - INTERVAL '2 days', 'FIN',    -34.9011, -56.1645, 1004, 0.0, 31000, 'Soja', 'TransPlata')
ON CONFLICT (id) DO NOTHING;

INSERT INTO eventos_viaje (id, uuid, tiempo, tipo, latitud, longitud, guia_id, distancia_desvio_km, cantidad_kg, mercaderia, empresa_carga) VALUES
(2007, 'a1111111-1111-4111-8111-111111111007', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'INICIO',   -34.5228, -56.2778, 1005, 0.0, 30000, 'Trigo', 'LogiOriental'),
(2008, 'a1111111-1111-4111-8111-111111111008', CURRENT_TIMESTAMP - INTERVAL '1 hour',  'POSICION', -34.1000, -56.8000, 1005, 0.1, 30000, 'Trigo', 'LogiOriental')
ON CONFLICT (id) DO NOTHING;

-- 9. Puntos de Tracking
INSERT INTO puntos_tracking (id, latitud, longitud, tiempo, distancia_desvio_km, guia_id) VALUES
(4001, -34.9011, -56.1645, CURRENT_TIMESTAMP - INTERVAL '4 hours', 0.0, 1001),
(4002, -34.6000, -56.1000, CURRENT_TIMESTAMP - INTERVAL '3 hours', 0.2, 1001),
(4003, -33.8000, -56.0500, CURRENT_TIMESTAMP - INTERVAL '2 hours', 0.4, 1001),
(4004, -33.2500, -56.0200, CURRENT_TIMESTAMP - INTERVAL '1 hour',  0.5, 1001)
ON CONFLICT (id) DO NOTHING;

-- 10. Pesadas en Balanza
INSERT INTO pesadas_balanza (id_pesada, fecha, hora, peso_registrado, guia_id, vehiculo_matricula) VALUES
(5001, CURRENT_DATE, (CURRENT_TIME - INTERVAL '2 hours')::time, 38500, 1001, 'STP 1234'),
(5002, CURRENT_DATE, (CURRENT_TIME - INTERVAL '1 hour')::time,  43500, 1001, 'STP 1234'),
(5003, CURRENT_DATE - INTERVAL '3 days', '14:30:00',            44200, 1004, 'STP 1234'),
(5004, CURRENT_DATE, (CURRENT_TIME - INTERVAL '30 minutes')::time, 37000, 1005, 'LOR 2024'),
(5005, CURRENT_DATE - INTERVAL '1 day', '16:00:00',             29500, 1006, 'LOR 9999'),
(5006, CURRENT_DATE, (CURRENT_TIME - INTERVAL '15 minutes')::time, 35000, NULL, 'ABC 9876')
ON CONFLICT (id_pesada) DO NOTHING;

-- 11. Infracciones, Descargos y Auditoría
INSERT INTO infracciones (id_caso, tipo, estado, fecha, evidencia, guia_id, vehiculo_matricula) VALUES
(501, 'EXCESO_DE_CARGA', 'NOTIFICADA', CURRENT_DATE,
 'Pesada registrada de 43.500 kg supera el peso máximo permitido de 40.000 kg (tolerancia 500 kg).',
 1001, 'STP 1234')
ON CONFLICT (id_caso) DO NOTHING;

INSERT INTO infracciones (id_caso, tipo, estado, fecha, evidencia, fecha_resolucion, resolucion, fundamento, guia_id, vehiculo_matricula) VALUES
(502, 'EXCESO_DE_CARGA', 'CONFIRMADA', CURRENT_DATE - INTERVAL '3 days',
 'Pesada registrada en balanza de 44.200 kg supera el límite de 40.000 kg.',
 CURRENT_DATE - INTERVAL '1 day', 'CONFIRMADA',
 'Se desestima el descargo presentado. La balanza oficial contaba con calibración metrológica al día.',
 1004, 'STP 1234')
ON CONFLICT (id_caso) DO NOTHING;

INSERT INTO descargos (id, fecha, descargo, infraccion_id) VALUES
(6001, CURRENT_DATE - INTERVAL '2 days', 'Se solicita la anulación del acta debido a ráfagas de viento y oscilaciones mecánicas en la plataforma.', 502)
ON CONFLICT (id) DO NOTHING;

INSERT INTO infracciones (id_caso, tipo, estado, fecha, evidencia, guia_id, vehiculo_matricula) VALUES
(503, 'VIAJE_SIN_GUIA', 'DETECTADA', CURRENT_DATE,
 'Vehículo detectado en balanza fiscalizadora transportando carga sin guía de viaje registrada ni activa.',
 NULL, 'ABC 9876')
ON CONFLICT (id_caso) DO NOTHING;

-- Trazabilidad de Auditoría Inmutable (sin hacer UPDATE ni DELETE para respetar trigger append-only)
INSERT INTO registro_auditoria (id, infraccion_id, resolucion, fecha, usuario, accion, fundamento, timestamp_creacion) VALUES
(7001, 502, 'DETECTADA',   CURRENT_DATE - INTERVAL '3 days', 'SISTEMA (MotorFiscalizacion)',   'CREAR_INFRACCION',     'Pesada registrada de 44.200 kg excediendo tolerancia.', CURRENT_TIMESTAMP - INTERVAL '3 days'),
(7002, 502, 'NOTIFICADA',  CURRENT_DATE - INTERVAL '3 days', 'SISTEMA (Notificación)',         'NOTIFICAR_INFRACCION', 'Notificación automática enviada a TransPlata.',        CURRENT_TIMESTAMP - INTERVAL '3 days'),
(7003, 502, 'EN_DESCARGO', CURRENT_DATE - INTERVAL '2 days', 'pedro.empresario@transporte.uy', 'PRESENTAR_DESCARGO',   'Se solicita la anulación del acta.',                   CURRENT_TIMESTAMP - INTERVAL '2 days'),
(7004, 502, 'CONFIRMADA',  CURRENT_DATE - INTERVAL '1 day',  'funcionario@cargauy.gub.uy',     'CONFIRMAR_INFRACCION', 'Se desestima descargo; balanza calibrada.',            CURRENT_TIMESTAMP - INTERVAL '1 day'),
(7005, 501, 'DETECTADA',   CURRENT_DATE,                     'SISTEMA (MotorFiscalizacion)',   'CREAR_INFRACCION',     'Pesada registrada de 43.500 kg excediendo tolerancia.', CURRENT_TIMESTAMP - INTERVAL '1 hour'),
(7006, 501, 'NOTIFICADA',  CURRENT_DATE,                     'funcionario@cargauy.gub.uy',     'NOTIFICAR_INFRACCION', 'Notificada a la empresa para presentación de descargo.', CURRENT_TIMESTAMP - INTERVAL '30 minutes')
ON CONFLICT (id) DO NOTHING;

-- 12. Actualizar secuencias de PostgreSQL de forma segura
SELECT setval(pg_get_serial_sequence('usuarios', 'id'), (SELECT COALESCE(MAX(id), 100) + 1 FROM usuarios), false);
SELECT setval(pg_get_serial_sequence('guias_de_viaje', 'id'), (SELECT COALESCE(MAX(id), 1000) + 1 FROM guias_de_viaje), false);
SELECT setval(pg_get_serial_sequence('eventos_viaje', 'id'), (SELECT COALESCE(MAX(id), 2000) + 1 FROM eventos_viaje), false);
SELECT setval(pg_get_serial_sequence('incidentes_ruta', 'id'), (SELECT COALESCE(MAX(id), 3000) + 1 FROM incidentes_ruta), false);
SELECT setval(pg_get_serial_sequence('puntos_tracking', 'id'), (SELECT COALESCE(MAX(id), 4000) + 1 FROM puntos_tracking), false);
SELECT setval(pg_get_serial_sequence('pesadas_balanza', 'id_pesada'), (SELECT COALESCE(MAX(id_pesada), 5000) + 1 FROM pesadas_balanza), false);
SELECT setval(pg_get_serial_sequence('infracciones', 'id_caso'), (SELECT COALESCE(MAX(id_caso), 500) + 1 FROM infracciones), false);
SELECT setval(pg_get_serial_sequence('descargos', 'id'), (SELECT COALESCE(MAX(id), 6000) + 1 FROM descargos), false);
SELECT setval(pg_get_serial_sequence('registro_auditoria', 'id'), (SELECT COALESCE(MAX(id), 7000) + 1 FROM registro_auditoria), false);
SELECT setval(pg_get_serial_sequence('rubros', 'codigo'), (SELECT COALESCE(MAX(codigo), 10) + 1 FROM rubros), false);
SELECT setval(pg_get_serial_sequence('tipos_carga', 'codigo'), (SELECT COALESCE(MAX(codigo), 10) + 1 FROM tipos_carga), false);
SELECT setval(pg_get_serial_sequence('itvs', 'nrocertificado'), (SELECT COALESCE(MAX(nrocertificado), 1000) + 1 FROM itvs), false);
SELECT setval(pg_get_serial_sequence('pncs', 'nro'), (SELECT COALESCE(MAX(nro), 2000) + 1 FROM pncs), false);

COMMIT;
