-- ============================================================================
-- CargaUY - Limpieza completa de tablas (Reset de base de datos)
-- ============================================================================

DROP TRIGGER IF EXISTS trg_audit_immutable ON registro_auditoria;
DROP TRIGGER IF EXISTS trg_audit_no_truncate ON registro_auditoria;

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

CREATE OR REPLACE FUNCTION trg_prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Operación denegada: La tabla registro_auditoria es estrictamente inmutable (append-only). No se permiten sentencias UPDATE, DELETE ni TRUNCATE.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
BEFORE UPDATE OR DELETE ON registro_auditoria
FOR EACH ROW
EXECUTE FUNCTION trg_prevent_audit_modification();

CREATE TRIGGER trg_audit_no_truncate
BEFORE TRUNCATE ON registro_auditoria
FOR EACH STATEMENT
EXECUTE FUNCTION trg_prevent_audit_modification();
