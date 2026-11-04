-- ============================================================================
-- CargaUY - Script Integral de Datos de Prueba (Seed Data)
-- ============================================================================

-- 1. Reglas de Fiscalización
INSERT INTO reglas_fiscalizacion (id, umbral_carga, umbral_tracking, version)
VALUES (1, 500, 5.0, 1)
ON CONFLICT (id) DO UPDATE SET umbral_carga = 500, umbral_tracking = 5.0;

-- 2. Rubros
INSERT INTO rubros (codigo, nombre, descripcion) VALUES
(1, 'Forestal', 'Transporte de madera en rolos, chips y subproductos forestales'),
(2, 'Granos y Cereales', 'Transporte de soja, trigo, maíz, arroz y cereales a granel'),
(3, 'Ganadería', 'Transporte de hacienda vacuna, ovina y equina'),
(4, 'Combustibles y Peligrosas', 'Transporte de hidrocarburos, sustancias inflamables y químicas')
ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre, descripcion = EXCLUDED.descripcion;

-- 3. Tipos de Carga
INSERT INTO tipos_carga (codigo, descripcion) VALUES
(1, 'Troncos de Pino'),
(2, 'Eucaliptus'),
(3, 'Chips de Celulosa'),
(4, 'Soja'),
(5, 'Trigo'),
(6, 'Ganado Vacuno'),
(7, 'Gasoil')
ON CONFLICT (codigo) DO UPDATE SET descripcion = EXCLUDED.descripcion;

-- 4. Usuarios
-- 4.1 Administrador Central (LDAP: admin.central / admin123)
INSERT INTO usuarios (id, tipo_usuario, ci, nombre, mail, fecha_nac, password, activo, origen_identidad, uid_externo, version)
VALUES (10, 'ADMINISTRADOR', 10000001, 'Admin Central', 'admin@cargauy.gub.uy', '1980-01-15', 'admin123', true, 'LDAP', 'admin.central', 1)
ON CONFLICT (ci) DO UPDATE SET nombre = EXCLUDED.nombre, mail = EXCLUDED.mail, password = EXCLUDED.password, origen_identidad = EXCLUDED.origen_identidad, uid_externo = EXCLUDED.uid_externo;

INSERT INTO administradores (usuario_id) VALUES (10) ON CONFLICT (usuario_id) DO NOTHING;

-- Asegurar rol al admin LDAP si existe (CI 90000001, martin.admin / admin123)
INSERT INTO administradores (usuario_id)
SELECT id FROM usuarios WHERE ci = 90000001
ON CONFLICT (usuario_id) DO NOTHING;

INSERT INTO usuario_roles (usuario_id, rol)
SELECT id, 'ADMINISTRADOR' FROM usuarios WHERE ci = 90000001
ON CONFLICT DO NOTHING;

-- 4.2 Autoridad MTOP (LDAP: alberto.autoridad / autoridad123)
INSERT INTO usuarios (id, tipo_usuario, ci, nombre, mail, fecha_nac, password, activo, origen_identidad, uid_externo, version)
VALUES (20, 'AUTORIDAD', 20000002, 'Dr. Alberto Director', 'autoridad@cargauy.gub.uy', '1975-06-20', 'autoridad123', true, 'LDAP', 'alberto.autoridad', 1)
ON CONFLICT (ci) DO UPDATE SET nombre = EXCLUDED.nombre, mail = EXCLUDED.mail, password = EXCLUDED.password, origen_identidad = EXCLUDED.origen_identidad, uid_externo = EXCLUDED.uid_externo;

INSERT INTO autoridades (usuario_id, cargo)
VALUES (20, 'Director Nacional de Transporte')
ON CONFLICT (usuario_id) DO UPDATE SET cargo = EXCLUDED.cargo;

-- 4.3 Funcionarios Fiscalizadores (Keycloak / gub.uy)
INSERT INTO usuarios (id, tipo_usuario, ci, nombre, mail, fecha_nac, password, activo, origen_identidad, uid_externo, version)
VALUES 
(30, 'FUNCIONARIO', 12345678, 'Juan Funcionario', 'funcionario@cargauy.gub.uy', '1985-05-20', 'funcionario123', true, 'GUB', '30303030-3030-4030-8030-303030303030', 1),
(31, 'FUNCIONARIO', 12345679, 'Laura Fiscalizadora', 'laura.fiscal@cargauy.gub.uy', '1988-11-14', 'funcionario123', true, 'GUB', '31313131-3131-4131-8131-313131313131', 1)
ON CONFLICT (ci) DO UPDATE SET nombre = EXCLUDED.nombre, mail = EXCLUDED.mail, password = EXCLUDED.password, origen_identidad = EXCLUDED.origen_identidad, uid_externo = EXCLUDED.uid_externo;

