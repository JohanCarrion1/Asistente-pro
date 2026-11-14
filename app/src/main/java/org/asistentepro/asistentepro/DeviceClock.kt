package org.asistentepro.asistentepro

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DeviceClock {
    fun context(now: ZonedDateTime = ZonedDateTime.now()): String =
        "Datos actuales del dispositivo en este turno: fecha y hora ${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}, zona ${now.zone.id}. " +
        "Puedes responder sobre la hora y fecha usando estos datos, o consultar current_time/current_date. No digas que desconoces la hora del teléfono."
    fun date(now: ZonedDateTime = ZonedDateTime.now()): String = "Hoy es ${now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")))}."
}
