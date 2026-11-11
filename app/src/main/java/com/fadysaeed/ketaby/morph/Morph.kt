package com.fadysaeed.ketaby.morph

/**
 * Turns STEPBible grammar codes into short descriptions, in Arabic or English.
 * Hebrew/Aramaic: OSHB-style codes (e.g. "HVqp3ms", "HC/Ncmsc/Sp1bs").
 * Greek: Robinson codes (e.g. "V-AAI-3S", "N-ASM", "T-NSM").
 */
object Morph {

    /** lang: word language ("heb", "arc", "grc"); out: "en" for English, anything else for Arabic. */
    fun describe(code: String?, lang: String, out: String = "ar"): String {
        if (code.isNullOrBlank()) return ""
        val en = out == "en"
        return if (lang == "grc") greekAll(code, en) else hebrew(code, en)
    }

    private class T(val ar: String, val en: String) {
        fun of(e: Boolean) = if (e) en else ar
    }

    private fun join(e: Boolean, parts: List<String>) = parts.filter { it.isNotBlank() }.joinToString(if (e) ", " else "، ")

    // ------------------------------------------------------------ Hebrew
    private val hebStems = mapOf(
        'q' to T("قال (البسيط)", "Qal"), 'N' to T("نفعال", "Niphal"), 'p' to T("پعّيل", "Piel"), 'P' to T("پُعّال", "Pual"),
        'h' to T("هفعيل", "Hiphil"), 'H' to T("هُفعال", "Hophal"), 't' to T("هتپعّيل", "Hithpael"), 'o' to T("پولِل", "Polel"),
        'O' to T("پولَل", "Polal"), 'r' to T("هتپولِل", "Hithpolel"), 'm' to T("پوعِل", "Poel"), 'M' to T("پوعَل", "Poal"),
        'k' to T("پَلِل", "Palel"), 'K' to T("پُلَل", "Pulal"), 'Q' to T("قال المجهول", "Qal passive"), 'l' to T("پِلپِل", "Pilpel"),
        'L' to T("پُلپَل", "Polpal"), 'f' to T("هتپَلپِل", "Hithpalpel"), 'D' to T("نِتپعّيل", "Nithpael"), 'j' to T("پَعلَل", "Pealal"),
        'i' to T("پِلِل", "Pilel"), 'u' to T("هُتپعّال", "Hothpaal"), 'c' to T("تِفعيل", "Tiphil"), 'v' to T("هِشتَفعيل", "Hishtaphel"),
        'w' to T("نِتپَلِل", "Nithpalel"), 'y' to T("نِتپوعِل", "Nithpoel"), 'z' to T("هِتپوعِل", "Hithpoel"),
    )
    private val aramStems = mapOf(
        'q' to T("پِعَل (البسيط)", "Peal"), 'Q' to T("پِعيل", "Peil"), 'u' to T("هِتپِعيل", "Hithpeel"), 'p' to T("پَعِّل", "Pael"),
        'P' to T("إتپَعَّل", "Ithpaal"), 'M' to T("هِتپَعَّل", "Hithpaal"), 'a' to T("أفعِل", "Aphel"), 'h' to T("هفعِل", "Haphel"),
        's' to T("سَفعِل", "Saphel"), 'e' to T("شَفعِل", "Shaphel"), 'H' to T("هُفعَل", "Hophal"), 'i' to T("إتپِعيل", "Ithpeel"),
        't' to T("هِشتَفعَل", "Hishtaphal"), 'v' to T("إشتَفعَل", "Ishtaphal"), 'w' to T("هِتأفعَل", "Hithaphal"), 'o' to T("پولِل", "Polel"),
        'z' to T("إتپوعَل", "Ithpoal"), 'r' to T("هِتپولِل", "Hithpolel"), 'f' to T("هِتپَلپَل", "Hithpalpal"), 'b' to T("هِفعَل", "Hephal"),
        'c' to T("تِفعَل", "Tiphel"), 'm' to T("پوعِل", "Poel"), 'l' to T("پَلپِل", "Palpel"), 'L' to T("إتپَلپَل", "Ithpalpal"),
        'O' to T("إتپولَل", "Ithpolal"), 'G' to T("إتّفعَل", "Ittaphal"),
    )
    private val hebAspect = mapOf(
        'p' to T("ماضٍ", "perfect"), 'q' to T("ماضٍ بواو القلب", "sequential perfect"), 'i' to T("مضارع", "imperfect"),
        'w' to T("مضارع بواو القلب", "sequential imperfect"), 'h' to T("صيغة الحث", "cohortative"), 'j' to T("صيغة الأمر للغائب", "jussive"),
        'v' to T("أمر", "imperative"), 'r' to T("اسم فاعل", "participle"), 's' to T("اسم مفعول", "passive participle"),
        'a' to T("مصدر مطلق", "infinitive absolute"), 'c' to T("مصدر", "infinitive construct"),
    )

