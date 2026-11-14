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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.fadysaeed.ketaby.data.Book
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext

private val strongRe = Regex("^\\s*([HhGg])\\s*0*(\\d{1,4})([A-Za-z]?)\\s*$")
private val arabicRe = Regex("[\\u0621-\\u064A]")
private val latinRe = Regex("^[A-Za-z0-9 ,.;:'\"!?-]+$")

/** A passage the query may refer to, e.g. "يوحنا 3:16", "1 كورنثوس 13", "John 3:16". */
data class RefHit(val book: Book, val ch: Int, val v: Int?)

private fun asciiDigits(s: String) = s.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("")

private val ordinalWords = mapOf(
    "الاول" to 1, "الاولي" to 1, "الثاني" to 2, "الثانيه" to 2, "الثالث" to 3, "الثالثه" to 3,
)

/** (ordinal, base) for a book name: "كورنثوس الأولى" -> (1, "كورنثوس"), "1 Corinthians" -> (1, "corinthians"). */
private fun bookKeys(b: Book): List<Pair<Int?, String>> {
    val out = mutableListOf<Pair<Int?, String>>()
    val ar = ArText.plainArabic(b.nameAr).removePrefix("سفر ").trim()
    val parts = ar.split(' ')
    val ord = ordinalWords[parts.last()]
    val base = if (ord != null) parts.dropLast(1).joinToString("") else ar.replace(" ", "")
    out += ord to base
    if (base.startsWith("ال")) out += ord to base.removePrefix("ال")
    val en = b.nameEn.lowercase()
    val m = Regex("^([123]) (.*)$").find(en)
    if (m != null) out += m.groupValues[1].toInt() to m.groupValues[2].replace(" ", "")
    else out += null to en.replace(" ", "")
    return out
}

suspend fun findRefs(q: String, books: List<Book>): List<RefHit> {
    val s = asciiDigits(q).trim()
    val m = Regex("^(.*?)\\s*(\\d+)(?:\\s*[:：.,،]\\s*(\\d+))?\\s*$").find(s)
    var name = (m?.groupValues?.get(1) ?: s).trim()
    val ch = m?.groupValues?.get(2)?.toIntOrNull()
    val v = m?.groupValues?.get(3)?.toIntOrNull()
    if (m != null && name.isEmpty()) return emptyList()
    // an ordinal written as a digit or a word: "1 كورنثوس", "كورنثوس الأولى", "1 Cor"
    var ord: Int? = null
    Regex("^([123])\\s*(.+)$").find(name)?.let { ord = it.groupValues[1].toInt(); name = it.groupValues[2] }
    val arabic = Regex("[\\u0621-\\u064A]").containsMatchIn(name)
    var key = if (arabic) ArText.plainArabic(name).removePrefix("سفر ") else name.lowercase().replace(Regex("[^a-z ]"), "")
    if (arabic) {
        val words = key.split(' ').filter { it.isNotEmpty() }
        words.lastOrNull()?.let { ordinalWords[it] }?.let { if (words.size > 1) { ord = it; key = words.dropLast(1).joinToString("") } }
    }
    key = key.replace(" ", "")
    if (key.length < 2) return emptyList()
    val hits = mutableListOf<RefHit>()
    for (b in books) {
        val ok = bookKeys(b).any { (o, base) -> base.startsWith(key) && (ord == null || o == ord) }
        if (!ok) continue
        val c = ch ?: 1
        if (c > b.chapters) continue
        if (v != null && v > Db.verseCount(b.id, c)) continue
        hits += RefHit(b, c, if (ch == null) null else v)
    }
    return if (ch == null && hits.size > 6) emptyList() else hits.take(8)
}

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

/** The last search stays on screen when you come back to it from a verse. */
private object SearchMemory {
    val query = mutableStateOf("")
    /** The query that produced the results on screen (the box may have changed since). */
    val shown = mutableStateOf("")
    val tab = mutableIntStateOf(0)
    val verses = mutableStateOf<List<SearchHit>?>(null)
    val versesEnglish = mutableStateOf(false)
    val total = mutableIntStateOf(0)
    val lemmas = mutableStateOf<List<Lemma>?>(null)
    val refs = mutableStateOf<List<RefHit>>(emptyList())
}

