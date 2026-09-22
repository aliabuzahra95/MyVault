package com.myvault.app.data.preferences

enum class NoteTitleColorPreference(val storedValue: String) {
    Standard("standard"),
    HighContrast("high_contrast");

    companion object {
        fun fromStoredValue(value: String?): NoteTitleColorPreference =
            entries.firstOrNull { it.storedValue == value } ?: Standard
    }
}
