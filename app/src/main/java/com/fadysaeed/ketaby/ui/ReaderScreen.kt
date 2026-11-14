package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Book
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.EVerse
import com.fadysaeed.ketaby.data.JUnder
import com.fadysaeed.ketaby.data.Jesuit
import com.fadysaeed.ketaby.data.OWord
import com.fadysaeed.ketaby.data.Token
import com.fadysaeed.ketaby.data.VerseRow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
fun scriptureFamily(): FontFamily = if (LocalPrefs.current.scriptureFont == "plex") Plex else Naskh

fun englishName(tr: String) = if (tr == "bsb") "BSB" else "KJV"

/** One verse as flowing Arabic text; every aligned unit is tappable. */
@Composable
fun VerseText(
    number: Int?,
    tokens: List<Token>,
    selected: Long? = null,
    marked: Set<Long> = emptySet(),
    onTap: (Token) -> Unit,
) {
    val prefs = LocalPrefs.current
    val size = (21 * prefs.textScale).sp
    val text = buildAnnotatedString {
        if (number != null) {
            withStyle(SpanStyle(color = Gold, fontSize = size * 0.62f, fontFamily = Plex, fontWeight = FontWeight.SemiBold)) {
                append(ArText.num(number))
            }
            append("  ")
        }
        for (t in tokens) {
            val s = if (prefs.harakat) t.text else ArText.stripHarakat(t.text)
            if (!t.linked) { append(s); continue }
            val style = when {
                t.id == selected -> SpanStyle(color = Color.Black, background = Gold)
                t.id in marked -> SpanStyle(color = Teal, fontWeight = FontWeight.Bold)
                else -> SpanStyle()
            }
            val link = LinkAnnotation.Clickable(
                tag = "t${t.id}",
                styles = TextLinkStyles(style = style, pressedStyle = SpanStyle(background = Teal.copy(alpha = 0.35f))),
                linkInteractionListener = { onTap(t) },
            )
            withLink(link) { append(s) }
        }
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(
            fontFamily = scriptureFamily(), fontSize = size, lineHeight = 1.9.em, color = Ink,
            textDirection = TextDirection.Rtl,
        ),
    )
}

/** One English verse; the parts tied to original words are tappable. */
@Composable
fun EnglishVerseText(
    number: Int?,
    verse: EVerse,
    selected: Int? = null,
    markedWords: Set<Long> = emptySet(),
    dim: Boolean = false,
    onTap: (text: String, words: List<Long>, start: Int) -> Unit,
) {
    val prefs = LocalPrefs.current
    val size = ((if (dim) 16 else 18) * prefs.textScale).sp
    val text = buildAnnotatedString {
        if (number != null) {
            withStyle(SpanStyle(color = Gold, fontSize = size * 0.7f, fontWeight = FontWeight.SemiBold)) { append("$number") }
            append(" ")
        }
        var pos = 0
        for (sp in verse.spans) {
            if (sp.start < pos || sp.end > verse.text.length) continue
            append(verse.text.substring(pos, sp.start))
            val piece = verse.text.substring(sp.start, sp.end)
            val style = when {
                sp.start == selected -> SpanStyle(color = Color.Black, background = Gold)
                sp.words.any { it in markedWords } -> SpanStyle(color = Teal, fontWeight = FontWeight.Bold)
                else -> SpanStyle()
            }
            val link = LinkAnnotation.Clickable(
                tag = "e${sp.start}",
                styles = TextLinkStyles(style = style, pressedStyle = SpanStyle(background = Teal.copy(alpha = 0.35f))),
                linkInteractionListener = { onTap(piece, sp.words, sp.start) },
            )
            withLink(link) { append(piece) }
            pos = sp.end
        }
        append(verse.text.substring(pos))
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(
            fontFamily = FontFamily.Serif, fontSize = size, lineHeight = 1.55.em,
            color = if (dim) Ink.copy(alpha = 0.78f) else Ink, textDirection = TextDirection.Ltr,
        ),
    )
}

