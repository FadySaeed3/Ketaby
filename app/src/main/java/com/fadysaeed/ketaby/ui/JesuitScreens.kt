package com.fadysaeed.ketaby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.JBook
import com.fadysaeed.ketaby.data.JItem
import com.fadysaeed.ketaby.data.JUnder
import com.fadysaeed.ketaby.data.Jesuit
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A note or a set of margin references shown in the bottom panel. */
data class JNote(val title: String, val body: String)

/**
 * Appends Jesuit text, turning the U+E000 n U+E001 markers into small tappable note numbers.
 * [noteKey] gives the key the note is stored under ("book:ch:n").
 */
fun AnnotatedString.Builder.appendJesuit(
    raw: String, harakat: Boolean, size: androidx.compose.ui.unit.TextUnit,
    noteKey: (Int) -> String, onNote: (Int, String) -> Unit,
) {
    var i = 0
    while (i < raw.length) {
        val a = raw.indexOf(Jesuit.MARK_L, i)
        if (a < 0) { append(if (harakat) raw.substring(i) else ArText.stripHarakat(raw.substring(i))); break }
        append(if (harakat) raw.substring(i, a) else ArText.stripHarakat(raw.substring(i, a)))
        val b = raw.indexOf(Jesuit.MARK_R, a)
        if (b < 0) break
        val n = raw.substring(a + 1, b).toIntOrNull()
        if (n != null) {
            val key = noteKey(n)
            val link = LinkAnnotation.Clickable(
                tag = "n$key",
                styles = TextLinkStyles(
                    style = SpanStyle(color = Teal, fontSize = size * 0.6f, fontFamily = Plex, fontWeight = FontWeight.SemiBold, baselineShift = BaselineShift(0.45f)),
                    pressedStyle = SpanStyle(background = Teal.copy(alpha = 0.35f)),
                ),
                linkInteractionListener = { onNote(n, key) },
            )
            withLink(link) { append(" (" + ArText.num(n) + ")") }
        }
        i = b + 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JNoteSheet(note: JNote, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text(note.title, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                note.body, color = Ink, fontSize = 16.sp, lineHeight = 29.sp, fontFamily = Plex,
                modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Rtl),
            )
        }
    }
}

private fun noteTitle(n: Int) = t("حاشية", "حاشية", "Note") + " " + ArText.num(n)

// ------------------------------------------------------------------ setup (import)

