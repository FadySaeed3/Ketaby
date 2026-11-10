package com.fadysaeed.ketaby.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class Book(val id: Int, val name: String, val testament: String, val chapters: Int)

data class Token(val id: Long, val text: String, val linked: Boolean)

data class VerseRow(val id: Long, val v: Int, val text: String, val tokens: List<Token>)

data class Lemma(
    val dstrong: String, val lang: String, val lemma: String, val tr: String?, val tr2: String?,
    val gloss: String?, val count: Int, val halotPage: Int?, val bdagPage: Int?,
)

data class OWord(
    val id: Long, val lang: String, val book: Int, val oc: Int, val ov: Int, val text: String,
    val tr: String?, val tr2: String?, val gloss: String?, val dstrong: String?, val grammar: String?,
    val editions: String?, val vdVerse: Long?, val lemma: Lemma?,
)

data class Card(
    val strong: String, val certainty: String?, val note: String, val vandykeWord: String?,
    val refsVandyke: List<String>, val halotChecked: Boolean, val halotEntry: String?, val halotPage: Int?,
)

data class ConcHit(val verseId: Long, val book: Int, val bookName: String, val ch: Int, val v: Int, val tokens: List<Token>)

data class SearchHit(val verseId: Long, val book: Int, val bookName: String, val ch: Int, val v: Int, val text: String)

/** Read-only access to ketaby.db (built by data-pipeline/build_db.py and shipped as an asset). */
object Db {
    private const val NAME = "ketaby.db"
    @Volatile private var db: SQLiteDatabase? = null
    private var lemmaCache: List<Lemma>? = null
    private var bookCache: List<Book>? = null

    /** Copies the asset to private storage the first time (and after every app update), then opens it. */
    suspend fun open(ctx: Context, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (db != null) return@withContext
        val file = File(ctx.filesDir, NAME)
        val prefs = ctx.getSharedPreferences("db", Context.MODE_PRIVATE)
        val stamp = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
        if (!file.exists() || prefs.getLong("stamp", -1) != stamp) {
            val total = try {
                ctx.assets.open("$NAME.len").bufferedReader().use { it.readText().trim().toLong() }
            } catch (e: Exception) { -1L }
            val tmp = File(ctx.filesDir, "$NAME.tmp")
            ctx.assets.open(NAME).use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - last > (1 shl 21)) {
                            last = done
                            progress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            file.delete()
            tmp.renameTo(file)
            prefs.edit().putLong("stamp", stamp).apply()
        }
        progress(1f)
        db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    }

    private val d: SQLiteDatabase get() = db ?: error("database not open")

    private inline fun <T> query(sql: String, args: Array<String>? = null, row: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        d.rawQuery(sql, args).use { c -> while (c.moveToNext()) out.add(row(c)) }
        return out
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
    private fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
    private fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)

    suspend fun books(): List<Book> = withContext(Dispatchers.IO) {
        bookCache ?: query("SELECT id, name_ar, testament, chapters FROM books ORDER BY id") {
            Book(it.getInt(0), it.getString(1), it.getString(2), it.getInt(3))
        }.also { bookCache = it }
    }

    suspend fun chapter(book: Int, ch: Int): List<VerseRow> = withContext(Dispatchers.IO) {
        val verses = query("SELECT id, v, text FROM verses WHERE book=? AND ch=? ORDER BY v", arrayOf("$book", "$ch")) {
            Triple(it.getLong(0), it.getInt(1), it.getString(2))
        }
        if (verses.isEmpty()) return@withContext emptyList()
        val byVerse = HashMap<Long, MutableList<Token>>()
        query(
            "SELECT t.verse_id, t.id, t.text, t.strongs FROM tokens t WHERE t.verse_id BETWEEN ? AND ? ORDER BY t.verse_id, t.idx",
            arrayOf("${verses.first().first}", "${verses.last().first}"),
        ) {
            byVerse.getOrPut(it.getLong(0)) { ArrayList() }.add(Token(it.getLong(1), it.getString(2), !it.isNull(3) && it.getString(3) != "-"))
        }
        verses.map { (id, v, text) -> VerseRow(id, v, text, byVerse[id] ?: listOf(Token(-id, text, false))) }
    }

