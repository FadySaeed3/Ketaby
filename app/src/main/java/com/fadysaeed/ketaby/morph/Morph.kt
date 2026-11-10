package com.fadysaeed.ketaby.morph

/**
 * Turns STEPBible grammar codes into short Arabic descriptions.
 * Hebrew/Aramaic: OSHB-style codes (e.g. "HVqp3ms", "HC/Ncmsc/Sp1bs").
 * Greek: Robinson codes (e.g. "V-AAI-3S", "N-ASM", "T-NSM").
 */
object Morph {

    fun describe(code: String?, lang: String): String {
        if (code.isNullOrBlank()) return ""
        return if (lang == "grc") greekAll(code) else hebrew(code)
    }

    // ------------------------------------------------------------ Hebrew
    private val hebStems = mapOf(
        'q' to "قال (البسيط)", 'N' to "نفعال", 'p' to "پعّيل", 'P' to "پُعّال", 'h' to "هفعيل", 'H' to "هُفعال",
        't' to "هتپعّيل", 'o' to "پولِل", 'O' to "پولَل", 'r' to "هتپولِل", 'm' to "پوعِل", 'M' to "پوعَل",
        'k' to "پَلِل", 'K' to "پُلَل", 'Q' to "قال المجهول", 'l' to "پِلپِل", 'L' to "پُلپَل", 'f' to "هتپَلپِل",
        'D' to "نِتپعّيل", 'j' to "پَعلَل", 'i' to "پِلِل", 'u' to "هُتپعّال", 'c' to "تِفعيل", 'v' to "هِشتَفعيل",
        'w' to "نِتپَلِل", 'y' to "نِتپوعِل", 'z' to "هِتپوعِل"
    )
    private val aramStems = mapOf(
        'q' to "پِعَل (البسيط)", 'Q' to "پِعيل", 'u' to "هِتپِعيل", 'p' to "پَعِّل", 'P' to "إتپَعَّل", 'M' to "هِتپَعَّل",
        'a' to "أفعِل", 'h' to "هفعِل", 's' to "سَفعِل", 'e' to "شَفعِل", 'H' to "هُفعَل", 'i' to "إتپِعيل",
        't' to "هِشتَفعَل", 'v' to "إشتَفعَل", 'w' to "هِتأفعَل", 'o' to "پولِل", 'z' to "إتپوعَل", 'r' to "هِتپولِل",
        'f' to "هِتپَلپَل", 'b' to "هِفعَل", 'c' to "تِفعَل", 'm' to "پوعِل", 'l' to "پَلپِل", 'L' to "إتپَلپَل",
        'O' to "إتپولَل", 'G' to "إتّفعَل"
    )
    private val hebAspect = mapOf(
        'p' to "ماضي", 'q' to "ماضي بواو القلب", 'i' to "مضارع", 'w' to "مضارع بواو القلب", 'h' to "صيغة الحث",
        'j' to "صيغة الأمر الغائب", 'v' to "أمر", 'r' to "اسم فاعل", 's' to "اسم مفعول", 'a' to "مصدر مطلق", 'c' to "مصدر"
    )

    private fun pgn(p: Char?, g: Char?, n: Char?): String {
        val parts = mutableListOf<String>()
        when (p) { '1' -> parts += "متكلم"; '2' -> parts += "مخاطَب"; '3' -> parts += "غائب" }
        when (g) { 'm' -> parts += "مذكر"; 'f' -> parts += "مؤنث"; 'b', 'c' -> parts += "مشترك" }
        when (n) { 's' -> parts += "مفرد"; 'p' -> parts += "جمع"; 'd' -> parts += "مثنى" }
        return parts.joinToString(" ")
    }

    private fun state(c: Char?): String = when (c) {
        'a' -> "مطلق"; 'c' -> "مضاف"; 'd' -> "معرّف"; else -> ""
    }

    fun hebrew(code: String): String {
        if (code.isEmpty()) return ""
        val aram = code[0] == 'A'
        val segs = code.substring(1).split('/')
        return segs.mapNotNull { seg -> hebSeg(seg, aram).takeIf { it.isNotBlank() } }.joinToString(" + ")
    }

    private fun hebSeg(s: String, aram: Boolean): String {
        if (s.isEmpty()) return ""
        val c = s.getOrNull(1); val d = s.getOrNull(2); val e = s.getOrNull(3); val f = s.getOrNull(4); val g = s.getOrNull(5)
        return when (s[0]) {
            'V' -> {
                val stem = (if (aram) aramStems else hebStems)[c] ?: ""
                val asp = hebAspect[d] ?: ""
                val rest = if (d == 'r' || d == 's') listOf(pgn(null, e, f), state(g)) else listOf(pgn(e, f, g))
                (listOf("فعل", "وزن $stem", asp) + rest).filter { it.isNotBlank() && it != "وزن " }.joinToString("، ")
            }
            'N' -> {
                val t = when (c) { 'p' -> "اسم علم"; 'g' -> "اسم نسبة"; else -> "اسم" }
                if (c == 'p') t else listOf(t, pgn(null, d, e), state(f)).filter { it.isNotBlank() }.joinToString("، ")
            }
            'A' -> {
                val t = when (c) { 'c' -> "عدد"; 'o' -> "عدد ترتيبي"; 'g' -> "صفة نسبة"; else -> "صفة" }
                listOf(t, pgn(null, d, e), state(f)).filter { it.isNotBlank() }.joinToString("، ")
            }
            'P' -> {
                val t = when (c) { 'd' -> "اسم إشارة"; 'f' -> "ضمير مبهم"; 'i' -> "اسم استفهام"; 'r' -> "اسم موصول"; else -> "ضمير" }
                listOf(t, pgn(d, e, f)).filter { it.isNotBlank() }.joinToString("، ")
            }
            'S' -> when (c) {
                'd' -> "هاء الاتجاه"; 'h' -> "هاء زائدة"; 'n' -> "نون زائدة"
                else -> listOf("ضمير متصل", pgn(d, e, f)).filter { it.isNotBlank() }.joinToString("، ")
            }
            'T' -> when (c) {
                'd' -> "أداة التعريف"; 'o' -> "علامة المفعول به (إت)"; 'n' -> "أداة نفي"; 'i' -> "أداة استفهام"
                'j' -> "تعجب"; 'r' -> "أداة وصل"; 'm' -> "أداة إشارة"; 'a' -> "أداة توكيد"; 'e' -> "أداة حث"
                'c' -> "أداة ربط"; else -> "أداة"
            }
            'R' -> if (c == 'd') "حرف جر + أداة التعريف" else "حرف جر"
            'C' -> "حرف عطف"
            'D' -> "ظرف"
            else -> ""
        }
    }

