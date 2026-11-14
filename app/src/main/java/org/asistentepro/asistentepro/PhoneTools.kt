package org.asistentepro.asistentepro

import org.json.JSONArray
import org.json.JSONObject

data class AiReply(val text: String = "", val action: DeviceAction? = null)

/** The model selects an intent; only validated Android operations can execute. */
object PhoneTools {
    const val name = "control_phone"
    const val description = "Realiza una acción del teléfono cuando el usuario la pide con sus propias palabras. " +
        "No requiere frases exactas. Usa open_app con el nombre de cualquier app instalada. " +
        "No uses esta función para preguntas informativas ni inventes acciones que no están disponibles. " +
        "Las alarmas, temporizadores y mensajes necesitan confirmación en la app del sistema."
    fun schema(): JSONObject = JSONObject().put("type", "object").put("properties", JSONObject()
        .put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf("open_app", "battery", "torch", "camera", "wifi_settings", "volume_up", "volume_down", "alarm", "timer", "messages"))))
        .put("app_name", JSONObject().put("type", "string").put("description", "Nombre de la aplicación que el usuario quiere abrir"))
        .put("enabled", JSONObject().put("type", "boolean").put("description", "Encender o apagar la linterna"))
        .put("hour", JSONObject().put("type", "integer").put("description", "Hora 0-23 para una alarma"))
        .put("minute", JSONObject().put("type", "integer").put("description", "Minutos 0-59 para una alarma"))
        .put("seconds", JSONObject().put("type", "integer").put("description", "Duración del temporizador, entre 1 y 86400 segundos")))
        .put("required", JSONArray().put("action")).put("additionalProperties", false)

    fun geminiSchema(): JSONObject {
        fun convert(value: JSONObject): JSONObject = JSONObject().apply {
            value.keys().forEach { key ->
                if (key != "additionalProperties") {
                    val child = value.get(key)
                    put(key, when {
                        key == "type" -> child.toString().uppercase()
                        child is JSONObject -> convert(child)
                        else -> child
                    })
                }
            }
        }
        return convert(schema())
    }

    fun decode(function: String, arguments: JSONObject): DeviceAction {
        if (function != name) throw AssistantException("La IA solicitó una función desconocida. No ejecuté ninguna acción.")
        fun integer(key: String, range: IntRange): Int {
            val raw = arguments.opt(key)
            val value = (raw as? Number)?.toDouble() ?: throw AssistantException("La IA no indicó un valor válido para $key.")
            if (value % 1.0 != 0.0 || value < range.first || value > range.last) throw AssistantException("La IA indicó un valor fuera del rango permitido.")
            return value.toInt()
        }
        return when (arguments.optString("action")) {
            "open_app" -> {
                val app = arguments.optString("app_name").trim()
                if (app.isBlank() || app.length > 100) throw AssistantException("¿Qué aplicación quieres abrir?")
                DeviceAction.OpenApp(app)
            }
            "battery" -> DeviceAction.Battery
            "torch" -> {
                val enabled = arguments.opt("enabled") as? Boolean ?: throw AssistantException("¿Quieres encender o apagar la linterna?")
                DeviceAction.Torch(enabled)
            }
            "camera" -> DeviceAction.Camera
            "wifi_settings" -> DeviceAction.Wifi
            "volume_up" -> DeviceAction.Volume(true)
            "volume_down" -> DeviceAction.Volume(false)
            "alarm" -> DeviceAction.Alarm(integer("hour", 0..23), integer("minute", 0..59))
            "timer" -> DeviceAction.Timer(integer("seconds", 1..86400))
            "messages" -> DeviceAction.Sms
            else -> throw AssistantException("Esa acción necesita una función que Android no ha concedido a Xylon. No la ejecuté.")
        }
    }
}