INSERT INTO funcionarios (usuario_id, nro_funcionario, departamento)
VALUES 
(30, 101, 'Montevideo'),
(31, 102, 'Rutas Nacionales')
ON CONFLICT (usuario_id) DO UPDATE SET nro_funcionario = EXCLUDED.nro_funcionario, departamento = EXCLUDED.departamento;

-- 4.4 Ciudadanos (Empresarios / Representantes - Keycloak / gub.uy)
INSERT INTO usuarios (id, tipo_usuario, ci, nombre, mail, fecha_nac, password, activo, origen_identidad, uid_externo, version)
VALUES 
(40, 'CIUDADANO', 87654321, 'Pedro Empresario', 'pedro.empresario@transporte.uy', '1978-03-10', 'ciudadano123', true, 'GUB', '40404040-4040-4040-8040-404040404040', 1),
(41, 'CIUDADANO', 23456789, 'Mariana Logística', 'mariana.logistica@oriental.uy', '1982-07-25', 'ciudadano123', true, 'GUB', '41414141-4141-4141-8141-414141414141', 1),
(42, 'CIUDADANO', 24681357, 'Gonzalo Norte', 'gonzalo.norte@expreso.uy', '1979-12-05', 'ciudadano123', true, 'GUB', '42424242-4242-4242-8242-424242424242', 1),
(43, 'CIUDADANO', 45678901, 'Lucía Ciudadana', 'lucia.ciudadana@correo.uy', '1995-09-30', 'ciudadano123', true, 'GUB', '43434343-4343-4343-8343-434343434343', 1),
(44, 'CIUDADANO', 80000001, 'Ana Prueba', 'ana@example.invalid', '1990-01-01', 'ciudadano123', true, 'GUB', '11111111-1111-4111-8111-111111111111', 1)
ON CONFLICT (ci) DO UPDATE SET nombre = EXCLUDED.nombre, mail = EXCLUDED.mail, password = EXCLUDED.password, origen_identidad = EXCLUDED.origen_identidad, uid_externo = EXCLUDED.uid_externo;

INSERT INTO ciudadanos (usuario_id) VALUES (40), (41), (42), (43), (44)
ON CONFLICT (usuario_id) DO NOTHING;

-- 4.5 Choferes (Keycloak / gub.uy)
INSERT INTO usuarios (id, tipo_usuario, ci, nombre, mail, fecha_nac, password, activo, origen_identidad, uid_externo, version)
VALUES 
(50, 'CHOFER', 11223344, 'Carlos Chofer', 'carlos.chofer@transporte.uy', '1990-08-15', 'chofer123', true, 'GUB', '50505050-5050-4050-8050-505050505050', 1),
(51, 'CHOFER', 55667788, 'Juan Chofer', 'juan.chofer@transporte.uy', '1992-04-12', 'chofer123', true, 'GUB', '51515151-5151-4151-8151-515151515151', 1),
(52, 'CHOFER', 34567890, 'Roberto Ruta', 'roberto.ruta@oriental.uy', '1987-10-02', 'chofer123', true, 'GUB', '52525252-5252-4252-8252-525252525252', 1),
(53, 'CHOFER', 33445566, 'Diego Tacuarembó', 'diego.chofer@expreso.uy', '1993-01-18', 'chofer123', true, 'GUB', '53535353-5353-4353-8353-535353535353', 1)
ON CONFLICT (ci) DO UPDATE SET nombre = EXCLUDED.nombre, mail = EXCLUDED.mail, password = EXCLUDED.password, origen_identidad = EXCLUDED.origen_identidad, uid_externo = EXCLUDED.uid_externo;

INSERT INTO choferes (usuario_id) VALUES (50), (51), (52), (53)
ON CONFLICT (usuario_id) DO NOTHING;

