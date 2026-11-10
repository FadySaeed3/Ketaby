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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.fadysaeed.ketaby.data.OWord
import com.fadysaeed.ketaby.data.Token
import com.fadysaeed.ketaby.data.VerseRow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun scriptureFamily(): FontFamily = if (LocalPrefs.current.scriptureFont == "plex") Plex else Naskh

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
        style = TextStyle(
            fontFamily = scriptureFamily(), fontSize = size, lineHeight = 1.9.em, color = Ink,
            textDirection = TextDirection.Rtl,
        ),
    )
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
    var selected by remember(screen) { mutableStateOf<Long?>(null) }

    val startIndex = remember(screen) {
        when {
            screen.verse > 0 -> screen.verse - 1
            prefs.lastBook == screen.book && prefs.lastChapter == screen.ch -> prefs.lastVerseIndex
            else -> 0
        }
    }
    val listState = remember(screen) { LazyListState(startIndex) }

    LaunchedEffect(screen) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { prefs.savePosition(screen.book, screen.ch, it) }
    }
    LaunchedEffect(screen) { prefs.savePosition(screen.book, screen.ch, startIndex) }

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
                title = if (book == null) "" else "${book.name} ${ArText.num(screen.ch)}",
                onBack = { nav.pop() },
                onTitle = { nav.push(Screen.Chapters(screen.book)) },
            ) {
                TextButton(onClick = { prefs.putInterlinear(!prefs.interlinear) }) {
                    Text("بين السطور", color = if (prefs.interlinear) Teal else Muted, fontSize = 13.sp)
                }
                SearchSettingsActions(nav)
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { go(-1) }) { Text("→ الإصحاح اللي فات", color = Muted) }
                TextButton(onClick = { go(1) }) { Text("الإصحاح الجاي ←", color = Gold) }
            }
        },
    ) { pad ->
        val vs = verses
        if (vs == null) {
            Box(Modifier.padding(pad).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (prefs.interlinear) 18.dp else 6.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            itemsIndexed(vs, key = { _, v -> v.id }) { _, v ->
                Column {
                    VerseText(v.v, v.tokens, selected = selected) { t ->
                        selected = t.id
                        openSheet(SheetRequest(t.text, t.id))
                    }
                    if (prefs.interlinear) {
                        val ws = words[v.id].orEmpty()
                        if (ws.isNotEmpty()) Interlinear(v, ws) { w -> openSheet(SheetRequest(null, null, listOf(w.id))) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Interlinear(verse: VerseRow, words: List<OWord>, onWord: (OWord) -> Unit) {
    val prefs = LocalPrefs.current
    val greek = words.first().lang == "grc"
    val first = words.first()
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Panel).padding(10.dp),
    ) {
        val ref = "${ArText.num(first.oc)}:${ArText.num(first.ov)}"
        Text(
            (if (greek) "اليوناني" else if (first.lang == "arc") "الآرامي" else "العبري") + " · $ref",
            color = Muted, fontSize = 11.sp,
        )
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
    val original = if (greek || cantillation) w.text else ArText.stripCantillation(w.text)
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
        for (t in trs) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(t, fontFamily = Plex, fontSize = (13 * scale).sp, color = Teal)
            }
        }
        if (!w.gloss.isNullOrBlank()) {
            Text(w.gloss, fontSize = (11 * scale).sp, color = Muted, maxLines = 2)
        }
    }
}
