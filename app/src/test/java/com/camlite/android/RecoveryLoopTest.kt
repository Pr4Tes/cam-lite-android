package com.camlite.android

import org.junit.Assert.*
import org.junit.Test

class RecoveryLoopTest {
    private class Harness {
        data class Job(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
        var time = 0L
        val jobs = mutableListOf<Job>()
        val connects = mutableListOf<Pair<Long, Long>>()
        var releases = 0
        var waiting = 0
        val loop = RecoveryLoop(schedule = { delay, action ->
            val job = Job(time + delay, action); jobs.add(job); { job.cancelled = true }
        }, connect = { key -> connects.add(time to key) }, release = { releases++ }, offline = { waiting++ })
        fun advance(ms: Long) {
            val end = time + ms
            while (true) {
                val job = jobs.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
                job.cancelled = true; time = job.at; job.action()
            }
            time = end
        }
    }
    @Test fun noNetworkNoRetryThenStaggeredReturn() {
        val cameras = List(4) { Harness() }
        cameras.forEachIndexed { i, h -> h.loop.start(i * 1000L); h.advance(60000); assertTrue(h.connects.isEmpty()) }
        cameras.forEach { it.loop.updateNetworks(setOf(7), 7) }
        cameras.forEach { it.advance(0) }
        assertEquals(1, cameras.sumOf { it.connects.size })
        cameras.forEach { it.advance(999) }; assertEquals(1, cameras.sumOf { it.connects.size })
        cameras.forEach { it.advance(2001) }
        assertEquals(listOf(60000L, 61000L, 62000L, 63000L), cameras.map { it.connects.single().first })
    }
    @Test fun networkLossCancelsRetryAndKeepsOtherConnectionAlive() {
        val lost = Harness(); val unaffected = Harness()
        lost.loop.updateNetworks(setOf(1), 1); unaffected.loop.updateNetworks(setOf(2), 2)
        lost.loop.start(0); unaffected.loop.start(0); lost.advance(0); unaffected.advance(0)
        lost.loop.fail(); lost.loop.updateNetworks(emptySet(), null)
        unaffected.loop.updateNetworks(setOf(2, 3), 3)
        lost.advance(30000); unaffected.advance(30000)
        assertEquals(1, lost.connects.size); assertEquals(1, unaffected.connects.size); assertEquals(0, unaffected.releases)
        lost.loop.updateNetworks(setOf(3), 3); lost.advance(0)
        assertEquals(3L, lost.connects.last().second)
    }
    @Test fun repeatedPlayerFailureHasOnePendingRetryAndBoundedBackoff() {
        val h = Harness(); h.loop.updateNetworks(setOf(5), 5); h.loop.start(0); h.advance(0)
        val expected = listOf(1000L, 2000L, 5000L, 10000L, 30000L, 30000L)
        for (delay in expected) {
            val before = h.connects.size
            h.loop.fail(); h.loop.fail() // Duplicate player/watchdog error must coalesce.
            h.advance(delay - 1); assertEquals(before, h.connects.size)
            h.advance(1); assertEquals(before + 1, h.connects.size)
        }
        h.loop.stable(); h.loop.fail(); h.advance(1000)
        assertEquals(8, h.connects.size)
        h.loop.fail(); h.loop.stop(); val before = h.connects.size
        h.advance(60000); h.loop.updateNetworks(setOf(6), 6); h.advance(3000)
        assertEquals(before, h.connects.size)
    }
    @Test fun profilesUseBoundedBuffersAndNormalIsDefaultFirstChoice() {
        assertEquals(PerformanceProfile.NORMAL, PerformanceProfile.entries.first())
        assertTrue(PerformanceProfile.ECONOMIC.maxBufferMs < PerformanceProfile.NORMAL.maxBufferMs)
        assertTrue(PerformanceProfile.ECONOMIC.targetBytes < PerformanceProfile.NORMAL.targetBytes)
        PerformanceProfile.entries.forEach { assertTrue(it.minBufferMs >= 500 && it.maxBufferMs >= it.minBufferMs) }
    }
    @Test fun returnedPreferredLanReschedulesOnlyFailedSessionAndIgnoresDuplicateEvents() {
        val h = Harness(); h.loop.updateNetworks(setOf(1), 1); h.loop.start(2000); h.advance(2000)
        repeat(5) { h.loop.fail(); h.advance(listOf(1000L, 2000L, 5000L, 10000L, 30000L)[it]) }
        h.loop.fail()
        val before = h.connects.size
        h.loop.updateNetworks(setOf(1, 2), 2)
        h.advance(1000); h.loop.updateNetworks(setOf(1, 2), 2)
        h.advance(1000)
        assertEquals(before + 1, h.connects.size)
        assertEquals(2L, h.connects.last().second)
        h.advance(30000); assertEquals(before + 1, h.connects.size)
    }
    @Test fun decoderCooldownOnlyDelaysAffectedSession() {
        val affected = Harness(); val healthy = Harness()
        listOf(affected, healthy).forEach { it.loop.updateNetworks(setOf(1),1); it.loop.start(0); it.advance(0) }
        affected.loop.fail(30000)
        affected.advance(29999); healthy.advance(29999)
        assertEquals(1, affected.connects.size); assertEquals(1, healthy.connects.size); assertEquals(0, healthy.releases)
        affected.advance(1); assertEquals(2, affected.connects.size)
        affected.loop.stable(); affected.loop.fail(); affected.advance(1000)
        assertEquals(3, affected.connects.size)
    }
}