-- 4.6 Roles en usuario_roles
INSERT INTO usuario_roles (usuario_id, rol) VALUES
(10, 'ADMINISTRADOR'),
(20, 'AUTORIDAD'),
(30, 'FUNCIONARIO'),
(31, 'FUNCIONARIO'),
(40, 'CIUDADANO'),
(41, 'CIUDADANO'),
(42, 'CIUDADANO'),
(43, 'CIUDADANO'),
(44, 'CIUDADANO'),
(50, 'CHOFER'),
(50, 'CIUDADANO'),
(51, 'CHOFER'),
(51, 'CIUDADANO'),
(52, 'CHOFER'),
(52, 'CIUDADANO'),
(53, 'CHOFER'),
(53, 'CIUDADANO')
ON CONFLICT DO NOTHING;

-- 5. Empresas
INSERT INTO empresas (nro_empresa, razon_social, nombre_publico, direccion, activa, version) VALUES
(101, 'Transportes del Plata S.A.', 'TransPlata', 'Ruta 1 km 28, San José', true, 1),
(102, 'Logística Oriental S.R.L.', 'LogiOriental', 'Av. Giannattasio km 22, Canelones', true, 1),
(103, 'Expreso del Norte S.A.', 'ExpresoNorte', 'Ruta 5 km 390, Tacuarembó', true, 1)
ON CONFLICT (nro_empresa) DO UPDATE SET razon_social = EXCLUDED.razon_social, nombre_publico = EXCLUDED.nombre_publico, direccion = EXCLUDED.direccion, activa = true;

-- Relaciones Empresa <-> Rubros
INSERT INTO empresa_rubros (nro_empresa, rubro_codigo) VALUES
(101, 1), (101, 2),
(102, 2), (102, 3),
(103, 1), (103, 4)
ON CONFLICT DO NOTHING;

-- Relaciones Empresa <-> Ciudadanos (Titulares / Responsables)
INSERT INTO empresa_ciudadanos (nro_empresa, ciudadano_id) VALUES
(101, 40), (101, 44),
(102, 41),
(103, 42)
ON CONFLICT DO NOTHING;

-- Relaciones Empresa <-> Choferes
INSERT INTO empresa_choferes (nro_empresa, chofer_id) VALUES
(101, 50), (101, 51),
(102, 52),
(103, 53)
ON CONFLICT DO NOTHING;

-- Vínculos formales en vinculos_empresa
INSERT INTO vinculos_empresa (clave, nro_empresa, usuario_id, responsable, chofer, version) VALUES
('101:40', 101, 40, true, false, 1),
('101:44', 101, 44, true, false, 1),
('101:50', 101, 50, false, true, 1),
('101:51', 101, 51, false, true, 1),
('102:41', 102, 41, true, false, 1),
('102:52', 102, 52, false, true, 1),
('103:42', 103, 42, true, false, 1),
('103:53', 103, 53, false, true, 1)
ON CONFLICT (clave) DO UPDATE SET responsable = EXCLUDED.responsable, chofer = EXCLUDED.chofer;

-- 6. Vehículos y Certificados (ITV / PNC)
-- 6.1 Vehículos
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidad_carga, estado_habilitacion, nro_empresa) VALUES
('STP 1234', 'Volvo', 'FH 540', 9000, 31000, true, 101),
('ABC 9876', 'Scania', 'R450', 10000, 30000, false, 101),
('STP 5678', 'Mercedes-Benz', 'Actros 2645', 8500, 33000, true, 101),
('LOR 2024', 'Iveco', 'Stralis 460', 8000, 32000, true, 102),
('LOR 9999', 'Volkswagen', 'Constellation 19.320', 7500, 24000, true, 102),
('EXP 1111', 'Scania', 'G410', 9500, 35000, true, 103)
ON CONFLICT (matricula) DO UPDATE SET marca = EXCLUDED.marca, modelo = EXCLUDED.modelo, peso = EXCLUDED.peso, capacidad_carga = EXCLUDED.capacidad_carga, estado_habilitacion = EXCLUDED.estado_habilitacion, nro_empresa = EXCLUDED.nro_empresa;

-- 6.2 ITVs (Inspección Técnica Vehicular)
INSERT INTO itvs (nrocertificado, fecha_vencimiento, vehiculo_matricula) VALUES
(1001, CURRENT_DATE + INTERVAL '180 days', 'STP 1234'),
(1002, CURRENT_DATE - INTERVAL '90 days',  'ABC 9876'),
(1003, CURRENT_DATE + INTERVAL '120 days', 'STP 5678'),
(1004, CURRENT_DATE + INTERVAL '240 days', 'LOR 2024'),
(1005, CURRENT_DATE + INTERVAL '150 days', 'LOR 9999'),
(1006, CURRENT_DATE + INTERVAL '300 days', 'EXP 1111')
ON CONFLICT (nrocertificado) DO UPDATE SET fecha_vencimiento = EXCLUDED.fecha_vencimiento, vehiculo_matricula = EXCLUDED.vehiculo_matricula;

