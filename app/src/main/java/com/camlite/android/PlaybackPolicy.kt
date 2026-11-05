package com.camlite.android

/** A single selected camera; mute old owners before enabling the next one. */
object ExclusiveAudio {
    fun apply(ids: List<String>, selected: String, enable: (Int, Boolean) -> Unit) {
        ids.indices.forEach { enable(it, false) }
        ids.indexOf(selected).takeIf { it >= 0 }?.let { enable(it, true) }
    }
}

/** Buffered media is a local backlog estimate, never camera-to-screen latency. */
class BacklogRecovery {
    private var excessiveSince: Long? = null
    private var lastRecovery: Long? = null
    fun sample(now: Long, bufferedMs: Long, playing: Boolean): Boolean {
        if (!playing || bufferedMs < 4000) { excessiveSince = null; return false }
        val since = excessiveSince ?: now.also { excessiveSince = it }
        if (now - since < 15000 || lastRecovery?.let { now - it < 120000 } == true) return false
        excessiveSince = null; lastRecovery = now
        return true
    }
    fun resetStream() { excessiveSince = null }
}
