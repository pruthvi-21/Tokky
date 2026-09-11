package com.boxy.authenticator.domain.models

data class AppLocale(
    val name: String,
    val nativeName: String,
    val localeTag: String,
) {
    val isSystemDefault: Boolean get() = localeTag.isEmpty()

    fun matches(other: String): Boolean {
        return localeTag == other || localeTag.substringBefore("-") == other.substringBefore("-")
    }

    companion object {
        val SystemDefault = AppLocale("", "", "")
    }
}

// Keep in sync with androidMain/res/xml/locales_config.xml.
// @formatter:off
val AppLocales = listOf(
    AppLocale("Arabic",                 "العربية",          "ar"),
    AppLocale("Chinese (Simplified)",   "简体中文",          "zh-CN"),
    AppLocale("Chinese (Traditional)",  "繁體中文",          "zh-TW"),
    AppLocale("French",                 "Français",         "fr"),
    AppLocale("German",                 "Deutsch",          "de"),
    AppLocale("Hindi",                  "हिन्दी",           "hi"),
    AppLocale("Indonesian",             "Bahasa Indonesia", "id"),
    AppLocale("Italian",                "Italiano",         "it"),
    AppLocale("Japanese",               "日本語",            "ja"),
    AppLocale("Korean",                 "한국어",             "ko"),
    AppLocale("Portuguese",             "Português",        "pt-PT"),
    AppLocale("Russian",                "Русский",          "ru"),
    AppLocale("Spanish",                "Español",          "es"),
)
// @formatter:on
