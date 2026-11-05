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

/** Tracks growth since the fastest observed presentation, not absolute camera latency. */
class FrameLagTracker {
    private var anchorTime = 0L
    private var anchorPts = 0L
    private var previousPts: Long? = null
    private var minimum = 0L
    @Volatile var lagMs = 0L; private set
    @Synchronized fun reset() { previousPts = null; lagMs = 0 }
    @Synchronized fun frame(nowMs: Long, ptsUs: Long) {
        if (previousPts == null || ptsUs < previousPts!!) {
            anchorTime = nowMs; anchorPts = ptsUs; minimum = 0
        }
        previousPts = ptsUs
        val delta = nowMs - anchorTime - (ptsUs - anchorPts) / 1000
        minimum = minOf(minimum, delta)
        lagMs = maxOf(0, delta - minimum)
    }
}
