package com.camlite.android

/** The one recovery owner for a session; scheduler is injectable for bounded JVM tests. */
class RecoveryLoop(
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val connect: (Long) -> Unit,
    private val release: () -> Unit,
    private val offline: () -> Unit
) {
    private var active = false
    private var keys = emptySet<Long>()
    private var preferred: Long? = null
    private var bound: Long? = null
    private var cancelPending: (() -> Unit)? = null
    private var generation = 0L
    private var stagger = 0L
    private var waiting = true
    private var attempt = 0
    fun updateNetworks(available: Set<Long>, selected: Long?) {
        val previousPreferred = preferred
        keys = available; preferred = selected?.takeIf { it in keys }
        if (!active) return
        if (bound != null && bound in keys) {
            // A healthy player stays on its network. A failed player can resume on a newly
            // preferred LAN without waiting for a previous 30-second failure backoff.
            if (cancelPending != null && preferred != null && preferred != previousPreferred) request(stagger)
            return
        }
        if (bound != null || keys.isEmpty()) {
            cancel(); bound = null; release(); waiting = true
        }
        if (preferred == null) { waiting = true; offline() }
        else if (waiting) { waiting = false; request(stagger) }
    }
    fun start(delay: Long) {
        if (active) return
        active = true; stagger = delay; waiting = false
        request(delay)
    }
    private fun cancel() { generation++; cancelPending?.invoke(); cancelPending = null }
    private fun request(delay: Long) {
        cancel()
        if (preferred == null) { waiting = true; offline(); return }
        val token = generation
        cancelPending = schedule(delay) {
            if (active && token == generation) {
                cancelPending = null
                val key = preferred
                if (key == null || key !in keys) { waiting = true; offline() }
                else { bound = key; connect(key) }
            }
        }
    }
    fun fail(minimumDelayMs: Long = 0) {
        if (!active || cancelPending != null || waiting) return
        release()
        val delays = longArrayOf(1000, 2000, 5000, 10000, 30000)
        val delay = maxOf(delays[attempt.coerceAtMost(delays.lastIndex)], minimumDelayMs.coerceIn(0, 30000))
        attempt = (attempt + 1).coerceAtMost(delays.size)
        request(delay)
    }
    fun stable() { attempt = 0 }
    fun stop() { active = false; cancel(); bound = null; waiting = true; release() }
}