@Composable
fun SearchScreen(nav: Nav) {
    var query by SearchMemory.query
    var tab by SearchMemory.tab
    var verses by SearchMemory.verses
    var versesEnglish by SearchMemory.versesEnglish
    val prefs = LocalPrefs.current
    var total by SearchMemory.total
    var lemmas by SearchMemory.lemmas
    var refs by SearchMemory.refs
    var shown by SearchMemory.shown
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    /** Every matching verse (the list on screen stops at 500). */
    suspend fun allHits(): List<SearchHit> =
        if (versesEnglish) Db.searchEnglish(prefs.english, shown, limit = 50000).first
        else Db.searchArabic(shown, limit = 50000)

    fun export(uri: android.net.Uri?, pdf: Boolean) {
        if (uri == null) return
        busy = true
        scope.launch {
            val ok = try {
                val hits = allHits()
                if (pdf) SearchExport.pdf(ctx, uri, shown, hits, versesEnglish, prefs.scriptureFont == "plex")
                else SearchExport.txt(ctx, uri, shown, hits)
                true
            } catch (e: Exception) { false }
            busy = false
            Toast.makeText(
                ctx,
                if (ok) t("الملف اتحفظ", "حُفظ الملف", "File saved") else t("ما قدرتش أحفظ الملف", "تعذّر حفظ الملف", "Could not save the file"),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { export(it, false) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { export(it, true) }
    fun fileName(ext: String) = t("بحث", "بحث", "search") + " - " + shown.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(40) + "." + ext

    fun run() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            shown = q
            refs = findRefs(q, Db.books())
            val arabic = arabicRe.containsMatchIn(q)
            lemmas = matchLemmas(Db.allLemmas(), q)
            when {
                arabic -> {
                    verses = Db.searchArabic(q); total = Db.countArabic(q); versesEnglish = false
                    tab = 0
                }
                latinRe.matches(q) && strongRe.find(q) == null -> {
                    val (hits, n) = Db.searchEnglish(prefs.english, q)
                    verses = hits; total = n; versesEnglish = true
                    tab = if (n > 0) 0 else 1
                }
                else -> {
                    verses = emptyList(); total = 0
                    tab = 1
                }
            }
        }
    }

    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            KTopBar(t("بحث", "بحث", "Search"), onBack = { nav.pop() }) {
                if (tab == 0 && !verses.isNullOrEmpty()) {
                    IconButton(onClick = { if (!busy) menu = true }) {
                        Icon(Icons.Filled.Share, t("تصدير النتايج", "تصدير النتائج", "Export results"), tint = if (busy) Muted else Ink)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = PanelHi) {
                        DropdownMenuItem(
                            text = { Text(t("حفظ كملف نص (TXT)", "حفظ كملف نصي (TXT)", "Save as text (TXT)"), color = Ink) },
                            onClick = { menu = false; txtLauncher.launch(fileName("txt")) },
                        )
                        DropdownMenuItem(
                            text = { Text(t("حفظ كملف PDF", "حفظ كملف PDF", "Save as PDF"), color = Ink) },
                            onClick = { menu = false; pdfLauncher.launch(fileName("pdf")) },
                        )
                    }
                }
                HomeAction(nav)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(t("كلمة، أو شاهد زي يوحنا 3:16، أو H430", "كلمة، أو شاهد مثل يوحنا 3:16، أو H430", "A word, a reference like John 3:16, or H430"), color = Muted) },
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
                    Text(t("الآيات", "الآيات", "Verses") + (verses?.let { " (${ArText.num(total)})" } ?: ""))
                })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                    Text(t("الكلمات الأصلية", "الكلمات الأصلية", "Original words") + (lemmas?.let { " (${ArText.num(it.size)})" } ?: ""))
                })
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (refs.isNotEmpty()) items(refs, key = { "ref${it.book.id}:${it.ch}:${it.v}" }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                            .clickable { nav.push(Screen.Reader(r.book.id, r.ch, r.v ?: 1)) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("افتح ", "افتح ", "Open "), color = Muted, fontSize = 14.sp)
                        Text(
                            "${r.book.name} ${ArText.num(r.ch)}" + (r.v?.let { ":" + ArText.num(it) } ?: ""),
                            color = Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (tab == 0) {
                    val vs = verses
                    if (vs != null && vs.isEmpty()) item {
                        Text(
                            t(
                                "مفيش آيات؛ البحث بالعربي بيدوّر في فاندايك من غير تشكيل، وبالإنجليزي في ${englishName(prefs.english)}.",
                                "لا توجد آيات؛ البحث بالعربية في فاندايك دون تشكيل، وبالإنجليزية في ${englishName(prefs.english)}.",
                                "No verses. Arabic searches Van Dyke (without vowels); English searches ${englishName(prefs.english)}.",
                            ),
                            color = Muted,
                        )
                    }
                    if (vs != null && total > vs.size) item {
                        Text(
                            t(
                                "بنعرض أول ${ArText.num(vs.size)} آية؛ ضيّق البحث بكلمة زيادة.",
                                "تُعرض أول ${ArText.num(vs.size)} آية؛ أضف كلمة لتضييق البحث.",
                                "Showing the first ${vs.size} verses; add a word to narrow the search.",
                            ),
                            color = Muted, fontSize = 12.sp,
                        )
                    }
                    items(vs.orEmpty(), key = { it.verseId }) { h ->
                        val marked = remember(h.verseId, shown, versesEnglish) {
                            SearchMarks.annotated(h.text, SearchMarks.ranges(h.text, shown, versesEnglish), Gold, Gold.copy(alpha = 0.22f))
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                                .clickable { nav.push(Screen.Reader(h.book, h.ch, h.v)) }.padding(12.dp),
                        ) {
                            Text("${h.bookName} ${ArText.num(h.ch)}:${ArText.num(h.v)}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (versesEnglish) {
                                Text(
                                    marked, fontFamily = FontFamily.Serif, fontSize = 17.sp, color = Ink, lineHeight = 26.sp,
                                    modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Ltr),
                                )
                            } else {
                                Text(
                                    marked, fontFamily = scriptureFamily(), fontSize = 18.sp, color = Ink, lineHeight = 32.sp,
                                    modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Rtl),
                                )
                            }
                        }
                    }
                } else {
                    val ls = lemmas
                    if (ls != null && ls.isEmpty()) item { Text(t("مفيش كلمات مطابقة.", "لا توجد كلمات مطابقة.", "No matching words."), color = Muted) }
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
                                    color = Teal, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Rtl),
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
