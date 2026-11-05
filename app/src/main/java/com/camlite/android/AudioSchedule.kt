package com.camlite.android

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** Selected days refer to the day the interval starts, including overnight schedules. */
data class AudioSchedule(val startMinute: Int = 420, val endMinute: Int = 1050, val days: Set<Int> = (1..6).toSet()) {
    fun validate() { require(startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute); require(days.all { it in 1..7 }) }
    private fun windows(now: ZonedDateTime) = (-1L..8L).mapNotNull { offset ->
        val date = now.toLocalDate().plusDays(offset)
        if (date.dayOfWeek.value !in days) null else {
            val start = date.atTime(startMinute / 60, startMinute % 60).atZone(now.zone)
            val finish = date.plusDays(if (endMinute < startMinute) 1 else 0).atTime(endMinute / 60, endMinute % 60).atZone(now.zone)
            start.toInstant() to finish.toInstant()
        }
    }
    fun active(now: ZonedDateTime): Boolean = windows(now).any { (start,end) -> !now.toInstant().isBefore(start) && now.toInstant().isBefore(end) }
    fun nextDelayMs(now: ZonedDateTime): Long? = windows(now).flatMap { listOf(it.first,it.second) }
        .filter { it.isAfter(now.toInstant()) }.minOrNull()?.let { java.time.Duration.between(now.toInstant(),it).toMillis().coerceAtLeast(1) }
    fun json() = JSONObject().put("start",startMinute).put("end",endMinute).put("days",JSONArray(days.sorted()))
    companion object {
        fun from(j: JSONObject): AudioSchedule {
            val a = j.getJSONArray("days")
            return AudioSchedule(j.getInt("start"),j.getInt("end"),(0 until a.length()).map { a.getInt(it) }.toSet()).also { it.validate() }
        }
    }
}
