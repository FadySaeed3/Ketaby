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
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.Prefs

sealed interface Screen {
    data object Books : Screen
    data class Chapters(val book: Int) : Screen
    /** verse = verse number to open at; 0 = restore the saved scroll position */
    data class Reader(val book: Int, val ch: Int, val verse: Int = 0) : Screen
    data class Concordance(val dstrong: String) : Screen
    data object Search : Screen
    data object Settings : Screen
}

/** What the word panel shows: the words aligned to one Arabic unit, or explicit original words. */
data class SheetRequest(val arabic: String?, val tokenId: Long?, val wordIds: List<Long> = emptyList())

class Nav(start: List<Screen>) {
    val stack = mutableStateListOf<Screen>().apply { addAll(start) }
    val top: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Go to a screen already in the stack (or push it). */
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx.applicationContext) }
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            Db.open(ctx.applicationContext) { progress = it }
            ready = true
        } catch (e: Throwable) {
            failure = e.toString()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalPrefs provides prefs) {
        KetabyTheme {
            if (!ready) Splash(progress, failure) else Main(prefs)
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
        Text("كتابي", color = Gold, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        if (failure != null) {
            Text("حصلت مشكلة وإحنا بنجهز الكتاب:", color = Rose)
            Text(failure, color = Muted, fontSize = 12.sp)
        } else {
            Text("بنجهز الكتاب على الموبايل أول مرة بس…", color = Teal)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = PanelHi)
        }
    }
}

@Composable
private fun Main(prefs: Prefs) {
    val nav = remember {
        Nav(listOf(Screen.Books, Screen.Reader(prefs.lastBook, prefs.lastChapter, 0)))
    }
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    val openSheet: (SheetRequest) -> Unit = { sheet = it }
    when (val s = nav.top) {
        is Screen.Books -> BooksScreen(nav)
        is Screen.Chapters -> ChaptersScreen(nav, s.book)
        is Screen.Reader -> ReaderScreen(nav, s, openSheet)
        is Screen.Concordance -> ConcordanceScreen(nav, s.dstrong)
        is Screen.Search -> SearchScreen(nav)
        is Screen.Settings -> SettingsScreen(nav)
    }

    sheet?.let { req ->
        WordSheet(
            req = req,
            onDismiss = { sheet = null },
            onConcordance = { ds -> sheet = null; nav.push(Screen.Concordance(ds)) },
        )
    }
}
