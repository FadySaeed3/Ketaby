package com.fadysaeed.ketaby.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/**
 * The owner's own HALOT and BDAG PDFs. They never ship with the app: the user picks them from
 * phone storage once, the app copies them into its private storage and opens pages from there.
 */
object Lexicons {
    const val HALOT = "halot"
    const val BDAG = "bdag"

    /** Bumped after every import or delete so screens refresh. */
    val version = mutableIntStateOf(0)

    fun name(lex: String) = if (lex == BDAG) "BDAG" else "HALOT"

    fun file(ctx: Context, lex: String) = File(File(ctx.filesDir, "lex"), "$lex.pdf")

    fun has(ctx: Context, lex: String) = file(ctx, lex).exists()

    /** true = same file the page index was built from; false = a different copy; null = not imported. */
    fun matches(ctx: Context, lex: String): Boolean? {
        if (!has(ctx, lex)) return null
        return ctx.getSharedPreferences("lex", Context.MODE_PRIVATE).getBoolean("$lex.ok", false)
    }

    /** Copies the chosen PDF in, checking its sha256 against the index. Returns whether it matched. */
    suspend fun import(ctx: Context, lex: String, uri: Uri, progress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val total = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
        val dir = File(ctx.filesDir, "lex").apply { mkdirs() }
        val tmp = File(dir, "$lex.tmp")
        val md = MessageDigest.getInstance("SHA-256")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var done = 0L
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    done += n
                    if (total > 0 && done - last > (1 shl 20)) { last = done; progress((done.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }
        }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        val expected = Db.meta("${lex}_sha256")
        PdfDocs.close(lex)
        val dest = file(ctx, lex)
        dest.delete()
        tmp.renameTo(dest)
        val ok = expected != null && sha == expected
        ctx.getSharedPreferences("lex", Context.MODE_PRIVATE).edit().putBoolean("$lex.ok", ok).putString("$lex.sha", sha).apply()
        progress(1f)
        withContext(Dispatchers.Main) { version.intValue++ }
        ok
    }

    fun delete(ctx: Context, lex: String) {
        PdfDocs.close(lex)
        file(ctx, lex).delete()
        ctx.getSharedPreferences("lex", Context.MODE_PRIVATE).edit().remove("$lex.ok").remove("$lex.sha").apply()
        version.intValue++
    }
}

/** One open PDF, rendered page by page with Android's PdfRenderer (one page at a time, so a lock). */
class PdfDoc(file: File) : Closeable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount
    /** Page size in PDF points (both lexicons use one size for every page). */
    val pageW: Float
    val pageH: Float

    init {
        val p = renderer.openPage(0)
        pageW = p.width.toFloat()
        pageH = p.height.toFloat()
        p.close()
    }

    private val cache = object : LruCache<String, Bitmap>(128 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** index: 0-based page; widthPx: rendered width. */
    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        val key = "$index@$widthPx"
        cache.get(key) ?: lock.withLock {
            cache.get(key) ?: run {
                val h = (widthPx * pageH / pageW).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                val page = renderer.openPage(index)
                try { page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } finally { page.close() }
                cache.put(key, bmp)
                bmp
            }
        }
    }

    override fun close() {
        try { renderer.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }
}

object PdfDocs {
    private val open = HashMap<String, PdfDoc>()

    @Synchronized
    fun get(ctx: Context, lex: String): PdfDoc? {
        open[lex]?.let { return it }
        val f = Lexicons.file(ctx, lex)
        if (!f.exists()) return null
        return try { PdfDoc(f).also { open[lex] = it } } catch (e: Exception) { null }
    }

    @Synchronized
    fun close(lex: String) { open.remove(lex)?.close() }
}