-- 6.3 PNCs (Permiso Nacional de Circulación)
INSERT INTO pncs (nro, fecha_vencimiento, vehiculo_matricula) VALUES
(2001, CURRENT_DATE + INTERVAL '365 days', 'STP 1234'),
(2002, CURRENT_DATE - INTERVAL '60 days',  'ABC 9876'),
(2003, CURRENT_DATE - INTERVAL '30 days',  'STP 5678'),
(2004, CURRENT_DATE + INTERVAL '240 days', 'LOR 2024'),
(2005, CURRENT_DATE + INTERVAL '180 days', 'LOR 9999'),
(2006, CURRENT_DATE + INTERVAL '300 days', 'EXP 1111')
ON CONFLICT (nro) DO UPDATE SET fecha_vencimiento = EXCLUDED.fecha_vencimiento, vehiculo_matricula = EXCLUDED.vehiculo_matricula;

-- 7. Guías de Viaje con todos los estados
-- 7.1 Guía 1001: EN_CURSO (con eventos, tracking, pesadas con sobrepeso e infracción DETECTADA)
INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
VALUES (1001, CURRENT_DATE, 'Montevideo', 'Rivera', 'Forestal', 45.0, 'EN_CURSO', 50, 101, 'STP 1234', 1)
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, origen = EXCLUDED.origen, destino = EXCLUDED.destino;

INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1001, 2) ON CONFLICT DO NOTHING;

-- 7.2 Guía 1002: SIN_INICIAR (lista para iniciar)
INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
VALUES (1002, CURRENT_DATE, 'Colonia del Sacramento', 'Montevideo', 'Forestal', 28.0, 'SIN_INICIAR', 51, 101, 'STP 1234', 1)
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, origen = EXCLUDED.origen, destino = EXCLUDED.destino;

INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1002, 1) ON CONFLICT DO NOTHING;

-- 7.3 Guía 1003: SIN_INICIAR con vehículo inhabilitado (para probar fiscalización preventiva)
INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
VALUES (1003, CURRENT_DATE, 'Nueva Palmira', 'Montevideo', 'Granos y Cereales', 32.0, 'SIN_INICIAR', 50, 101, 'ABC 9876', 1)
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, origen = EXCLUDED.origen, destino = EXCLUDED.destino;

INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1003, 4) ON CONFLICT DO NOTHING;

-- 7.4 Guía 1004: FINALIZADO con Infracción CONFIRMADA, Descargo y Registro de Auditoría
INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
VALUES (1004, CURRENT_DATE - INTERVAL '3 days', 'Salto', 'Montevideo', 'Granos y Cereales', 38.0, 'FINALIZADO', 50, 101, 'STP 1234', 1)
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, origen = EXCLUDED.origen, destino = EXCLUDED.destino;

INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1004, 4) ON CONFLICT DO NOTHING;

-- 7.5 Guía 1005: EN_CURSO en Empresa 102 (Viaje normal sin excesos)
INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
VALUES (1005, CURRENT_DATE, 'Canelones', 'Fray Bentos', 'Granos y Cereales', 30.0, 'EN_CURSO', 52, 102, 'LOR 2024', 1)
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, origen = EXCLUDED.origen, destino = EXCLUDED.destino;

INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1005, 5) ON CONFLICT DO NOTHING;

-- 7.6 Guía 1006: FINALIZADO en Empresa 102 (Viaje limpio exitoso)
INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
VALUES (1006, CURRENT_DATE - INTERVAL '1 day', 'Maldonado', 'Montevideo', 'Ganadería', 25.0, 'FINALIZADO', 52, 102, 'LOR 9999', 1)
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, origen = EXCLUDED.origen, destino = EXCLUDED.destino;

INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1006, 6) ON CONFLICT DO NOTHING;

-- 7.7 Guía 1007: SIN_INICIAR en Empresa 103 (Expreso del Norte)
INSERT INTO guias_de_viaje (id, fecha, origen, destino, rubro_cliente, volumen_carga, estado, chofer_id, nro_empresa, vehiculo_matricula, version)
VALUES (1007, CURRENT_DATE, 'Tacuarembó', 'Punta Pereira', 'Forestal', 35.0, 'SIN_INICIAR', 53, 103, 'EXP 1111', 1)
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, origen = EXCLUDED.origen, destino = EXCLUDED.destino;

