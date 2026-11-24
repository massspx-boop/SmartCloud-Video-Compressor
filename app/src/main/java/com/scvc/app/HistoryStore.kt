package com.scvc.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryEntry(
    val id: Long, val time: Long, val mode: String, val inputName: String,
    val outputName: String, val inputBytes: Long, val outputBytes: Long,
    val sha256: String, val success: Boolean
)
class HistoryStore(context: Context) : SQLiteOpenHelper(context, "scvc_history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE operations (
            id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, mode TEXT NOT NULL,
            input_name TEXT NOT NULL, output_name TEXT NOT NULL, input_bytes INTEGER NOT NULL,
            output_bytes INTEGER NOT NULL, sha256 TEXT NOT NULL, success INTEGER NOT NULL
        )""".trimIndent())
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun add(e: HistoryEntry) {
        val v = ContentValues().apply {
            put("time", e.time); put("mode", e.mode); put("input_name", e.inputName)
            put("output_name", e.outputName); put("input_bytes", e.inputBytes)
            put("output_bytes", e.outputBytes); put("sha256", e.sha256)
            put("success", if (e.success) 1 else 0)
        }
        writableDatabase.insert("operations", null, v)
    }
    fun latest(limit: Int = 20): List<HistoryEntry> {
        val rows = mutableListOf<HistoryEntry>()
        readableDatabase.query("operations", null, null, null, null, null, "id DESC",
            limit.coerceIn(1, 100).toString()).use { c ->
            while (c.moveToNext()) rows += HistoryEntry(
                c.getLong(c.getColumnIndexOrThrow("id")), c.getLong(c.getColumnIndexOrThrow("time")),
                c.getString(c.getColumnIndexOrThrow("mode")), c.getString(c.getColumnIndexOrThrow("input_name")),
                c.getString(c.getColumnIndexOrThrow("output_name")), c.getLong(c.getColumnIndexOrThrow("input_bytes")),
                c.getLong(c.getColumnIndexOrThrow("output_bytes")), c.getString(c.getColumnIndexOrThrow("sha256")),
                c.getInt(c.getColumnIndexOrThrow("success")) == 1
            )
        }
        return rows
    }
    fun clear() { writableDatabase.delete("operations", null, null) }
}