@Composable
private fun MenuItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (checked) Gold else Ink) },
        leadingIcon = { if (checked) Icon(Icons.Filled.Check, null, tint = Gold) else Spacer(Modifier.padding(12.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun ViewMenu(hasJesuit: Boolean, onJesuit: () -> Unit, onHome: () -> Unit) {
    val prefs = LocalPrefs.current
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, t("طريقة العرض", "طريقة العرض", "View"), tint = Ink) }
    KDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        val item: @Composable (String, Boolean, () -> Unit) -> Unit = { label, checked, onClick ->
            MenuItem(label, checked) { onClick(); open = false }
        }
        val en = englishName(prefs.english)
        item(t("فاندايك بس", "فاندايك فقط", "Van Dyke only"), prefs.readerMode == 0) { prefs.putReaderMode(0) }
        item(t("فاندايك وتحته $en", "فاندايك وتحته $en", "Van Dyke with $en"), prefs.readerMode == 1) { prefs.putReaderMode(1) }
        item(t("$en بس", "$en فقط", "$en only"), prefs.readerMode == 2) { prefs.putReaderMode(2) }
        HorizontalDivider(color = Line)
        item("KJV", prefs.english == "kjv") { prefs.putEnglish("kjv") }
        item("BSB", prefs.english == "bsb") { prefs.putEnglish("bsb") }
        HorizontalDivider(color = Line)
        if (hasJesuit) {
            item(t("اليسوعية تحت كل آية", "اليسوعية تحت كل آية", "Jesuit under each verse"), prefs.jesuitUnder) { prefs.putJesuitUnder(!prefs.jesuitUnder) }
            item(t("افتح الحتة دي في اليسوعية", "افتح هذا الموضع في اليسوعية", "Open this passage in the Jesuit"), false) { onJesuit() }
        } else {
            item(t("ضيف الترجمة اليسوعية", "أضف الترجمة اليسوعية", "Add the Jesuit translation"), false) { onJesuit() }
        }
        HorizontalDivider(color = Line)
        item(t("الصفحة الرئيسية (الأسفار)", "الصفحة الرئيسية (الأسفار)", "Home (books)"), false) { onHome() }
        HorizontalDivider(color = Line)
        item(t("بين السطور (الأصل)", "بين السطور (النص الأصلي)", "Interlinear (original)"), prefs.interlinear) { prefs.putInterlinear(!prefs.interlinear) }
    }
}

