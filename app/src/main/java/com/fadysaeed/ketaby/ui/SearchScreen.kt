package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.Lemma
import com.fadysaeed.ketaby.data.SearchHit
import kotlinx.coroutines.launch

private val strongRe = Regex("^\\s*([HhGg])\\s*0*(\\d{1,4})([A-Za-z]?)\\s*$")
private val arabicRe = Regex("[\\u0621-\\u064A]")

/** Lemmas matching a Strong's number, a Hebrew/Greek word, an English gloss or an Arabic transliteration. */
private fun matchLemmas(all: List<Lemma>, q: String): List<Lemma> {
    val m = strongRe.find(q)
    if (m != null) {
        val prefix = m.groupValues[1].uppercase() + m.groupValues[2].padStart(4, '0') + m.groupValues[3].uppercase()
        return all.filter { it.dstrong.startsWith(prefix) }
    }
    val bq = ArText.bare(q.trim())
    if (bq.isEmpty()) return emptyList()
    val hits = if (arabicRe.containsMatchIn(q)) {
        val pq = ArText.plainArabic(q)
        all.filter { l -> listOfNotNull(l.tr, l.tr2).any { ArText.plainArabic(it).contains(pq) } }
    } else {
        all.filter { l ->
            ArText.bare(l.lemma).contains(bq) || (l.gloss?.lowercase()?.contains(bq) ?: false)
        }
    }
    return hits.take(300)
}

@Composable
fun SearchScreen(nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var verses by remember { mutableStateOf<List<SearchHit>?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var lemmas by remember { mutableStateOf<List<Lemma>?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    fun run() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            val arabic = arabicRe.containsMatchIn(q)
            lemmas = matchLemmas(Db.allLemmas(), q)
            if (arabic) {
                verses = Db.searchArabic(q)
                total = Db.countArabic(q)
                tab = 0
            } else {
                verses = emptyList(); total = 0
                tab = 1
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar("بحث", onBack = { nav.pop() }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("كلمة عربي، أو H430، أو λόγος", color = Muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold, unfocusedBorderColor = Line, cursorColor = Gold,
                    focusedTextColor = Ink, unfocusedTextColor = Ink,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
            TabRow(selectedTabIndex = tab, containerColor = Color.Black, contentColor = Gold) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = {
                    Text("الآيات" + (verses?.let { " (${ArText.num(total)})" } ?: ""))
                })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                    Text("الكلمات الأصلية" + (lemmas?.let { " (${ArText.num(it.size)})" } ?: ""))
                })
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tab == 0) {
                    val vs = verses
                    if (vs != null && vs.isEmpty()) item { Text("مفيش آيات؛ البحث بالعربي بيدوّر في نص فاندايك من غير تشكيل.", color = Muted) }
                    if (vs != null && total > vs.size) item {
                        Text("بنعرض أول ${ArText.num(vs.size)} آية؛ ضيّق البحث بكلمة زيادة.", color = Muted, fontSize = 12.sp)
                    }
                    items(vs.orEmpty(), key = { it.verseId }) { h ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                                .clickable { nav.push(Screen.Reader(h.book, h.ch, h.v)) }.padding(12.dp),
                        ) {
                            Text("${h.bookName} ${ArText.num(h.ch)}:${ArText.num(h.v)}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(h.text, fontFamily = scriptureFamily(), fontSize = 18.sp, color = Ink, lineHeight = 32.sp)
                        }
                    }
                } else {
                    val ls = lemmas
                    if (ls != null && ls.isEmpty()) item { Text("مفيش كلمات مطابقة.", color = Muted) }
                    items(ls.orEmpty(), key = { it.dstrong }) { l ->
                        val greek = l.lang == "grc"
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                                .clickable { nav.push(Screen.Concordance(l.dstrong)) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(l.lemma, fontFamily = if (greek) GreekFont else HebrewFont, fontSize = 24.sp, color = Gold)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (greek) listOfNotNull(l.tr, l.tr2).distinct().joinToString(" / ") else (l.tr ?: ""),
                                    color = Teal, fontSize = 15.sp,
                                )
                                Text((l.gloss ?: "") + " · " + l.dstrong, color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            Text(ArText.num(l.count), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
