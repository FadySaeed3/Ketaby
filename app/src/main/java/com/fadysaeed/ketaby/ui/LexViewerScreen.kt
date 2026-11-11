package com.fadysaeed.ketaby.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.LexLink
import com.fadysaeed.ketaby.data.LexPos
import com.fadysaeed.ketaby.data.Lexicons
import com.fadysaeed.ketaby.data.PdfDoc
import com.fadysaeed.ketaby.data.PdfDocs
import kotlinx.coroutines.launch

/** Light pages shown as light text on black (AMOLED); off = the PDF as printed. */
private val invert = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.93f, 0f, 0f, 0f, 236f,
            0f, -0.93f, 0f, 0f, 231f,
            0f, 0f, -0.93f, 0f, 218f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private object ViewerPrefs {
    val zoom = mutableFloatStateOf(1f)
    val night = mutableStateOf(true)
}

/** Opens the owner's HALOT/BDAG PDF on an entry, highlights it, and follows cross-references. */
@Composable
fun LexViewerScreen(nav: Nav, s: Screen.Lex) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Lexicons.version.intValue
    val doc = remember(s.lex, v) { PdfDocs.get(ctx, s.lex) }
    val label by produceState<String?>(null, s) { value = s.n?.let { Db.entryLabel(s.lex, it) } }
    val end by produceState<LexPos?>(null, s) { value = Db.nextEntry(s.lex, s.page, s.y) }
    var zoom by ViewerPrefs.zoom
    var night by ViewerPrefs.night

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            KTopBar(
                Lexicons.name(s.lex) + (label?.let { " · $it" } ?: ""),
                onBack = { nav.pop() },
            ) {
                TextButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }) { Text("−", color = Ink, fontSize = 20.sp) }
                TextButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }) { Text("+", color = Ink, fontSize = 20.sp) }
                TextButton(onClick = { night = !night }) { Text(if (night) "☾" else "☀", color = Gold, fontSize = 18.sp) }
                HomeAction(nav)
            }
        },
    ) { pad ->
        if (doc == null) {
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                ExplainText(
                    t(
                        "لسه ماضفتش ملف ${Lexicons.name(s.lex)}. ضيفه مرة واحدة، وبعدها الزرار ده هيفتح المدخل على طول.",
                        "لم تُضف ملف ${Lexicons.name(s.lex)} بعد. أضفه مرة واحدة، وبعدها يفتح هذا الزر المدخل مباشرة.",
                        "${Lexicons.name(s.lex)} has not been added yet. Add it once and this button will open the entry directly.",
                    ),
                    if (ArText.english) "en" else "ar",
                )
                Button(
                    onClick = { nav.replace(Screen.Lexicons) },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text(t("ضيف الملفات", "أضف الملفات", "Add files")) }
            }
            return@Scaffold
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            PdfPages(nav, s, doc, end, zoom, night, Modifier.padding(pad))
        }
    }
}

@Composable
private fun PdfPages(nav: Nav, s: Screen.Lex, doc: PdfDoc, end: LexPos?, zoom: Float, night: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = with(density) { (maxWidth * zoom).roundToPx() }
        val scale = widthPx / doc.pageW
        val pageHeightPx = (doc.pageH * scale).toInt()
        val pageWdp = with(density) { widthPx.toDp() }
        val pageHdp = with(density) { pageHeightPx.toDp() }
        val firstIndex = (s.page - 1).coerceIn(0, doc.pageCount - 1)
        val listState = remember(s, zoom) {
            LazyListState(firstIndex, ((s.y * scale) - with(density) { 72.dp.toPx() }).toInt().coerceAtLeast(0))
        }
        val hScroll = rememberScrollState()
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = zoom > 1f)) {
            LazyColumn(state = listState, modifier = Modifier.width(pageWdp).fillMaxSize()) {
                items(doc.pageCount) { i ->
                    val pageNo = i + 1
                    val bmp by produceState<Bitmap?>(null, i, widthPx) { value = doc.render(i, widthPx) }
                    val links by produceState<List<LexLink>>(emptyList(), i) { value = Db.linksOnPage(s.lex, pageNo) }
                    Box(
                        Modifier.width(pageWdp).height(pageHdp).background(if (night) Color.Black else Color.White)
                            .pointerInput(i, scale, links) {
                                detectTapGestures { off ->
                                    val x = off.x / scale; val y = off.y / scale
                                    val hit = links.firstOrNull { x in (it.x0 - 3)..(it.x1 + 3) && y in (it.y0 - 3)..(it.y1 + 3) }
                                    if (hit != null) scope.launch {
                                        Db.entryPos(s.lex, hit.to)?.let { p -> nav.push(Screen.Lex(s.lex, p.page, p.y, hit.to)) }
                                    }
                                }
                            },
                    ) {
                        bmp?.let {
                            Image(
                                it.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                                colorFilter = if (night) invert else null, modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            // the entry: from its first line to the next entry (or the page end)
                            if (pageNo == s.page) {
                                val top = (s.y - 4f) * scale
                                val bottom = (if (end != null && end.page == s.page) end.y - 2f else doc.pageH) * scale
                                drawRect(Gold.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, bottom - top))
                                drawRect(Gold, Offset(0f, top), Size(6f, bottom - top))
                            } else if (end != null && pageNo > s.page && pageNo <= end.page) {
                                val bottom = (if (pageNo == end.page) end.y - 2f else doc.pageH) * scale
                                drawRect(Gold.copy(alpha = 0.16f), Offset(0f, 0f), Size(size.width, bottom))
                                drawRect(Gold, Offset(0f, 0f), Size(6f, bottom))
                            }
                            // cross-references: thin teal underline, tappable
                            for (l in links) {
                                drawRect(Teal.copy(alpha = 0.85f), Offset(l.x0 * scale, l.y1 * scale - 2f), Size((l.x1 - l.x0) * scale, 3f))
                            }
                        }
                        Text(
                            "${ArText.num(pageNo)} / ${ArText.num(doc.pageCount)}",
                            color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color.Black))
                }
            }
        }
    }
}
