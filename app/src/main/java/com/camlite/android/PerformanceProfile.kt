package com.camlite.android

/** Memory caps affect encoded buffering, not the camera's resolution or frame rate. */
enum class PerformanceProfile(val label: String, val minBufferMs: Int, val maxBufferMs: Int, val targetBytes: Int) {
    NORMAL("Normal", 300, 800, 8 * 1024 * 1024),
    ECONOMIC("Econômico", 300, 600, 2 * 1024 * 1024)
}
