package com.example.myapplication

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Simple language configuration for multi-language support
 */
enum class Language(
    val displayName: String,
    val code: String,
    val voskModelName: String,
    val mlKitCode: String,
    val isAutoDetect: Boolean = false
) {
    AUTO_DETECT("🌐 Auto-detect", "auto", "", "", true),
    TURKISH("🇹🇷 Türkçe", "tr", "vosk-model-small-tr-0.3", TranslateLanguage.TURKISH),
    SPANISH("🇪🇸 Español", "es", "vosk-model-small-es-0.42", TranslateLanguage.SPANISH),
    RUSSIAN("🇷🇺 Русский", "ru", "vosk-model-small-ru-0.22", TranslateLanguage.RUSSIAN),
    FRENCH("🇫🇷 Français", "fr", "vosk-model-small-fr-0.22", TranslateLanguage.FRENCH);

    companion object {
        fun getDefault(): Language = AUTO_DETECT
        fun getLanguageByCode(code: String): Language = values().find { it.code == code } ?: getDefault()
        fun getLanguagesWithSTT(): List<Language> = values().filter { !it.isAutoDetect && it.voskModelName.isNotEmpty() }
    }

    override fun toString(): String = displayName
    
    fun hasSTTSupport(): Boolean = !isAutoDetect && voskModelName.isNotEmpty()
    fun hasTranslationSupport(): Boolean = !isAutoDetect && mlKitCode.isNotEmpty()
} 