@Composable
fun ReaderScreen(nav: Nav, screen: Screen.Reader, openSheet: (SheetRequest) -> Unit) {
    val prefs = LocalPrefs.current
    val books by produceState<List<Book>>(emptyList()) { value = Db.books() }
    val book = books.firstOrNull { it.id == screen.book }
    val verses by produceState<List<VerseRow>?>(null, screen.book, screen.ch) { value = Db.chapter(screen.book, screen.ch) }
    val words by produceState<Map<Long, List<OWord>>>(emptyMap(), verses, prefs.interlinear) {
        val vs = verses
        value = if (prefs.interlinear && !vs.isNullOrEmpty()) Db.chapterWords(vs.first().id, vs.last().id) else emptyMap()
    }
    val english by produceState<Map<Long, EVerse>>(emptyMap(), verses, prefs.readerMode, prefs.english) {
        val vs = verses
        value = if (prefs.readerMode != 0 && !vs.isNullOrEmpty()) Db.englishRange(prefs.english, vs.first().id, vs.last().id) else emptyMap()
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val jver = Jesuit.version.intValue
    val hasJesuit = Jesuit.has(ctx)
    val jUnder by produceState<Map<Int, List<JUnder>>>(emptyMap(), screen.book, screen.ch, prefs.jesuitUnder, jver) {
        value = if (prefs.jesuitUnder && hasJesuit) Jesuit.underVd(ctx, screen.book, screen.ch) else emptyMap()
    }
    val jNotes by produceState<Map<String, String>>(emptyMap(), jUnder) {
        value = if (jUnder.isEmpty()) emptyMap() else Jesuit.notes(ctx, jUnder.values.flatten().map { it.book to it.ch })
    }
    var jShown by remember { mutableStateOf<JNote?>(null) }
    var selected by remember(screen) { mutableStateOf<Long?>(null) }
    var selectedEn by remember(screen) { mutableStateOf<Pair<Long, Int>?>(null) }

    val startIndex = remember(screen) {
        when {
            screen.verse > 0 -> screen.verse - 1
            prefs.lastBook == screen.book && prefs.lastChapter == screen.ch -> prefs.lastVerseIndex
            else -> 0
        }
    }
    val listState = remember(screen) { LazyListState(startIndex) }

    // Opening at a verse (from the concordance, search or the picker): the English lines and the
    // interlinear arrive after the Arabic text and make every verse taller. Near the end of a chapter
    // the list cannot put the verse at the top, so it settles on an earlier verse, and once the
    // verses grow that earlier verse stays put and pushes the target off the screen. So jump to the
    // verse again each time the content grows, until the reader touches the list.
    var touched by remember(screen) { mutableStateOf(false) }
    LaunchedEffect(screen) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) touched = true }
    }
    LaunchedEffect(screen, verses, english, words, jUnder) {
        val vs = verses ?: return@LaunchedEffect
        if (screen.verse > 0 && !touched && screen.verse <= vs.size) listState.scrollToItem(screen.verse - 1)
    }

    LaunchedEffect(screen) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { prefs.savePosition(screen.book, screen.ch, it) }
    }
    LaunchedEffect(screen) { prefs.savePosition(screen.book, screen.ch, startIndex) }
    // diagnostics for the smoke test: which verses are on screen right after opening at a verse
    LaunchedEffect(screen, verses) {
        val vs = verses ?: return@LaunchedEffect
        if (screen.verse <= 0) return@LaunchedEffect
        val t0 = System.currentTimeMillis()
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { vs.getOrNull(it.index)?.v } }
            .distinctUntilChanged()
            .collect { if (System.currentTimeMillis() - t0 < 10_000) android.util.Log.d("KetabyReader", "target=${screen.verse} t=${System.currentTimeMillis() - t0} visible=$it") }
    }
    LaunchedEffect(screen.book, screen.ch) { prefs.pushRecent(screen.book, screen.ch) }

    fun go(delta: Int) {
        val b = book ?: return
        var bk = b.id
        var ch = screen.ch + delta
        if (ch < 1) {
            val prev = books.firstOrNull { it.id == bk - 1 } ?: return
            bk = prev.id; ch = prev.chapters
        } else if (ch > b.chapters) {
            val next = books.firstOrNull { it.id == bk + 1 } ?: return
            bk = next.id; ch = 1
        }
        nav.replace(Screen.Reader(bk, ch, 1))
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            KTopBar(
                title = if (book == null) "" else "${book.name} ${ArText.num(screen.ch)} ▾",
                onBack = { nav.pop() },
                onTitle = { nav.push(Screen.Picker(screen.book, screen.ch)) },
            ) {
                SearchSettingsActions(nav)
                ViewMenu(hasJesuit, onJesuit = {
                    if (!hasJesuit) nav.push(Screen.JesuitSetup)
                    else scope.launch {
                        val v = verses?.getOrNull(listState.firstVisibleItemIndex)?.v ?: 1
                        val at = Jesuit.fromVd(ctx, screen.book, screen.ch, v)
                        if (at != null) nav.push(Screen.JReader(at.first, at.second, at.third))
                    }
                }) { nav.home() }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val rtl = !ArText.english
                TextButton(onClick = { go(-1) }) {
                    Text(if (rtl) t("→ اللي فات", "→ السابق", "") else "← Previous", color = Muted)
                }
                TextButton(
                    onClick = { nav.push(Screen.Picker(screen.book, screen.ch)) },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Panel),
                ) {
                    Icon(Icons.Filled.Menu, null, tint = Teal)
                    Spacer(Modifier.width(6.dp))
                    Text(t("الأسفار والإصحاحات", "الأسفار والأصحاحات", "Books & chapters"), color = Teal)
                }
                TextButton(onClick = { go(1) }) {
                    Text(if (rtl) t("الجاي ←", "التالي ←", "") else "Next →", color = Gold)
                }
            }
        },
    ) { pad ->
        val vs = verses
        if (vs == null) {
            Box(Modifier.padding(pad).fillMaxSize())
            return@Scaffold
        }
        val mode = prefs.readerMode
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (prefs.interlinear || mode == 1 || jUnder.isNotEmpty()) 16.dp else 6.dp),
            modifier = Modifier.padding(pad).fillMaxSize().pointerInput(screen, book) {
                // swipe sideways to change chapter (in Arabic the next chapter comes from the left)
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        if (abs(dx) > 160f) {
                            val forward = if (ArText.english) dx < 0 else dx > 0
                            go(if (forward) 1 else -1)
                        }
                    },
                ) { _, amount -> dx += amount }
            },
        ) {
            itemsIndexed(vs, key = { _, v -> v.id }) { _, v ->
                val target = screen.verse > 1 && v.v == screen.verse
                Column(
                    if (target) Modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(horizontal = 8.dp, vertical = 4.dp)
                    else Modifier,
                ) {
                    if (mode != 2) {
                        VerseText(v.v, v.tokens, selected = selected) { tk ->
                            selected = tk.id; selectedEn = null
                            openSheet(SheetRequest(tk.text, tk.id))
                        }
                    }
                    val ev = english[v.id]
                    if (mode != 0 && ev != null) {
                        if (mode == 1) Spacer(Modifier.height(4.dp))
                        EnglishVerseText(
                            if (mode == 2) v.v else null, ev,
                            selected = selectedEn?.takeIf { it.first == v.id }?.second,
                            dim = mode == 1,
                        ) { piece, ids, start ->
                            selectedEn = v.id to start; selected = null
                            openSheet(SheetRequest(piece, null, ids, headerIsEnglish = true))
                        }
                    }
                    val jl = jUnder[v.v]
                    if (jl != null) {
                        Spacer(Modifier.height(4.dp))
                        JesuitUnder(jl, jNotes) { jShown = it }
                    }
                    if (prefs.interlinear) {
                        val ws = words[v.id].orEmpty()
                        if (ws.isNotEmpty()) Interlinear(ws) { w -> openSheet(SheetRequest(null, null, listOf(w.id))) }
                    }
                }
            }
        }
    }
    jShown?.let { JNoteSheet(it) { jShown = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Interlinear(words: List<OWord>, onWord: (OWord) -> Unit) {
    val prefs = LocalPrefs.current
    val greek = words.first().lang == "grc"
    val first = words.first()
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Panel).padding(10.dp),
    ) {
        val ref = "${ArText.num(first.oc)}:${ArText.num(first.ov)}"
        val label = when {
            greek -> t("اليوناني", "اليونانية", "Greek")
            first.lang == "arc" -> t("الآرامي", "الآرامية", "Aramaic")
            else -> t("العبري", "العبرية", "Hebrew")
        }
        Text("$label · $ref", color = Muted, fontSize = 11.sp)
        CompositionLocalProvider(LocalLayoutDirection provides if (greek) LayoutDirection.Ltr else LayoutDirection.Rtl) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                for (w in words) WordCell(w, prefs.greekMode, prefs.cantillation, prefs.textScale) { onWord(w) }
            }
        }
    }
}

@Composable
private fun WordCell(w: OWord, greekMode: Int, cantillation: Boolean, scale: Float, onClick: () -> Unit) {
    val greek = w.lang == "grc"
    val inMain = !greek || (w.editions?.contains('N') ?: true)
    val original = (if (greek || cantillation) w.text else ArText.stripCantillation(w.text)).replace("¶", "")
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }.background(Color.Black).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            original,
            fontFamily = if (greek) GreekFont else HebrewFont,
            fontSize = ((if (greek) 19 else 22) * scale).sp,
            color = if (inMain) Gold else Muted,
        )
        val trs = if (!greek) listOfNotNull(w.tr) else when (greekMode) {
            1 -> listOfNotNull(w.tr)
            2 -> listOfNotNull(w.tr2)
            else -> listOfNotNull(w.tr, w.tr2).distinct()
        }
        for (tx in trs) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(tx, fontFamily = Plex, fontSize = (13 * scale).sp, color = Teal)
            }
        }
        if (!w.gloss.isNullOrBlank()) {
            Text(w.gloss, fontSize = (11 * scale).sp, color = Muted, maxLines = 2, style = TextStyle(textDirection = TextDirection.Ltr))
        }
    }
}
