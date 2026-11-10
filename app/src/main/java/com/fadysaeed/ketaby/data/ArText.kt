package com.fadysaeed.ketaby.data

object ArText {
    private val harakat = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
    private val cantillation = Regex("[\\u0591-\\u05AF\\u05BD]")

    /** Same normalisation as plain_ar() in build_db.py. */
    fun plainArabic(s: String): String {
        var t = harakat.replace(s, "")
        t = t.replace(Regex("[أإآٱ]"), "ا").replace('ى', 'ي').replace('ة', 'ه')
        return t.replace(Regex("[^\\u0621-\\u064A ]"), " ").trim()
    }

    fun stripHarakat(s: String): String = harakat.replace(s, "")

    fun stripCantillation(s: String): String = cantillation.replace(s, "")

    /** Greek/Hebrew without accents and points, lower-case, for lemma search. */
    fun bare(s: String): String {
        val d = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        return d.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .replace('ς', 'σ')
            .replace('ך', 'כ').replace('ם', 'מ').replace('ן', 'נ').replace('ף', 'פ').replace('ץ', 'צ')
    }

    private const val DIGITS = "٠١٢٣٤٥٦٧٨٩"
    fun num(n: Int): String = n.toString().map { if (it in '0'..'9') DIGITS[it - '0'] else it }.joinToString("")
    fun num(s: String): String = s.map { if (it in '0'..'9') DIGITS[it - '0'] else it }.joinToString("")

    fun times(n: Int): String = when {
        n == 1 -> "مرة واحدة"
        n == 2 -> "مرتين"
        n in 3..10 -> "${num(n)} مرات"
        else -> "${num(n)} مرة"
    }
}
