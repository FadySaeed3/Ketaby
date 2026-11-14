package com.fadysaeed.ketaby.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.content.res.ResourcesCompat
import com.fadysaeed.ketaby.R
import com.fadysaeed.ketaby.data.ArSearch
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the searched words sit in a verse, widened to whole words so Arabic letters stay joined. */
object SearchMarks {
    private fun isLatinWordChar(c: Char) = c.isLetterOrDigit() || c == '\''

    fun ranges(text: String, query: String, english: Boolean, mode: ArSearch.Mode): List<IntRange> =
        if (english) latin(text, query) else ArSearch.ranges(text, query, mode)

    private fun latin(text: String, query: String): List<IntRange> {
        val words = query.lowercase().replace(Regex("[^a-z0-9 ]+"), " ").split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val low = text.lowercase()
        if (low.length != text.length) return emptyList()
        val out = ArrayList<IntRange>()
        for (w in words) {
            var from = 0
            while (true) {
                val k = low.indexOf(w, from)
                if (k < 0) break
                var s = k
                var e = k + w.length
                while (s > 0 && isLatinWordChar(text[s - 1])) s--
                while (e < text.length && isLatinWordChar(text[e])) e++
                out += s until e
                from = k + w.length
            }
        }
        return ArSearch.merge(out)
    }

    fun annotated(text: String, ranges: List<IntRange>, color: Color, background: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        for (r in ranges) addStyle(SpanStyle(color = color, background = background), r.first, r.last + 1)
    }
}

/** Saves search results as a plain text file or a PDF. */
object SearchExport {
    private fun ref(h: SearchHit) = (if (h.jesuit) t("اليسوعية · ", "اليسوعية · ", "Jesuit · ") else "") +
        "${h.bookName} ${ArText.num(h.ch)}:${ArText.num(h.v)}${h.sub}"

    private fun header(query: String, total: Int, english: Boolean, mode: ArSearch.Mode): List<String> = listOf(
        t("كتابي · نتايج البحث", "كتابي · نتائج البحث", "Ketaby · Search results"),
        t("البحث: «$query»", "البحث: «$query»", "Search: \"$query\"") + (if (english) "" else " · " + modeLabel(mode)),
        t("عدد الآيات: ${ArText.num(total)}", "عدد الآيات: ${ArText.num(total)}", "Verses: $total"),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()).let { ArText.num(it) },
    )

    fun modeLabel(mode: ArSearch.Mode): String =
        (if (mode.wholeWord) t("كلمة كاملة", "كلمة كاملة", "whole word") else t("جزء من كلمة", "جزء من كلمة", "part of a word")) +
            (if (mode.loose) t("، همزات مرنة", "، همزات مرنة", ", loose hamza") else "")

    suspend fun txt(ctx: Context, uri: Uri, query: String, hits: List<SearchHit>, english: Boolean, mode: ArSearch.Mode) = withContext(Dispatchers.IO) {
        val sb = StringBuilder("\uFEFF")
        header(query, hits.size, english, mode).forEach { sb.append(it).append('\n') }
        sb.append('\n')
        for (h in hits) sb.append(ref(h)).append('\n').append(h.text).append("\n\n")
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
    }

    suspend fun pdf(ctx: Context, uri: Uri, query: String, hits: List<SearchHit>, english: Boolean, plexScripture: Boolean, mode: ArSearch.Mode) =
        withContext(Dispatchers.IO) {
            val W = 595; val H = 842; val M = 42f
            val width = (W - 2 * M).toInt()
            val bottom = H - M - 18f
            val plexReg = ResourcesCompat.getFont(ctx, R.font.plex_regular) ?: Typeface.DEFAULT
            val plexBold = ResourcesCompat.getFont(ctx, R.font.plex_semibold) ?: Typeface.DEFAULT_BOLD
            val scripture = if (english) Typeface.SERIF
            else if (plexScripture) plexReg else ResourcesCompat.getFont(ctx, R.font.naskh_regular) ?: plexReg
            val dark = 0xFF1A1A1A.toInt()
            val gold = 0xFF8A6A00.toInt()
            val grey = 0xFF6B6B6B.toInt()
            val markText = 0xFF6B4E00.toInt()
            val markBg = 0xFFFFE9A8.toInt()
            val uiRtl = ArText.ui != "en"

            fun paint(tf: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color }
            fun layout(text: CharSequence, p: TextPaint, rtl: Boolean, spacing: Float = 1.15f): StaticLayout =
                StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                    .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, spacing)
                    .setIncludePad(true)
                    .build()

            val doc = PdfDocument()
            var pageNo = 0
            var page: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var y = M
            val footer = paint(plexReg, 9f, grey).apply { textAlign = Paint.Align.CENTER }

            fun newPage() {
                page?.let { doc.finishPage(it) }
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                canvas = page!!.canvas
                canvas!!.drawColor(0xFFFFFFFF.toInt())
                canvas!!.drawText(ArText.num(pageNo), W / 2f, H - M / 2f, footer)
                y = M
            }

            /** Draws a layout line by line, starting a new page whenever a line would not fit. */
            fun draw(l: StaticLayout) {
                for (i in 0 until l.lineCount) {
                    val top = l.getLineTop(i).toFloat()
                    val h = l.getLineBottom(i) - top
                    if (y + h > bottom) newPage()
                    val c = canvas!!
                    c.save()
                    c.translate(M, y - top)
                    c.clipRect(0f, top, width.toFloat(), top + h)
                    l.draw(c)
                    c.restore()
                    y += h
                }
            }

            newPage()
            val head = header(query, hits.size, english, mode)
            draw(layout(head[0], paint(plexBold, 17f, gold), uiRtl))
            y += 4f
            draw(layout(head[1], paint(plexBold, 13f, dark), uiRtl))
            draw(layout(head[2] + "   ·   " + head[3], paint(plexReg, 10f, grey), uiRtl))
            y += 6f
            canvas!!.drawLine(M, y, W - M, y, Paint().apply { color = 0xFFD9CFA8.toInt(); strokeWidth = 1f })
            y += 12f

            val refPaint = paint(plexBold, 11f, gold)
            val textPaint = paint(scripture, if (english) 11.5f else 13f, dark)
            for (h in hits) {
                val refLayout = layout(ref(h), refPaint, uiRtl)
                val s = SpannableString(h.text)
                for (r in SearchMarks.ranges(h.text, query, english, mode)) {
                    s.setSpan(BackgroundColorSpan(markBg), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.setSpan(ForegroundColorSpan(markText), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val textLayout = layout(s, textPaint, !english, if (english) 1.2f else 1.3f)
                // keep a reference with at least the first line of its verse
                if (y + refLayout.height + textLayout.getLineBottom(0) > bottom) newPage()
                draw(refLayout)
                draw(textLayout)
                y += 10f
            }
            page?.let { doc.finishPage(it) }
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { doc.writeTo(it) }
            } finally {
                doc.close()
            }
        }
}
