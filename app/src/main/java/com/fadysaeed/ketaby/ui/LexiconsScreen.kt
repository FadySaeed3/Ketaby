package com.fadysaeed.ketaby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fadysaeed.ketaby.data.Lexicons
import kotlinx.coroutines.launch

/** Add or replace the owner's HALOT and BDAG PDFs. */
@Composable
fun LexiconsScreen(nav: Nav) {
    Scaffold(
        containerColor = Color.Black,
        topBar = { KTopBar(t("قواميسك: هالوت وباور", "معاجمك: HALOT وBDAG", "Your lexicons: HALOT & BDAG"), onBack = { nav.pop() }) { HomeAction(nav) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ExplainText(
                t(
                    "الملفات دي ملكك ومش جوه التطبيق. اختارها مرة واحدة من الموبايل، والتطبيق هياخد منها نسخة في مساحته الخاصة ويفتح منها الصفحات من غير نت. لما تدوس \"افتح في هالوت\" في لوحة أي كلمة، هيفتح على المدخل بالظبط.",
                    "هذه الملفات ملكك وليست داخل التطبيق. اخترها مرة واحدة من جهازك، فيحتفظ التطبيق بنسخة في مساحته الخاصة ويفتح منها الصفحات دون اتصال. وعند الضغط على \"افتح في HALOT\" في لوحة أي كلمة يُفتح المدخل نفسه.",
                    "These files are yours and are not part of the app. Pick each once from your phone; the app keeps a private copy and opens pages from it offline. \"Open in HALOT\" in any word panel then jumps to the exact entry.",
                ),
                if (com.fadysaeed.ketaby.data.ArText.english) "en" else "ar", size = 14, color = Muted,
            )
            Spacer(Modifier.height(16.dp))
            LexCard(Lexicons.HALOT)
            Spacer(Modifier.height(14.dp))
            LexCard(Lexicons.BDAG)
        }
    }
}

@Composable
private fun LexCard(lex: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val v = Lexicons.version.intValue
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val has = Lexicons.has(ctx, lex)
    val ok = Lexicons.matches(ctx, lex)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; error = null; progress = 0f
            scope.launch {
                try {
                    Lexicons.import(ctx, lex, uri) { progress = it }
                } catch (e: Exception) {
                    error = e.toString()
                }
                busy = false
            }
        }
    }
    val full = if (lex == Lexicons.HALOT) "HALOT · Hebrew and Aramaic Lexicon of the Old Testament" else "BDAG · Greek-English Lexicon of the New Testament"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
        Text(Lexicons.name(lex), color = Gold, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(full, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        val (status, color) = when {
            busy -> t("بننسخ الملف…", "جارٍ نسخ الملف…", "Copying…") to Teal
            !has -> t("لسه ماضفتش الملف", "لم يُضف الملف بعد", "Not added yet") to Muted
            ok == true -> t("موجود ✓ ونفس النسخة اللي اتعمل عليها الفهرس، فالمداخل هتفتح في مكانها بالظبط", "موجود ✓ ومطابق للنسخة التي بُني عليها الفهرس، فتُفتح المداخل في مواضعها بدقة", "Added ✓ and identical to the indexed copy, so entries open exactly in place") to Teal
            else -> t("موجود، بس مش نفس النسخة اللي اتعمل عليها الفهرس؛ أرقام الصفحات ممكن ماتطابقش", "موجود، لكنه ليس النسخة التي بُني عليها الفهرس؛ قد لا تتطابق الصفحات", "Added, but not the copy the index was built from; pages may not line up") to Rose
        }
        Text(status, color = color, fontSize = 14.sp)
        if (busy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = PanelHi)
        }
        error?.let { Text(it, color = Rose, fontSize = 12.sp) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { launcher.launch(arrayOf("application/pdf")) }, enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
            ) { Text(if (has) t("بدّل الملف", "استبدل الملف", "Replace file") else t("اختار الملف", "اختر الملف", "Choose file")) }
            if (has && !busy) OutlinedButton(onClick = { Lexicons.delete(ctx, lex) }) {
                Text(t("امسحه", "احذفه", "Remove"), color = Muted)
            }
        }
    }
}
