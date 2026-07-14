package com.example.sportsai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sportsai.data.GeminiApiKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiApiKeyStoreInstrumentedTest {
    private val testPreferences = "gemini_secure_settings_instrumented_test"
    private val testAlias = "sportsai_gemini_api_key_instrumented_test"
    private val testKey = "test-only-key-that-is-not-a-real-credential"
    private lateinit var context: Context
    private lateinit var store: GeminiApiKeyStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = GeminiApiKeyStore(context, testPreferences, testAlias)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun keyRoundTripsEncryptedAndCanBeRemoved() {
        store.save("  $testKey  ")

        assertEquals(testKey, store.read())
        assertEquals("•••••••• tial", store.maskedKey())

        val serializedPreferences = context
            .getSharedPreferences(testPreferences, Context.MODE_PRIVATE)
            .all
            .values
            .joinToString()
        assertFalse(serializedPreferences.contains(testKey))

        store.clear()
        assertNull(store.read())
    }
}
