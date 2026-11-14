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
    private val engine get() = XylonRuntime.get(compose.activity).engine
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
        compose.onNodeWithText("Gemini", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("api-key").performScrollTo().performTextInput("gemini_verification_key_not_real_1234")
        compose.onNodeWithText("Guardar clave").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(engine.state.value.hasKey); assertEquals(AiProvider.Gemini, engine.state.value.provider) }
        compose.onNodeWithTag("saved-api-key").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("•••• •••• 1234").assertIsDisplayed()
        compose.onNodeWithTag("check-connection").assertIsEnabled()
        compose.onNodeWithTag("api-key").assertDoesNotExist()
        compose.onNodeWithText("Anthropic", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(engine.state.value.hasKey); assertEquals(AiProvider.Anthropic.defaultModel, engine.state.value.model) }
        compose.onNodeWithText("Gemini", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(engine.state.value.hasKey)
            val stored = compose.activity.getSharedPreferences("asistente_native", 0).getString("key_Gemini", "")!!
            assertFalse(stored.contains("gemini_verification")); assertTrue(stored.contains(":"))
        }
        capture("06-proveedores-aislados")
    }
    @Test fun clockUsesPhoneTimeEvenWithAnAiKeyConfigured() {
        compose.runOnIdle {
            engine.provider(AiProvider.Gemini); engine.keyDraft("time_test_not_a_real_key_1234"); engine.saveKey()
        }
        val before = android.text.format.DateFormat.getTimeFormat(compose.activity).format(java.util.Date())
        compose.onNodeWithContentDescription("Escribe tu mensaje").performTextInput("que hora es")
        compose.onNodeWithContentDescription("Enviar mensaje").performClick()
        compose.waitUntil(5000) { engine.state.value.messages.any { !it.user && it.text.startsWith("Son las ") } }
        val after = android.text.format.DateFormat.getTimeFormat(compose.activity).format(java.util.Date())
        val answer = engine.state.value.messages.last().text
        assertTrue(answer.contains(before) || answer.contains(after))
        assertTrue(answer.contains(java.util.TimeZone.getDefault().id))
        assertTrue(engine.state.value.hasKey)
        capture("09-hora-real-del-telefono")
    }
    @Test fun unreadableCredentialIsReportedAndCanBeReplaced() {
        val preferences = compose.activity.getSharedPreferences("asistente_native", 0)
        preferences.edit().putString("key_Gemini", "corrupted-credential").commit()
        compose.runOnIdle { engine.provider(AiProvider.Gemini); engine.page(Page.Settings) }
        compose.onNodeWithTag("saved-key-error").performScrollTo().assertIsDisplayed()
        assertFalse(engine.state.value.hasKey)
        compose.onNodeWithTag("api-key").performScrollTo().performTextInput("replacement_not_a_real_key_5678")
        compose.onNodeWithText("Guardar clave").performScrollTo().performClick()
        compose.onNodeWithTag("saved-api-key").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("•••• •••• 5678").assertIsDisplayed()
        assertFalse(engine.state.value.keyUnreadable)
        assertEquals("replacement_not_a_real_key_5678", LocalStore(compose.activity).apiKey(AiProvider.Gemini))
        capture("11-clave-recuperada")
    }
    @Test fun systemControlsRequirePermissionAndChangeRealAndroidState() {
        val configuration = androidx.test.uiautomator.Configurator.getInstance()
        val oldFlags = configuration.uiAutomationFlags
        configuration.uiAutomationFlags = android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val resolver = compose.activity.contentResolver
        val originalBrightness = android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128)
        val originalMode = android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        val originalRotation = android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0)
        val originalServices = device.executeShellCommand("settings get secure enabled_accessibility_services").trim()
        val component = "app.asistentepro.android/org.asistentepro.asistentepro.XylonControlService"
        val services = listOf(originalServices.takeUnless { it == "null" || it.isBlank() }, component).filterNotNull().joinToString(":")
        try {
            device.executeShellCommand("appops set app.asistentepro.android WRITE_SETTINGS deny")
            InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.action(DeviceAction.Brightness(42)) }
            assertTrue(engine.state.value.error!!.contains("Modificar ajustes"))
            assertEquals(originalBrightness, android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS))
            device.executeShellCommand("appops set app.asistentepro.android WRITE_SETTINGS allow")
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                engine.dismissError(); engine.action(DeviceAction.Brightness(42)); engine.action(DeviceAction.AutoRotate(false))
            }
            assertEquals(107, android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS))
            assertEquals(0, android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.ACCELEROMETER_ROTATION))
            device.executeShellCommand("settings put secure enabled_accessibility_services $services")
            device.executeShellCommand("settings put secure accessibility_enabled 1")
            compose.waitUntil(20000) { XylonControlService.active != null }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                engine.action(DeviceAction.Navigate(SystemNavigation.Home))
            }
            assertTrue("Native HOME did not leave Xylon", device.wait(Until.gone(By.pkg("app.asistentepro.android").depth(0)), 10000))
            compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java))
            assertTrue(device.wait(Until.hasObject(By.desc("Abrir ajustes")), 10000))
            compose.runOnIdle { engine.systemPermissions(true, true); engine.page(Page.Settings) }
            compose.onNodeWithTag("permission-accessibility").performScrollTo().assertIsDisplayed()
            capture("10-control-del-sistema")
        } finally {
            device.executeShellCommand("settings put system screen_brightness $originalBrightness")
            device.executeShellCommand("settings put system screen_brightness_mode $originalMode")
            device.executeShellCommand("settings put system accelerometer_rotation $originalRotation")
            if (originalServices == "null" || originalServices.isBlank()) device.executeShellCommand("settings delete secure enabled_accessibility_services")
            else device.executeShellCommand("settings put secure enabled_accessibility_services $originalServices")
            device.executeShellCommand("appops set app.asistentepro.android WRITE_SETTINGS default")
            configuration.uiAutomationFlags = oldFlags
        }
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
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("xylon-open-youtube.pcm").use { it.readBytes() }
            recognizer.acceptWaveForm(bytes, bytes.size)
            return recognizer.finalResult
        } finally { recognizer.close() }
    }
    @Test fun realOfflineAudioOpensAppFromOverlayOutsideXylon() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant app.asistentepro.android android.permission.RECORD_AUDIO")
        device.executeShellCommand("appops set app.asistentepro.android SYSTEM_ALERT_WINDOW allow")
        val hypothesis = recognizedFixture()
        val text = org.json.JSONObject(hypothesis).getString("text")
        assertEquals(DeviceAction.OpenApp("youtube"), ActionRouter.parse(WakePhrase.extract(text)!!))
        val audio = compose.activity.getSystemService(android.media.AudioManager::class.java)
        compose.runOnIdle { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 1, 0) }
        compose.runOnIdle { ContextCompat.startForegroundService(compose.activity, Intent(compose.activity, HandsFreeService::class.java)) }
        compose.waitUntil(30000) { engine.state.value.handsFreeStatus.contains("escucha local activa") }
        device.pressHome(); device.waitForIdle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { HandsFreeService.active!!.onResult(hypothesis) }
        assertTrue("Target application was not opened from background", device.wait(Until.hasObject(By.pkg("app.xylon.verification.youtube").depth(0)), 12000))
        assertTrue(device.wait(Until.hasObject(By.text("Xylon")), 7000))
        capture("07-superposicion-y-app-abierta")
        assertTrue(engine.state.value.messages.any { it.text == "Abrí YouTube (prueba)." })
        InstrumentationRegistry.getInstrumentation().runOnMainSync { engine.action(DeviceAction.Volume(true)) }
        compose.waitUntil(5000) { audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) > 1 }
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
        compose.waitUntil(10000) { android.service.voice.VoiceInteractionService.isActiveService(compose.activity, android.content.ComponentName(compose.activity, XylonVoiceService::class.java)) }
        // The selected component is published before the system finishes binding.
        // A real assist gesture is valid only after VoiceInteractionService.onReady.
        compose.waitUntil(20000) { XylonVoiceService.isReady }
        device.pressHome(); device.waitForIdle()
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_ASSIST)
        // The launcher may already display an icon labelled Xylon. Wait for a
        // control unique to our session instead of mistaking that icon for it.
        assertTrue("Assistant session did not show after the assist key", device.wait(Until.hasObject(By.desc("Cerrar superposición")), 20000))
        assertTrue(device.hasObject(By.text("Xylon")))
        assertTrue(device.hasObject(By.text("Hablar")))
        capture("08-asistente-del-sistema")
        device.findObject(By.desc("Cerrar superposición"))?.click()
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
            preferences.edit().remove("model_Groq").putString("model", "llama-3.3-70b-versatile").commit()
            assertEquals(AiProvider.Groq.defaultModel, store.model)
            preferences.edit().putString("model", "custom-legacy-model").commit()
            assertEquals("custom-legacy-model", store.model)
            store.model = "explicit-provider-model"
            assertEquals("explicit-provider-model", store.model)
            preferences.edit().remove("model").remove("model_Groq").commit()
            store.deleteKey()
        }
    }
    @Test fun localRecognizerBindsThroughAndroidSpeechProtocol() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant app.asistentepro.android android.permission.RECORD_AUDIO")
        val ready = CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicInteger(0)
        var speech: android.speech.SpeechRecognizer? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            speech = android.speech.SpeechRecognizer.createSpeechRecognizer(compose.activity, android.content.ComponentName(compose.activity, XylonRecognitionService::class.java)).apply {
                setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) { ready.countDown() }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) { failure.set(error); ready.countDown() }
                    override fun onResults(results: android.os.Bundle?) = Unit
                    override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                })
                startListening(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
            }
        }
        try {
            assertTrue("Local speech service did not become ready", ready.await(45, TimeUnit.SECONDS))
            assertEquals("Speech protocol returned an error", 0, failure.get())
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { speech?.destroy() } }
    }
}
