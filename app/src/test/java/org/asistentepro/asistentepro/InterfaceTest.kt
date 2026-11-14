package org.asistentepro.asistentepro

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test

class InterfaceTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:style/Theme.Material.NoActionBar", showSystemUi = false)
    @Test fun home() { paparazzi.snapshot { AsistenteApp(AssistantState(), animate = false) } }
    @Test fun conversation() {
        paparazzi.snapshot { AsistenteApp(AssistantState(hasKey = true, messages = listOf(
            Message("1", "Ayúdame a organizar mejor mi día.", true, 0),
            Message("2", "Vamos a hacer espacio para lo importante.\n\n1. Elige una prioridad realista.\n2. Reserva un bloque sin interrupciones.\n3. Deja tiempo para descansar.\n\n¿Qué te gustaría terminar hoy?", false, 0),
        )), animate = false) }
    }
    @Test fun settings() { paparazzi.snapshot { AsistenteApp(AssistantState(page = Page.Settings), animate = false) } }
    @Test fun savedCredentialIsVisibleAsAMaskedCard() {
        paparazzi.snapshot { AsistenteApp(AssistantState(page = Page.Settings, provider = AiProvider.Gemini,
            model = AiProvider.Gemini.defaultModel, hasKey = true, keyMask = "•••• •••• 1234", configuredProviders = setOf(AiProvider.Gemini)), animate = false) }
    }
    @Test fun launcherIcon() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig(screenWidth = 432, screenHeight = 432, xdpi = 160, ydpi = 160, density = com.android.resources.Density.MEDIUM))
        val icon = android.widget.ImageView(paparazzi.context).apply {
            // Layoutlib does not supply an OEM adaptive-icon mask. Render the
            // exact foreground vector; Android's launcher supplies its mask.
            setImageResource(R.drawable.assistant_brand)
            setBackgroundColor(android.graphics.Color.rgb(13, 21, 20))
            setPadding(82, 82, 82, 82)
        }
        paparazzi.snapshot(icon)
    }
    @Test fun smallPhoneAndLargeText() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_4)
        paparazzi.snapshot {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                AsistenteApp(AssistantState(), animate = false)
            }
        }
    }
    @Test fun drawer() {
        paparazzi.snapshot { AsistenteApp(AssistantState(conversations = listOf(
            Conversation("1", "Organizar mi semana", listOf(Message("a", "Organizar mi semana", true))),
            Conversation("2", "Una idea para mi apiario", listOf(Message("b", "Una idea para mi apiario", true))),
        )), animate = false, drawerOpen = true) }
    }
}