    private fun pgn(e: Boolean, p: Char?, g: Char?, n: Char?): String {
        val parts = mutableListOf<String>()
        when (p) { '1' -> parts += if (e) "1st" else "متكلم"; '2' -> parts += if (e) "2nd" else "مخاطَب"; '3' -> parts += if (e) "3rd" else "غائب" }
        when (g) { 'm' -> parts += if (e) "masc." else "مذكر"; 'f' -> parts += if (e) "fem." else "مؤنث"; 'b', 'c' -> parts += if (e) "common" else "مشترك" }
        when (n) { 's' -> parts += if (e) "sing." else "مفرد"; 'p' -> parts += if (e) "pl." else "جمع"; 'd' -> parts += if (e) "dual" else "مثنى" }
        return parts.joinToString(" ")
    }

    private fun state(e: Boolean, c: Char?): String = when (c) {
        'a' -> if (e) "absolute" else "مطلق"; 'c' -> if (e) "construct" else "مضاف"; 'd' -> if (e) "determined" else "معرّف"; else -> ""
    }

    fun hebrew(code: String, e: Boolean = false): String {
        if (code.isEmpty()) return ""
        val aram = code[0] == 'A'
        val segs = code.substring(1).split('/')
        return segs.mapNotNull { seg -> hebSeg(seg, aram, e).takeIf { it.isNotBlank() } }.joinToString(" + ")
    }

