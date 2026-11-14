package org.asistentepro.asistentepro

import org.junit.Assert.*
import org.junit.Test

class WakePhraseTest {
    @Test fun wakeWordRequiresANameAtTheStart() {
        assertNull(WakePhrase.extract("Estoy hablando de Aurea con alguien"))
        assertNull(WakePhrase.extract("abre YouTube"))
        assertNull(WakePhrase.extract("aureamente"))
        assertEquals("", WakePhrase.extract("Oye Áurea"))
    }
    @Test fun understandsSpanishOpeningVariantsWithoutVoiceProfiles() {
        listOf("Aurea, abra la aplicación YouTube", "oye áurea abre la app YouTube", "Aura abre YouTube", "abrir YouTube", "ábreme YouTube por favor").forEach {
            assertEquals(DeviceAction.OpenApp("youtube"), ActionRouter.parse(it))
        }
        assertNull(ActionRouter.parse("cómo abrir YouTube"))
    }
}
