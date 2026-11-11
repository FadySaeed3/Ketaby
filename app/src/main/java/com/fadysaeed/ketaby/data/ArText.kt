package com.fadysaeed.ketaby.data

object ArText {
    /** Set from the interface language: English UI uses Latin digits and English book names. */
    @Volatile var english = false
    /** "eg" | "msa" | "en" — interface language, mirrored here for non-Compose helpers. */
    @Volatile var ui = "eg"
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
    fun num(n: Int): String = num(n.toString())
    fun num(s: String): String = if (english) s else s.map { if (it in '0'..'9') DIGITS[it - '0'] else it }.joinToString("")

    fun times(n: Int): String = when (ui) {
        "en" -> if (n == 1) "once" else if (n == 2) "twice" else "$n times"
        "msa" -> when {
            n == 1 -> "مرة واحدة"; n == 2 -> "مرتين"; n in 3..10 -> "${num(n)} مرات"; else -> "${num(n)} مرة"
        }
        else -> when {
            n == 1 -> "مرة واحدة"; n == 2 -> "مرتين"; n in 3..10 -> "${num(n)} مرات"; else -> "${num(n)} مرة"
        }
    }
}
