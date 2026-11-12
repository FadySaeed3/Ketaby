package com.fadysaeed.ketaby.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class JBook(
    val id: Int, val slug: String, val name: String, val short: String, val testament: String,
    val vdBook: Int?, val chapters: Int, val notes: Int,
)

/** One block of a Jesuit chapter: a heading, or a verse (v + optional letter for the Greek additions to Esther). */
data class JItem(val kind: Char, val lvl: String?, val v: Int, val sub: String, val para: Boolean, val text: String) {
    val label: String get() = ArText.num(v) + sub
}

/** A tappable reference inside a margin group: characters [start, end) open book/ch/v. */
data class JLink(val start: Int, val end: Int, val book: Int, val ch: Int, val v: Int)

/** One margin reference group as printed, with the parts that open a passage. */
data class JMargin(val refs: String, val links: List<JLink>)

/** A Jesuit verse shown under a Van Dyke verse. */
data class JUnder(val book: Int, val ch: Int, val v: Int, val sub: String, val text: String)

/**
 * The Jesuit (Catholic) Arabic translation. The text is copyrighted, so it never ships with the app:
 * the owner imports a package file (jesuit.ketaby) once; the app keeps it in private storage.
 * Footnote markers sit in the text as U+E000 n U+E001.
 */
object Jesuit {
    const val MARK_L = ''
    const val MARK_R = ''

    /** Bumped after every import or delete so screens refresh. */
    val version = mutableIntStateOf(0)

    private var db: SQLiteDatabase? = null

    fun file(ctx: Context) = File(File(ctx.filesDir, "tr"), "jesuit.ketaby")

    fun has(ctx: Context) = file(ctx).exists()

