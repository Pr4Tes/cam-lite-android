package com.camlite.android

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScreenAndMigrationTest {
    @Test fun legacyCredentialsOrderAndPreferencesSurviveSchemaUpgradeAndRoundTrip() {
        val legacy = JSONObject("""{"cameras":[
          {"id":"second","name":"Portão","host":"camera.local","port":8554,"user":"operador","password":"fake:@/%?#","channel":3,"substream":false,"customUrl":"rtsp://camera.local:8554/custom?channel=3"},
          {"id":"first","name":"Quintal","host":"192.0.2.2","port":554,"user":"guest","password":"fake2","channel":1,"substream":true,"customUrl":""}],
          "settings":{"layout":2,"orientation":1,"keepOn":false,"brightness":23,"startMonitor":true,"home":true,"performance":"ECONOMIC"}}""")
        val migrated = AppConfig.from(legacy)
        val saved = migrated.json()
        val reloaded = AppConfig.from(JSONObject(saved.toString()))
        assertEquals(migrated, reloaded)
        assertEquals(listOf("second", "first"), reloaded.cameras.map { it.id })
        assertEquals("fake:@/%?#", reloaded.cameras[0].password)
        assertEquals(legacy.getJSONArray("cameras").toString(), saved.getJSONArray("cameras").toString())
        for (key in listOf("layout", "orientation", "keepOn", "brightness", "startMonitor", "home", "performance"))
            assertEquals(legacy.getJSONObject("settings").get(key), saved.getJSONObject("settings").get(key))
        assertEquals(2, saved.getInt("schemaVersion"))
        assertFalse(reloaded.settings.screen.scheduledBrightness)
        assertEquals(23, reloaded.settings.brightness)
        assertTrue(reloaded.settings.screen.touchLock)
        val customScreen = ScreenSettings(true, 22 * 60, 6 * 60, 55, 7, false, false, 45)
        migrated.settings.screen = customScreen
        assertEquals(customScreen, AppConfig.from(JSONObject(migrated.json().toString())).settings.screen)
        assertEquals(PerformanceProfile.NORMAL, Settings.from(JSONObject("""{"brightness":-1}""")).performance)
    }
    @Test fun invalidOrFutureSchemaCannotTurnIntoEmptyConfig() {
        for (json in listOf(JSONObject("""{"schemaVersion":99,"cameras":[],"settings":{}}"""), JSONObject("""{"settings":{}}"""))) {
            try { AppConfig.from(json); fail("Invalid input accepted") } catch (_: IllegalArgumentException) {} catch (_: org.json.JSONException) {}
        }
        val valid = AppConfig().json()
        val cameras = valid.getJSONArray("cameras")
        repeat(5) { cameras.put(CameraConfig(id = "$it", name = "Fictícia", host = "192.0.2.1").json()) }
        try { AppConfig.from(valid); fail("More than four cameras accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun dayNightExactBoundariesAndMidnightCrossing() {
        val settings = ScreenSettings()
        assertEquals(10, BrightnessSchedule.percentage(settings, 419))
        assertEquals(40, BrightnessSchedule.percentage(settings, 420))
        assertEquals(40, BrightnessSchedule.percentage(settings, 1139))
        assertEquals(10, BrightnessSchedule.percentage(settings, 1140))
        assertEquals(10, BrightnessSchedule.percentage(settings, 0))
        settings.dayStart = 22 * 60; settings.nightStart = 6 * 60
        assertEquals(40, BrightnessSchedule.percentage(settings, 23 * 60))
        assertEquals(40, BrightnessSchedule.percentage(settings, 5 * 60))
        assertEquals(10, BrightnessSchedule.percentage(settings, 6 * 60))
    }
    @Test fun schedulingUsesNextBoundaryAndHandlesTimezoneOffsetTransition() {
        val settings = ScreenSettings()
        val now = ZonedDateTime.of(2026, 10, 12, 18, 59, 30, 0, ZoneId.of("America/Cuiaba"))
        assertEquals(30000L, BrightnessSchedule.delayUntilChange(settings, now))
        assertEquals(12 * 60 * 60 * 1000L, BrightnessSchedule.delayUntilChange(settings, now.withHour(19).withMinute(0).withSecond(0)))
        val fallBack = ZonedDateTime.of(2026, 11, 1, 1, 59, 30, 0, ZoneId.of("America/New_York")).withEarlierOffsetAtOverlap()
        assertEquals(30000L, BrightnessSchedule.delayUntilChange(settings, fallBack))
    }
    @Test fun touchTapPartialWrongDirectionAndCancelNeverUnlockButFullSwipeDoes() {
        val g = TouchUnlockGesture()
        g.begin(10f, 10f, 0f, 0f, 300f, 50f); assertFalse(g.finish())
        g.begin(10f, 10f, 0f, 0f, 300f, 50f); g.move(100f, 10f, 0f, 300f, 50f); assertFalse(g.finish())
        assertFalse(g.begin(280f, 10f, 0f, 0f, 300f, 50f)); g.move(10f, 10f, 0f, 300f, 50f); assertFalse(g.finish())
        g.begin(10f, 10f, 0f, 0f, 300f, 50f); g.move(280f, 10f, 0f, 300f, 50f); g.cancel(); assertFalse(g.finish())
        g.begin(10f, 10f, 0f, 0f, 300f, 50f); g.move(280f, 200f, 0f, 300f, 50f); assertFalse(g.finish())
        g.begin(10f, 10f, 0f, 0f, 300f, 50f); g.move(100f, 10f, 0f, 300f, 50f); g.move(280f, 10f, 0f, 300f, 50f); assertTrue(g.finish())
        g.begin(10f, 10f, 0f, 0f, 300f, 50f); assertFalse(g.finish()) // can relock after an unlock
    }
}
