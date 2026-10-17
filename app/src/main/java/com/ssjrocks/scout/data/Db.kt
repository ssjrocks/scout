package com.ssjrocks.scout.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Stories, what the user did with them, and per-source fetch status. Plain SQLite, no ORM. */
class Db(context: Context) : SQLiteOpenHelper(context, "scout.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items(
                id TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT NOT NULL, link TEXT NOT NULL,
                summary TEXT, image TEXT, image_small INTEGER DEFAULT 0, author TEXT,
                published INTEGER NOT NULL, fetched INTEGER NOT NULL, domain TEXT, publisher TEXT,
                comments_url TEXT, ext_url TEXT, points INTEGER, comments INTEGER, video_id TEXT, price TEXT,
                expires INTEGER, enriched INTEGER DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX items_published ON items(published)")
        db.execSQL(
            """CREATE TABLE state(
                id TEXT PRIMARY KEY, read INTEGER DEFAULT 0, saved INTEGER DEFAULT 0,
                hidden INTEGER DEFAULT 0, liked INTEGER DEFAULT 0, reader TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE source_status(
                id TEXT PRIMARY KEY, last_try INTEGER, last_ok INTEGER, last_count INTEGER, error TEXT)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Inserts new stories; for ones already stored, refreshes the counts. Returns the ids that were new. */
    fun upsertItems(items: List<Item>): List<String> {
        val added = ArrayList<String>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (it in items) {
                val cv = ContentValues().apply {
                    put("id", it.id); put("source", it.sourceId); put("title", it.title); put("link", it.link)
                    put("summary", it.summary); put("image", it.image); put("image_small", if (it.imageSmall) 1 else 0)
                    put("author", it.author); put("published", it.published); put("fetched", it.fetched)
                    put("domain", it.domain); put("publisher", it.publisher); put("comments_url", it.commentsUrl); put("ext_url", it.extUrl)
                    put("points", it.points); put("comments", it.comments); put("video_id", it.videoId)
                    put("price", it.price); put("expires", it.expires); put("enriched", if (it.enriched) 1 else 0)
                }
                if (db.insertWithOnConflict("items", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                    added += it.id
                } else if (it.points != null || it.comments != null) {
                    val up = ContentValues().apply { put("points", it.points); put("comments", it.comments) }
                    db.update("items", up, "id = ?", arrayOf(it.id))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun setEnriched(id: String, image: String?, link: String?, domain: String?) {
        val cv = ContentValues().apply {
            put("enriched", 1)
            if (image != null) { put("image", image); put("image_small", 0) }
            if (link != null) put("link", link)
            if (domain != null) put("domain", domain)
        }
        writableDatabase.update("items", cv, "id = ?", arrayOf(id))
    }

    fun items(since: Long, limit: Int = 4000): List<Item> =
        readableDatabase.rawQuery(
            "SELECT * FROM items WHERE published >= ? OR id IN (SELECT id FROM state WHERE saved > 0 OR read > 0) " +
                "ORDER BY published DESC LIMIT ?",
            arrayOf(since.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toItem()) } }

    fun itemsNeedingEnrichment(limit: Int): List<Item> =
        readableDatabase.rawQuery(
            "SELECT * FROM items WHERE enriched = 0 ORDER BY published DESC LIMIT ?", arrayOf(limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toItem()) } }

    fun states(): Map<String, ItemState> =
        readableDatabase.rawQuery("SELECT id, read, saved, hidden, liked FROM state", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), ItemState(c.getLong(1), c.getLong(2), c.getLong(3), c.getInt(4))) }
        }

    fun putState(id: String, s: ItemState) {
        val cv = ContentValues().apply {
            put("id", id); put("read", s.read); put("saved", s.saved); put("hidden", s.hidden); put("liked", s.liked)
        }
        val db = writableDatabase
        if (db.update("state", cv, "id = ?", arrayOf(id)) == 0) db.insert("state", null, cv)
    }

    fun clearHidden() {
        writableDatabase.execSQL("UPDATE state SET hidden = 0")
    }

    fun clearHistory() {
        writableDatabase.execSQL("UPDATE state SET read = 0")
    }

    fun readerCache(id: String): String? =
        readableDatabase.rawQuery("SELECT reader FROM state WHERE id = ?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun putReaderCache(id: String, json: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("reader", json) }
        if (db.update("state", cv, "id = ?", arrayOf(id)) == 0) {
            cv.put("id", id)
            db.insert("state", null, cv)
        }
    }

    fun statuses(): Map<String, SourceStatus> =
        readableDatabase.rawQuery("SELECT id, last_try, last_ok, last_count, error FROM source_status", null).use { c ->
            buildMap {
                while (c.moveToNext()) put(c.getString(0), SourceStatus(c.getLong(1), c.getLong(2), c.getInt(3), c.getString(4)))
            }
        }

    fun putStatus(id: String, s: SourceStatus) {
        val cv = ContentValues().apply {
            put("id", id); put("last_try", s.lastTry); put("last_ok", s.lastOk); put("last_count", s.lastCount); put("error", s.error)
        }
        writableDatabase.insertWithOnConflict("source_status", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM items", null).use { c -> c.moveToFirst(); c.getInt(0) }

    /** Drops old stories, but keeps anything saved. */
    fun prune(olderThan: Long) {
        val db = writableDatabase
        db.execSQL(
            "DELETE FROM items WHERE published < ? AND id NOT IN (SELECT id FROM state WHERE saved > 0)",
            arrayOf(olderThan),
        )
        db.execSQL("DELETE FROM state WHERE saved = 0 AND id NOT IN (SELECT id FROM items)")
    }

    fun deleteSource(sourceId: String) {
        writableDatabase.execSQL(
            "DELETE FROM items WHERE source = ? AND id NOT IN (SELECT id FROM state WHERE saved > 0)", arrayOf(sourceId),
        )
    }

    private fun Cursor.str(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.lng(name: String): Long? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.int(name: String): Int? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getInt(it) }

    private fun Cursor.toItem() = Item(
        id = str("id")!!, sourceId = str("source")!!, title = str("title")!!, link = str("link")!!,
        summary = str("summary").orEmpty(), image = str("image"), imageSmall = int("image_small") == 1,
        author = str("author"), published = lng("published") ?: 0, fetched = lng("fetched") ?: 0,
        domain = str("domain").orEmpty(), publisher = str("publisher"), commentsUrl = str("comments_url"), extUrl = str("ext_url"),
        points = int("points"), comments = int("comments"), videoId = str("video_id"), price = str("price"),
        expires = lng("expires"), enriched = int("enriched") == 1,
    )
}