@Composable
fun JesuitSetupScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val ver = Jesuit.version.intValue
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val has = Jesuit.has(ctx)
    val notes by produceState<String?>(null, ver) { value = if (has) Jesuit.meta(ctx, "notes") else null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; error = null; progress = 0f
            scope.launch {
                error = try {
                    Jesuit.import(ctx, uri) { progress = it }?.let {
                        t("الملف ده مش ملف اليسوعية بتاع كتابي", "هذا الملف ليس ملف الترجمة اليسوعية الخاص بكتابي", "This is not a Ketaby Jesuit package")
                    }
                } catch (e: Exception) {
                    e.toString()
                }
                busy = false
            }
        }
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar(t("الترجمة اليسوعية", "الترجمة اليسوعية", "Jesuit translation"), onBack = { nav.pop() }) { HomeAction(nav) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ExplainText(
                t(
                    "الترجمة دي ليها حقوق نشر، فمش جوه التطبيق. اختار ملف jesuit.ketaby مرة واحدة من الموبايل، والتطبيق هياخد منه نسخة في مساحته الخاصة ويفتحها من غير نت: النص كامل بتشكيله وعناوينه ومقدمات الأسفار والحواشي.",
                    "لهذه الترجمة حقوق نشر، فليست داخل التطبيق. اختر ملف jesuit.ketaby مرة واحدة من جهازك، فيحتفظ التطبيق بنسخة في مساحته الخاصة ويفتحها دون اتصال: النص كاملًا بتشكيله وعناوينه ومقدمات الأسفار والحواشي.",
                    "This translation is under copyright, so it is not inside the app. Pick the jesuit.ketaby file once; the app keeps a private copy and opens it offline: the full text with vowels, headings, book introductions and notes.",
                ),
                if (ArText.english) "en" else "ar", size = 14, color = Muted,
            )
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                Text(t("اليسوعية (دار المشرق)", "اليسوعية (دار المشرق)", "Jesuit (Dar el-Machreq)"), color = Gold, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                val (status, color) = when {
                    busy -> t("بننسخ الملف…", "جارٍ نسخ الملف…", "Copying…") to Teal
                    !has -> t("لسه ماضفتش الملف", "لم يُضف الملف بعد", "Not added yet") to Muted
                    else -> (t("موجودة ✓", "موجودة ✓", "Added ✓") + (notes?.let { " · " + t("حواشي", "حواشٍ", "notes") + " " + ArText.num(it) } ?: "")) to Teal
                }
                Text(status, color = color, fontSize = 14.sp)
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = PanelHi)
                }
                error?.let { Text(it, color = Rose, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { launcher.launch(arrayOf("*/*")) }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    ) { Text(if (has) t("بدّل الملف", "استبدل الملف", "Replace file") else t("اختار الملف", "اختر الملف", "Choose file")) }
                    if (has && !busy) {
                        OutlinedButton(onClick = { nav.push(Screen.JBooks) }) { Text(t("افتحها", "افتحها", "Open"), color = Gold) }
                        OutlinedButton(onClick = { Jesuit.delete(ctx) }) { Text(t("امسحها", "احذفها", "Remove"), color = Muted) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ books

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JBooksScreen(nav: Nav) {
    val ctx = LocalContext.current
    val books by produceState<List<JBook>>(emptyList()) { value = Jesuit.books(ctx) }
    var open by rememberSaveable { mutableStateOf(-1) }
    var testament by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar(t("اليسوعية", "اليسوعية", "Jesuit"), onBack = { nav.pop() }) { SearchSettingsActions(nav) } },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(4.dp)) {
                    for ((i, label) in listOf(t("العهد القديم", "العهد القديم", "Old Testament"), t("العهد الجديد", "العهد الجديد", "New Testament")).withIndex()) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (testament == i) Gold else Color.Transparent)
                                .clickable { testament = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (testament == i) Color.Black else Ink, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            items(books.filter { it.testament == if (testament == 0) "OT" else "NT" }, key = { it.id }) { b ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (open == b.id) -1 else b.id }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.short, color = Ink, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (b.vdBook == null) Text(t("مش في فاندايك", "ليس في فاندايك", "not in Van Dyke"), color = Muted, fontSize = 12.sp)
                        if (b.notes > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(t("بحواشي", "بحواشٍ", "notes"), color = Teal, fontSize = 12.sp)
                        }
                    }
                    if (open == b.id) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(PanelHi).clickable { nav.push(Screen.JIntro(b.id)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text(t("المقدمة", "المقدمة", "Introduction"), color = Gold, fontSize = 14.sp) }
                            for (c in 1..b.chapters) {
                                Box(
                                    Modifier.width(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black).clickable { nav.push(Screen.JReader(b.id, c, 0)) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(ArText.num(c), color = Ink, fontSize = 15.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ introduction

@Composable
fun JIntroScreen(nav: Nav, book: Int) {
    val ctx = LocalContext.current
    val prefs = LocalPrefs.current
    val books by produceState<List<JBook>>(emptyList()) { value = Jesuit.books(ctx) }
    val paras by produceState<List<Pair<String, String>>?>(null, book) { value = Jesuit.intro(ctx, book) }
    val b = books.firstOrNull { it.id == book }
    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar((b?.short ?: "") + " · " + t("المقدمة", "المقدمة", "Introduction"), onBack = { nav.pop() }) { HomeAction(nav) } },
    ) { pad ->
        val ps = paras ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            if (ps.isEmpty()) item { Text(t("السفر ده مالوش مقدمة في الملف.", "لا مقدمة لهذا السفر في الملف.", "No introduction for this book."), color = Muted) }
            items(ps) { (tag, text) ->
                val heading = tag.startsWith("h")
                Text(
                    if (prefs.harakat) text else ArText.stripHarakat(text),
                    color = if (heading) Gold else Ink, fontSize = if (heading) 18.sp else (16 * prefs.textScale).sp,
                    fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal, lineHeight = if (heading) 28.sp else (16 * prefs.textScale * 1.8f).sp,
                    fontFamily = Plex, modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Rtl),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ reader

private sealed interface JBlock {
    data class Head(val text: String, val lvl: String) : JBlock
    data class Para(val verses: List<JItem>) : JBlock
}

private fun blocks(items: List<JItem>): List<JBlock> {
    val out = ArrayList<JBlock>()
    var cur = ArrayList<JItem>()
    fun flush() { if (cur.isNotEmpty()) { out.add(JBlock.Para(cur)); cur = ArrayList() } }
    for (it in items) {
        if (it.kind == 'h') { flush(); out.add(JBlock.Head(it.text, it.lvl ?: "")); continue }
        if (it.para) flush()
        cur.add(it)
    }
    flush()
    return out
}

@Composable
fun JReaderScreen(nav: Nav, s: Screen.JReader) {
    val ctx = LocalContext.current
    val prefs = LocalPrefs.current
    val books by produceState<List<JBook>>(emptyList()) { value = Jesuit.books(ctx) }
    val book = books.firstOrNull { it.id == s.book }
    val items by produceState<List<JItem>?>(null, s.book, s.ch) { value = Jesuit.chapter(ctx, s.book, s.ch) }
    val notes by produceState<Map<String, String>>(emptyMap(), s.book, s.ch) { value = Jesuit.notes(ctx, listOf(s.book to s.ch)) }
    val margin by produceState<Map<String, List<String>>>(emptyMap(), s.book, s.ch) { value = Jesuit.margin(ctx, s.book, s.ch) }
    var shown by remember { mutableStateOf<JNote?>(null) }
    val scope = rememberCoroutineScope()
    val bl = remember(items) { items?.let { blocks(it) } }

    val listState = remember(s) { LazyListState(0) }
    var touched by remember(s) { mutableStateOf(false) }
    LaunchedEffect(s) { listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) touched = true } }
    LaunchedEffect(s, bl) {
        val b = bl ?: return@LaunchedEffect
        if (s.verse <= 1 || touched) return@LaunchedEffect
        val idx = b.indexOfFirst { it is JBlock.Para && it.verses.any { v -> v.v == s.verse } }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    fun go(delta: Int) {
        val b = book ?: return
        var bk = b.id
        var ch = s.ch + delta
        if (ch < 1) { val p = books.firstOrNull { it.id == bk - 1 } ?: return; bk = p.id; ch = p.chapters }
        else if (ch > b.chapters) { val n = books.firstOrNull { it.id == bk + 1 } ?: return; bk = n.id; ch = 1 }
        nav.replace(Screen.JReader(bk, ch, 0))
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            KTopBar(
                title = if (book == null) "" else "${book.short} ${ArText.num(s.ch)} ▾",
                onBack = { nav.pop() },
                onTitle = { nav.push(Screen.JBooks) },
            ) {
                HomeAction(nav)
                IconButton(onClick = { nav.push(Screen.JIntro(s.book)) }) { Icon(Icons.Filled.Info, t("مقدمة السفر", "مقدمة السفر", "Introduction"), tint = Ink) }
                if (book?.vdBook != null) TextButton(onClick = {
                    scope.launch {
                        val firstV = (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0).let { i ->
                            (bl?.getOrNull(i) as? JBlock.Para)?.verses?.firstOrNull()?.v ?: 1
                        }
                        val at = Jesuit.toVd(ctx, s.book, s.ch, firstV)
                        if (at != null) nav.push(Screen.Reader(at.first, at.second, at.third))
                    }
                }) { Text(t("فاندايك", "فاندايك", "Van Dyke"), color = Teal) }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val rtl = !ArText.english
                TextButton(onClick = { go(-1) }) { Text(if (rtl) t("→ اللي فات", "→ السابق", "") else "← Previous", color = Muted) }
                TextButton(onClick = { nav.push(Screen.JBooks) }, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Panel)) {
                    Icon(Icons.Filled.Menu, null, tint = Teal)
                    Spacer(Modifier.width(6.dp))
                    Text(t("أسفار اليسوعية", "أسفار اليسوعية", "Jesuit books"), color = Teal)
                }
                TextButton(onClick = { go(1) }) { Text(if (rtl) t("الجاي ←", "التالي ←", "") else "Next →", color = Gold) }
            }
        },
    ) { pad ->
        val b = bl
        if (b == null) { Box(Modifier.padding(pad).fillMaxSize()); return@Scaffold }
        val size = (21 * prefs.textScale).sp
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).fillMaxSize().pointerInput(s, book) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (abs(dx) > 160f) go(if (if (ArText.english) dx < 0 else dx > 0) 1 else -1) },
                ) { _, amount -> dx += amount }
            },
        ) {
            itemsIndexed(b) { _, blk ->
                when (blk) {
                    is JBlock.Head -> {
                        val big = blk.lvl.startsWith("h1")
                        val text = buildAnnotatedString {
                            appendJesuit(blk.text, prefs.harakat, 18.sp, { n -> "${s.book}:${s.ch}:$n" }) { n, key ->
                                shown = JNote(noteTitle(n), notes[key] ?: t("الحاشية دي لسه مااتقرتش من الكتاب.", "لم تُنقل هذه الحاشية بعد.", "This note has not been added yet."))
                            }
                        }
                        Text(
                            text, color = if (big) Gold else Teal, fontSize = if (big) 20.sp else 17.sp, fontWeight = FontWeight.SemiBold,
                            fontFamily = Plex, modifier = Modifier.fillMaxWidth().padding(top = if (big) 10.dp else 4.dp),
                            textAlign = if (blk.lvl.endsWith("c") || big) TextAlign.Center else TextAlign.Start,
                            style = TextStyle(textDirection = TextDirection.Rtl),
                        )
                    }
                    is JBlock.Para -> {
                        val text = buildAnnotatedString {
                            blk.verses.forEachIndexed { k, v ->
                                if (k > 0) append(if (v.text.contains('\n') || blk.verses[k - 1].text.contains('\n')) "\n" else " ")
                                val refs = margin[v.v.toString() + v.sub]
                                val numStyle = SpanStyle(color = Gold, fontSize = size * 0.62f, fontFamily = Plex, fontWeight = FontWeight.SemiBold)
                                if (refs != null) {
                                    val link = LinkAnnotation.Clickable(
                                        tag = "m${v.v}${v.sub}",
                                        styles = TextLinkStyles(style = numStyle.copy(background = Teal.copy(alpha = 0.18f))),
                                        linkInteractionListener = {
                                            shown = JNote(t("شواهد الآية", "شواهد الآية", "References for verse") + " " + v.label, refs.joinToString("\n"))
                                        },
                                    )
                                    withLink(link) { append(v.label) }
                                } else withStyle(numStyle) { append(v.label) }
                                append(" ")
                                appendJesuit(v.text, prefs.harakat, size, { n -> "${s.book}:${s.ch}:$n" }) { n, key ->
                                    shown = JNote(noteTitle(n), notes[key] ?: t("الحاشية دي لسه مااتقرتش من الكتاب.", "لم تُنقل هذه الحاشية بعد.", "This note has not been added yet."))
                                }
                            }
                        }
                        val target = s.verse > 1 && blk.verses.any { it.v == s.verse }
                        Text(
                            text,
                            modifier = Modifier.fillMaxWidth().then(
                                if (target) Modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(horizontal = 8.dp, vertical = 4.dp) else Modifier,
                            ),
                            style = TextStyle(fontFamily = scriptureFamily(), fontSize = size, lineHeight = 1.9.em, color = Ink, textDirection = TextDirection.Rtl),
                        )
                    }
                }
            }
        }
    }
    shown?.let { JNoteSheet(it) { shown = null } }
}

