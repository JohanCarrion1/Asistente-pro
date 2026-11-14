package org.asistentepro.asistentepro

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class DeviceClockTest {
    @Test fun contextUsesTheDeviceZoneAndCorrectCalendarDay() {
        val instant = java.time.Instant.parse("2026-11-15T02:30:00Z")
        val lima = instant.atZone(ZoneId.of("America/Lima"))
        assertTrue(DeviceClock.context(lima).contains("2026-11-14T21:30:00-05:00"))
        assertTrue(DeviceClock.context(lima).contains("America/Lima"))
        assertEquals("Hoy es sábado 14 de noviembre de 2026.", DeviceClock.date(lima))
    }
    @Test fun questionsAboutTheClockAreLocalButExplanationsRemainConversations() {
        assertEquals(DeviceAction.CurrentTime, ActionRouter.parse("¿Qué hora es?"))
        assertEquals(DeviceAction.CurrentTime, ActionRouter.parse("Dime la hora actual"))
        assertEquals(DeviceAction.CurrentDate, ActionRouter.parse("¿Qué día es hoy?"))
        assertNull(ActionRouter.parse("Explícame qué es una zona horaria"))
        assertNull(ActionRouter.parse("Qué hora es en Marte y cómo se calcula"))
    }
}
