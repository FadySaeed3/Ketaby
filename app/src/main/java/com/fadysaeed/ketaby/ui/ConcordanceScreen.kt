package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.ConcHit
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.Lemma
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

@Composable
fun ConcordanceScreen(nav: Nav, dstrong: String) {
    val lemma by produceState<Lemma?>(null, dstrong) { value = Db.lemma(dstrong) }
    val prefs = LocalPrefs.current
    val englishTr = if (prefs.readerMode != 0) prefs.english else null
    val result by produceState<Pair<List<ConcHit>, Set<Long>>?>(null, dstrong, englishTr) { value = Db.concordance(dstrong, englishTr) }
    val lemmaWords by produceState<Set<Long>>(emptySet(), dstrong) { value = Db.wordIdsForLemma(dstrong) }
    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar(t("كل المواضع", "كل المواضع", "All occurrences"), onBack = { nav.pop() }) { SearchSettingsActions(nav) } },
    ) { pad ->
        val r = result
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            item {
                val l = lemma
                if (l != null) {
                    val greek = l.lang == "grc"
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.lemma, fontFamily = if (greek) GreekFont else HebrewFont, fontSize = 30.sp, color = Gold)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (greek) listOfNotNull(l.tr, l.tr2).distinct().joinToString(" / ") else (l.tr ?: ""),
                                color = Teal, fontSize = 16.sp, style = TextStyle(textDirection = TextDirection.Rtl),
                            )
                        }
                        if (!l.gloss.isNullOrBlank()) Text(l.gloss, color = Muted, fontSize = 13.sp, style = TextStyle(textDirection = TextDirection.Ltr))
                        val shown = r?.first?.size
                        Text(
                            t("اتذكرت", "وردت", "Occurs") + " ${ArText.times(l.count)}" +
                                (shown?.let { t(" في ${ArText.num(it)} آية في فاندايك", " في ${ArText.num(it)} آية في فاندايك", " in $it Van Dyke verses") } ?: ""),
                            color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                        if (greek) Text(
                            t(
                                "العدد بيشمل كل النسخ اليوناني، ومنها كلمات النص المستلم اللي مش في النص النقدي.",
                                "يشمل العدد كل النسخ اليونانية، ومنها كلمات النص المستلم غير الموجودة في النص النقدي.",
                                "The count covers all Greek editions, including Textus Receptus words absent from the critical text.",
                            ),
                            color = Muted, fontSize = 11.sp,
                        )
                    }
                }
            }
            if (r == null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp)) { Text(t("بندوّر…", "جارٍ البحث…", "Searching…"), color = Muted) } }
            } else {
                items(r.first, key = { it.verseId }) { hit ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                            .clickable { nav.push(Screen.Reader(hit.book, hit.ch, hit.v)) }
                            .padding(12.dp),
                    ) {
                        Text(
                            "${hit.bookName} ${ArText.num(hit.ch)}:${ArText.num(hit.v)}",
                            color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        if (prefs.readerMode != 2) {
                            VerseText(null, hit.tokens, marked = r.second) { nav.push(Screen.Reader(hit.book, hit.ch, hit.v)) }
                        }
                        hit.english?.let { ev ->
                            EnglishVerseText(null, ev, markedWords = lemmaWords, dim = prefs.readerMode == 1) { _, _, _ ->
                                nav.push(Screen.Reader(hit.book, hit.ch, hit.v))
                            }
                        }
                    }
                }
            }
        }
    }
}

