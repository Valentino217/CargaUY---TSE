-- Migración 04: Columnas de desvío de ruta en kilómetros (FaaS Tracking)
BEGIN;

ALTER TABLE eventos_viaje
    ADD COLUMN IF NOT EXISTS distancia_desvio_km DOUBLE PRECISION;

ALTER TABLE puntos_tracking
    ADD COLUMN IF NOT EXISTS distancia_desvio_km DOUBLE PRECISION;

COMMIT;
