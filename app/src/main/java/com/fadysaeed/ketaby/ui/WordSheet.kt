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
import androidx.compose.material3.TextButton
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
import com.fadysaeed.ketaby.data.Lexicons
import com.fadysaeed.ketaby.data.OpenLex
import com.fadysaeed.ketaby.data.DictEntry
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import com.fadysaeed.ketaby.data.OWord
import com.fadysaeed.ketaby.data.Tri
import com.fadysaeed.ketaby.morph.Morph

private data class Extra(val open: List<OpenLex>, val dict: DictEntry?, val rend: List<Pair<String, Int>>)
private data class SheetData(val words: List<OWord>, val cards: Map<String, Card>, val extra: Map<String, Extra>)

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
        boldMarks(text), color = color, fontSize = size.sp, lineHeight = (size * 1.75).sp,
        fontFamily = if (lang == "en") FontFamily.Serif else Plex,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(textDirection = if (lang == "en") TextDirection.Ltr else TextDirection.Rtl),
    )
}

/** "**a lead-in:** text" -> the lead-in in gold semibold (dictionary entries use it for section names). */
fun boldMarks(s: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1 && i < parts.size - 1) {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = Gold)); append(p); pop()
        } else {
            if (i % 2 == 1) append("**")
            append(p)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(
    req: SheetRequest, onDismiss: () -> Unit, onConcordance: (String) -> Unit,
    onOpenLex: (lex: String, page: Int, y: Float, n: Int?) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val data by produceState<SheetData?>(null, req) {
        val ws = if (req.tokenId != null) Db.wordsForToken(req.tokenId) else Db.wordsById(req.wordIds)
        val cards = HashMap<String, Card>()
        val extra = HashMap<String, Extra>()
        for (w in ws) {
            val ds = w.dstrong ?: continue
            Db.card(ds)?.let { cards[ds] = it }
            if (ds !in extra) extra[ds] = Extra(Db.openLexicons(ds), Db.dictEntry(ds), Db.renderings(ds))
        }
        value = SheetData(ws, cards, extra)
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
                        WordBlock(w, w.dstrong?.let { d.cards[it] }, w.dstrong?.let { d.extra[it] }, onConcordance, onOpenLex)
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
private fun WordBlock(w: OWord, card: Card?, extra: Extra?, onConcordance: (String) -> Unit, onOpenLex: (String, Int, Float, Int?) -> Unit) {
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
                (if (greek || prefs.cantillation) w.text else ArText.stripCantillation(w.text)).replace("¶", ""),
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
            if (!l.gloss.isNullOrBlank() && extra?.dict == null) Label(t("المعنى الأساسي (إنجليزي مؤقتًا)", "المعنى الأساسي (بالإنجليزية مؤقتًا)", "Basic meaning"), l.gloss, ltr = true)
            Label(t("اتذكرت في الكتاب", "عدد مرات ورودها", "Occurs"), ArText.times(l.count))
            Label(t("رقم سترونج", "رقم سترونج", "Strong's number"), l.dstrong, ltr = true)
        }

        extra?.dict?.let { de ->
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelHi).padding(12.dp)) {
                Text(t("المعنى", "المعنى", "Meaning"), color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TriBlock(de.gloss, size = 16)
                if (de.entry.eg != null || de.entry.msa != null || de.entry.en != null) {
                    var full by remember(de) { mutableStateOf(false) }
                    Text(
                        if (full) t("اقفل الشرح ▴", "إخفاء الشرح ▴", "Hide explanation ▴") else t("الشرح الكامل ▾", "الشرح الكامل ▾", "Full explanation ▾"),
                        color = Teal, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).clickable { full = !full },
                    )
                    if (full) TriBlock(de.entry)
                }
                val lexName = if (greek) "BDAG" else "HALOT"
                Text(
                    if (de.checked) t("شرح Claude، واتراجع على $lexName", "شرح Claude، رُوجع على $lexName", "Claude's entry, checked against $lexName")
                    else t("شرح Claude من القواميس المفتوحة، لسه متراجعش على $lexName", "شرح Claude من المعاجم المفتوحة، لم يُراجع على $lexName بعد", "Claude's entry from the open lexicons, not yet checked against $lexName"),
                    color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        extra?.let { RenderingsRow(it.rend) }
        extra?.let { OpenLexiconList(it.open, greek) }

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
                card.halotPage?.let { pg ->
                    TextButton(onClick = { onOpenLex(Lexicons.HALOT, pg, card.halotY ?: 0f, card.halotEntry?.toIntOrNull()) }) {
                        Text(t("افتح المدخل ده في هالوت ←", "افتح هذا المدخل في HALOT ←", "Open this entry in HALOT →"), color = Gold, fontSize = 13.sp)
                    }
                }
            }
        }

        val ds = w.dstrong
        if (ds != null) {
            Spacer(Modifier.height(12.dp))
            val lex = if (greek) Lexicons.BDAG else Lexicons.HALOT
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onConcordance(ds) }) {
                    Text(t("كل المواضع", "كل المواضع", "All occurrences") + (l?.let { " (${ArText.num(it.count)})" } ?: ""), color = Gold)
                }
                val page = if (greek) l?.bdagPage else l?.halotPage
                val y = if (greek) l?.bdagY else l?.halotY
                val n = if (greek) l?.bdagN else l?.halotN
                OutlinedButton(onClick = { if (page != null) onOpenLex(lex, page, y ?: 0f, n) }, enabled = page != null) {
                    Text(
                        if (greek) t("افتح في باور", "افتح في BDAG", "Open in BDAG") else t("افتح في هالوت", "افتح في HALOT", "Open in HALOT"),
                        color = if (page != null) Gold else Line,
                    )
                }
            }
            val all = (if (greek) l?.bdagAll else l?.halotAll).orEmpty()
            if (all.size > 1) {
                val scope = rememberCoroutineScope()
                Text(
                    t(
                        "${Lexicons.name(lex)} فيه أكتر من مدخل بنفس الشكل، والفهرس مش متأكد أنهي فيهم المقصود هنا. اختار:",
                        "في ${Lexicons.name(lex)} أكثر من مدخل بالشكل نفسه، والفهرس غير متأكد أيها المقصود هنا. اختر:",
                        "${Lexicons.name(lex)} has more than one entry with this form and the index is not sure which is meant here. Choose:",
                    ),
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    all.take(8).forEachIndexed { i, n ->
                        TextButton(onClick = {
                            scope.launch { Db.entryPos(lex, n)?.let { p -> onOpenLex(lex, p.page, p.y, n) } }
                        }) { Text(t("مدخل", "مدخل", "Entry") + " " + ArText.num(i + 1), color = Gold, fontSize = 13.sp) }
                    }
                }
            }
            if ((if (greek) l?.bdagPage else l?.halotPage) == null) Text(
                t("الكلمة دي لسه مش مربوطة بمدخل في القاموس.", "لم تُربط هذه الكلمة بمدخل في المعجم بعد.", "This word is not linked to a lexicon entry yet."),
                color = Muted, fontSize = 11.sp,
            )
        }
    }
}
