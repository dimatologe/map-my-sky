package com.codex.starmapper.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Sprachwahl der App: dem System folgen oder fest auf eine der unterstützten Sprachen stellen. */
enum class AppLang { System, German, English, Chinese, Spanish, Russian, Arabic, Japanese }

// BCP-47-Kürzel je Sprache -- eine einzige Quelle für SharedPreferences UND (Region-Info) die
// Wikipedia-Subdomain (zh.wikipedia.org, es.wikipedia.org, ...), da die Kürzel identisch sind.
private val LANGUAGE_TAGS = mapOf(
    AppLang.German to "de",
    AppLang.English to "en",
    AppLang.Chinese to "zh",
    AppLang.Spanish to "es",
    AppLang.Russian to "ru",
    AppLang.Arabic to "ar",
    AppLang.Japanese to "ja",
)
private val TAG_TO_LANGUAGE = LANGUAGE_TAGS.entries.associate { (lang, tag) -> tag to lang }

/**
 * Prozessweite, reaktive Sprachwahl. [current] ist Compose-State: Lesen (z. B. via [resolvedLanguageTag])
 * in einer Composable verfolgt Änderungen -> die ganze UI komponiert bei Sprachwechsel automatisch neu,
 * ohne Activity-Neustart. Persistenz in SharedPreferences; [load] beim App-Start, [set] beim Umschalten.
 *
 * Spiegelt jede Änderung zusätzlich an `AppCompatDelegate.setApplicationLocales` (s. [applyToAppCompat])
 * -- seit der AppCompat-Theme-Migration (MainActivity/styles.xml) Voraussetzung dafür, dass
 * `stringResource()`-Aufrufe (Android-Ressourcensystem) die richtige Sprache sehen. [current] bleibt
 * dabei die alleinige Quelle der Wahrheit (nicht umgekehrt aus AppCompatDelegate zurücklesen) -- das
 * erhält die sofortige Reactive-Recomposition ohne Activity-Neustart, extra für dieses Verhalten gebaut.
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "lang"
    private const val FALLBACK_KEY = "fallback_lang"

    var current by mutableStateOf(AppLang.System)
        private set

    /**
     * Fallback-Sprache für Region-Info (WikipediaSummaryService): wird versucht, wenn die aktuelle
     * App-Sprache keinen Artikel liefert, bevor als letzte Instanz immer Englisch versucht wird. Bewusst
     * getrennt von [current] -- eine eigene, unabhängige Einstellung, kein Bezug zur UI-Sprache selbst.
     */
    var fallbackLanguage by mutableStateOf(AppLang.English)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = TAG_TO_LANGUAGE[prefs.getString(KEY, null)] ?: AppLang.System
        fallbackLanguage = TAG_TO_LANGUAGE[prefs.getString(FALLBACK_KEY, null)] ?: AppLang.English
        applyToAppCompat(current)
    }

    fun set(context: Context, lang: AppLang) {
        current = lang
        val value = LANGUAGE_TAGS[lang] ?: "system"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value)
            .apply()
        applyToAppCompat(lang)
    }

    /** Meldet [lang] an AppCompatDelegate -- [System] als leere Liste (System-Standard folgen). */
    private fun applyToAppCompat(lang: AppLang) {
        val locales = if (lang == AppLang.System) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(LANGUAGE_TAGS[lang] ?: "en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setFallbackLanguage(context: Context, lang: AppLang) {
        fallbackLanguage = lang
        val value = LANGUAGE_TAGS[lang] ?: "en"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(FALLBACK_KEY, value)
            .apply()
    }

    /** BCP-47-Kürzel einer konkreten Sprache (nicht für [AppLang.System] -- s. [resolvedLanguageTag]). */
    fun tagFor(lang: AppLang): String = LANGUAGE_TAGS[lang] ?: "en"

    /** Aufgelöstes BCP-47-Kürzel: bei [AppLang.System] die Geräte-Sprache, falls unterstützt, sonst Englisch. */
    val resolvedLanguageTag: String
        get() = when (current) {
            AppLang.System -> {
                val deviceLang = Locale.getDefault().language
                TAG_TO_LANGUAGE.keys.firstOrNull { it.equals(deviceLang, ignoreCase = true) } ?: "en"
            }
            else -> LANGUAGE_TAGS[current] ?: "en"
        }
}