    private const val OWORD_COLS =
        "o.id, o.lang, o.book, o.oc, o.ov, o.text, o.tr, o.tr2, o.gloss, o.dstrong, o.grammar, o.editions, o.vd_verse, " +
            "l.dstrong, l.lang, l.lemma, l.tr, l.tr2, l.gloss, l.count, l.halot_page, l.bdag_page"

    private fun oword(c: Cursor): OWord {
        val lemma = if (c.isNull(13)) null else Lemma(
            c.getString(13), c.getString(14), c.str(15) ?: "", c.str(16), c.str(17), c.str(18), c.getInt(19),
            c.intOrNull(20), c.intOrNull(21),
        )
        return OWord(
            c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4), c.str(5) ?: "", c.str(6), c.str(7),
            c.str(8), c.str(9), c.str(10), c.str(11), c.longOrNull(12), lemma,
        )
    }

    /** Original-language words aligned to one Arabic unit. */
    suspend fun wordsForToken(tokenId: Long): List<OWord> = withContext(Dispatchers.IO) {
        query(
            "SELECT $OWORD_COLS FROM links k JOIN owords o ON o.id=k.oword_id LEFT JOIN lemmas l ON l.dstrong=o.dstrong " +
                "WHERE k.token_id=? ORDER BY o.id",
            arrayOf("$tokenId"),
        ) { oword(it) }
    }

    suspend fun wordsById(ids: List<Long>): List<OWord> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        query(
            "SELECT $OWORD_COLS FROM owords o LEFT JOIN lemmas l ON l.dstrong=o.dstrong WHERE o.id IN (${ids.joinToString(",")}) ORDER BY o.id",
        ) { oword(it) }
    }

    /** All original words of a chapter, grouped by Van Dyke verse id (interlinear view). */
    suspend fun chapterWords(firstVerse: Long, lastVerse: Long): Map<Long, List<OWord>> = withContext(Dispatchers.IO) {
        query(
            "SELECT $OWORD_COLS FROM owords o LEFT JOIN lemmas l ON l.dstrong=o.dstrong " +
                "WHERE o.vd_verse BETWEEN ? AND ? ORDER BY o.id",
            arrayOf("$firstVerse", "$lastVerse"),
        ) { oword(it) }.groupBy { it.vdVerse ?: -1L }
    }

    /** Arabic units aligned to the given original words (to highlight them in the verse). */
    suspend fun tokensForWords(ids: List<Long>): Set<Long> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptySet()
        query("SELECT token_id FROM links WHERE oword_id IN (${ids.joinToString(",")})") { it.getLong(0) }.toSet()
    }

    suspend fun lemma(dstrong: String): Lemma? = withContext(Dispatchers.IO) {
        query("SELECT dstrong, lang, lemma, tr, tr2, gloss, count, halot_page, bdag_page FROM lemmas WHERE dstrong=?", arrayOf(dstrong)) {
            Lemma(it.getString(0), it.getString(1), it.str(2) ?: "", it.str(3), it.str(4), it.str(5), it.getInt(6), it.intOrNull(7), it.intOrNull(8))
        }.firstOrNull()
    }

    suspend fun allLemmas(): List<Lemma> = withContext(Dispatchers.IO) {
        lemmaCache ?: query("SELECT dstrong, lang, lemma, tr, tr2, gloss, count, halot_page, bdag_page FROM lemmas ORDER BY count DESC") {
            Lemma(it.getString(0), it.getString(1), it.str(2) ?: "", it.str(3), it.str(4), it.str(5), it.getInt(6), it.intOrNull(7), it.intOrNull(8))
        }.also { lemmaCache = it }
    }

    /** "H0430G" -> "H430": rare-word cards are keyed by plain Strong's numbers. */
    fun plainStrong(dstrong: String): String {
        val m = Regex("^([HG])0*(\\d+)").find(dstrong) ?: return dstrong
        return m.groupValues[1] + m.groupValues[2]
    }

    suspend fun card(dstrong: String): Card? = withContext(Dispatchers.IO) {
        query("SELECT json FROM cards WHERE strong=?", arrayOf(plainStrong(dstrong))) { it.getString(0) }.firstOrNull()?.let { js ->
            val o = JSONObject(js)
            val refs = o.optJSONArray("refs_vandyke")
            val h = o.optJSONObject("halot")
            Card(
                strong = o.optString("strong"),
                certainty = o.optString("certainty").ifBlank { null },
                note = o.optString("note_ar"),
                vandykeWord = o.optString("vandyke_word").ifBlank { null },
                refsVandyke = if (refs == null) emptyList() else List(refs.length()) { refs.optString(it) },
                halotChecked = h?.optBoolean("checked") ?: false,
                halotEntry = h?.opt("entry")?.toString(),
                halotPage = h?.optInt("page")?.takeIf { it > 0 },
            )
        }
    }

    /** Every Van Dyke verse where the lemma occurs, with the aligned Arabic units marked. */
    suspend fun concordance(dstrong: String, limit: Int = 3000): Pair<List<ConcHit>, Set<Long>> = withContext(Dispatchers.IO) {
        val verseIds = query(
            "SELECT DISTINCT vd_verse FROM owords WHERE dstrong=? AND vd_verse IS NOT NULL ORDER BY vd_verse LIMIT $limit",
            arrayOf(dstrong),
        ) { it.getLong(0) }
        if (verseIds.isEmpty()) return@withContext Pair(emptyList(), emptySet())
        val marked = query(
            "SELECT k.token_id FROM owords o JOIN links k ON k.oword_id=o.id WHERE o.dstrong=?", arrayOf(dstrong),
        ) { it.getLong(0) }.toSet()
        val idList = verseIds.joinToString(",")
        val head = HashMap<Long, ConcHit>()
        query("SELECT v.id, v.book, b.name_ar, v.ch, v.v FROM verses v JOIN books b ON b.id=v.book WHERE v.id IN ($idList)") {
            head[it.getLong(0)] = ConcHit(it.getLong(0), it.getInt(1), it.getString(2), it.getInt(3), it.getInt(4), emptyList())
        }
        val toks = HashMap<Long, MutableList<Token>>()
        query("SELECT verse_id, id, text, strongs FROM tokens WHERE verse_id IN ($idList) ORDER BY verse_id, idx") {
            toks.getOrPut(it.getLong(0)) { ArrayList() }.add(Token(it.getLong(1), it.getString(2), !it.isNull(3) && it.getString(3) != "-"))
        }
        val hits = verseIds.mapNotNull { id ->
            head[id]?.copy(tokens = toks[id] ?: emptyList())
        }
        Pair(hits, marked)
    }

    suspend fun verseRef(verseId: Long): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        query("SELECT book, ch, v FROM verses WHERE id=?", arrayOf("$verseId")) { Triple(it.getInt(0), it.getInt(1), it.getInt(2)) }.firstOrNull()
    }

    /** Arabic search without harakat; every word of the query must appear in the verse. */
    suspend fun searchArabic(q: String, limit: Int = 500): List<SearchHit> = withContext(Dispatchers.IO) {
        val words = ArText.plainArabic(q).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()
        val where = words.joinToString(" AND ") { "v.plain LIKE ?" }
        query(
            "SELECT v.id, v.book, b.name_ar, v.ch, v.v, v.text FROM verses v JOIN books b ON b.id=v.book WHERE $where ORDER BY v.id LIMIT $limit",
            words.map { "%$it%" }.toTypedArray(),
        ) { SearchHit(it.getLong(0), it.getInt(1), it.getString(2), it.getInt(3), it.getInt(4), it.getString(5)) }
    }

    suspend fun countArabic(q: String): Int = withContext(Dispatchers.IO) {
        val words = ArText.plainArabic(q).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext 0
        val where = words.joinToString(" AND ") { "plain LIKE ?" }
        query("SELECT COUNT(*) FROM verses WHERE $where", words.map { "%$it%" }.toTypedArray()) { it.getInt(0) }.first()
    }
}
