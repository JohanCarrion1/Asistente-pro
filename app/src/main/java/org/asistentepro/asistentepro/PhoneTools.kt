package org.asistentepro.asistentepro

import org.json.JSONArray
import org.json.JSONObject

data class AiReply(val text: String = "", val action: DeviceAction? = null)

/** The model selects an intent; only validated Android operations can execute. */
object PhoneTools {
    const val name = "control_phone"
    const val description = "Realiza una acción del teléfono cuando el usuario la pide con sus propias palabras. " +
        "No requiere frases exactas. Usa open_app con el nombre de cualquier app instalada. " +
        "Usa current_time/current_date para consultar reloj/fecha reales. " +
        "navigate controla Inicio/Atrás/recientes/notificaciones/ajustes rápidos/bloqueo con Accesibilidad activada por el usuario. " +
        "brightness y auto_rotate necesitan Modificar ajustes. volume_level cambia multimedia, media envía controles de reproducción. " +
        "open_settings abre un panel del sistema; Wi-Fi/Bluetooth/ubicación requieren la interacción que Android indique. " +
        "No inventes acciones que no están disponibles. " +
        "Las alarmas, temporizadores y mensajes necesitan confirmación en la app del sistema."
    fun schema(): JSONObject = JSONObject().put("type", "object").put("properties", JSONObject()
        .put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf("open_app", "battery", "torch", "camera", "wifi_settings", "volume_up", "volume_down", "alarm", "timer", "messages", "current_time", "current_date", "navigate", "brightness", "auto_rotate", "volume_level", "media", "open_settings"))))
        .put("app_name", JSONObject().put("type", "string").put("description", "Nombre de la aplicación que el usuario quiere abrir"))
        .put("enabled", JSONObject().put("type", "boolean").put("description", "Encender/apagar linterna o rotación automática"))
        .put("percent", JSONObject().put("type", "integer").put("description", "Porcentaje 0-100 de brillo o volumen multimedia"))
        .put("destination", JSONObject().put("type", "string").put("enum", JSONArray(listOf("back", "home", "recents", "notifications", "quick_settings", "lock"))))
        .put("media_command", JSONObject().put("type", "string").put("enum", JSONArray(listOf("play_pause", "next", "previous"))))
        .put("panel", JSONObject().put("type", "string").put("enum", JSONArray(listOf("bluetooth", "location", "internet", "sound", "display"))))
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
            "current_time" -> DeviceAction.CurrentTime
            "current_date" -> DeviceAction.CurrentDate
            "navigate" -> DeviceAction.Navigate(when (arguments.optString("destination")) {
                "back" -> SystemNavigation.Back; "home" -> SystemNavigation.Home; "recents" -> SystemNavigation.Recents
                "notifications" -> SystemNavigation.Notifications; "quick_settings" -> SystemNavigation.QuickSettings; "lock" -> SystemNavigation.Lock
                else -> throw AssistantException("No se indicó una acción de navegación válida.")
            })
            "brightness" -> DeviceAction.Brightness(integer("percent", 0..100))
            "volume_level" -> DeviceAction.VolumeLevel(integer("percent", 0..100))
            "auto_rotate" -> DeviceAction.AutoRotate(arguments.opt("enabled") as? Boolean ?: throw AssistantException("¿Quieres activar o desactivar la rotación automática?"))
            "media" -> DeviceAction.Media(when (arguments.optString("media_command")) {
                "play_pause" -> MediaCommand.PlayPause; "next" -> MediaCommand.Next; "previous" -> MediaCommand.Previous
                else -> throw AssistantException("No se indicó un control multimedia válido.")
            })
            "open_settings" -> DeviceAction.OpenSettings(when (arguments.optString("panel")) {
                "bluetooth" -> SettingsPanel.Bluetooth; "location" -> SettingsPanel.Location; "internet" -> SettingsPanel.Internet
                "sound" -> SettingsPanel.Sound; "display" -> SettingsPanel.Display
                else -> throw AssistantException("No se indicó un panel de ajustes válido.")
            })
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
