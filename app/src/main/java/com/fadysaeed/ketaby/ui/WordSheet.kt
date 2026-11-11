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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.ArText
import com.fadysaeed.ketaby.data.Card
import com.fadysaeed.ketaby.data.Db
import com.fadysaeed.ketaby.data.OWord
import com.fadysaeed.ketaby.data.Tri
import com.fadysaeed.ketaby.morph.Morph

private data class SheetData(val words: List<OWord>, val cards: Map<String, Card>)

/** Languages to show explanations in, from the "explanation language" setting. */
@Composable
fun explainLangs(): List<String> = when (val l = LocalPrefs.current.explainLang) {
    "all" -> listOf("eg", "msa", "en")
    else -> listOf(l)
}

private fun langLabel(l: String) = when (l) {
    "eg" -> t("مصري", "بالعامية المصرية", "Egyptian Arabic")
    "msa" -> t("فصحى", "بالفصحى", "Standard Arabic")
    else -> t("إنجليزي", "بالإنجليزية", "English")
}

/** A paragraph in the explanation language; Arabic right-to-left, English left-to-right. */
@Composable
fun ExplainText(text: String, lang: String, size: Int = 15, color: Color = Ink) {
    Text(
        text, color = color, fontSize = size.sp, lineHeight = (size * 1.75).sp,
        fontFamily = if (lang == "en") FontFamily.Serif else Plex,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(textDirection = if (lang == "en") TextDirection.Ltr else TextDirection.Rtl),
    )
}

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
    val dir = if (ArText.english) LayoutDirection.Ltr else LayoutDirection.Rtl
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Panel) {
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding(),
            ) {
                if (req.header != null) {
                    if (req.headerIsEnglish) {
                        Text(
                            req.header, fontFamily = FontFamily.Serif, fontSize = 24.sp, color = Gold,
                            modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Ltr),
                        )
                    } else {
                        Text(
                            if (LocalPrefs.current.harakat) req.header else ArText.stripHarakat(req.header),
                            fontFamily = scriptureFamily(), fontSize = 26.sp, color = Gold,
                            modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Rtl),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val d = data
                when {
                    d == null -> Text("…", color = Muted)
                    d.words.isEmpty() -> Text(
                        t(
                            "الكلمة دي ملهاش مقابل مباشر في الأصل؛ غالبًا المترجم زودها عشان المعنى يوضح.",
                            "ليس لهذه الكلمة مقابل مباشر في النص الأصلي؛ والأرجح أن المترجم أضافها لإيضاح المعنى.",
                            "This word has no direct counterpart in the original; the translator most likely added it for clarity.",
                        ),
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
private fun Label(k: String, v: String, vColor: Color = Ink, ltr: Boolean = false, rtlValue: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("$k: ", color = Muted, fontSize = 14.sp)
        Text(
            v, color = vColor, fontSize = 15.sp,
            style = TextStyle(textDirection = if (ltr) TextDirection.Ltr else if (rtlValue) TextDirection.Rtl else TextDirection.Content),
        )
    }
}

@Composable
private fun TriBlock(tri: Tri, size: Int = 15) {
    val langs = explainLangs()
    langs.forEachIndexed { i, l ->
        val txt = tri.get(l) ?: return@forEachIndexed
        if (langs.size > 1) {
            if (i > 0) Spacer(Modifier.height(8.dp))
            Text(langLabel(l), color = Teal, fontSize = 11.sp)
        }
        ExplainText(txt, l, size)
    }
}

@Composable
private fun WordBlock(w: OWord, card: Card?, onConcordance: (String) -> Unit) {
    val prefs = LocalPrefs.current
    val greek = w.lang == "grc"
    val langName = when (w.lang) {
        "grc" -> t("يوناني", "يوناني", "Greek"); "arc" -> t("آرامي", "آرامي", "Aramaic"); else -> t("عبري", "عبري", "Hebrew")
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (greek || prefs.cantillation) w.text else ArText.stripCantillation(w.text),
                fontFamily = if (greek) GreekFont else HebrewFont, fontSize = 34.sp, color = Gold,
            )
            Text("$langName · ${ArText.num(w.oc)}:${ArText.num(w.ov)}", color = Muted, fontSize = 12.sp)
        }
        if (greek) {
            if (prefs.greekMode != 2) w.tr?.let { Label(t("النطق الأكاديمي", "النطق الأكاديمي", "Academic reading"), it, Teal, rtlValue = true) }
            if (prefs.greekMode != 1) w.tr2?.let { Label(t("النطق الحديث", "النطق الحديث", "Modern reading"), it, Teal, rtlValue = true) }
        } else {
            w.tr?.let { Label(t("النطق", "النطق", "Reading"), it, Teal, rtlValue = true) }
        }
        val langs = explainLangs()
        for (l in langs.distinctBy { if (it == "en") "en" else "ar" }) {
            val morph = Morph.describe(w.grammar, w.lang, l)
            if (morph.isNotBlank()) Label(
                if (l == "en" && !ArText.english) t("التحليل (إنجليزي)", "التحليل (بالإنجليزية)", "Parsing")
                else t("التحليل", "التحليل الصرفي", "Parsing"),
                morph, ltr = l == "en", rtlValue = l != "en",
            )
        }
        if (!w.gloss.isNullOrBlank()) Label(t("معناها هنا (إنجليزي مؤقتًا)", "معناها هنا (بالإنجليزية مؤقتًا)", "Meaning here"), w.gloss, ltr = true)
        if (greek && w.editions != null && !w.editions.contains('N')) {
            Text(
                t(
                    "الكلمة دي مش موجودة في النص النقدي (نسله-ألاند)؛ موجودة في نسخ تانية زي النص المستلم اللي فاندايك ترجم منه.",
                    "هذه الكلمة غير موجودة في النص النقدي (نسله-ألاند)، بل في نسخ أخرى مثل النص المستلم الذي ترجم عنه فاندايك.",
                    "This word is not in the critical text (Nestle-Aland); it comes from other editions such as the Textus Receptus that Van Dyke translated.",
                ),
                color = Rose, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        val l = w.lemma
        if (l != null) {
            Spacer(Modifier.height(10.dp))
            Text(t("الكلمة في القاموس", "المدخل المعجمي", "Dictionary form"), color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.lemma, fontFamily = if (greek) GreekFont else HebrewFont, fontSize = 24.sp, color = Ink)
                Spacer(Modifier.padding(horizontal = 6.dp))
                val lt = if (greek) listOfNotNull(l.tr, l.tr2).distinct().joinToString(" / ") else (l.tr ?: "")
                Text(lt, color = Teal, fontSize = 15.sp, style = TextStyle(textDirection = TextDirection.Rtl))
            }
            if (!l.gloss.isNullOrBlank()) Label(t("المعنى الأساسي (إنجليزي مؤقتًا)", "المعنى الأساسي (بالإنجليزية مؤقتًا)", "Basic meaning"), l.gloss, ltr = true)
            Label(t("اتذكرت في الكتاب", "عدد مرات ورودها", "Occurs"), ArText.times(l.count))
            Label(t("رقم سترونج", "رقم سترونج", "Strong's number"), l.dstrong, ltr = true)
        }

        if (card != null) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Gold.copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                val cert = card.certainty?.get(if (ArText.english) "en" else if (ArText.ui == "msa") "msa" else "eg")
                Text(
                    t("كلمة نادرة", "كلمة نادرة", "Rare word") + (cert?.let { t(" · المعنى $it", " · المعنى $it", " · meaning $it") } ?: ""),
                    color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                TriBlock(card.notes)
                val src = if (card.halotChecked) {
                    t("اتراجعت على هالوت", "رُوجعت على HALOT", "Checked against HALOT") +
                        (card.halotEntry?.let { t("، مدخل ${ArText.num(it)}", "، المدخل ${ArText.num(it)}", ", entry $it") } ?: "")
                } else t("لسه متراجعتش على هالوت", "لم تُراجع على HALOT بعد", "Not yet checked against HALOT")
                Text(src, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        val ds = w.dstrong
        if (ds != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onConcordance(ds) }) {
                    Text(t("كل المواضع", "كل المواضع", "All occurrences") + (l?.let { " (${ArText.num(it.count)})" } ?: ""), color = Gold)
                }
                val hasLex = if (greek) l?.bdagPage != null else l?.halotPage != null
                OutlinedButton(onClick = {}, enabled = false) {
                    Text(
                        if (greek) t("افتح في باور", "افتح في BDAG", "Open in BDAG") else t("افتح في هالوت", "افتح في HALOT", "Open in HALOT"),
                        color = if (hasLex) Muted else Line,
                    )
                }
            }
            Text(
                t("زرار القاموس هيشتغل لما تضيف ملفاتك في المرحلة الجاية.", "سيعمل زر المعجم بعد إضافة ملفاتك في المرحلة القادمة.", "The lexicon button will work once you add your files in the next phase."),
                color = Muted, fontSize = 11.sp,
            )
        }
    }
}
