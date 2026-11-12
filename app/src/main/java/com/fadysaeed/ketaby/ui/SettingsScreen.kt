package com.fadysaeed.ketaby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.EVerse
import com.fadysaeed.ketaby.data.Span
import com.fadysaeed.ketaby.data.Token

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
        Text(title, color = Teal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Gold))
        Text(label, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun Toggle(label: String, hint: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontSize = 15.sp)
            if (hint != null) Text(hint, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Gold),
        )
    }
}

@Composable
fun SettingsScreen(nav: Nav) {
    val p = LocalPrefs.current
    Scaffold(containerColor = Color.Black, topBar = { KTopBar(t("الإعدادات", "الإعدادات", "Settings"), onBack = { nav.pop() }) { HomeAction(nav) } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section(t("قواميسك (هالوت وباور)", "معاجمك (HALOT وBDAG)", "Your lexicons (HALOT & BDAG)")) {
                Text(
                    t("ضيف ملفاتك أو بدّلها", "أضف ملفاتك أو استبدلها", "Add or replace your PDF files") + " ←",
                    color = Gold, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { nav.push(Screen.Lexicons) }.padding(vertical = 6.dp),
                )
            }
            Section(t("الترجمة اليسوعية", "الترجمة اليسوعية", "Jesuit translation")) {
                Text(
                    t("ضيف ملف اليسوعية أو بدّله", "أضف ملف اليسوعية أو استبدله", "Add or replace the Jesuit file") + " ←",
                    color = Gold, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { nav.push(Screen.JesuitSetup) }.padding(vertical = 6.dp),
                )
            }
            Section(t("لغة التطبيق", "لغة الواجهة", "App language")) {
                Choice("مصري", p.uiLang == "eg") { p.putUiLang("eg") }
                Choice("العربية الفصحى", p.uiLang == "msa") { p.putUiLang("msa") }
                Choice("English", p.uiLang == "en") { p.putUiLang("en") }
            }
            Section(t("لغة الشرح", "لغة الشروح", "Explanation language")) {
                Text(
                    t(
                        "دي اللغة اللي هتظهر بيها كروت الكلمات النادرة والتحليل والقاموس.",
                        "هي لغة كروت الكلمات النادرة والتحليل الصرفي والمعجم.",
                        "Used for rare-word notes, parsing and the dictionary.",
                    ),
                    color = Muted, fontSize = 12.sp,
                )
                Choice("مصري", p.explainLang == "eg") { p.putExplainLang("eg") }
                Choice("العربية الفصحى", p.explainLang == "msa") { p.putExplainLang("msa") }
                Choice("English", p.explainLang == "en") { p.putExplainLang("en") }
                Choice(t("التلاتة ورا بعض", "الثلاث معًا", "All three"), p.explainLang == "all") { p.putExplainLang("all") }
            }
            Section(t("الترجمة الإنجليزي", "الترجمة الإنجليزية", "English Bible")) {
                Choice(t("KJV · الملك جيمس (١٦١١)", "KJV · ترجمة الملك جيمس (١٦١١)", "KJV · King James Version (1611)"), p.english == "kjv") { p.putEnglish("kjv") }
                Choice(t("BSB · بيريان الحديثة (٢٠٢٢)", "BSB · ترجمة بيريان الحديثة (٢٠٢٢)", "BSB · Berean Standard Bible (2022)"), p.english == "bsb") { p.putEnglish("bsb") }
                Spacer(Modifier.height(6.dp))
                Choice(t("فاندايك بس", "فاندايك فقط", "Van Dyke only"), p.readerMode == 0) { p.putReaderMode(0) }
                Choice(t("فاندايك وتحته الإنجليزي", "فاندايك وتحته الإنجليزية", "Van Dyke with English"), p.readerMode == 1) { p.putReaderMode(1) }
                Choice(t("الإنجليزي بس", "الإنجليزية فقط", "English only"), p.readerMode == 2) { p.putReaderMode(2) }
                Text(
                    t(
                        "الـ KJV والفاندايك الاتنين من النص المستلم، والـ BSB من النص النقدي الحديث، فهتلاقي فرق في كام آية زي يوحنا الأولى ٥:٧.",
                        "ترجمتا KJV وفاندايك عن النص المستلم، وBSB عن النص النقدي الحديث، فتختلف في آيات قليلة مثل يوحنا الأولى ٥:٧.",
                        "KJV and Van Dyke follow the Textus Receptus; BSB follows the modern critical text, so a few verses differ (e.g. 1 John 5:7).",
                    ),
                    color = Muted, fontSize = 12.sp,
                )
            }
            Section(t("حجم الخط", "حجم الخط", "Text size")) {
                Slider(
                    value = p.textScale, onValueChange = { p.putTextScale(it) }, valueRange = 0.8f..1.7f, steps = 8,
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold),
                )
                VerseText(1, listOf(Token(-1, "فِي الْبَدْءِ كَانَ الْكَلِمَةُ، وَالْكَلِمَةُ كَانَ عِنْدَ اللهِ.", false))) {}
                val en = "In the beginning was the Word, and the Word was with God."
                EnglishVerseText(1, EVerse(-1, en, emptyList<Span>())) { _, _, _ -> }
            }
            Section(t("خط الكتاب", "خط النص", "Arabic Bible font")) {
                Choice(t("نسخ (كلاسيكي)", "نسخ (كلاسيكي)", "Naskh (classic)"), p.scriptureFont == "naskh") { p.putScriptureFont("naskh") }
                Choice(t("بلكس (حديث)", "بلكس (حديث)", "Plex (modern)"), p.scriptureFont == "plex") { p.putScriptureFont("plex") }
            }
            Section(t("العربي", "العربية", "Arabic")) {
                Toggle(t("التشكيل", "التشكيل", "Vowel marks"), t("إظهار الحركات في نص فاندايك", "إظهار الحركات في نص فاندايك", "Show harakat in Van Dyke"), p.harakat) { p.putHarakat(it) }
            }
            Section(t("العبري", "العبرية", "Hebrew")) {
                Toggle(
                    t("علامات الترنيم", "علامات الترنيم", "Cantillation marks"),
                    t(
                        "علامات التنغيم (الطعاميم) فوق وتحت الحروف؛ النقط بتفضل ظاهرة دايمًا",
                        "علامات التنغيم (الطعاميم) فوق الحروف وتحتها؛ تبقى الحركات ظاهرة دائمًا",
                        "Accents above and below the letters; vowel points always stay",
                    ),
                    p.cantillation,
                ) { p.putCantillation(it) }
            }
            Section(t("نطق اليوناني", "نطق اليونانية", "Greek reading")) {
                Choice(t("الاتنين جنب بعض", "الاثنان معًا", "Both"), p.greekMode == 0) { p.putGreekMode(0) }
                Choice(t("الأكاديمي بس (إيراسموس)", "الأكاديمي فقط (إيراسموس)", "Academic only (Erasmian)"), p.greekMode == 1) { p.putGreekMode(1) }
                Choice(t("الحديث بس (زي الكنيسة)", "الحديث فقط (كما في الكنيسة)", "Modern only (as in church)"), p.greekMode == 2) { p.putGreekMode(2) }
            }
            Section(t("المصادر", "المصادر", "Sources")) {
                ExplainText(
                    t(
                        "نص فاندايك المشكَّل والمربوط بأرقام سترونج: سامي عبد الملك وArabic Bible Outreach Ministry، رخصة CC BY-SA 4.0.\n\n" +
                            "النص العبري واليوناني والتحليل والربط بين العربي والأصل: STEPBible.org (Tyndale House)، رخصة CC BY 4.0.\n\n" +
                            "الـ KJV بأرقام سترونج من eBible.org، والـ BSB وجداول ربطها بالأصل من Berean Bible، والاتنين ملكية عامة.\n\n" +
                            "المعاني الإنجليزي اللي في اللوحة مؤقتة من STEPBible لحد ما القاموس يخلص.\n\n" +
                            "كروت الكلمات النادرة مكتوبة بعد مراجعة هالوت، ومكتوب على كل كارت اتراجع ولا لأ. التطبيق مفيهوش أي نص من هالوت أو باور.\n\n" +
                            "الخطوط: IBM Plex Sans Arabic وNoto Naskh Arabic وNoto Serif Hebrew وNoto Serif، رخصة OFL.",
                        "نص فاندايك المشكّل والمرتبط بأرقام سترونج: سامي عبد الملك وArabic Bible Outreach Ministry، برخصة CC BY-SA 4.0.\n\n" +
                            "النص العبري واليوناني والتحليل الصرفي والربط بين العربية والأصل: STEPBible.org (Tyndale House)، برخصة CC BY 4.0.\n\n" +
                            "ترجمة KJV بأرقام سترونج من eBible.org، وترجمة BSB وجداول ربطها بالأصل من Berean Bible، وكلتاهما ملكية عامة.\n\n" +
                            "المعاني الإنجليزية في اللوحة مؤقتة من STEPBible إلى أن يكتمل المعجم.\n\n" +
                            "كُتبت كروت الكلمات النادرة بعد مراجعة HALOT، ويُذكر على كل كارت هل رُوجع أم لا. لا يحتوي التطبيق على أي نص من HALOT أو BDAG.\n\n" +
                            "الخطوط: IBM Plex Sans Arabic وNoto Naskh Arabic وNoto Serif Hebrew وNoto Serif، برخصة OFL.",
                        "Van Dyke text with vowels and Strong's tagging: Sami Abdel Malik and Arabic Bible Outreach Ministry, CC BY-SA 4.0.\n\n" +
                            "Hebrew and Greek texts, morphology and the Arabic-to-original alignment: STEPBible.org (Tyndale House), CC BY 4.0.\n\n" +
                            "KJV with Strong's numbers from eBible.org; BSB and its translation tables from the Berean Bible; both public domain.\n\n" +
                            "English meanings in the word panel are temporary STEPBible glosses until the dictionary is finished.\n\n" +
                            "Rare-word notes were written after checking HALOT, and each note says whether it was checked. The app contains no text from HALOT or BDAG.\n\n" +
                            "Fonts: IBM Plex Sans Arabic, Noto Naskh Arabic, Noto Serif Hebrew and Noto Serif, OFL.",
                    ),
                    if (com.fadysaeed.ketaby.data.ArText.english) "en" else "ar", size = 13,
                )
            }
        }
    }
}
