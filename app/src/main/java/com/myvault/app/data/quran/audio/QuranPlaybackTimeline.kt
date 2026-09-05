package com.myvault.app.data.quran.audio

/** Playback policy uses sampled media time, never elapsed wall time. */
class QuranPlaybackTimeline(val timing: QuranTimingMap, startAyah: Int, initialMode: QuranListeningMode) {
    var mode = initialMode
        private set
    var target = requireNotNull(timing.ayah(startAyah))
        private set
    var boundaryReached = false
        private set

    fun shouldPause(positionMs: Long): Boolean = mode == QuranListeningMode.ThisAyah && positionMs >= target.endMs

    fun reachedBoundary() { boundaryReached = true }

    fun seek(positionMs: Long) {
        timing.at(positionMs)?.let { target = it }
        boundaryReached = false
    }

    fun changeMode(next: QuranListeningMode, positionMs: Long): Long? {
        if (next == mode) return null
        val wasBoundary = boundaryReached
        mode = next
        if (next == QuranListeningMode.ThisAyah) {
            timing.at(positionMs)?.let { target = it }
            boundaryReached = false
            return null
        }
        if (!wasBoundary) return null
        val index = timing.ayahs.indexOf(target)
        val following = timing.ayahs.getOrNull(index + 1) ?: return null
        boundaryReached = false
        target = following
        return following.startMs
    }
}
