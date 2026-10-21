package com.camlite.android

/** Memory caps affect encoded buffering, not the camera's resolution or frame rate. */
enum class PerformanceProfile(val label: String, val minBufferMs: Int, val maxBufferMs: Int, val targetBytes: Int) {
    NORMAL("Normal", 1000, 3000, 8 * 1024 * 1024),
    ECONOMIC("Econômico", 1000, 2000, 2 * 1024 * 1024)
}