// ------------------------------------------------------------------ under Van Dyke

/** The Jesuit verses that belong under one Van Dyke verse, smaller and dimmer, notes tappable. */
@Composable
fun JesuitUnder(list: List<JUnder>, notes: Map<String, String>, onNote: (JNote) -> Unit) {
    val prefs = LocalPrefs.current
    val size = (17 * prefs.textScale).sp
    val text = buildAnnotatedString {
        list.forEachIndexed { k, j ->
            if (k > 0) append(" ")
            withStyle(SpanStyle(color = Teal, fontSize = size * 0.65f, fontFamily = Plex)) {
                append(ArText.num(j.ch) + ":" + ArText.num(j.v) + j.sub)
            }
            append(" ")
            appendJesuit(j.text, prefs.harakat, size, { n -> "${j.book}:${j.ch}:$n" }) { n, key ->
                onNote(JNote(noteTitle(n), notes[key] ?: t("الحاشية دي لسه مااتقرتش من الكتاب.", "لم تُنقل هذه الحاشية بعد.", "This note has not been added yet.")))
            }
        }
    }
    Text(
        text, modifier = Modifier.fillMaxWidth(),
        style = TextStyle(fontFamily = scriptureFamily(), fontSize = size, lineHeight = 1.8.em, color = Ink.copy(alpha = 0.8f), textDirection = TextDirection.Rtl),
    )
}
