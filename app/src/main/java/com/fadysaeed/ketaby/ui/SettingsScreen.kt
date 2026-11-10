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
    Scaffold(containerColor = Color.Black, topBar = { KTopBar("الإعدادات", onBack = { nav.pop() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section("حجم الخط") {
                Slider(
                    value = p.textScale, onValueChange = { p.setTextScale(it) }, valueRange = 0.8f..1.7f, steps = 8,
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold),
                )
                VerseText(1, listOf(Token(-1, "فِي الْبَدْءِ كَانَ الْكَلِمَةُ، وَالْكَلِمَةُ كَانَ عِنْدَ اللهِ.", false))) {}
            }
            Section("خط الكتاب") {
                Choice("نسخ (كلاسيكي)", p.scriptureFont == "naskh") { p.setScriptureFont("naskh") }
                Choice("بلكس (حديث)", p.scriptureFont == "plex") { p.setScriptureFont("plex") }
            }
            Section("العربي") {
                Toggle("التشكيل", "إظهار الحركات في نص فاندايك", p.harakat) { p.setHarakat(it) }
            }
            Section("العبري") {
                Toggle("علامات الترنيم", "علامات التنغيم (الطعاميم) فوق وتحت الحروف؛ النقط بتفضل ظاهرة دايمًا", p.cantillation) { p.setCantillation(it) }
            }
            Section("نطق اليوناني") {
                Choice("الاتنين جنب بعض", p.greekMode == 0) { p.setGreekMode(0) }
                Choice("الأكاديمي بس (إيراسموس)", p.greekMode == 1) { p.setGreekMode(1) }
                Choice("الحديث بس (زي الكنيسة)", p.greekMode == 2) { p.setGreekMode(2) }
            }
            Section("المصادر") {
                Text(
                    "نص فاندايك المشكَّل والمربوط بأرقام سترونج: سامي عبد الملك وArabic Bible Outreach Ministry، رخصة CC BY-SA 4.0.\n\n" +
                        "النص العبري واليوناني والتحليل والربط بين العربي والأصل: STEPBible.org (Tyndale House)، رخصة CC BY 4.0.\n\n" +
                        "المعاني الإنجليزي اللي في اللوحة مؤقتة من STEPBible لحد ما القاموس العربي يخلص.\n\n" +
                        "كروت الكلمات النادرة مكتوبة بالعربي بعد مراجعة هالوت، ومكتوب على كل كارت اتراجع ولا لأ. التطبيق مفيهوش أي نص من هالوت أو باور.\n\n" +
                        "الخطوط: IBM Plex Sans Arabic وNoto Naskh Arabic وNoto Serif Hebrew وNoto Serif، رخصة OFL.",
                    color = Ink, fontSize = 13.sp, lineHeight = 22.sp,
                )
            }
        }
    }
}
