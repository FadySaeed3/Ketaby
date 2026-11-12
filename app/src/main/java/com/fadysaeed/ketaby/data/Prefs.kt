package com.fadysaeed.ketaby.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User settings, observable from Compose and saved to SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var textScale by mutableFloatStateOf(sp.getFloat("textScale", 1f)); private set
    /** 0 = both readings, 1 = academic only, 2 = modern only */
    var greekMode by mutableIntStateOf(sp.getInt("greekMode", 0)); private set
    var cantillation by mutableStateOf(sp.getBoolean("cantillation", false)); private set
    var harakat by mutableStateOf(sp.getBoolean("harakat", true)); private set
    /** "naskh" or "plex" */
    var scriptureFont by mutableStateOf(sp.getString("scriptureFont", "naskh") ?: "naskh"); private set
    var interlinear by mutableStateOf(sp.getBoolean("interlinear", false)); private set
    /** interface language: "eg" (Egyptian), "msa" (Modern Standard Arabic), "en" */
    var uiLang by mutableStateOf(sp.getString("uiLang", "eg") ?: "eg"); private set
    /** explanation language: "eg", "msa", "en" or "all" */
    var explainLang by mutableStateOf(sp.getString("explainLang", "eg") ?: "eg"); private set
    /** English Bible: "kjv" or "bsb" */
    var english by mutableStateOf(sp.getString("english", "kjv") ?: "kjv"); private set
    /** 0 = Van Dyke only, 1 = Van Dyke + English, 2 = English only */
    var readerMode by mutableIntStateOf(sp.getInt("readerMode", 0)); private set
    /** show the imported Jesuit translation under every Van Dyke verse */
    var jesuitUnder by mutableStateOf(sp.getBoolean("jesuitUnder", false)); private set

    var lastBook by mutableIntStateOf(sp.getInt("lastBook", 1)); private set
    var lastChapter by mutableIntStateOf(sp.getInt("lastChapter", 1)); private set
    var lastVerseIndex by mutableIntStateOf(sp.getInt("lastVerseIndex", 0)); private set

    fun putTextScale(v: Float) { textScale = v; sp.edit().putFloat("textScale", v).apply() }
    fun putGreekMode(v: Int) { greekMode = v; sp.edit().putInt("greekMode", v).apply() }
    fun putCantillation(v: Boolean) { cantillation = v; sp.edit().putBoolean("cantillation", v).apply() }
    fun putHarakat(v: Boolean) { harakat = v; sp.edit().putBoolean("harakat", v).apply() }
    fun putScriptureFont(v: String) { scriptureFont = v; sp.edit().putString("scriptureFont", v).apply() }
    fun putInterlinear(v: Boolean) { interlinear = v; sp.edit().putBoolean("interlinear", v).apply() }
    fun putUiLang(v: String) { uiLang = v; sp.edit().putString("uiLang", v).apply() }
    fun putExplainLang(v: String) { explainLang = v; sp.edit().putString("explainLang", v).apply() }
    fun putEnglish(v: String) { english = v; sp.edit().putString("english", v).apply() }
    fun putJesuitUnder(v: Boolean) { jesuitUnder = v; sp.edit().putBoolean("jesuitUnder", v).apply() }
    fun putReaderMode(v: Int) { readerMode = v; sp.edit().putInt("readerMode", v).apply() }

    /** Recently opened chapters, newest first: "book:ch,book:ch,…" */
    var recent by mutableStateOf(sp.getString("recent", "") ?: ""); private set

    fun recentList(): List<Pair<Int, Int>> = recent.split(',').mapNotNull { e ->
        val p = e.split(':'); if (p.size == 2) (p[0].toIntOrNull() ?: return@mapNotNull null) to (p[1].toIntOrNull() ?: return@mapNotNull null) else null
    }

    fun pushRecent(book: Int, ch: Int) {
        val l = (listOf(book to ch) + recentList().filter { it != book to ch }).take(8)
        recent = l.joinToString(",") { "${it.first}:${it.second}" }
        sp.edit().putString("recent", recent).apply()
    }

    fun savePosition(book: Int, ch: Int, verseIndex: Int) {
        lastBook = book; lastChapter = ch; lastVerseIndex = verseIndex
        sp.edit().putInt("lastBook", book).putInt("lastChapter", ch).putInt("lastVerseIndex", verseIndex).apply()
    }
}
