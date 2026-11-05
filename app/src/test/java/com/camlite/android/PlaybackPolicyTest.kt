package com.camlite.android

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class PlaybackPolicyTest {
    @Test fun audioSwitchNeverOverlapsAndUnknownOrMutedChoiceSilencesAll() {
        val ids=listOf("a","b","c","d")
        val states=BooleanArray(4)
        fun choose(id:String) = ExclusiveAudio.apply(ids,id) { i,on ->
            states[i]=on; assertTrue(states.count { it }<=1)
        }
        choose("a"); assertTrue(states[0]); choose("d"); assertTrue(states[3]); assertFalse(states[0])
        choose(""); assertFalse(states.any { it }); choose("removed"); assertFalse(states.any { it })
    }
    @Test fun legacySettingsStaySilentAndAudioCameraSurvivesSerialization() {
        assertEquals("",Settings.from(JSONObject("{}")).audioCameraId)
        val config=AppConfig(mutableListOf(CameraConfig(id="camera",name="Teste",host="192.0.2.1")))
        config.settings.audioCameraId="camera"
        val restored=AppConfig.from(JSONObject(config.json().toString()))
        assertEquals("camera",restored.settings.audioCameraId)
        assertEquals(config.cameras,restored.cameras)
    }
    @Test fun shortBurstsAndNonPlayingPeriodsDoNotRestartStreams() {
        val policy=BacklogRecovery()
        assertFalse(policy.sample(0,5000,true)); assertFalse(policy.sample(10000,5000,true))
        assertFalse(policy.sample(11000,300,true))
        assertFalse(policy.sample(20000,5000,true)); assertFalse(policy.sample(30000,5000,false))
        assertFalse(policy.sample(40000,5000,true)); assertFalse(policy.sample(50000,5000,true))
    }
    @Test fun persistentBacklogRecoversOneSessionAndCooldownSurvivesReconnect() {
        val a=BacklogRecovery(); val b=BacklogRecovery()
        assertFalse(a.sample(0,4000,true)); assertTrue(a.sample(15000,4000,true))
        a.resetStream()
        assertFalse(a.sample(20000,5000,true)); assertFalse(a.sample(40000,5000,true))
        assertFalse(b.sample(40000,500,true)); assertTrue(a.sample(135000,5000,true))
    }
}
