package com.fadysaeed.ketaby.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

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
    /** A zoom step asked for by the top bar (+0.5 / −0.5), consumed by the pages. */
    val request = mutableStateOf<Float?>(null)
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
    var night by ViewerPrefs.night

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            KTopBar(
                Lexicons.name(s.lex) + (label?.let { " · $it" } ?: ""),
                onBack = { nav.pop() },
            ) {
                TextButton(onClick = { ViewerPrefs.request.value = -0.5f }) { Text("−", color = Ink, fontSize = 20.sp) }
                TextButton(onClick = { ViewerPrefs.request.value = 0.5f }) { Text("+", color = Ink, fontSize = 20.sp) }
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
            PdfPages(nav, s, doc, end, night, Modifier.padding(pad))
        }
    }
}

private const val MAX_SCALE = 4f

/** Render sharpness follows the zoom in a few steps (memory), so a pinch does not re-render every frame. */
private fun renderStep(scale: Float) = when {
    scale <= 1.15f -> 1f
    scale <= 1.6f -> 1.5f
    scale <= 2.1f -> 2f
    else -> 2.5f
}

/**
 * Pages laid out at screen width; zoom is a single transform (scale + offset) on top of them, so a pinch
 * never re-lays out the pages and nothing jumps. The point between the fingers stays under the fingers;
 * moving both fingers pans; one finger scrolls up/down (the list) and sideways (the offset) when zoomed.
 */
@Composable
private fun PdfPages(nav: Nav, s: Screen.Lex, doc: PdfDoc, end: LexPos?, night: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var committed by ViewerPrefs.zoom            // drives render sharpness; updated when a gesture ends
    var scale by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var outer by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().onGloballyPositioned { outer = it }) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val renderPx = (viewW * renderStep(committed)).toInt()
        val pageScale = viewW / doc.pageW
        val pageHdp = with(density) { (doc.pageH * pageScale).toDp() }
        val firstIndex = (s.page - 1).coerceIn(0, doc.pageCount - 1)
        val listState = remember(s) {
            LazyListState(firstIndex, ((s.y * pageScale) - with(density) { 72.dp.toPx() }).toInt().coerceAtLeast(0))
        }

        fun clampX(x: Float, sc: Float) = x.coerceIn(minOf(0f, viewW - viewW * sc), 0f)
        fun clampY(y: Float, sc: Float) = y.coerceIn(minOf(0f, viewH - viewH * sc), 0f)

        // zoom by z around screen point c, then pan by p; vertical movement past the edge scrolls the pages
        fun transform(c: Offset, z: Float, p: Offset) {
            val s0 = scale
            val s1 = (s0 * z).coerceIn(1f, MAX_SCALE)
            val k = s1 / s0
            val nx = c.x - (c.x - tx) * k + p.x
            val ny = c.y - (c.y - ty) * k + p.y
            val cy = clampY(ny, s1)
            scale = s1
            tx = clampX(nx, s1)
            ty = cy
            val over = ny - cy
            if (over != 0f) listState.dispatchRawDelta(-over / s1)
        }

        fun animateZoom(target: Float, c: Offset) {
            scope.launch {
                animate(scale, target.coerceIn(1f, MAX_SCALE), animationSpec = tween(240)) { v, _ ->
                    transform(c, v / scale, Offset.Zero)
                }
                committed = scale
            }
        }

        // the top bar's − / + buttons zoom around the middle of the screen
        val req by ViewerPrefs.request
        LaunchedEffect(req) {
            req?.let { animateZoom(scale + it, Offset(viewW / 2, viewH / 2)) }
            ViewerPrefs.request.value = null
        }
        LaunchedEffect(Unit) { if (committed > 1.01f) transform(Offset.Zero, committed, Offset.Zero) }

        val gestures = Modifier.pointerInput(viewW, viewH) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var pinched = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.count { it.pressed }
                    if (down >= 2) {
                        transform(event.calculateCentroid(useCurrent = false), event.calculateZoom(), event.calculatePan())
                        event.changes.forEach { it.consume() }
                        pinched = true
                    } else if (pinched) {
                        event.changes.forEach { it.consume() }          // the finger left after a pinch stays still
                    } else if (down == 1 && scale > 1.01f) {
                        val ch = event.changes.first { it.pressed }
                        val dx = ch.position.x - ch.previousPosition.x
                        if (dx != 0f) tx = clampX(tx + dx, scale)      // sideways pan; the list takes the vertical part
                    }
                } while (event.changes.any { it.pressed })
                if (pinched) committed = scale
            }
        }

        Box(Modifier.fillMaxSize().then(gestures)) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale; scaleY = scale
                    translationX = tx; translationY = ty
                },
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(doc.pageCount) { i ->
                        val pageNo = i + 1
                        // keep the current picture until the sharper one is ready (no blank flash)
                        var bmp by remember(i) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(i, renderPx) { bmp = doc.render(i, renderPx) }
                        val links by produceState<List<LexLink>>(emptyList(), i) { value = Db.linksOnPage(s.lex, pageNo) }
                        var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        Box(
                            Modifier.fillMaxWidth().height(pageHdp).background(if (night) Color.Black else Color.White)
                                .onGloballyPositioned { pageCoords = it }
                                .pointerInput(i, pageScale, links) {
                                    detectTapGestures(
                                        onDoubleTap = { off ->
                                            val o = outer; val pc = pageCoords
                                            val c = if (o != null && pc != null) o.localPositionOf(pc, off) else Offset(viewW / 2, viewH / 2)
                                            animateZoom(if (scale < 1.9f) 2f else 1f, c)
                                        },
                                    ) { off ->
                                        val x = off.x / pageScale; val y = off.y / pageScale
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
                                    val top = (s.y - 4f) * pageScale
                                    val bottom = (if (end != null && end.page == s.page) end.y - 2f else doc.pageH) * pageScale
                                    drawRect(Gold.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, bottom - top))
                                    drawRect(Gold, Offset(0f, top), Size(6f, bottom - top))
                                } else if (end != null && pageNo > s.page && pageNo <= end.page) {
                                    val bottom = (if (pageNo == end.page) end.y - 2f else doc.pageH) * pageScale
                                    drawRect(Gold.copy(alpha = 0.16f), Offset(0f, 0f), Size(size.width, bottom))
                                    drawRect(Gold, Offset(0f, 0f), Size(6f, bottom))
                                }
                                // cross-references: thin teal underline, tappable
                                for (l in links) {
                                    drawRect(Teal.copy(alpha = 0.85f), Offset(l.x0 * pageScale, l.y1 * pageScale - 2f), Size((l.x1 - l.x0) * pageScale, 3f))
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
}
