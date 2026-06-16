package com.example.ui

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

object LanguageManager {

    data class LanguageInfo(val code: String, val displayName: String, val displayLocal: String)

    val supportedLanguages = listOf(
        LanguageInfo("en", "English", "English"),
        LanguageInfo("hi", "Hindi", "हिन्दी"),
        LanguageInfo("es", "Spanish", "Español"),
        LanguageInfo("pt", "Portuguese (Brazil)", "Português (Brasil)"),
        LanguageInfo("ar", "Arabic", "العربية"),
        LanguageInfo("id", "Indonesian", "Bahasa Indonesia"),
        LanguageInfo("tr", "Turkish", "Türkçe"),
        LanguageInfo("vi", "Vietnamese", "Tiếng Việt"),
        LanguageInfo("th", "Thai", "ไทย"),
        LanguageInfo("ru", "Russian", "Русский"),
        LanguageInfo("fr", "French", "Français"),
        LanguageInfo("de", "German", "Deutsch"),
        LanguageInfo("ja", "Japanese", "日本語"),
        LanguageInfo("ko", "Korean", "한국어"),
        LanguageInfo("ur", "Urdu", "اردو")
    )

    private val _currentLanguage = mutableStateOf("en")
    val currentLanguage: State<String> = _currentLanguage

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        _currentLanguage.value = prefs.getString("selected_language", "en") ?: "en"
    }

    fun isLanguageSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.contains("selected_language")
    }

    fun setLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_language", langCode).apply()
        _currentLanguage.value = langCode
    }

}