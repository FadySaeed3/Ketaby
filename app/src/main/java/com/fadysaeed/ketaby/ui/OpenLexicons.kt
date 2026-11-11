package com.fadysaeed.ketaby.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.OpenLex

/** <b>, <i> and <br> only (the pipeline strips everything else). */
fun htmlLite(s: String): AnnotatedString = buildAnnotatedString {
    var bold = 0
    var italic = 0
    val tag = Regex("<(/?)(b|i|br)\\s*/?>", RegexOption.IGNORE_CASE)
    var pos = 0
    fun text(t: String) {
        if (t.isEmpty()) return
        val st = SpanStyle(
            fontWeight = if (bold > 0) FontWeight.SemiBold else null,
            fontStyle = if (italic > 0) FontStyle.Italic else null,
            color = if (bold > 0) Gold else androidx.compose.ui.graphics.Color.Unspecified,
        )
        pushStyle(st); append(t); pop()
    }
    for (m in tag.findAll(s)) {
        text(s.substring(pos, m.range.first))
        val close = m.groupValues[1] == "/"
        when (m.groupValues[2].lowercase()) {
            "b" -> bold = (bold + if (close) -1 else 1).coerceAtLeast(0)
            "i" -> italic = (italic + if (close) -1 else 1).coerceAtLeast(0)
            "br" -> append("\n")
        }
        pos = m.range.last + 1
    }
    text(s.substring(pos))
}

private fun srcName(src: String, greek: Boolean) = when (src) {
    "bdb" -> "BDB · Brown-Driver-Briggs"
    "ubs" -> if (greek) "UBS · Dictionary of the Greek NT" else "UBS · Dictionary of Biblical Hebrew"
    "lsj" -> "LSJ · Liddell-Scott-Jones"
    "mm" -> "Moulton & Milligan · " + t("نص ممسوح، ممكن فيه أخطاء قراءة", "نص ممسوح ضوئيًا وقد يحتوي أخطاء", "scanned text, may have OCR slips")
    else -> if (greek) "Abbott-Smith (STEPBible)" else "STEPBible · brief lexicon"
}

/** The open lexicons, each folded to a few lines until tapped. */
@Composable
fun OpenLexiconList(items: List<OpenLex>, greek: Boolean) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text(
        t("القواميس المفتوحة (بالإنجليزي)", "المعاجم المفتوحة (بالإنجليزية)", "Open lexicons"),
        color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    )
    for (x in items) OpenLexCard(x, greek)
}

@Composable
private fun OpenLexCard(item: OpenLex, greek: Boolean) {
    var open by remember(item) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(PanelHi)
            .clickable { open = !open }.padding(10.dp).animateContentSize(),
    ) {
        Text(srcName(item.src, greek) + if (open) "  ▴" else "  ▾", color = Muted, fontSize = 12.sp)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                htmlLite(item.html), color = Ink, fontSize = 14.sp, lineHeight = 21.sp,
                maxLines = if (open) Int.MAX_VALUE else 4, fontFamily = FontFamily.Serif,
                style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** How Van Dyke renders the lemma (Arabic without vowels, with counts). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderingsRow(items: List<Pair<String, Int>>) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(t("فاندايك ترجمها", "ترجمها فاندايك", "Van Dyke renders it"), color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        for ((w, n) in items) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(PanelHi).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(w, color = Ink, fontSize = 14.sp, fontFamily = Naskh)
                Text(" " + ArText.num(n), color = Muted, fontSize = 11.sp)
            }
        }
    }
}