INSERT INTO guia_tipos_carga (guia_id, tipo_carga_codigo) VALUES (1007, 3) ON CONFLICT DO NOTHING;

-- 8. Eventos de Viaje e Incidentes
-- Eventos para Guía 1001 (EN_CURSO)
INSERT INTO eventos_viaje (id, uuid, tiempo, tipo, latitud, longitud, guia_id, distancia_desvio_km, cantidad_kg, mercaderia, empresa_carga) VALUES
(2001, 'a1111111-1111-4111-8111-111111111001', CURRENT_TIMESTAMP - INTERVAL '4 hours', 'INICIO',    -34.9011, -56.1645, 1001, 0.0, 45000, 'Eucaliptus', 'TransPlata'),
(2002, 'a1111111-1111-4111-8111-111111111002', CURRENT_TIMESTAMP - INTERVAL '3 hours', 'CARGA',     -34.8500, -56.1500, 1001, 0.2, 45000, 'Eucaliptus', 'TransPlata'),
(2003, 'a1111111-1111-4111-8111-111111111003', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'POSICION',  -33.8000, -56.0500, 1001, 0.4, 45000, 'Eucaliptus', 'TransPlata'),
(2004, 'a1111111-1111-4111-8111-111111111004', CURRENT_TIMESTAMP - INTERVAL '1 hour',  'INCIDENTE', -33.2500, -56.0200, 1001, 0.5, 45000, 'Eucaliptus', 'TransPlata')
ON CONFLICT (id) DO NOTHING;

INSERT INTO incidentes_ruta (id, descripcion, foto, evento_id) VALUES
(3001, 'Pinchadura de neumático trasero derecho en Ruta 5 km 98', 'pinchadura_rueda.jpg', 2004)
ON CONFLICT (id) DO NOTHING;

-- Eventos para Guía 1004 (FINALIZADO)
INSERT INTO eventos_viaje (id, uuid, tiempo, tipo, latitud, longitud, guia_id, distancia_desvio_km, cantidad_kg, mercaderia, empresa_carga) VALUES
(2005, 'a1111111-1111-4111-8111-111111111005', CURRENT_TIMESTAMP - INTERVAL '3 days', 'INICIO', -31.3833, -57.9667, 1004, 0.0, 38000, 'Soja', 'TransPlata'),
(2006, 'a1111111-1111-4111-8111-111111111006', CURRENT_TIMESTAMP - INTERVAL '2 days', 'FIN',    -34.9011, -56.1645, 1004, 0.0, 38000, 'Soja', 'TransPlata')
ON CONFLICT (id) DO NOTHING;

-- Eventos para Guía 1005 (EN_CURSO)
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
(5005, CURRENT_DATE - INTERVAL '1 day', '16:00:00',             32000, 1006, 'LOR 9999'),
(5006, CURRENT_DATE, (CURRENT_TIME - INTERVAL '15 minutes')::time, 35000, NULL, 'ABC 9876')
ON CONFLICT (id_pesada) DO NOTHING;

-- 11. Infracciones, Descargos y Auditoría
-- Infracción 501: DETECTADA en Guía 1001 (Exceso de peso)
INSERT INTO infracciones (id_caso, tipo, estado, fecha, evidencia, guia_id, vehiculo_matricula) VALUES
(501, 'EXCESO_DE_CARGA', 'DETECTADA', CURRENT_DATE,
 'Pesada registrada de 43.500 kg supera el peso máximo permitido de 40.000 kg (tolerancia 500 kg).',
 1001, 'STP 1234')
ON CONFLICT (id_caso) DO UPDATE SET estado = EXCLUDED.estado, evidencia = EXCLUDED.evidencia;

-- Infracción 502: CONFIRMADA en Guía 1004 (con descargo y auditoría)
INSERT INTO infracciones (id_caso, tipo, estado, fecha, evidencia, fecha_resolucion, resolucion, fundamento, guia_id, vehiculo_matricula) VALUES
(502, 'EXCESO_DE_CARGA', 'CONFIRMADA', CURRENT_DATE - INTERVAL '3 days',
 'Pesada registrada en balanza de 44.200 kg supera el límite de 40.000 kg.',
 CURRENT_DATE - INTERVAL '1 day', 'CONFIRMADA',
 'Se desestima el descargo presentado. La balanza oficial contaba con calibración metrológica al día.',
 1004, 'STP 1234')
