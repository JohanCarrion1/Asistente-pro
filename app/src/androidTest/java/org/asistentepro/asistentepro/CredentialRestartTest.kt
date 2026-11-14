package org.asistentepro.asistentepro

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** CI invokes these methods in separate instrumentations with am force-stop between them. */
@RunWith(AndroidJUnit4::class)
class CredentialRestartTest {
    private fun key(provider: AiProvider) = "xylon_persistence_test_${provider.name}_1234"
    @Test fun seed() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val engine = AssistantEngine(app)
            AiProvider.entries.forEach { provider ->
                engine.provider(provider); engine.keyDraft(key(provider)); engine.saveKey()
                assertTrue(engine.state.value.hasKey)
            }
            engine.provider(AiProvider.Gemini)
            val store = LocalStore(app)
            store.draft = "persistence-check"
            store.saveConversations(listOf(Conversation("restart-test", "Persistencia entre procesos", listOf(Message("1", "Prueba de historial", true)))))
            assertTrue(app.getSharedPreferences("asistente_native", 0).edit().putInt("restart_test_pid", android.os.Process.myPid()).commit())
        }
    }
    @Test fun verifyAfterProcessDeath() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val store = LocalStore(app)
        val preferences = app.getSharedPreferences("asistente_native", 0)
        assertNotEquals("Verification must run in a new process", preferences.getInt("restart_test_pid", -1), android.os.Process.myPid())
        assertEquals(AiProvider.Gemini, store.provider)
        AiProvider.entries.forEach { provider ->
            assertEquals("Credential was lost after process death: ${provider.title}", key(provider), store.apiKey(provider))
            assertFalse(store.keyUnreadable(provider))
        }
        assertEquals("persistence-check", store.draft)
        assertEquals("restart-test", store.conversations().first().id)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val engine = AssistantEngine(app)
            assertTrue(engine.state.value.hasKey)
            assertEquals(AiProvider.entries.toSet(), engine.state.value.configuredProviders)
            assertEquals("•••• •••• 1234", engine.state.value.keyMask)
        }
        AiProvider.entries.forEach { store.provider = it; store.deleteKey() }
        store.provider = AiProvider.Groq; store.draft = ""; store.saveConversations(emptyList())
        preferences.edit().remove("restart_test_pid").commit()
    }
}
