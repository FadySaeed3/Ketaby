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

    var lastBook by mutableIntStateOf(sp.getInt("lastBook", 1)); private set
    var lastChapter by mutableIntStateOf(sp.getInt("lastChapter", 1)); private set
    var lastVerseIndex by mutableIntStateOf(sp.getInt("lastVerseIndex", 0)); private set

    fun setTextScale(v: Float) { textScale = v; sp.edit().putFloat("textScale", v).apply() }
    fun setGreekMode(v: Int) { greekMode = v; sp.edit().putInt("greekMode", v).apply() }
    fun setCantillation(v: Boolean) { cantillation = v; sp.edit().putBoolean("cantillation", v).apply() }
    fun setHarakat(v: Boolean) { harakat = v; sp.edit().putBoolean("harakat", v).apply() }
    fun setScriptureFont(v: String) { scriptureFont = v; sp.edit().putString("scriptureFont", v).apply() }
    fun setInterlinear(v: Boolean) { interlinear = v; sp.edit().putBoolean("interlinear", v).apply() }

    fun savePosition(book: Int, ch: Int, verseIndex: Int) {
        lastBook = book; lastChapter = ch; lastVerseIndex = verseIndex
        sp.edit().putInt("lastBook", book).putInt("lastChapter", ch).putInt("lastVerseIndex", verseIndex).apply()
    }
}
