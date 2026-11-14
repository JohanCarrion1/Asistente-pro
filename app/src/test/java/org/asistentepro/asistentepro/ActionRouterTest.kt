package org.asistentepro.asistentepro

import org.junit.Assert.*
import org.junit.Test

class ActionRouterTest {
    @Test fun `native app alarm help and volume commands`() {
        assertEquals(DeviceAction.OpenApp("spotify"), ActionRouter.parse("Abre Spotify"))
        assertEquals(DeviceAction.Alarm(7, 0), ActionRouter.parse("Pon alarma a las 7:00 AM"))
        assertEquals(DeviceAction.Alarm(19, 30), ActionRouter.parse("Pon alarma a las 7:30 PM"))
        assertEquals(DeviceAction.Volume(true), ActionRouter.parse("Sube el volumen"))
        assertEquals(DeviceAction.Help, ActionRouter.parse("Qué puedes hacer"))
    }
    @Test fun `recognizes native commands with accents`() {
        assertEquals(DeviceAction.Camera, ActionRouter.parse("abre la cámara"))
        assertEquals(DeviceAction.Torch(false), ActionRouter.parse("apaga la linterna"))
        assertEquals(DeviceAction.Torch(true), ActionRouter.parse("enciende la linterna"))
        assertEquals(DeviceAction.Battery, ActionRouter.parse("cuánta batería tengo"))
    }
    @Test fun `does not perform actions for informational questions`() {
        assertNull(ActionRouter.parse("Cómo funciona una linterna"))
        assertNull(ActionRouter.parse("Explica la linterna"))
        assertNull(ActionRouter.parse("Escribe un poema"))
        assertNull(ActionRouter.parse("Cómo funciona un temporizador de 5 minutos"))
    }
    @Test fun `timer uses actual requested duration`() {
        assertEquals(DeviceAction.Timer(300), ActionRouter.parse("temporizador de 5 minutos"))
        assertEquals(DeviceAction.Timer(30), ActionRouter.parse("temporizador 30 segundos"))
        assertEquals(DeviceAction.Timer(7200), ActionRouter.parse("temporizador 2 horas"))
        assertNull(ActionRouter.parse("temporizador"))
    }
}
