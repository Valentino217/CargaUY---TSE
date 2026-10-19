package com.example.cargauyandroid

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OfflineDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "cargauy_offline.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_GUIAS = "guias_cache"
        private const val TABLE_EVENTOS = "eventos_locales"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_GUIAS (
                id INTEGER PRIMARY KEY,
                cedula_chofer INTEGER NOT NULL,
                fecha TEXT,
                origen TEXT NOT NULL,
                destino TEXT NOT NULL,
                rubro_cliente TEXT,
                volumen_carga REAL NOT NULL,
                estado TEXT NOT NULL,
                version INTEGER NOT NULL,
                vehiculo_matricula TEXT,
                vehiculo_marca_modelo TEXT,
                nro_empresa INTEGER,
                empresa_razon_social TEXT,
                chofer_id INTEGER,
                chofer_nombre TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTOS (
                uuid TEXT PRIMARY KEY,
                guia_id INTEGER NOT NULL,
                tipo TEXT NOT NULL,
                tiempo TEXT NOT NULL,
                latitud REAL NOT NULL,
                longitud REAL NOT NULL,
                descripcion TEXT,
                foto TEXT,
                estado_sync TEXT NOT NULL,
                mensaje_sync TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTOS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GUIAS")
        onCreate(db)
    }

    fun guardarGuiasCache(cedula: Int, guias: List<Viaje>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_GUIAS, "cedula_chofer = ?", arrayOf(cedula.toString()))
            for (g in guias) {
                val values = ContentValues().apply {
                    put("id", g.id)
                    put("cedula_chofer", cedula)
                    put("fecha", g.fecha)
                    put("origen", g.origen)
                    put("destino", g.destino)
                    put("rubro_cliente", g.rubroCliente)
                    put("volumen_carga", g.volumenCarga.toDouble())
                    put("estado", g.estado)
                    put("version", g.version)
                    put("vehiculo_matricula", g.vehiculoMatricula)
                    put("vehiculo_marca_modelo", g.vehiculoMarcaModelo)
                    if (g.nroEmpresa != null) put("nro_empresa", g.nroEmpresa) else putNull("nro_empresa")
                    put("empresa_razon_social", g.empresaRazonSocial)
                    if (g.choferId != null) put("chofer_id", g.choferId) else putNull("chofer_id")
                    put("chofer_nombre", g.choferNombre)
                }
                db.insertWithOnConflict(TABLE_GUIAS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun obtenerGuiasCache(cedula: Int): List<Viaje> {
        val lista = mutableListOf<Viaje>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_GUIAS,
            null,
            "cedula_chofer = ?",
            arrayOf(cedula.toString()),
            null,
            null,
            "id DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val nroEmpIdx = c.getColumnIndexOrThrow("nro_empresa")
                val choferIdIdx = c.getColumnIndexOrThrow("chofer_id")
                lista.add(
                    Viaje(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        fecha = c.getString(c.getColumnIndexOrThrow("fecha")),
                        origen = c.getString(c.getColumnIndexOrThrow("origen")),
                        destino = c.getString(c.getColumnIndexOrThrow("destino")),
                        rubroCliente = c.getString(c.getColumnIndexOrThrow("rubro_cliente")),
                        volumenCarga = c.getDouble(c.getColumnIndexOrThrow("volumen_carga")).toFloat(),
                        estado = c.getString(c.getColumnIndexOrThrow("estado")),
                        version = c.getInt(c.getColumnIndexOrThrow("version")),
                        vehiculoMatricula = c.getString(c.getColumnIndexOrThrow("vehiculo_matricula")),
                        vehiculoMarcaModelo = c.getString(c.getColumnIndexOrThrow("vehiculo_marca_modelo")),
                        nroEmpresa = if (c.isNull(nroEmpIdx)) null else c.getInt(nroEmpIdx),
                        empresaRazonSocial = c.getString(c.getColumnIndexOrThrow("empresa_razon_social")),
                        choferId = if (c.isNull(choferIdIdx)) null else c.getInt(choferIdIdx),
                        choferNombre = c.getString(c.getColumnIndexOrThrow("chofer_nombre"))
                    )
                )
            }
        }
        return lista
    }

    fun obtenerGuiaPorId(guiaId: Long): Viaje? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_GUIAS,
            null,
            "id = ?",
            arrayOf(guiaId.toString()),
            null,
            null,
            null,
            "1"
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                val nroEmpIdx = c.getColumnIndexOrThrow("nro_empresa")
                val choferIdIdx = c.getColumnIndexOrThrow("chofer_id")
                return Viaje(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    fecha = c.getString(c.getColumnIndexOrThrow("fecha")),
                    origen = c.getString(c.getColumnIndexOrThrow("origen")),
                    destino = c.getString(c.getColumnIndexOrThrow("destino")),
                    rubroCliente = c.getString(c.getColumnIndexOrThrow("rubro_cliente")),
                    volumenCarga = c.getDouble(c.getColumnIndexOrThrow("volumen_carga")).toFloat(),
                    estado = c.getString(c.getColumnIndexOrThrow("estado")),
                    version = c.getInt(c.getColumnIndexOrThrow("version")),
                    vehiculoMatricula = c.getString(c.getColumnIndexOrThrow("vehiculo_matricula")),
                    vehiculoMarcaModelo = c.getString(c.getColumnIndexOrThrow("vehiculo_marca_modelo")),
                    nroEmpresa = if (c.isNull(nroEmpIdx)) null else c.getInt(nroEmpIdx),
                    empresaRazonSocial = c.getString(c.getColumnIndexOrThrow("empresa_razon_social")),
                    choferId = if (c.isNull(choferIdIdx)) null else c.getInt(choferIdIdx),
                    choferNombre = c.getString(c.getColumnIndexOrThrow("chofer_nombre"))
                )
            }
        }
        return null
    }

    fun actualizarEstadoGuiaLocal(guiaId: Long, nuevoEstado: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("estado", nuevoEstado)
        }
        db.update(TABLE_GUIAS, values, "id = ?", arrayOf(guiaId.toString()))
    }

    fun insertarEventoLocal(evento: EventoLocal): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("uuid", evento.uuid)
            put("guia_id", evento.guiaId)
            put("tipo", evento.tipo)
            put("tiempo", evento.tiempo)
            put("latitud", evento.latitud)
            put("longitud", evento.longitud)
            put("descripcion", evento.descripcion)
            put("foto", evento.foto)
            put("estado_sync", evento.estadoSync)
            put("mensaje_sync", evento.mensajeSync)
        }
        val rowId = db.insertWithOnConflict(TABLE_EVENTOS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        return rowId != -1L
    }

    fun listarEventosPendientes(): List<EventoLocal> {
        return consultarEventos("estado_sync = ?", arrayOf(EstadoSyncLocal.PENDIENTE.name))
    }

    fun contarEventosPendientes(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_EVENTOS WHERE estado_sync = ?",
            arrayOf(EstadoSyncLocal.PENDIENTE.name)
        )
        cursor.use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun listarEventosPorGuiaLocal(guiaId: Long): List<EventoLocal> {
        return consultarEventos("guia_id = ?", arrayOf(guiaId.toString()))
    }

    fun actualizarEstadoEvento(uuid: String, nuevoEstado: EstadoSyncLocal, mensaje: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("estado_sync", nuevoEstado.name)
            put("mensaje_sync", mensaje)
        }
        db.update(TABLE_EVENTOS, values, "uuid = ?", arrayOf(uuid))
    }

    fun eliminarEventoDuplicado(uuid: String) {
        val db = writableDatabase
        db.delete(TABLE_EVENTOS, "uuid = ?", arrayOf(uuid))
    }

    private fun consultarEventos(selection: String?, selectionArgs: Array<String>?): List<EventoLocal> {
        val lista = mutableListOf<EventoLocal>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_EVENTOS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "tiempo ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                lista.add(
                    EventoLocal(
                        uuid = c.getString(c.getColumnIndexOrThrow("uuid")),
                        guiaId = c.getLong(c.getColumnIndexOrThrow("guia_id")),
                        tipo = c.getString(c.getColumnIndexOrThrow("tipo")),
                        tiempo = c.getString(c.getColumnIndexOrThrow("tiempo")),
                        latitud = c.getDouble(c.getColumnIndexOrThrow("latitud")),
                        longitud = c.getDouble(c.getColumnIndexOrThrow("longitud")),
                        descripcion = c.getString(c.getColumnIndexOrThrow("descripcion")),
                        foto = c.getString(c.getColumnIndexOrThrow("foto")),
                        estadoSync = c.getString(c.getColumnIndexOrThrow("estado_sync")),
                        mensajeSync = c.getString(c.getColumnIndexOrThrow("mensaje_sync"))
                    )
                )
            }
        }
        return lista
    }
}