    @Synchronized
    private fun db(ctx: Context): SQLiteDatabase? {
        db?.let { return it }
        val f = file(ctx)
        if (!f.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    private fun closeDb() {
        try { db?.close() } catch (_: Exception) {}
        db = null
        hasLinks = null
    }

    /** Packages built before v0.5.1 have no `links` column in jmargin. */
    private var hasLinks: Boolean? = null

    private fun linksColumn(ctx: Context): Boolean = hasLinks ?: run {
        val d = db(ctx) ?: return false
        d.rawQuery("PRAGMA table_info(jmargin)", null).use { c -> var f = false; while (c.moveToNext()) if (c.getString(1) == "links") f = true; f }
            .also { hasLinks = it }
    }

    /** The text without the note markers. */
    fun plain(text: String): String = text.replace(Regex("\uE000\\d+\uE001"), "")

    /** Copies the chosen package in. Returns null when it worked, or a short reason when the file is not a Jesuit package. */
    suspend fun import(ctx: Context, uri: Uri, progress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        val total = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
        val dir = File(ctx.filesDir, "tr").apply { mkdirs() }
        val tmp = File(dir, "jesuit.tmp")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var done = 0L
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0 && done - last > (1 shl 19)) { last = done; progress((done.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }
        }
        val kind = try {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
                d.rawQuery("SELECT value FROM meta WHERE key='kind'", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
        } catch (e: Exception) {
            null
        }
        if (kind != "jesuit") {
            tmp.delete()
            return@withContext "not-a-package"
        }
        closeDb()
        val dest = file(ctx)
        dest.delete()
        tmp.renameTo(dest)
        progress(1f)
        withContext(Dispatchers.Main) { version.intValue++ }
        null
    }

    fun delete(ctx: Context) {
        closeDb()
        file(ctx).delete()
        version.intValue++
    }

    private inline fun <T> q(ctx: Context, sql: String, args: Array<String>, row: (android.database.Cursor) -> T): List<T> {
        val d = db(ctx) ?: return emptyList()
        return d.rawQuery(sql, args).use { c -> val out = ArrayList<T>(c.count); while (c.moveToNext()) out.add(row(c)); out }
    }

    suspend fun meta(ctx: Context, key: String): String? = withContext(Dispatchers.IO) {
        q(ctx, "SELECT value FROM meta WHERE key=?", arrayOf(key)) { it.getString(0) }.firstOrNull()
    }

    suspend fun books(ctx: Context): List<JBook> = withContext(Dispatchers.IO) {
        q(ctx, "SELECT id, slug, name, short, testament, vd_book, chapters, notes FROM jbooks ORDER BY id", arrayOf()) { c ->
            JBook(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                if (c.isNull(5)) null else c.getInt(5), c.getInt(6), c.getInt(7))
        }
    }

    suspend fun chapter(ctx: Context, book: Int, ch: Int): List<JItem> = withContext(Dispatchers.IO) {
        q(ctx, "SELECT kind, lvl, v, sub, para, text FROM jitems WHERE book=? AND ch=? ORDER BY seq", arrayOf("$book", "$ch")) { c ->
            JItem(c.getString(0)[0], c.getString(1), if (c.isNull(2)) 0 else c.getInt(2), c.getString(3) ?: "", c.getInt(4) == 1, c.getString(5))
        }
    }

    /** Footnotes of the given chapters, keyed "book:ch:n". */
    suspend fun notes(ctx: Context, chapters: Collection<Pair<Int, Int>>): Map<String, String> = withContext(Dispatchers.IO) {
        val out = HashMap<String, String>()
        for ((b, ch) in chapters.toSet()) {
            for ((n, text) in q(ctx, "SELECT n, text FROM jnotes WHERE book=? AND ch=?", arrayOf("$b", "$ch")) { it.getInt(0) to it.getString(1) }) {
                out["$b:$ch:$n"] = text
            }
        }
        out
    }

    /** Margin cross-references of a chapter, keyed by verse label (v + sub). */
    suspend fun margin(ctx: Context, book: Int, ch: Int): Map<String, List<JMargin>> = withContext(Dispatchers.IO) {
        val links = linksColumn(ctx)
        q(ctx, "SELECT v, sub, refs" + (if (links) ", links" else "") + " FROM jmargin WHERE book=? AND ch=?", arrayOf("$book", "$ch")) { c ->
            val ls = if (links && !c.isNull(3)) parseLinks(c.getString(3)) else emptyList()
            "${c.getInt(0)}${c.getString(1) ?: ""}" to JMargin(c.getString(2), ls)
        }.groupBy({ it.first }, { it.second })
    }

    private fun parseLinks(json: String): List<JLink> = try {
        val a = org.json.JSONArray(json)
        (0 until a.length()).map { i -> val e = a.getJSONArray(i); JLink(e.getInt(0), e.getInt(1), e.getInt(2), e.getInt(3), e.getInt(4)) }
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun intro(ctx: Context, book: Int): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        q(ctx, "SELECT tag, text FROM jintro WHERE book=? ORDER BY seq", arrayOf("$book")) { it.getString(0) to it.getString(1) }
    }

    /** The Jesuit verses that sit under each Van Dyke verse of a chapter. */
    suspend fun underVd(ctx: Context, vdBook: Int, ch: Int): Map<Int, List<JUnder>> = withContext(Dispatchers.IO) {
        q(
            ctx,
            """SELECT m.vd_v, m.book, m.ch, m.v, m.sub, i.text FROM jmap m
               JOIN jitems i ON i.book = m.book AND i.ch = m.ch AND i.kind = 'v' AND i.v = m.v AND i.sub = m.sub
               WHERE m.vd_book = ? AND m.vd_ch = ? ORDER BY m.vd_v, m.ch, m.v, m.sub""",
            arrayOf("$vdBook", "$ch"),
        ) { c -> c.getInt(0) to JUnder(c.getInt(1), c.getInt(2), c.getInt(3), c.getString(4) ?: "", c.getString(5)) }
            .groupBy({ it.first }, { it.second })
    }

    /** Where a Van Dyke verse is in the Jesuit text: (book, ch, v). */
    suspend fun fromVd(ctx: Context, vdBook: Int, ch: Int, v: Int): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        q(ctx, "SELECT book, ch, v FROM jmap WHERE vd_book=? AND vd_ch=? AND vd_v>=? ORDER BY vd_v, ch, v LIMIT 1", arrayOf("$vdBook", "$ch", "$v")) {
            Triple(it.getInt(0), it.getInt(1), it.getInt(2))
        }.firstOrNull()
    }

    /** Where a Jesuit verse is in Van Dyke: (book, ch, v), or null for the books Van Dyke does not have. */
    suspend fun toVd(ctx: Context, book: Int, ch: Int, v: Int): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        q(ctx, "SELECT vd_book, vd_ch, vd_v FROM jmap WHERE book=? AND ch=? AND v>=? ORDER BY ch, v LIMIT 1", arrayOf("$book", "$ch", "$v")) {
            Triple(it.getInt(0), it.getInt(1), it.getInt(2))
        }.firstOrNull()
    }
}
