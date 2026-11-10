package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Card
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.OWord
import com.fadysaeed.ketaby.morph.Morph

private data class SheetData(val words: List<OWord>, val cards: Map<String, Card>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(req: SheetRequest, onDismiss: () -> Unit, onConcordance: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val data by produceState<SheetData?>(null, req) {
        val ws = if (req.tokenId != null) Db.wordsForToken(req.tokenId) else Db.wordsById(req.wordIds)
        val cards = HashMap<String, Card>()
        for (w in ws) {
            val ds = w.dstrong ?: continue
            Db.card(ds)?.let { cards[ds] = it }
        }
        value = SheetData(ws, cards)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Panel) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding(),
            ) {
                if (req.arabic != null) {
                    Text(
                        if (LocalPrefs.current.harakat) req.arabic else ArText.stripHarakat(req.arabic),
                        fontFamily = scriptureFamily(), fontSize = 26.sp, color = Gold,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val d = data
                when {
                    d == null -> Text("…", color = Muted)
                    d.words.isEmpty() -> Text(
                        "الكلمة دي ملهاش مقابل مباشر في الأصل؛ غالبًا المترجم زودها عشان المعنى يوضح بالعربي.",
                        color = Muted,
                    )
                    else -> d.words.forEachIndexed { i, w ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        WordBlock(w, w.dstrong?.let { d.cards[it] }, onConcordance)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Label(k: String, v: String, vColor: androidx.compose.ui.graphics.Color = Ink, ltr: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("$k: ", color = Muted, fontSize = 14.sp)
        Text(
            v, color = vColor, fontSize = 15.sp,
            style = TextStyle(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
        )
    }
}

@Composable
private fun WordBlock(w: OWord, card: Card?, onConcordance: (String) -> Unit) {
    val prefs = LocalPrefs.current
    val greek = w.lang == "grc"
    val langName = when (w.lang) { "grc" -> "يوناني"; "arc" -> "آرامي"; else -> "عبري" }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color.Black).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (greek || prefs.cantillation) w.text else ArText.stripCantillation(w.text),
                fontFamily = if (greek) GreekFont else HebrewFont, fontSize = 34.sp, color = Gold,
            )
            Text("$langName · ${ArText.num(w.oc)}:${ArText.num(w.ov)}", color = Muted, fontSize = 12.sp)
        }
        if (greek) {
            if (prefs.greekMode != 2) w.tr?.let { Label("النطق الأكاديمي", it, Teal) }
            if (prefs.greekMode != 1) w.tr2?.let { Label("النطق الحديث", it, Teal) }
        } else {
            w.tr?.let { Label("النطق", it, Teal) }
        }
        val morph = Morph.describe(w.grammar, w.lang)
        if (morph.isNotBlank()) Label("التحليل", morph)
        if (!w.gloss.isNullOrBlank()) Label("معناها هنا (إنجليزي مؤقتًا)", w.gloss, ltr = true)
        if (greek && w.editions != null && !w.editions.contains('N')) {
            Text(
                "الكلمة دي مش موجودة في النص النقدي (نسله-ألاند)؛ موجودة في نسخ تانية زي النص المستلم اللي فاندايك ترجم منه.",
                color = Rose, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        val l = w.lemma
        if (l != null) {
            Spacer(Modifier.height(10.dp))
            Text("الكلمة في القاموس", color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.lemma, fontFamily = if (greek) GreekFont else HebrewFont, fontSize = 24.sp, color = Ink)
                Spacer(Modifier.padding(horizontal = 6.dp))
                val lt = if (greek) listOfNotNull(l.tr, l.tr2).distinct().joinToString(" / ") else (l.tr ?: "")
                Text(lt, color = Teal, fontSize = 15.sp)
            }
            if (!l.gloss.isNullOrBlank()) Label("المعنى الأساسي (إنجليزي مؤقتًا)", l.gloss, ltr = true)
            Label("اتذكرت في الكتاب", ArText.times(l.count))
            Label("رقم سترونج", l.dstrong, ltr = true)
        }

        if (card != null) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Gold.copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(
                    "كلمة نادرة" + (card.certainty?.let { " · المعنى $it" } ?: ""),
                    color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(card.note, color = Ink, fontSize = 15.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 6.dp))
                val src = if (card.halotChecked) "اتراجعت على هالوت" + (card.halotEntry?.let { "، مدخل ${ArText.num(it)}" } ?: "")
                else "لسه متراجعتش على هالوت"
                Text(src, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        val ds = w.dstrong
        if (ds != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onConcordance(ds) }) {
                    Text("كل المواضع" + (l?.let { " (${ArText.num(it.count)})" } ?: ""), color = Gold)
                }
                val hasLex = if (greek) l?.bdagPage != null else l?.halotPage != null
                OutlinedButton(onClick = {}, enabled = false) {
                    Text(if (greek) "افتح في باور" else "افتح في هالوت", color = if (hasLex) Muted else Line)
                }
            }
            Text("زرار القاموس هيشتغل لما تضيف ملفاتك في المرحلة الجاية.", color = Muted, fontSize = 11.sp)
        }
    }
}
