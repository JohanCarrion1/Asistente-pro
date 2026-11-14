package org.asistentepro.asistentepro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build

@RunWith(AndroidJUnit4::class)
class InstalledAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
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
        compose.onNodeWithText("Tu clave de Groq").performClick().performTextInput("gsk_test_not_a_real_api_key_123456789")
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
}
