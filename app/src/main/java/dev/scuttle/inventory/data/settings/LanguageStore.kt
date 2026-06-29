package dev.scuttle.inventory.data.settings

enum class AppLanguage(val tag: String, val label: String) {
    EN("en", "English"),
    NL("nl", "Dutch"),
}

interface LanguageStore {
    fun get(): AppLanguage
    fun set(language: AppLanguage)
}