    private fun hebSeg(s: String, aram: Boolean, e: Boolean): String {
        if (s.isEmpty()) return ""
        val c = s.getOrNull(1); val d = s.getOrNull(2); val f3 = s.getOrNull(3); val f4 = s.getOrNull(4); val f5 = s.getOrNull(5)
        return when (s[0]) {
            'V' -> {
                val stem = (if (aram) aramStems else hebStems)[c]?.of(e) ?: ""
                val asp = hebAspect[d]?.of(e) ?: ""
                val rest = if (d == 'r' || d == 's') listOf(pgn(e, null, f3, f4), state(e, f5)) else listOf(pgn(e, f3, f4, f5))
                join(e, listOf(if (e) "Verb" else "فعل", if (stem.isEmpty()) "" else if (e) stem else "وزن $stem", asp) + rest)
            }
            'N' -> {
                val t = when (c) { 'p' -> if (e) "Proper noun" else "اسم علم"; 'g' -> if (e) "Gentilic noun" else "اسم نسبة"; else -> if (e) "Noun" else "اسم" }
                if (c == 'p') t else join(e, listOf(t, pgn(e, null, d, f3), state(e, f4)))
            }
            'A' -> {
                val t = when (c) {
                    'c' -> if (e) "Cardinal number" else "عدد"; 'o' -> if (e) "Ordinal number" else "عدد ترتيبي"
                    'g' -> if (e) "Gentilic adjective" else "صفة نسبة"; else -> if (e) "Adjective" else "صفة"
                }
                join(e, listOf(t, pgn(e, null, d, f3), state(e, f4)))
            }
            'P' -> {
                val t = when (c) {
                    'd' -> if (e) "Demonstrative pronoun" else "اسم إشارة"; 'f' -> if (e) "Indefinite pronoun" else "ضمير مبهم"
                    'i' -> if (e) "Interrogative pronoun" else "اسم استفهام"; 'r' -> if (e) "Relative pronoun" else "اسم موصول"
                    else -> if (e) "Pronoun" else "ضمير"
                }
                join(e, listOf(t, pgn(e, d, f3, f4)))
            }
            'S' -> when (c) {
                'd' -> if (e) "Directional he" else "هاء الاتجاه"; 'h' -> if (e) "Paragogic he" else "هاء زائدة"
                'n' -> if (e) "Paragogic nun" else "نون زائدة"
                else -> join(e, listOf(if (e) "Pronominal suffix" else "ضمير متصل", pgn(e, d, f3, f4)))
            }
            'T' -> when (c) {
                'd' -> if (e) "Article" else "أداة التعريف"; 'o' -> if (e) "Direct object marker" else "علامة المفعول به (إت)"
                'n' -> if (e) "Negative particle" else "أداة نفي"; 'i' -> if (e) "Interrogative particle" else "أداة استفهام"
                'j' -> if (e) "Interjection" else "تعجب"; 'r' -> if (e) "Relative particle" else "أداة وصل"
                'm' -> if (e) "Demonstrative particle" else "أداة إشارة"; 'a' -> if (e) "Affirmation particle" else "أداة توكيد"
                'e' -> if (e) "Exhortation particle" else "أداة حث"; 'c' -> if (e) "Conjunctive particle" else "أداة ربط"
                else -> if (e) "Particle" else "أداة"
            }
            'R' -> if (c == 'd') (if (e) "Preposition + article" else "حرف جر + أداة التعريف") else (if (e) "Preposition" else "حرف جر")
            'C' -> if (e) "Conjunction" else "حرف عطف"
            'D' -> if (e) "Adverb" else "ظرف"
            else -> ""
        }
    }

    // ------------------------------------------------------------ Greek
    private fun greekAll(code: String, e: Boolean): String =
        code.split('+', ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" + ") { greek(it, e) }

    private val gTense = mapOf(
        'P' to T("مضارع", "present"), 'I' to T("ماضٍ مستمر", "imperfect"), 'F' to T("مستقبل", "future"),
        'A' to T("ماضٍ (أوريست)", "aorist"), 'R' to T("تام", "perfect"), 'L' to T("ماضٍ تام", "pluperfect"),
    )
    private val gVoice = mapOf(
        'A' to T("معلوم", "active"), 'M' to T("متوسط", "middle"), 'P' to T("مجهول", "passive"),
        'E' to T("متوسط أو مجهول", "middle or passive"), 'D' to T("متوسط لازم", "middle deponent"),
        'O' to T("مجهول لازم", "passive deponent"), 'N' to T("متوسط أو مجهول لازم", "middle or passive deponent"),
    )
    private val gMood = mapOf(
        'I' to T("خبري", "indicative"), 'S' to T("شرطي", "subjunctive"), 'O' to T("تمنٍّ", "optative"),
        'M' to T("أمر", "imperative"), 'N' to T("مصدر", "infinitive"), 'P' to T("اسم فاعل/مفعول", "participle"),
    )
    private val gCase = mapOf(
        'N' to T("رفع", "nominative"), 'G' to T("إضافة", "genitive"), 'D' to T("مفعول غير مباشر", "dative"),
        'A' to T("نصب", "accusative"), 'V' to T("نداء", "vocative"),
    )
    private val gNum = mapOf('S' to T("مفرد", "sing."), 'P' to T("جمع", "pl."))
    private val gGen = mapOf('M' to T("مذكر", "masc."), 'F' to T("مؤنث", "fem."), 'N' to T("محايد", "neut."))

