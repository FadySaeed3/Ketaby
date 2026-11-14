package com.fadysaeed.ketaby.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.Jesuit
import com.fadysaeed.ketaby.data.Prefs

sealed interface Screen {
    data object Books : Screen
    data class Chapters(val book: Int) : Screen
    /** verse = verse number to open at; 0 = restore the saved scroll position */
    data class Reader(val book: Int, val ch: Int, val verse: Int = 0) : Screen
    data class Concordance(val dstrong: String) : Screen
    data object Search : Screen
    data object Settings : Screen
    /** book → chapter → verse picker, opened from the reader */
    data class Picker(val book: Int?, val ch: Int?) : Screen
    data object Lexicons : Screen
    /** The owner's HALOT/BDAG PDF opened on an entry (1-based page, y in PDF points). */
    data class Lex(val lex: String, val page: Int, val y: Float, val n: Int?) : Screen
    /** Jesuit translation: import screen, books, reader (book = Jesuit book id), book introduction. */
    data object JesuitSetup : Screen
    data object JBooks : Screen
    data class JReader(val book: Int, val ch: Int, val verse: Int = 0) : Screen
    data class JIntro(val book: Int) : Screen
}

/** What the word panel shows: the words aligned to one Arabic unit, or explicit original words. */
data class SheetRequest(val header: String?, val tokenId: Long?, val wordIds: List<Long> = emptyList(), val headerIsEnglish: Boolean = false)

class Nav(start: List<Screen>) {
    val stack = mutableStateListOf<Screen>().apply { addAll(start) }
    val top: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Back to the books list (the home screen). */
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

/** Interface text in the chosen language: Egyptian, Modern Standard Arabic, or English. */
fun t(eg: String, msa: String, en: String): String = when (ArText.ui) { "msa" -> msa; "en" -> en; else -> eg }

@Composable
fun App(sheetStrong: String? = null) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx.applicationContext) }
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failure by remember { mutableStateOf<String?>(null) }
    val nav = remember { Nav(listOf(Screen.Books, Screen.Reader(prefs.lastBook, prefs.lastChapter, 0))) }

    LaunchedEffect(Unit) {
        try {
            Db.open(ctx.applicationContext) { progress = it }
            ready = true
        } catch (e: Throwable) {
            failure = e.toString()
        }
    }

    val lang = prefs.uiLang
    ArText.ui = lang
    ArText.english = lang == "en"
    // Rebuild the whole interface when its language changes (navigation state is kept).
    key(lang) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (lang == "en") LayoutDirection.Ltr else LayoutDirection.Rtl,
            LocalPrefs provides prefs,
        ) {
            KetabyTheme {
                if (!ready) Splash(progress, failure) else Main(prefs, nav, sheetStrong)
            }
        }
    }
}

@Composable
private fun Splash(progress: Float, failure: String?) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t("كتابي", "كتابي", "Ketaby"), color = Gold, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        if (failure != null) {
            Text(t("حصلت مشكلة وإحنا بنجهز الكتاب:", "حدثت مشكلة أثناء تجهيز الكتاب:", "Something went wrong while preparing the Bible:"), color = Rose)
            Text(failure, color = Muted, fontSize = 12.sp)
        } else {
            Text(t("بنجهز الكتاب على الموبايل أول مرة بس…", "يجري تجهيز الكتاب على الجهاز للمرة الأولى فقط…", "Preparing the Bible on this phone (first run only)…"), color = Teal)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = PanelHi)
        }
    }
}

@Composable
private fun Main(prefs: Prefs, nav: Nav, sheetStrong: String?) {
    val ctxMain = LocalContext.current
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    LaunchedEffect(sheetStrong) {
        val lexStart = sheetStrong?.let { Regex("lex:(halot|bdag):(\\d+)").matchEntire(it) }
        val readAt = sheetStrong?.let { Regex("read:(\\d+):(\\d+):(\\d+)").matchEntire(it) }
        if (readAt != null) {
            // "read:40:7:28" opens the reader at a verse (emulator smoke test)
            val (b, c, v) = readAt.destructured
            nav.push(Screen.Reader(b.toInt(), c.toInt(), v.toInt()))
        } else if (sheetStrong?.startsWith("jread:") == true) {
            // "jread:1:1:3" opens the Jesuit reader (emulator smoke test)
            val p = sheetStrong.removePrefix("jread:").split(':').map { it.toInt() }
            nav.push(Screen.JReader(p[0], p[1], p[2]))
        } else if (sheetStrong?.startsWith("search:") == true) {
            // "search:<0|1|2>:<url-encoded query>" opens the search and runs it (emulator smoke test)
            val p = sheetStrong.removePrefix("search:").split(':', limit = 2)
            startSearch(java.net.URLDecoder.decode(p.getOrElse(1) { "" }, "UTF-8"), p[0].toIntOrNull() ?: 0)
            nav.push(Screen.Search)
        } else if (sheetStrong == "jesuit") {
            // "jesuit" opens the Jesuit translation screen (emulator smoke test)
            nav.push(if (Jesuit.has(ctxMain)) Screen.JBooks else Screen.JesuitSetup)
        } else if (sheetStrong?.startsWith("conc:") == true) {
            // "conc:G3056" opens the concordance of a lemma (emulator smoke test)
            nav.push(Screen.Concordance(sheetStrong.removePrefix("conc:")))
        } else if (lexStart != null) {
            // "lex:halot:1200" opens the lexicon viewer on a page (emulator smoke test)
            nav.push(Screen.Lex(lexStart.groupValues[1], lexStart.groupValues[2].toInt(), 120f, null))
        } else if (sheetStrong != null) {
            Db.firstWordOfLemma(sheetStrong)?.let { sheet = SheetRequest(null, null, listOf(it)) }
        }
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    val openSheet: (SheetRequest) -> Unit = { sheet = it }
    when (val s = nav.top) {
        is Screen.Books -> HomeScreen(nav)
        is Screen.Chapters -> ChaptersScreen(nav, s.book)
        is Screen.Reader -> ReaderScreen(nav, s, openSheet)
        is Screen.Concordance -> ConcordanceScreen(nav, s.dstrong)
        is Screen.Search -> SearchScreen(nav)
        is Screen.Settings -> SettingsScreen(nav)
        is Screen.Picker -> PickerScreen(nav, s.book, s.ch)
        is Screen.Lexicons -> LexiconsScreen(nav)
        is Screen.Lex -> LexViewerScreen(nav, s)
        is Screen.JesuitSetup -> JesuitSetupScreen(nav)
        is Screen.JBooks -> if (Jesuit.version.intValue >= 0 && Jesuit.has(ctxMain)) JBooksScreen(nav) else JesuitSetupScreen(nav)
        is Screen.JReader -> JReaderScreen(nav, s)
        is Screen.JIntro -> JIntroScreen(nav, s.book)
    }

    sheet?.let { req ->
        WordSheet(
            req = req,
            onDismiss = { sheet = null },
            onConcordance = { ds -> sheet = null; nav.push(Screen.Concordance(ds)) },
            onOpenLex = { lex, page, y, n -> sheet = null; nav.push(Screen.Lex(lex, page, y, n)) },
        )
    }
}
