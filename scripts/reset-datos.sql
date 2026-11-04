-- ============================================================================
-- CargaUY - Limpieza completa de tablas (Reset de base de datos)
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_auditoria_inmutable') THEN
        ALTER TABLE registro_auditoria DISABLE TRIGGER trg_auditoria_inmutable;
    END IF;
END $$;

TRUNCATE TABLE 
  registro_auditoria,
  descargos,
  infracciones,
  incidentes_ruta,
  eventos_viaje,
  puntos_tracking,
  pesadas_balanza,
  guia_tipos_carga,
  guias_de_viaje,
  itvs,
  pncs,
  vehiculos,
  vinculos_empresa,
  empresa_choferes,
  empresa_ciudadanos,
  empresa_rubros,
  empresas,
  usuario_roles,
  choferes,
  ciudadanos,
  funcionarios,
  autoridades,
  administradores,
  sesiones_acceso,
  flujos_identidad,
  usuarios,
  tipos_carga,
  rubros,
  reglas_fiscalizacion
RESTART IDENTITY CASCADE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_auditoria_inmutable') THEN
        ALTER TABLE registro_auditoria ENABLE TRIGGER trg_auditoria_inmutable;
    END IF;
END $$;
