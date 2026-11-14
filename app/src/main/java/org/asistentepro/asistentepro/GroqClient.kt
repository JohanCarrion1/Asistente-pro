package org.asistentepro.asistentepro

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AssistantException(message: String) : IOException(message)

class GroqClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build(),
    private val endpoint: String = "https://api.groq.com/openai/v1/chat/completions",
) {
    fun call(key: String, model: String, history: List<Message>): Call {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content",
            "Eres Asistente Pro. Responde en español con claridad, cercanía y precisión. " +
                "Sé breve salvo que pidan detalle. No afirmes haber controlado el teléfono ni realizado acciones: " +
                "solo la aplicación nativa ejecuta acciones. Nunca inventes resultados ni acceso a datos personales."))
        history.takeLast(24).forEach { messages.put(JSONObject().put("role", if (it.user) "user" else "assistant").put("content", it.text)) }
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.65).put("max_tokens", 1800)
        return client.newCall(Request.Builder().url(endpoint).header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
    }

    fun execute(call: Call): String = call.execute().use { response ->
        if (!response.isSuccessful) throw AssistantException(when (response.code) {
            401, 403 -> "Groq no aceptó la clave. Revísala en Ajustes."
            429 -> "Has llegado al límite de Groq. Espera un momento e inténtalo otra vez."
            400, 404 -> "El modelo no está disponible. Prueba el otro modelo en Ajustes."
            else -> "Groq no pudo responder (${response.code}). Inténtalo de nuevo."
        })
        val text = JSONObject(response.body?.string() ?: "{}").optJSONArray("choices")
            ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
        text?.takeIf { it.isNotBlank() } ?: throw AssistantException("La respuesta llegó vacía. Inténtalo otra vez.")
    }
}