    private fun cng(s: String, e: Boolean): String {
        val parts = mutableListOf<String>()
        s.getOrNull(0)?.let { ch -> gCase[ch]?.let { parts += it.of(e) } }
        s.getOrNull(1)?.let { ch -> gNum[ch]?.let { parts += it.of(e) } }
        s.getOrNull(2)?.let { ch -> gGen[ch]?.let { parts += it.of(e) } }
        return parts.joinToString(" ")
    }

    private fun person(c: Char, e: Boolean) = when (c) {
        '1' -> if (e) "1st" else "متكلم"; '2' -> if (e) "2nd" else "مخاطَب"; else -> if (e) "3rd" else "غائب"
    }

    fun greek(code: String, e: Boolean = false): String {
        val p = code.split('-')
        val head = p[0]
        return when (head) {
            "V" -> {
                val t = p.getOrNull(1) ?: ""
                val second = t.startsWith("2")
                val tn = if (second) (if (e) "second aorist" else "أوريست ثانٍ") else gTense[t.getOrNull(0)]?.of(e) ?: ""
                val i = if (second) 2 else 1
                val voice = gVoice[t.getOrNull(i)]?.of(e) ?: ""
                val mood = gMood[t.getOrNull(i + 1)]?.of(e) ?: ""
                val tail = p.getOrNull(2) ?: ""
                val pn = if (tail.isNotEmpty() && tail[0].isDigit()) {
                    "${person(tail[0], e)} ${gNum[tail.getOrNull(1)]?.of(e) ?: ""}".trim()
                } else cng(tail, e)
                join(e, listOf(if (e) "Verb" else "فعل", tn, voice, mood, pn))
            }
            "N" -> join(e, listOf(if (e) "Noun" else "اسم", cng(p.getOrNull(1) ?: "", e)))
            "A" -> join(e, listOf(if (e) "Adjective" else "صفة", cng(p.getOrNull(1) ?: "", e)))
            "T" -> join(e, listOf(if (e) "Article" else "أداة التعريف", cng(p.getOrNull(1) ?: "", e)))
            "P", "R", "C", "D", "K", "I", "X", "Q", "F", "S" -> {
                val name = when (head) {
                    "P" -> if (e) "Personal pronoun" else "ضمير شخصي"; "R" -> if (e) "Relative pronoun" else "اسم موصول"
                    "C" -> if (e) "Reciprocal pronoun" else "ضمير تبادلي"; "D" -> if (e) "Demonstrative pronoun" else "اسم إشارة"
                    "K" -> if (e) "Correlative pronoun" else "ضمير مقابلة"; "I" -> if (e) "Interrogative pronoun" else "اسم استفهام"
                    "X" -> if (e) "Indefinite pronoun" else "ضمير مبهم"; "Q" -> if (e) "Correlative interrogative pronoun" else "ضمير مقابلة استفهامي"
                    "F" -> if (e) "Reflexive pronoun" else "ضمير انعكاسي"; else -> if (e) "Possessive pronoun" else "ضمير ملكية"
                }
                val rest = p.drop(1).joinToString("")
                val per = rest.firstOrNull()?.takeIf { it.isDigit() }
                val r2 = if (per != null) rest.drop(1) else rest
                join(e, listOf(name, per?.let { person(it, e) } ?: "", cng(r2, e)))
            }
            "CONJ" -> if (e) "Conjunction" else "حرف عطف"
            "ADV" -> if (e) "Adverb" else "ظرف"
            "PREP" -> if (e) "Preposition" else "حرف جر"
            "PRT" -> when (p.getOrNull(1)) {
                "N" -> if (e) "Negative particle" else "أداة نفي"
                "I" -> if (e) "Interrogative particle" else "أداة استفهام"
                else -> if (e) "Particle" else "أداة"
            }
            "INJ" -> if (e) "Interjection" else "تعجب"
            "COND" -> if (e) "Conditional particle" else "أداة شرط"
            "HEB" -> if (e) "Hebrew word" else "كلمة عبرية"
            "ARAM" -> if (e) "Aramaic word" else "كلمة آرامية"
            else -> code
        }
    }
}
