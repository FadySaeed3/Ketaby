package com.fadysaeed.ketaby.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/** Open a passage from the picker: the reader under it changes place, so "back" stays simple. */
fun goTo(nav: Nav, book: Int, ch: Int, v: Int) {
    if (nav.top is Screen.Picker) nav.pop()
    val target = Screen.Reader(book, ch, v)
    if (nav.top is Screen.Reader) nav.replace(target) else nav.push(target)
}

/**
 * Book → chapter → verse picker. Opens on the current book when one is given.
 */
@Composable
fun PickerScreen(nav: Nav, startBook: Int?, startChapter: Int?) {
    val books by produceState<List<Book>>(emptyList()) { value = Db.books() }
    var bookId by rememberSaveable { mutableStateOf<Int?>(null) }
    var ch by rememberSaveable { mutableStateOf<Int?>(null) }
    val book = books.firstOrNull { it.id == bookId }
    val verseCount by produceState(0, bookId, ch) {
        val b = bookId; val c = ch
        value = if (b != null && c != null) Db.verseCount(b, c) else 0
    }

    BackHandler(enabled = bookId != null) {
        if (ch != null) ch = null else bookId = null
    }

    val title = when {
        book == null -> t("اختار السفر", "اختر السفر", "Choose a book")
        ch == null -> "${book.name} · " + t("اختار الإصحاح", "اختر الأصحاح", "chapter")
        else -> "${book.name} ${ArText.num(ch!!)} · " + t("اختار الآية", "اختر الآية", "verse")
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            KTopBar(title, onBack = {
                when {
                    ch != null -> ch = null
                    bookId != null -> bookId = null
                    else -> nav.pop()
                }
            }) { HomeAction(nav) }
        },
    ) { pad ->
        val b = book
        when {
            b == null -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(pad).fillMaxSize(),
            ) {
                for ((label, tst) in listOf(
                    t("العهد القديم", "العهد القديم", "Old Testament") to "OT",
                    t("العهد الجديد", "العهد الجديد", "New Testament") to "NT",
                )) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(label, color = Teal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(books.filter { it.testament == tst }, key = { it.id }) { bk ->
                        val current = bk.id == startBook
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                                .then(if (current) Modifier.border(BorderStroke(1.dp, Gold), RoundedCornerShape(12.dp)) else Modifier)
                                .clickable {
                                    if (bk.chapters == 1) { bookId = bk.id; ch = 1 } else bookId = bk.id
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bk.name, color = if (current) Gold else Ink, fontSize = 16.sp, maxLines = 1)
                            Text(ArText.num(bk.chapters), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            ch == null -> NumberGrid(
                pad, b.chapters, highlight = if (b.id == startBook) startChapter else null,
                header = t("دوس على رقم الإصحاح، وبعدها اختار الآية", "اختر رقم الأصحاح ثم الآية", "Pick a chapter, then a verse"),
            ) { ch = it }
            else -> NumberGrid(
                pad, verseCount, highlight = null,
                header = t("أو اقرا الإصحاح من أوله", "أو اقرأ الأصحاح من بدايته", "or read the chapter from the start"),
                onHeader = { goTo(nav, b.id, ch!!, 1) },
            ) { goTo(nav, b.id, ch!!, it) }
        }
    }
}

@Composable
private fun NumberGrid(
    pad: PaddingValues, count: Int, highlight: Int?, header: String, onHeader: (() -> Unit)? = null, onPick: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(58.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(pad).fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                header, color = if (onHeader != null) Gold else Muted, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
                    .then(if (onHeader != null) Modifier.clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, Gold.copy(alpha = 0.5f)), RoundedCornerShape(10.dp)).clickable { onHeader() } else Modifier)
                    .padding(12.dp),
            )
        }
        items((1..count).toList()) { n ->
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (n == highlight) Gold else Panel)
                    .clickable { onPick(n) },
                contentAlignment = Alignment.Center,
            ) {
                Text(ArText.num(n), color = if (n == highlight) Color.Black else Ink, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
