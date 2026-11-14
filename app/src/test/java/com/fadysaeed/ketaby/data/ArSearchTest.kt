package com.fadysaeed.ketaby.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ArSearch against the cases shared with data-pipeline/ar_search.py, plus highlighting. */
class ArSearchTest {
    private val cases: List<List<String>> by lazy {
        val f = listOf("../data-pipeline/tests/ar_search_cases.tsv", "data-pipeline/tests/ar_search_cases.tsv")
            .map(::File).first { it.exists() }
        f.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
    }

    @Test fun sharedCases() {
        val fails = ArrayList<String>()
        for (c in cases) {
            when (c[0]) {
                "norm" -> {
                    val got = ArSearch.normalize(c[2], loose = c[1] == "loose")
                    if (got != c[3]) fails += "norm ${c[1]} ${c[2]}: got $got want ${c[3]}"
                    if (c[1] == "strict" && ArText.plainArabic(c[2]) != c[3]) fails += "plainArabic ${c[2]}"
                }
                "match" -> {
                    val mode = ArSearch.Mode(wholeWord = "whole" in c[1], loose = c[1].startsWith("loose"))
                    val got = ArSearch.matchesPlain(ArSearch.normalize(c[2]), c[3], mode)
                    if (got != (c[4] == "1")) fails += "match ${c[1]} ${c[2]} / ${c[3]}: got $got"
                }
                "forms" -> if (c[3] !in ArSearch.forms(c[2])) fails += "forms(${c[2]}) lacks ${c[3]}"
            }
        }
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
        assertTrue("cases file looks empty", cases.size > 20)
    }

    @Test fun highlightWholeWordWithProclitic() {
        val text = "وَخُبْزًا لِلأَكْلِ"
        assertEquals(listOf(0..8), ArSearch.ranges(text, "خبزا", ArSearch.Mode()))
    }

    @Test fun wholeWordSkipsLongerWord() {
        val text = "وَلاَ سَبْعَ خُبْزَاتِ"
        assertEquals(emptyList<IntRange>(), ArSearch.ranges(text, "خبزا", ArSearch.Mode()))
        val part = ArSearch.ranges(text, "خبزا", ArSearch.Mode(wholeWord = false))
        assertEquals(1, part.size)
        assertEquals("خُبْزَاتِ", text.substring(part[0].first, part[0].last + 1))
    }

    @Test fun looseHamzaHighlight() {
        val text = "لاَ سُؤَالَ"
        val r = ArSearch.ranges(text, "سوال", ArSearch.Mode(loose = true))
        assertEquals("سُؤَالَ", text.substring(r[0].first, r[0].last + 1))
    }

    @Test fun sqlHasOneArgumentPerForm() {
        val (where, args) = ArSearch.sql("plain", "خبزا الرب", ArSearch.Mode())!!
        assertEquals(ArSearch.forms("خبزا").size + ArSearch.forms("الرب").size, args.size)
        assertEquals(args.size, where.count { it == '?' })
        assertTrue(args.all { it.startsWith(" ") && it.endsWith(" ") })
    }
}