    // ------------------------------------------------------------ Greek
    private fun greekAll(code: String): String =
        code.split('+', ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" + ") { greek(it) }

    private val gTense = mapOf(
        'P' to "مضارع", 'I' to "ماضي مستمر", 'F' to "مستقبل", 'A' to "ماضي (أوريست)", 'R' to "تام",
        'L' to "ماضي التام", '2' to "أوريست تاني"
    )
    private val gVoice = mapOf(
        'A' to "معلوم", 'M' to "متوسط", 'P' to "مجهول", 'E' to "متوسط أو مجهول", 'D' to "متوسط لازم",
        'O' to "مجهول لازم", 'N' to "متوسط أو مجهول لازم"
    )
    private val gMood = mapOf(
        'I' to "خبري", 'S' to "شرطي", 'O' to "تمني", 'M' to "أمر", 'N' to "مصدر", 'P' to "اسم فاعل/مفعول"
    )
    private val gCase = mapOf('N' to "رفع", 'G' to "إضافة", 'D' to "مفعول غير مباشر", 'A' to "نصب", 'V' to "نداء")
    private val gNum = mapOf('S' to "مفرد", 'P' to "جمع")
    private val gGen = mapOf('M' to "مذكر", 'F' to "مؤنث", 'N' to "محايد")

    private fun cng(s: String): String {
        val parts = mutableListOf<String>()
        s.getOrNull(0)?.let { gCase[it]?.let(parts::add) }
        s.getOrNull(1)?.let { gNum[it]?.let(parts::add) }
        s.getOrNull(2)?.let { gGen[it]?.let(parts::add) }
        return parts.joinToString(" ")
    }

    fun greek(code: String): String {
        val p = code.split('-')
        val head = p[0]
        return when (head) {
            "V" -> {
                val t = p.getOrNull(1) ?: ""
                var i = 0
                val tense = if (t.startsWith("2")) { i = 1; "أوريست تاني" } else null
                val tn = tense ?: gTense[t.getOrNull(0)] ?: ""
                if (tense == null) i = 1 else i = 2
                val voice = gVoice[t.getOrNull(i)] ?: ""
                val mood = gMood[t.getOrNull(i + 1)] ?: ""
                val tail = p.getOrNull(2) ?: ""
                val pn = if (tail.isNotEmpty() && tail[0].isDigit()) {
                    val per = when (tail[0]) { '1' -> "متكلم"; '2' -> "مخاطَب"; else -> "غائب" }
                    "$per ${gNum[tail.getOrNull(1)] ?: ""}".trim()
                } else cng(tail)
                listOf("فعل", tn, voice, mood, pn).filter { it.isNotBlank() }.joinToString("، ")
            }
            "N" -> listOf("اسم", cng(p.getOrNull(1) ?: "")).filter { it.isNotBlank() }.joinToString("، ")
            "A" -> listOf("صفة", cng(p.getOrNull(1) ?: "")).filter { it.isNotBlank() }.joinToString("، ")
            "T" -> listOf("أداة التعريف", cng(p.getOrNull(1) ?: "")).filter { it.isNotBlank() }.joinToString("، ")
            "P", "R", "C", "D", "K", "I", "X", "Q", "F", "S" -> {
                val name = when (head) {
                    "P" -> "ضمير شخصي"; "R" -> "اسم موصول"; "C" -> "ضمير تبادلي"; "D" -> "اسم إشارة"
                    "K" -> "ضمير مقابلة"; "I" -> "اسم استفهام"; "X" -> "ضمير مبهم"; "Q" -> "ضمير مقابلة استفهامي"
                    "F" -> "ضمير انعكاسي"; else -> "ضمير ملكية"
                }
                val rest = p.drop(1).joinToString("") { it }
                val per = rest.firstOrNull()?.takeIf { it.isDigit() }
                val r2 = if (per != null) rest.drop(1) else rest
                listOfNotNull(name, per?.let { when (it) { '1' -> "متكلم"; '2' -> "مخاطَب"; else -> "غائب" } }, cng(r2))
                    .filter { it.isNotBlank() }.joinToString("، ")
            }
            "CONJ" -> "حرف عطف"
            "ADV" -> "ظرف"
            "PREP" -> "حرف جر"
            "PRT" -> if (p.getOrNull(1) == "N") "أداة نفي" else if (p.getOrNull(1) == "I") "أداة استفهام" else "أداة"
            "INJ" -> "تعجب"
            "COND" -> "أداة شرط"
            "HEB" -> "كلمة عبري"
            "ARAM" -> "كلمة آرامي"
            else -> code
        }
    }
}
