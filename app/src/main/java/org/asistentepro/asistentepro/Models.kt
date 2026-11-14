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
)

sealed interface DeviceAction {
    data class Torch(val enabled: Boolean?) : DeviceAction
    data object Camera : DeviceAction
    data object Wifi : DeviceAction
    data object Battery : DeviceAction
    data class Timer(val seconds: Int) : DeviceAction
    data object Sms : DeviceAction
}

object ActionRouter {
    fun parse(text: String): DeviceAction? {
        val value = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "").trim()
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
        if (listOf("temporizador", "pon temporizador", "pon un temporizador", "inicia un temporizador", "cuenta atras").any { value.startsWith(it) }) {
            val amount = "\\b(\\d{1,4})\\b".toRegex().find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val seconds = amount * if (value.contains("segundo")) 1 else if (value.contains("hora")) 3600 else 60
            return DeviceAction.Timer(seconds.coerceIn(1, 86400))
        }
        return null
    }
}