ON CONFLICT (id_caso) DO UPDATE SET estado = EXCLUDED.estado, resolucion = EXCLUDED.resolucion, fundamento = EXCLUDED.fundamento;

INSERT INTO descargos (id, fecha, descargo, infraccion_id) VALUES
(6001, CURRENT_DATE - INTERVAL '2 days', 'Se solicita la anulación del acta debido a ráfagas de viento y oscilaciones mecánicas en la plataforma.', 502)
ON CONFLICT (id) DO NOTHING;

-- Infracción 503: VIAJE_SIN_GUIA (pesaje sin guía en vehículo inhabilitado)
INSERT INTO infracciones (id_caso, tipo, estado, fecha, evidencia, guia_id, vehiculo_matricula) VALUES
(503, 'VIAJE_SIN_GUIA', 'DETECTADA', CURRENT_DATE,
 'Vehículo detectado en balanza fiscalizadora transportando carga sin guía de viaje registrada ni activa.',
 NULL, 'ABC 9876')
ON CONFLICT (id_caso) DO UPDATE SET estado = EXCLUDED.estado, evidencia = EXCLUDED.evidencia;

-- Trazabilidad de Auditoría Inmutable para Infracción 502
INSERT INTO registro_auditoria (id, infraccion_id, resolucion, fecha, usuario, accion, fundamento, timestamp_creacion) VALUES
(7001, 502, 'DETECTADA',   CURRENT_DATE - INTERVAL '3 days', 'SISTEMA (MotorFiscalizacion)', 'CREAR_INFRACCION',   'Pesada registrada de 44.200 kg excediendo tolerancia.', CURRENT_TIMESTAMP - INTERVAL '3 days'),
(7002, 502, 'NOTIFICADA',  CURRENT_DATE - INTERVAL '3 days', 'SISTEMA (Notificación)',     'NOTIFICAR_INFRACCION', 'Notificación automática enviada a TransPlata.',        CURRENT_TIMESTAMP - INTERVAL '3 days'),
(7003, 502, 'EN_DESCARGO', CURRENT_DATE - INTERVAL '2 days', 'pedro.empresario@transporte.uy', 'PRESENTAR_DESCARGO',  'Se solicita la anulación del acta.',                   CURRENT_TIMESTAMP - INTERVAL '2 days'),
(7004, 502, 'CONFIRMADA',  CURRENT_DATE - INTERVAL '1 day',  'funcionario@cargauy.gub.uy',   'CONFIRMAR_INFRACCION', 'Se desestima descargo; balanza calibrada.',           CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- 12. Actualizar secuencias de PostgreSQL para evitar colisiones en futuros inserts
SELECT setval('usuarios_id_seq', (SELECT COALESCE(MAX(id), 1) + 1 FROM usuarios), false);
SELECT setval('guias_de_viaje_id_seq', (SELECT COALESCE(MAX(id), 1000) + 1 FROM guias_de_viaje), false);
SELECT setval('eventos_viaje_id_seq', (SELECT COALESCE(MAX(id), 2000) + 1 FROM eventos_viaje), false);
SELECT setval('incidentes_ruta_id_seq', (SELECT COALESCE(MAX(id), 3000) + 1 FROM incidentes_ruta), false);
SELECT setval('puntos_tracking_id_seq', (SELECT COALESCE(MAX(id), 4000) + 1 FROM puntos_tracking), false);
SELECT setval('pesadas_balanza_id_pesada_seq', (SELECT COALESCE(MAX(id_pesada), 5000) + 1 FROM pesadas_balanza), false);
SELECT setval('infracciones_id_caso_seq', (SELECT COALESCE(MAX(id_caso), 500) + 1 FROM infracciones), false);
SELECT setval('descargos_id_seq', (SELECT COALESCE(MAX(id), 6000) + 1 FROM descargos), false);
SELECT setval('registro_auditoria_id_seq', (SELECT COALESCE(MAX(id), 7000) + 1 FROM registro_auditoria), false);
SELECT setval('rubros_codigo_seq', (SELECT COALESCE(MAX(codigo), 10) + 1 FROM rubros), false);
SELECT setval('tipos_carga_codigo_seq', (SELECT COALESCE(MAX(codigo), 10) + 1 FROM tipos_carga), false);
