package org.asistentepro.asistentepro

import org.junit.Assert.*
import org.junit.Test

class WakePhraseTest {
    @Test fun wakeWordRequiresANameAtTheStart() {
        assertNull(WakePhrase.extract("Estoy hablando de Xylon con alguien"))
        assertNull(WakePhrase.extract("abre YouTube"))
        assertNull(WakePhrase.extract("xylonicamente"))
        assertNull(WakePhrase.extract("sigo viendo una película"))
        assertEquals("", WakePhrase.extract("Oye Xylon"))
    }
    @Test fun understandsSpanishOpeningVariantsWithoutVoiceProfiles() {
        listOf("Xylon, abra la aplicación YouTube", "oye xylon abre la app YouTube", "Cylon abre YouTube", "abrir YouTube", "ábreme YouTube por favor").forEach {
            assertEquals(DeviceAction.OpenApp("youtube"), ActionRouter.parse(it))
        }
        assertNull(ActionRouter.parse("cómo abrir YouTube"))
    }
    @Test fun freeConversationDoesNotRequireAPresetCommand() {
        assertEquals("ayudame a pensar una idea para mi negocio", WakePhrase.extract("Xylon, ayúdame a pensar una idea para mi negocio"))
        assertEquals("cuentame algo sobre las estrellas", WakePhrase.extract("Saigón cuéntame algo sobre las estrellas"))
    }
}
