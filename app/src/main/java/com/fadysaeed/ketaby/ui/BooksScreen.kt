package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Book
import com.fadysaeed.ketaby.data.Db

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
                title, color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1,
                modifier = if (onTitle != null) Modifier.clickable { onTitle() } else Modifier,
            )
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Ink)
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, scrolledContainerColor = Color.Black),
    )
}

@Composable
fun SearchSettingsActions(nav: Nav) {
    IconButton(onClick = { nav.push(Screen.Search) }) { Icon(Icons.Filled.Search, "بحث", tint = Ink) }
    IconButton(onClick = { nav.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "الإعدادات", tint = Ink) }
}

@Composable
fun BooksScreen(nav: Nav) {
    val prefs = LocalPrefs.current
    val books by produceState<List<Book>>(emptyList()) { value = Db.books() }
    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar("كتابي", onBack = null) { SearchSettingsActions(nav) } },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            val last = books.firstOrNull { it.id == prefs.lastBook }
            if (last != null) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
                        .border(BorderStroke(1.dp, Gold.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                        .clickable { nav.push(Screen.Reader(last.id, prefs.lastChapter, 0)) }
                        .padding(16.dp),
                ) {
                    Text("كمّل من آخر مكان", color = Muted, fontSize = 13.sp)
                    Text("${last.name} ${ArText.num(prefs.lastChapter)}", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            for ((label, t) in listOf("العهد القديم" to "OT", "العهد الجديد" to "NT")) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(label, color = Teal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                }
                items(books.filter { it.testament == t }, key = { it.id }) { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                            .clickable {
                                if (b.chapters == 1) nav.push(Screen.Reader(b.id, 1, 1)) else nav.push(Screen.Chapters(b.id))
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(b.name, color = Ink, fontSize = 16.sp, maxLines = 1)
                        Text(ArText.num(b.chapters), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
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
                            // the chapter grid turns into the reader, so "back" returns to the books
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
