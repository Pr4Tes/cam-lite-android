package com.camlite.android

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.ZonedDateTime

class AudioScheduleTest {
    private fun time(s:String) = ZonedDateTime.parse(s)
    @Test fun storeHoursAreInclusiveAtStartExclusiveAtEndAndSundaySilent() {
        val s=AudioSchedule()
        assertFalse(s.active(time("2026-11-05T06:59:59-04:00")))
        assertTrue(s.active(time("2026-11-05T07:00:00-04:00")))
        assertTrue(s.active(time("2026-11-07T17:29:59-04:00")))
        assertFalse(s.active(time("2026-11-07T17:30:00-04:00")))
        assertFalse(s.active(time("2026-11-08T10:00:00-04:00")))
        assertEquals(1000L,s.nextDelayMs(time("2026-11-05T06:59:59-04:00")))
    }
    @Test fun overnightBelongsToStartingDayAndEmptyDaysStaySilent() {
        val s=AudioSchedule(22*60,2*60,setOf(6))
        assertTrue(s.active(time("2026-11-08T01:00:00-04:00")))
        assertFalse(s.active(time("2026-11-08T02:00:00-04:00")))
        assertFalse(s.active(time("2026-11-08T23:00:00-04:00")))
        assertNull(s.copy(days=emptySet()).nextDelayMs(time("2026-11-08T01:00:00-04:00")))
    }
    @Test fun migrationAndRoundtripPreserveScheduleAndRejectInvalidTimes() {
        assertEquals(AudioSchedule(),Settings.from(JSONObject("{}")).audioSchedule)
        val s=Settings(audioCameraId="store",audioSchedule=AudioSchedule(450,1020,setOf(2,5)))
        assertEquals(s,Settings.from(JSONObject(s.json().toString())))
        try { AudioSchedule(100,100).validate(); fail() } catch (_:IllegalArgumentException) {}
        try { AudioSchedule(days=setOf(8)).validate(); fail() } catch (_:IllegalArgumentException) {}
    }
    @Test fun timezoneChangeUsesLocalHoursAndDstTransitionIsAnInstant() {
        val s=AudioSchedule(7*60,17*60+30,setOf(7))
        assertTrue(s.active(time("2026-11-08T07:01:00+01:00")))
        assertFalse(s.active(time("2026-11-08T06:01:00Z")))
        assertEquals(3600000L,AudioSchedule(3*60,4*60,setOf(7)).nextDelayMs(time("2026-03-08T01:00:00-05:00[America/New_York]")))
    }
    @Test fun lagGrowthResetsOnPtsRewindAndDoesNotTreatStaticScenesAsStalls() {
        val t=FrameLagTracker()
        t.frame(1000,0); t.frame(2000,1_000_000); assertEquals(0L,t.lagMs)
        t.frame(7000,2_000_000); assertEquals(4000L,t.lagMs)
        t.frame(8000,0); assertEquals(0L,t.lagMs)
        t.reset(); t.frame(20000,5_000_000); assertEquals(0L,t.lagMs)
    }
}
