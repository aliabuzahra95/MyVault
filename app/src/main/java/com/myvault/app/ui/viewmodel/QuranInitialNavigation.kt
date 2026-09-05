package com.myvault.app.ui.viewmodel

/** Queues explicit navigation while the initial reader preferences are loading. */
internal class QuranInitialNavigation {
    data class Target(val surah: Int, val ayah: Int, val exact: Boolean)
    private var ready = false
    private var pending: Target? = null

    fun request(surah: Int, ayah: Int): Target? {
        val target = Target(surah, ayah, exact = true)
        if (ready) return target
        pending = target
        return null
    }

    fun initialize(savedSurah: Int, savedAyah: Int): Target {
        ready = true
        return (pending ?: Target(savedSurah, savedAyah, exact = false)).also { pending = null }
    }
}
