package com.dannyowelch.oscilla.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalHealthTest {
    @Test
    fun noFramesBecomesItsOwnStateAfterTheWindow() {
        assertEquals(SignalHealth.Waiting, signalHealth(0, 0, null, windowMs = 3_000))
        assertEquals(SignalHealth.Waiting, signalHealth(2_999, 0, null, windowMs = 3_000))
        assertEquals(SignalHealth.NoFrames, signalHealth(3_000, 0, null, windowMs = 3_000))
    }

    @Test
    fun framesWithEnergyStayLive() {
        assertEquals(SignalHealth.Live, signalHealth(5_000, 10, null, windowMs = 3_000))
        assertEquals(SignalHealth.Live, signalHealth(5_000, 10, 2_999, windowMs = 3_000))
    }

    @Test
    fun aLongZeroStreakIsSilentOnlyAfterFramesHaveArrived() {
        assertEquals(SignalHealth.Silent, signalHealth(5_000, 10, 3_000, windowMs = 3_000))
        assertEquals(SignalHealth.NoFrames, signalHealth(5_000, 0, 3_000, windowMs = 3_000))
    }
}
