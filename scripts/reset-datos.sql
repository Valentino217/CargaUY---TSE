-- ============================================================================
-- CargaUY - Limpieza completa de tablas (Reset de base de datos)
-- ============================================================================

ALTER TABLE registro_auditoria DISABLE TRIGGER ALL;

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

ALTER TABLE registro_auditoria ENABLE TRIGGER ALL;
