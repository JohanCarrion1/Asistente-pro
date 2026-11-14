package org.asistentepro.asistentepro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import java.io.File
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.Manifest
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.vosk.Recognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class InstalledAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val engine get() = AureaRuntime.get(compose.activity).engine
    @Before fun cleanSession() {
        compose.runOnIdle {
            compose.activity.stopService(Intent(compose.activity, HandsFreeService::class.java))
            for (p in AiProvider.entries) { engine.provider(p); engine.removeKey() }
            engine.provider(AiProvider.Groq); engine.clearHistory(); engine.newConversation(); engine.dismissError()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        val path = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        val image = File(path, "$name.png")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(image)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AsistenteProVerification")
            }
            val resolver = compose.activity.contentResolver
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                resolver.openOutputStream(uri)?.use { out -> image.inputStream().use { it.copyTo(out) } }
            }
        }
    }
    @Test fun nativeIdentityAndStartup() {
        assertEquals("app.asistentepro.android", compose.activity.packageName)
        compose.onNodeWithContentDescription("Abrir menú").assertIsDisplayed()
        compose.onNodeWithContentDescription("Escribe tu mensaje").assertIsDisplayed()
        capture("01-inicio-instalado")
    }
    @Test fun drawerAndSettingsWorkOnAndroid() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Nueva conversación").assertIsDisplayed()
        capture("02-menu-instalado")
        compose.onNodeWithText("Ajustes y privacidad").performClick()
        compose.onNodeWithText("Conexión con Groq").assertIsDisplayed()
        compose.onNodeWithTag("api-key").performScrollTo().performClick().performTextInput("gsk_test_not_a_real_api_key_123456789")
        compose.onNodeWithText("Guardar clave").assertIsEnabled()
        capture("03-ajustes-teclado")
    }
    @Test fun nativeBatteryCommandAndHistoryWithoutApiKey() {
        compose.onNodeWithContentDescription("Escribe tu mensaje").performTextInput("cuánta batería tengo")
        compose.onNodeWithContentDescription("Enviar mensaje").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Tu batería está", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tu batería está", substring = true).assertIsDisplayed()
        capture("04-respuesta-nativa")
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithContentDescription("Abrir historial").performClick()
        compose.onNode(hasText("cuánta batería tengo") and hasAnyAncestor(hasTestTag("conversation-history"))).assertIsDisplayed()
        capture("05-historial-instalado")
    }
    @Test fun providersKeepSeparateEncryptedCredentials() {
        compose.onNodeWithContentDescription("Abrir ajustes").performClick()
        compose.onNodeWithText("Gemini", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("api-key").performScrollTo().performTextInput("gemini_verification_key_not_real_1234")
        compose.onNodeWithText("Guardar clave").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(engine.state.value.hasKey); assertEquals(AiProvider.Gemini, engine.state.value.provider) }
        compose.onNodeWithText("Anthropic", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(engine.state.value.hasKey); assertEquals(AiProvider.Anthropic.defaultModel, engine.state.value.model) }
        compose.onNodeWithText("Gemini", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertTrue(engine.state.value.hasKey)
            val stored = compose.activity.getSharedPreferences("asistente_native", 0).getString("key_Gemini", "")!!
            assertFalse(stored.contains("gemini_verification")); assertTrue(stored.contains(":"))
        }
        capture("06-proveedores-aislados")
    }
    private fun recognizedFixture(): String {
        val ready = CountDownLatch(1)
        var loaded: org.vosk.Model? = null
        var error: Exception? = null
        OfflineSpeech.load(compose.activity, { loaded = it; ready.countDown() }, { error = it; ready.countDown() })
        assertTrue("Speech model timed out", ready.await(45, TimeUnit.SECONDS))
        error?.let { throw it }
        val recognizer = Recognizer(loaded!!, 16000f)
        try {
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("aurea-open-youtube.pcm").use { it.readBytes() }
            recognizer.acceptWaveForm(bytes, bytes.size)
            return recognizer.finalResult
        } finally { recognizer.close() }
    }
    @Test fun realOfflineAudioOpensAppFromOverlayOutsideAurea() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant app.asistentepro.android android.permission.RECORD_AUDIO")
        device.executeShellCommand("appops set app.asistentepro.android SYSTEM_ALERT_WINDOW allow")
        val hypothesis = recognizedFixture()
        val text = org.json.JSONObject(hypothesis).getString("text")
        assertEquals(DeviceAction.OpenApp("youtube"), ActionRouter.parse(WakePhrase.extract(text)!!))
        compose.runOnIdle { ContextCompat.startForegroundService(compose.activity, Intent(compose.activity, HandsFreeService::class.java)) }
        compose.waitUntil(30000) { engine.state.value.handsFreeStatus.contains("escucha local activa") }
        device.pressHome(); device.waitForIdle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { HandsFreeService.active!!.onResult(hypothesis) }
        assertTrue("Target application was not opened from background", device.wait(Until.hasObject(By.pkg("app.aurea.verification.youtube").depth(0)), 12000))
        assertTrue(device.wait(Until.hasObject(By.text("Aurea")), 7000))
        capture("07-superposicion-y-app-abierta")
        assertTrue(engine.state.value.messages.any { it.text == "Abrí YouTube (prueba)." })
        device.findObject(By.desc("Cerrar superposición"))?.click()
        compose.activity.stopService(Intent(compose.activity, HandsFreeService::class.java))
        device.waitForIdle()
        compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java))
        compose.waitUntil(5000) { !engine.state.value.handsFreeActive }
    }
    @Test fun systemAssistantShowsOutsideAppWithoutOverlayPermission() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("appops set app.asistentepro.android SYSTEM_ALERT_WINDOW deny")
        device.executeShellCommand("cmd role add-role-holder android.app.role.ASSISTANT app.asistentepro.android")
        compose.waitUntil(10000) { android.service.voice.VoiceInteractionService.isActiveService(compose.activity, android.content.ComponentName(compose.activity, AureaVoiceService::class.java)) }
        device.pressHome(); device.waitForIdle()
        val result = device.executeShellCommand("cmd voiceinteraction show")
        assertTrue("Assistant session did not show: $result", device.wait(Until.hasObject(By.text("Aurea")), 12000))
        assertTrue(device.hasObject(By.text("Hablar")))
        capture("08-asistente-del-sistema")
        device.executeShellCommand("cmd voiceinteraction hide")
        device.executeShellCommand("cmd role remove-role-holder android.app.role.ASSISTANT app.asistentepro.android")
        compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java))
    }
    @Test fun legacyGroqCredentialCanBeReadAfterUpgrade() {
        compose.runOnIdle {
            val store = LocalStore(compose.activity)
            store.provider = AiProvider.Groq
            store.saveKey("gsk_migration_test_not_real_12345678")
            val preferences = compose.activity.getSharedPreferences("asistente_native", 0)
            val encrypted = preferences.getString("key_Groq", null)
            preferences.edit().remove("key_Groq").putString("groq_key", encrypted).commit()
            assertEquals("gsk_migration_test_not_real_12345678", store.apiKey(AiProvider.Groq))
            assertEquals("", store.apiKey(AiProvider.Gemini))
            store.deleteKey()
        }
    }
}
