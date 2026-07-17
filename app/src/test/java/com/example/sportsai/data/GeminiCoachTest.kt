package com.example.sportsai.data

import com.example.sportsai.model.Sport
import org.junit.Assert.assertThrows
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

    @Test
    fun battingPromptRequiresVisibleBatterAndFrameEvidence() {
        val prompt = GeminiCoach { "test" }.promptFor(Sport.BASEBALL_BAT)

        assertTrue(prompt.contains("body-box target is visibly the batter"))
        assertTrue(prompt.contains("do not choose the largest bystander"))
        assertTrue(prompt.contains("Every strength and issue must"))
        assertTrue(prompt.contains("contact-zone frame"))
        assertTrue(prompt.contains("set athleteVisible=false"))
        assertFalse(prompt.contains("best-effort", ignoreCase = true))
        assertFalse(prompt.contains("infer as much", ignoreCase = true))
    }

    @Test
    fun visibilityGateRejectsUnsupportedCoaching() {
        assertThrows(InsufficientVisualEvidenceException::class.java) {
            validateVisualEvidence(
                athleteVisible = true,
                confidence = 40,
                observedFrameLabels = listOf("Frame 1", "Frame 2"),
                visibilitySummary = "The batter is too blurred to verify.",
                limitations = listOf("Bat is not visible.")
            )
        }
    }

    @Test
    fun visibilityGateAcceptsGroundedSequence() {
        validateVisualEvidence(
            athleteVisible = true,
            confidence = 82,
            observedFrameLabels = listOf("Frame 1", "Frame 4", "Frame 7"),
            visibilitySummary = "The batter and bat are visible through the swing window.",
            limitations = emptyList()
        )
    }
}
