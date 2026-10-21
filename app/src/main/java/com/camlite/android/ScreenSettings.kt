package com.camlite.android

import org.json.JSONObject
import java.time.Duration
import java.time.ZonedDateTime

data class ScreenSettings(
    var scheduledBrightness: Boolean = false, var dayStart: Int = 7 * 60, var nightStart: Int = 19 * 60,
    var dayBrightness: Int = 40, var nightBrightness: Int = 10,
    var touchLock: Boolean = true, var autoLock: Boolean = true, var autoLockSeconds: Int = 30
) {
    fun validate() {
        require(dayStart in 0..1439 && nightStart in 0..1439 && dayStart != nightStart)
        require(dayBrightness in 1..100 && nightBrightness in 1..100 && autoLockSeconds in 5..3600)
    }
    fun json() = JSONObject().put("scheduledBrightness", scheduledBrightness).put("dayStart", dayStart).put("nightStart", nightStart)
        .put("dayBrightness", dayBrightness).put("nightBrightness", nightBrightness).put("touchLock", touchLock).put("autoLock", autoLock).put("autoLockSeconds", autoLockSeconds)
    companion object {
        fun from(j: JSONObject) = ScreenSettings(j.optBoolean("scheduledBrightness"), j.optInt("dayStart", 420), j.optInt("nightStart", 1140),
            j.optInt("dayBrightness", 40), j.optInt("nightBrightness", 10), j.optBoolean("touchLock", true), j.optBoolean("autoLock", true), j.optInt("autoLockSeconds", 30)).apply { validate() }
    }
}

object BrightnessSchedule {
    fun percentage(settings: ScreenSettings, minute: Int): Int {
        settings.validate()
        val dayDuration = (settings.nightStart - settings.dayStart + 1440) % 1440
        val elapsed = (minute - settings.dayStart + 1440) % 1440
        return if (elapsed < dayDuration) settings.dayBrightness else settings.nightBrightness
    }
    fun delayUntilChange(settings: ScreenSettings, now: ZonedDateTime): Long {
        settings.validate()
        val candidates = (0L..1L).flatMap { days -> listOf(settings.dayStart, settings.nightStart).flatMap { minute ->
            val local = now.toLocalDate().plusDays(days).atTime(minute / 60, minute % 60)
            val offsets = now.zone.rules.getValidOffsets(local)
            if (offsets.isEmpty()) listOf(local.atZone(now.zone)) else offsets.map { ZonedDateTime.ofLocal(local, now.zone, it) }
        } }.filter { it.isAfter(now) }.toMutableList()
        // Re-evaluate once at an offset transition, including a repeated/skipped clock hour.
        now.zone.rules.nextTransition(now.toInstant())?.let { candidates.add(it.instant.atZone(now.zone)) }
        val next = candidates.minOrNull()!!
        return Duration.between(now, next).toMillis().coerceAtLeast(1)
    }
}
