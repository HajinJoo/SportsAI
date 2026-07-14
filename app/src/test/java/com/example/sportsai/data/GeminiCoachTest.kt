package com.example.sportsai.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiCoachTest {
    @Test
    fun configurationReflectsDeviceKeyProvider() {
        assertFalse(GeminiCoach { null }.isConfigured)
        assertFalse(GeminiCoach { "   " }.isConfigured)
        assertTrue(GeminiCoach { "user-owned-test-key" }.isConfigured)
    }

    @Test
    fun authenticationErrorsNeverIncludeKeyOrServerBody() {
        val message = GeminiCoach.connectionMessage(403)

        assertTrue(message.contains("rejected", ignoreCase = true))
        assertFalse(message.contains("user-owned-test-key"))
        assertFalse(message.contains("response", ignoreCase = true))
    }

    @Test
    fun currentStableModelIsUsed() {
        assertTrue(GeminiCoach.MODEL == "gemini-3.5-flash")
    }
}
