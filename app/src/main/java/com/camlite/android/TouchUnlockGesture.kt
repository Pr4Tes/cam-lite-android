package com.camlite.android

/** Requires a drag from the handle to the end; taps and cancelled gestures fail. */
class TouchUnlockGesture {
    var progress: Float = 0f
        private set
    private var dragging = false
    private var startX = 0f
    fun begin(x: Float, y: Float, left: Float, top: Float, width: Float, height: Float): Boolean {
        cancel()
        dragging = width > 0 && x in left..(left + height) && y in top..(top + height)
        startX = x
        return dragging
    }
    fun move(x: Float, y: Float, top: Float, width: Float, height: Float) {
        if (!dragging) return
        if (y !in (top - height / 2)..(top + height * 1.5f)) { cancel(); return }
        progress = ((x - startX) / (width - height).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    fun finish(): Boolean {
        val unlocked = dragging && progress >= 1f
        cancel()
        return unlocked
    }
    fun cancel() { dragging = false; progress = 0f }
}
