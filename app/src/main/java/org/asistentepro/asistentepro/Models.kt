package org.asistentepro.asistentepro

import java.text.Normalizer

enum class Page { Assistant, History, Settings }
enum class VoicePhase { Idle, Listening, Thinking, Speaking }
data class Message(val id: String, val text: String, val user: Boolean, val time: Long = System.currentTimeMillis())
data class Conversation(val id: String, val title: String, val messages: List<Message>, val updatedAt: Long = System.currentTimeMillis())
data class AssistantState(
    val page: Page = Page.Assistant,
    val messages: List<Message> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val draft: String = "",
    val phase: VoicePhase = VoicePhase.Idle,
    val hasKey: Boolean = false,
    val readAloud: Boolean = false,
    val torchOn: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val keyDraft: String = "",
    val model: String = "llama-3.1-8b-instant",
    val provider: AiProvider = AiProvider.Groq,
    val configuredProviders: Set<AiProvider> = emptySet(),
    val handsFreeActive: Boolean = false,
    val handsFreeStatus: String = "Desactivado",
    val overlayGranted: Boolean = false,
    val assistantSelected: Boolean = false,
)

sealed interface DeviceAction {
    data class Torch(val enabled: Boolean?) : DeviceAction
    data object Camera : DeviceAction
    data object Wifi : DeviceAction
    data object Battery : DeviceAction
    data class Timer(val seconds: Int) : DeviceAction
    data object Sms : DeviceAction
    data object Help : DeviceAction
    data class OpenApp(val name: String) : DeviceAction
    data class Alarm(val hour: Int, val minute: Int) : DeviceAction
    data class Volume(val raise: Boolean) : DeviceAction
}

object ActionRouter {
    fun parse(text: String): DeviceAction? {
        val value = Normalizer.normalize(WakePhrase.stripOptionalName(text).lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "").trim().trim('¿', '?', '¡', '!', '.', ',').trim()
        if (value in listOf("ayuda", "que puedes hacer", "como uso asistente pro", "como te uso")) return DeviceAction.Help
        if (value in listOf("sube el volumen", "subir volumen", "sube volumen")) return DeviceAction.Volume(true)
        if (value in listOf("baja el volumen", "bajar volumen", "baja volumen")) return DeviceAction.Volume(false)
        if (value.startsWith("pon alarma") || value.startsWith("pon una alarma") || value.startsWith("alarma a")) {
            val match = "\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b".toRegex().find(value) ?: return null
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            when (match.groupValues[3]) { "pm" -> if (hour < 12) hour += 12; "am" -> if (hour == 12) hour = 0 }
            if (hour !in 0..23 || minute !in 0..59) return null
            return DeviceAction.Alarm(hour, minute)
        }
        if (value.contains("linterna")) {
            if (value.startsWith("como") || value.contains("explica")) return null
            return DeviceAction.Torch(when {
                value.contains("apaga") || value.contains("desactiva") -> false
                value.contains("enciende") || value.contains("activa") -> true
                value == "linterna" -> null
                else -> return null
            })
        }
        if (value in listOf("camara", "abre la camara", "abrir camara", "abre camara")) return DeviceAction.Camera
        if (value in listOf("wifi", "wi-fi", "abre wifi", "ajustes wifi", "abre ajustes wifi")) return DeviceAction.Wifi
        if (value.contains("bateria") && (value.contains("cuanta") || value.contains("estado") || value == "bateria")) return DeviceAction.Battery
        if (value in listOf("sms", "enviar sms", "abre mensajes", "abrir mensajes")) return DeviceAction.Sms
        val opening = Regex("^(?:abre|abra|abrir|abreme|inicia|abrime)\\s+(?:(?:la|el)\\s+)?(?:(?:aplicacion|app)\\s+)?(.+?)(?:\\s+por favor)?$").matchEntire(value)
        if (opening != null) return DeviceAction.OpenApp(opening.groupValues[1].trim())
        if (listOf("temporizador", "pon temporizador", "pon un temporizador", "inicia un temporizador", "cuenta atras").any { value.startsWith(it) }) {
            val amount = "\\b(\\d{1,4})\\b".toRegex().find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val seconds = amount * if (value.contains("segundo")) 1 else if (value.contains("hora")) 3600 else 60
            return DeviceAction.Timer(seconds.coerceIn(1, 86400))
        }
        return null
    }
}
