package com.cagri.real.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

enum class AppLanguage(val pref: String) {
    System("system"), Turkish("tr"), English("en");

    companion object {
        fun fromPref(value: String?) = entries.firstOrNull { it.pref == value } ?: System
    }
}

/**
 * Türkçe metin anahtar; İngilizce karşılığı assets/i18n/en.json'da. Karşılık yoksa Türkçe gösterilir.
 * Dil kaynağı: Android 13+ LocaleManager (sistemin uygulama başına dil ayarıyla aynı), eski sürümlerde kendi prefs'imiz.
 * [version] bir Compose state'i: t() çağıran her ekran dil değişince yeniden çizilir.
 */
object I18n {
    private const val PREFS = "real_i18n"
    private const val KEY = "language"

    private var version by mutableIntStateOf(0)
    private var choice: AppLanguage = AppLanguage.System
    private var dictionary: Map<String, String> = emptyMap()

    val selected: AppLanguage get() { version; return choice }

    /** Etkin dil İngilizce mi. Telefon dili Türkçe değilse "Telefonun dili" İngilizce demektir. */
    val isEnglish: Boolean get() {
        version
        return when (choice) {
            AppLanguage.English -> true
            AppLanguage.Turkish -> false
            AppLanguage.System -> phoneLanguage() != "tr"
        }
    }

    val locale: Locale get() = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("tr-TR")

    fun init(context: Context) {
        if (dictionary.isEmpty()) dictionary = loadDictionary(context)
        refresh(context)
    }

    /** Sistem ayarlarından dil değişmiş olabilir; MainActivity açılış/dönüşte çağırır. */
    fun refresh(context: Context) {
        val read = read(context)
        if (read != choice) {
            choice = read
            version++
        }
    }

    fun set(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.System) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language.pref)
        } else {
            prefs(context).edit().putString(KEY, language.pref).apply()
        }
        choice = language
        version++
    }

    private fun read(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) return AppLanguage.System
            return when (locales[0].language) {
                "tr" -> AppLanguage.Turkish
                "en" -> AppLanguage.English
                else -> AppLanguage.System
            }
        }
        return AppLanguage.fromPref(prefs(context).getString(KEY, null))
    }

    private fun phoneLanguage(): String = Resources.getSystem().configuration.locales[0].language

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadDictionary(context: Context): Map<String, String> = runCatching {
        val json = context.assets.open("i18n/en.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val obj = JSONObject(json)
        buildMap(obj.length()) { obj.keys().forEach { put(it, obj.getString(it)) } }
    }.getOrDefault(emptyMap())

    internal fun translate(tr: String): String = if (isEnglish) dictionary[tr] ?: tr else tr
}

/** t("Kaydet"), t("{0} madde kaldı", n) */
fun t(tr: String): String = I18n.translate(tr)

/**
 * Bağlam duyarlı çeviri: aynı Türkçe sözcük başka anlamlarda da kullanılıyorsa ("Tarih" = Date/History),
 * "genre:Tarih" gibi bağlam anahtarına bakar; yoksa normal [t]'ye düşer.
 */
fun tIn(context: String, tr: String): String {
    val key = "$context:$tr"
    val translated = I18n.translate(key)
    return if (translated == key) t(tr) else translated
}

fun t(tr: String, vararg args: Any?): String {
    var out = I18n.translate(tr)
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
    return out
}
