package org.asistentepro.asistentepro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("asistente_native", Context.MODE_PRIVATE)
    private fun secret(create: Boolean = false): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("AsistenteProGroq", null) as? SecretKey)?.let { return it }
        check(create) { "La clave de cifrado de Android no está disponible" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("AsistenteProGroq", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    var provider: AiProvider
        get() = AiProvider.from(preferences.getString("provider", null))
        set(value) { preferences.edit().putString("provider", value.name).apply() }
    fun apiKey(provider: AiProvider = this.provider): String = runCatching {
        val encoded = preferences.getString("key_${provider.name}", null)
            ?: (if (provider == AiProvider.Groq) preferences.getString("groq_key", null) else null) ?: return@runCatching ""
        val parts = encoded.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrDefault("")
    fun saveKey(value: String, provider: AiProvider = this.provider) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret(create = true)) }
        val data = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        check(preferences.edit().putString("key_${provider.name}", data).commit()) { "No se pudo guardar la clave" }
        check(apiKey(provider) == value) { "Android no pudo recuperar la clave guardada" }
    }
    fun hasStoredKey(provider: AiProvider = this.provider) = preferences.contains("key_${provider.name}") || (provider == AiProvider.Groq && preferences.contains("groq_key"))
    fun keyMask(provider: AiProvider = this.provider) = apiKey(provider).takeIf { it.isNotBlank() }?.let { "•••• •••• ${it.takeLast(4)}" }.orEmpty()
    fun keyUnreadable(provider: AiProvider = this.provider) = hasStoredKey(provider) && apiKey(provider).isBlank()
    fun deleteKey() { preferences.edit().remove("key_${provider.name}").apply(); if (provider == AiProvider.Groq) preferences.edit().remove("groq_key").apply() }
    fun configuredProviders() = AiProvider.entries.filter { apiKey(it).isNotBlank() }.toSet()
    var draft: String
        get() = preferences.getString("draft", "") ?: ""
        set(value) { preferences.edit().putString("draft", value).apply() }
    var readAloud: Boolean
        get() = preferences.getBoolean("read_aloud", false)
        set(value) { preferences.edit().putBoolean("read_aloud", value).apply() }
    var model: String
        get() {
            preferences.getString("model_${provider.name}", null)?.let { return it }
            val legacy = if (provider == AiProvider.Groq) preferences.getString("model", null) else null
            // The old app's default Llama models were retired for normal Groq
            // accounts on 2026-08-16. Preserve explicit per-provider choices.
            return if (legacy.isNullOrBlank() || legacy in setOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")) provider.defaultModel else legacy
        }
        set(value) { preferences.edit().putString("model_${provider.name}", value).apply() }
    fun conversations(): List<Conversation> = runCatching {
        val array = JSONArray(preferences.getString("conversations", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val messages = item.getJSONArray("messages")
            Conversation(item.getString("id"), item.getString("title"), (0 until messages.length()).map {
                val msg = messages.getJSONObject(it)
                Message(msg.getString("id"), msg.getString("text"), msg.getBoolean("user"), msg.getLong("time"))
            }, item.getLong("updated"))
        }
    }.getOrDefault(emptyList())
    fun saveConversations(items: List<Conversation>) {
        val array = JSONArray()
        items.take(40).forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.takeLast(100).forEach { messages.put(JSONObject().put("id", it.id).put("text", it.text).put("user", it.user).put("time", it.time)) }
            array.put(JSONObject().put("id", conversation.id).put("title", conversation.title).put("updated", conversation.updatedAt).put("messages", messages))
        }
        preferences.edit().putString("conversations", array.toString()).apply()
    }
}
