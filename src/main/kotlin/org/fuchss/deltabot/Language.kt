package org.fuchss.deltabot

/**
 * Definition of all supported languages with their locales.
 */
enum class Language(val locale: String) {
    ENGLISH("en_GB"), DEUTSCH("de_DE");

    override fun toString(): String {
        val name = super.toString()
        return name[0] + name.lowercase().substring(1)
    }
}
