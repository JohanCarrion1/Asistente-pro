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

class AiClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build(),
    private val endpointOverride: String? = null,
) {
    private val instruction = "Eres Aurea, un asistente personal. Responde en español con claridad y cercanía. " +
        "Sé breve salvo que pidan detalle. No afirmes haber controlado el teléfono ni realizado acciones: " +
        "solo el motor nativo puede hacerlo. No inventes acceso a información personal."

    fun call(provider: AiProvider, key: String, model: String, history: List<Message>): Call {
        require(model.isNotBlank() && model.length <= 150 && model.matches(Regex("[a-zA-Z0-9._:/-]+"))) { "Modelo no válido" }
        val recent = history.takeLast(24)
        val request = Request.Builder()
        val body: JSONObject
        var endpoint = provider.endpoint
        when (provider) {
            AiProvider.Gemini -> {
                endpoint += model.removePrefix("models/") + ":generateContent"
                request.header("x-goog-api-key", key)
                val contents = JSONArray()
                recent.forEach { contents.put(JSONObject().put("role", if (it.user) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))) }
                body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
                    .put("contents", contents).put("generationConfig", JSONObject().put("maxOutputTokens", 2048))
            }
            AiProvider.Anthropic -> {
                request.header("x-api-key", key).header("anthropic-version", "2023-06-01")
                body = JSONObject().put("model", model).put("system", instruction).put("max_tokens", 1800)
                    .put("messages", messages(recent))
            }
            else -> {
                request.header("Authorization", "Bearer $key")
                if (provider == AiProvider.OpenRouter) request.header("X-OpenRouter-Title", "Aurea")
                    .header("HTTP-Referer", "https://github.com/JohanCarrion1/Asistente-pro")
                val messages = JSONArray().put(JSONObject().put("role", "system").put("content", instruction))
                recent.forEach { messages.put(JSONObject().put("role", if (it.user) "user" else "assistant").put("content", it.text)) }
                body = JSONObject().put("model", model).put("messages", messages).put("max_tokens", 1800)
            }
        }
        return client.newCall(request.url(endpointOverride ?: endpoint)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
    }

    private fun messages(history: List<Message>): JSONArray = JSONArray().apply {
        history.forEach { put(JSONObject().put("role", if (it.user) "user" else "assistant").put("content", it.text)) }
    }

    fun execute(provider: AiProvider, call: Call): String = call.execute().use { response ->
        if (!response.isSuccessful) throw AssistantException(when (response.code) {
            401, 403 -> "${provider.title} no aceptó la clave o el acceso. Revísalos en Ajustes."
            402 -> "${provider.title} necesita saldo o un plan con acceso al modelo."
            429 -> "Has llegado al límite de ${provider.title}. Espera e inténtalo otra vez."
            400, 404 -> "${provider.title} no aceptó la solicitud o el modelo. Revisa su identificador en Ajustes."
            else -> "${provider.title} no pudo responder (${response.code}). Inténtalo de nuevo."
        })
        val json = JSONObject(response.body?.string() ?: "{}")
        val text = when (provider) {
            AiProvider.Gemini -> {
                val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                parts?.let { (0 until it.length()).mapNotNull { i -> it.optJSONObject(i)?.takeUnless { p -> p.optBoolean("thought") }?.optString("text") }.joinToString("\n") }
            }
            AiProvider.Anthropic -> json.optJSONArray("content")?.let { parts ->
                (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.takeIf { p -> p.optString("type") == "text" }?.optString("text") }.joinToString("\n")
            }
            else -> json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
        }
        text?.trim()?.takeIf { it.isNotBlank() } ?: throw AssistantException("${provider.title} no devolvió texto. Prueba otro modelo o reformula tu pregunta.")
    }
}
