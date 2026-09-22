package com.myvault.app.data.preferences

enum class NoteTitleColorPreference(val storedValue: String, val label: String) {
    Standard("standard", "Standard"),
    HighContrast("high_contrast", "High contrast"),
    Accent("accent", "Accent"),
    Green("green", "Green"),
    Gold("gold", "Gold");

    companion object {
        fun fromStoredValue(value: String?): NoteTitleColorPreference =
            entries.firstOrNull { it.storedValue == value } ?: Standard
    }
}
