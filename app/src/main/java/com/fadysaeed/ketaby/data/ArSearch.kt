package com.fadysaeed.ketaby.data

/**
 * Rules for Arabic search, shared by the SQL query, the highlighting and the export.
 * data-pipeline/ar_search.py implements the same rules; both are tested against
 * data-pipeline/tests/ar_search_cases.tsv, so change the three together.
 */
object ArSearch {
    /** wholeWord: the query word must be a whole word, optionally with the proclitics below;
     *  loose: hamza seats are ignored (ؤ→و، ئ→ي، ء dropped). */
    data class Mode(val wholeWord: Boolean = true, val loose: Boolean = false)

    /** و / ف, then ب / ل / ك, then the article (ل + ال is written لل). */
    private val CONJ = listOf("", "و", "ف")
    private val PREP = listOf("", "ب", "ل", "ك")

    fun isMark(c: Char) = c in 'ً'..'ٟ' || c == 'ٰ' || c == 'ـ'

    /** The search form of one character, or null when it is dropped. Non-letters become a space. */
    fun normChar(c: Char, loose: Boolean): Char? = when {
        isMark(c) -> null
        c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ' -> 'ا'
        c == 'ى' -> 'ي'
        c == 'ة' -> 'ه'
        loose && c == 'ؤ' -> 'و'
        loose && c == 'ئ' -> 'ي'
        loose && c == 'ء' -> null
        c in 'ء'..'ي' -> c
        else -> ' '
    }

    /** Same as plain_ar() in build_db.py (strict) or plain_ar_loose() (loose), trimmed. */
    fun normalize(s: String, loose: Boolean = false): String {
        val sb = StringBuilder(s.length)
        for (c in s) normChar(c, loose)?.let { sb.append(it) }
        return sb.toString().trim()
    }

    fun words(query: String, loose: Boolean): List<String> =
        normalize(query, loose).split(' ').filter { it.isNotBlank() }

    /** Every written form that counts as the whole word: the word itself and the word with proclitics. */
    fun forms(word: String): List<String> {
        val out = LinkedHashSet<String>()
        val article = word.length > 2 && word.startsWith("ال")
        for (a in CONJ) for (b in PREP) {
            out += a + b + word
            if (article) {
                if (b == "ل") out += a + "لل" + word.substring(2)
            } else {
                out += a + (if (b == "ل") "لل" else b + "ال") + word
            }
        }
        return out.toList()
    }

    /** Does a verse (already in search form, see [normalize]) match the query? */
    fun matchesPlain(plainVerse: String, query: String, mode: Mode): Boolean {
        val ws = words(query, mode.loose)
        if (ws.isEmpty()) return false
        val text = if (mode.loose) loosen(plainVerse) else plainVerse
        val tokens = if (mode.wholeWord) text.split(' ').filter { it.isNotEmpty() }.toHashSet() else emptySet()
        return ws.all { w -> if (mode.wholeWord) forms(w).any { it in tokens } else text.contains(w) }
    }

    /** The loose form of text that is already strict search form (the verses.plain column). */
    fun loosen(plain: String): String = plain.replace('ؤ', 'و').replace('ئ', 'ي').replace("ء", "")

    /** SQL condition on a strict-form column, with its arguments; null when the query has no Arabic word. */
    fun sql(column: String, query: String, mode: Mode): Pair<String, List<String>>? {
        val ws = words(query, mode.loose)
        if (ws.isEmpty()) return null
        val col = if (mode.loose) "replace(replace(replace($column,'ؤ','و'),'ئ','ي'),'ء','')" else column
        val args = ArrayList<String>()
        val parts = ws.map { w ->
            if (mode.wholeWord) {
                val fs = forms(w)
                args += fs.map { " $it " }
                "(" + fs.joinToString(" OR ") { "instr(' ' || $col || ' ', ?) > 0" } + ")"
            } else {
                args += "%$w%"
                "$col LIKE ?"
            }
        }
        return Pair(parts.joinToString(" AND "), args)
    }

    /** Where the query words sit in an original verse (with harakat), as character ranges covering whole words. */
    fun ranges(text: String, query: String, mode: Mode): List<IntRange> {
        val ws = words(query, mode.loose)
        if (ws.isEmpty()) return emptyList()
        val norm = StringBuilder()
        val map = ArrayList<Int>()
        text.forEachIndexed { i, c -> normChar(c, mode.loose)?.let { norm.append(it); map.add(i) } }
        val ns = norm.toString()
        val out = ArrayList<IntRange>()
        fun add(k: Int, len: Int) {
            var s = map[k]
            var e = map[k + len - 1] + 1
            while (s > 0 && isWordChar(text[s - 1])) s--
            while (e < text.length && isWordChar(text[e])) e++
            out += s until e
        }
        if (mode.wholeWord) {
            val all = ws.flatMap { forms(it) }.toHashSet()
            var i = 0
            while (i < ns.length) {
                if (ns[i] == ' ') { i++; continue }
                var j = i
                while (j < ns.length && ns[j] != ' ') j++
                if (ns.substring(i, j) in all) add(i, j - i)
                i = j
            }
        } else {
            for (w in ws) {
                var from = 0
                while (true) {
                    val k = ns.indexOf(w, from)
                    if (k < 0) break
                    add(k, w.length)
                    from = k + w.length
                }
            }
        }
        return merge(out)
    }

    private fun isWordChar(c: Char) = c in 'ء'..'ي' || isMark(c)

    fun merge(rs: List<IntRange>): List<IntRange> {
        if (rs.isEmpty()) return rs
        val sorted = rs.sortedBy { it.first }
        val out = ArrayList<IntRange>()
        var cur = sorted[0]
        for (r in sorted.drop(1)) {
            cur = if (r.first <= cur.last + 1) cur.first..maxOf(cur.last, r.last) else { out += cur; r }
        }
        out += cur
        return out
    }
}
