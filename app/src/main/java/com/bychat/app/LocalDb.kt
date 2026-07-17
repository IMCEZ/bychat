package com.bychat.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalDb(context: Context) : SQLiteOpenHelper(context, "bychat.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE account(username TEXT PRIMARY KEY, salt TEXT NOT NULL, hash TEXT NOT NULL)")
        db.execSQL("CREATE TABLE remote_user(username TEXT PRIMARY KEY, credential TEXT NOT NULL, banned INTEGER NOT NULL DEFAULT 0, muted INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE message(id TEXT PRIMARY KEY, room TEXT NOT NULL, sender TEXT NOT NULL, type TEXT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun createAccount(username: String, password: CharArray): Boolean {
        if (username.length !in 2..24 || !username.matches(Regex("[\\p{L}\\p{N}_-]+")) || password.size < 6) return false
        val salt = Security.newSalt()
        val hash = Security.passwordHash(password, salt)
        return writableDatabase.insert("account", null, ContentValues().apply {
            put("username", username); put("salt", salt); put("hash", hash)
        }) != -1L
    }

    @Synchronized fun login(username: String, password: CharArray): String? {
        readableDatabase.query("account", arrayOf("salt", "hash"), "username=?", arrayOf(username), null, null, null).use {
            if (!it.moveToFirst()) { password.fill('\u0000'); return null }
            val hash = Security.passwordHash(password, it.getString(0))
            return if (Security.equals(hash, it.getString(1))) hash else null
        }
    }

    @Synchronized fun provisionRemote(username: String, credential: String) {
        writableDatabase.insertWithOnConflict("remote_user", null, ContentValues().apply {
            put("username", username); put("credential", credential); put("banned", 0); put("muted", 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun authenticateRemote(username: String, credential: String): String? {
        readableDatabase.query("remote_user", arrayOf("credential", "banned"), "username=?", arrayOf(username), null, null, null).use {
            if (it.moveToFirst()) {
                if (it.getInt(1) == 1) return "你已被此服务器封禁"
                return if (Security.equals(it.getString(0), credential)) null else "用户名已存在或凭据错误"
            }
        }
        writableDatabase.insertOrThrow("remote_user", null, ContentValues().apply {
            put("username", username); put("credential", credential)
        })
        return null
    }

    @Synchronized fun isMuted(username: String): Boolean = readableDatabase.rawQuery("SELECT muted FROM remote_user WHERE username=?", arrayOf(username)).use { it.moveToFirst() && it.getInt(0) == 1 }

    @Synchronized fun setUserFlag(username: String, flag: String, value: Boolean): Boolean {
        require(flag == "muted" || flag == "banned")
        return writableDatabase.update("remote_user", ContentValues().apply { put(flag, if (value) 1 else 0) }, "username=?", arrayOf(username)) > 0
    }

    @Synchronized fun save(message: Message) {
        writableDatabase.insertWithOnConflict("message", null, ContentValues().apply {
            put("id", message.id); put("room", message.room); put("sender", message.sender); put("type", message.type); put("content", message.content); put("timestamp", message.timestamp)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized fun history(room: String, limit: Int = 100): List<Message> {
        val result = mutableListOf<Message>()
        readableDatabase.query("message", null, "room=?", arrayOf(room), null, null, "timestamp DESC", limit.toString()).use {
            while (it.moveToNext()) result += Message(it.getString(it.getColumnIndexOrThrow("id")), room, it.getString(it.getColumnIndexOrThrow("sender")), it.getString(it.getColumnIndexOrThrow("type")), it.getString(it.getColumnIndexOrThrow("content")), it.getLong(it.getColumnIndexOrThrow("timestamp")))
        }
        return result.reversed()
    }

    @Synchronized fun clearRoom(room: String) { writableDatabase.delete("message", "room=?", arrayOf(room)) }
}
