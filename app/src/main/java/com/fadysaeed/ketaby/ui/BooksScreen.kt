package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.R
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Book
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.Jesuit
import com.fadysaeed.ketaby.data.Lexicons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KTopBar(
    title: String,
    onBack: (() -> Unit)?,
    onTitle: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title, color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (onTitle != null) Modifier.clickable { onTitle() } else Modifier,
            )
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("رجوع", "رجوع", "Back"), tint = Ink)
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, scrolledContainerColor = Color.Black),
    )
}

/** The home button shown on every screen except home itself. */
@Composable
fun HomeAction(nav: Nav) {
    IconButton(onClick = { nav.home() }) { Icon(Icons.Filled.Home, t("الرئيسية", "الرئيسية", "Home"), tint = Ink) }
}

@Composable
fun SearchSettingsActions(nav: Nav, home: Boolean = true) {
    if (home) HomeAction(nav)
    IconButton(onClick = { nav.push(Screen.Search) }) { Icon(Icons.Filled.Search, t("بحث", "بحث", "Search"), tint = Ink) }
    IconButton(onClick = { nav.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, t("الإعدادات", "الإعدادات", "Settings"), tint = Ink) }
}

/** Groups of books, the way the Bible is usually divided. */
private val SECTIONS = listOf(
    Triple(Triple("أسفار موسى الخمسة", "أسفار موسى الخمسة", "The Law"), 1..5, "OT"),
    Triple(Triple("الأسفار التاريخية", "الأسفار التاريخية", "History"), 6..17, "OT"),
    Triple(Triple("الشعر والحكمة", "أسفار الشعر والحكمة", "Poetry & Wisdom"), 18..22, "OT"),
    Triple(Triple("الأنبيا الكبار", "الأنبياء الكبار", "Major Prophets"), 23..27, "OT"),
    Triple(Triple("الأنبيا الصغار", "الأنبياء الصغار", "Minor Prophets"), 28..39, "OT"),
    Triple(Triple("الأناجيل", "الأناجيل", "Gospels"), 40..43, "NT"),
    Triple(Triple("أعمال الرسل", "أعمال الرسل", "Acts"), 44..44, "NT"),
    Triple(Triple("رسايل بولس", "رسائل بولس", "Paul's Letters"), 45..58, "NT"),
    Triple(Triple("الرسايل العامة", "الرسائل الجامعة", "General Letters"), 59..65, "NT"),
    Triple(Triple("الرؤيا", "الرؤيا", "Revelation"), 66..66, "NT"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: Nav) {
    val prefs = LocalPrefs.current
    val ctx = LocalContext.current
    val books by produceState<List<Book>>(emptyList()) { value = Db.books() }
    val snippet by produceState<String?>(null, prefs.lastBook, prefs.lastChapter, prefs.lastVerseIndex) {
        value = Db.verseSnippet(prefs.lastBook, prefs.lastChapter, prefs.lastVerseIndex)
    }
    var testament by rememberSaveable { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val lv = Lexicons.version.intValue
    val byId = books.associateBy { it.id }

    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar(t("كتابي", "كتابي", "Ketaby"), onBack = null) { SearchSettingsActions(nav, home = false) } },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            // ---- mark + search field
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Color.Black)) {
                        // the icon's visible circle is the middle 72 of its 108 units
                        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize().scale(1.5f))
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.5f))
                    }
                    Spacer(Modifier.width(12.dp))
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Panel)
                            .border(BorderStroke(1.dp, Line), RoundedCornerShape(24.dp))
                            .clickable { nav.push(Screen.Search) }.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Muted)
                        Spacer(Modifier.width(8.dp))
                        Text(t("دوّر، أو اكتب شاهد زي يوحنا 3:16", "ابحث، أو اكتب شاهدًا مثل يوحنا 3:16", "Search, or type John 3:16"), color = Muted, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
            // ---- continue reading
            byId[prefs.lastBook]?.let { last ->
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                            .border(BorderStroke(1.dp, Gold.copy(alpha = 0.55f)), RoundedCornerShape(16.dp))
                            .clickable { nav.push(Screen.Reader(last.id, prefs.lastChapter, 0)) }
                            .padding(16.dp),
                    ) {
                        Text(t("كمّل من آخر مكان", "تابع من حيث توقفت", "Continue reading"), color = Muted, fontSize = 13.sp)
                        Text("${last.name} ${ArText.num(prefs.lastChapter)}", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        snippet?.let {
                            Text(
                                if (prefs.harakat) it else ArText.stripHarakat(it), color = Ink, fontSize = 16.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, fontFamily = scriptureFamily(), lineHeight = 28.sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), style = TextStyle(textDirection = TextDirection.Rtl),
                            )
                        }
                    }
                }
            }
            // ---- recent places
            val recent = prefs.recentList().filter { (b, c) -> !(b == prefs.lastBook && c == prefs.lastChapter) && byId[b] != null }
            if (recent.isNotEmpty()) item {
                Column {
                    Text(t("قريت قريب", "قرأت مؤخرًا", "Recently read"), color = Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((b, c) in recent) Chip("${byId[b]!!.name} ${ArText.num(c)}") { nav.push(Screen.Reader(b, c, 0)) }
                    }
                }
            }
            // ---- tools
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val lexReady = Lexicons.has(ctx, Lexicons.HALOT) && Lexicons.has(ctx, Lexicons.BDAG)
                    Tool(
                        Modifier.weight(1f), t("قواميسك", "معاجمك", "Lexicons"),
                        if (lexReady) t("هالوت وباور جاهزين", "HALOT وBDAG جاهزان", "HALOT & BDAG ready") else t("ضيف هالوت وباور", "أضف HALOT وBDAG", "Add HALOT & BDAG"),
                        if (lexReady) Teal else Gold,
                    ) { nav.push(Screen.Lexicons) }
                    Tool(Modifier.weight(1f), t("روح لشاهد", "انتقل إلى شاهد", "Go to passage"), t("سفر، إصحاح، آية", "سفر، أصحاح، آية", "Book, chapter, verse"), Teal) {
                        nav.push(Screen.Picker(prefs.lastBook, prefs.lastChapter))
                    }
                }
            }
            item {
                @Suppress("UNUSED_VARIABLE") val jv = Jesuit.version.intValue
                val jReady = Jesuit.has(ctx)
                Row {
                    Tool(
                        Modifier.weight(1f), t("الترجمة اليسوعية", "الترجمة اليسوعية", "Jesuit translation"),
                        if (jReady) t("بمقدماتها وحواشيها", "بمقدماتها وحواشيها", "with introductions and notes") else t("ضيف ملف اليسوعية", "أضف ملف اليسوعية", "Add the Jesuit file"),
                        if (jReady) Teal else Gold,
                    ) { nav.push(if (jReady) Screen.JBooks else Screen.JesuitSetup) }
                }
            }
            // ---- books, grouped
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(4.dp)) {
                    for ((i, label) in listOf(t("العهد القديم", "العهد القديم", "Old Testament"), t("العهد الجديد", "العهد الجديد", "New Testament")).withIndex()) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (testament == i) Gold else Color.Transparent)
                                .clickable { testament = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (testament == i) Color.Black else Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            for ((names, range, tst) in SECTIONS) {
                if ((tst == "OT") != (testament == 0)) continue
                item(key = "sec${range.first}") {
                    Column {
                        Text(t(names.first, names.second, names.third), color = Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (id in range) {
                                val b = byId[id] ?: continue
                                Chip(b.name, b.id == prefs.lastBook) {
                                    if (b.chapters == 1) nav.push(Screen.Reader(b.id, 1, 1)) else nav.push(Screen.Chapters(b.id))
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Chip(label: String, current: Boolean = false, onClick: () -> Unit) {
    Text(
        label, color = if (current) Gold else Ink, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Panel)
            .then(if (current) Modifier.border(BorderStroke(1.dp, Gold), RoundedCornerShape(20.dp)) else Modifier)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Tool(modifier: Modifier, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Panel).clickable { onClick() }.padding(14.dp)) {
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChaptersScreen(nav: Nav, bookId: Int) {
    val books by produceState<List<Book>>(emptyList()) { value = Db.books() }
    val book = books.firstOrNull { it.id == bookId } ?: return
    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar(book.name, onBack = { nav.pop() }) { SearchSettingsActions(nav) } },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(58.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            items((1..book.chapters).toList()) { ch ->
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Panel)
                        .clickable {
                            // the chapter grid turns into the reader, so "back" returns home
                            val below = nav.stack.getOrNull(nav.stack.lastIndex - 1)
                            if (below is Screen.Reader) nav.pop()
                            nav.replace(Screen.Reader(book.id, ch, 1))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ArText.num(ch), color = Ink, fontSize = 18.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
