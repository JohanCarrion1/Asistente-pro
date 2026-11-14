package org.asistentepro.asistentepro

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiClientTest {
    private val history = listOf(Message("1", "Hola", true), Message("2", "¿En qué te ayudo?", false), Message("3", "Un plan", true))
    @Test fun compatibleProvidersUseBearerAndConversation() {
        listOf(AiProvider.Groq, AiProvider.OpenRouter, AiProvider.OpenAI).forEach { provider ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Tu plan"}}]}"""))
                val client = AiClient(OkHttpClient(), server.url("/chat").toString())
                assertEquals("Tu plan", client.execute(provider, client.call(provider, "test-key", provider.defaultModel, history)))
                val request = server.takeRequest()
                assertEquals("Bearer test-key", request.getHeader("Authorization"))
                assertNull(request.getHeader("x-api-key"))
                val body = JSONObject(request.body.readUtf8())
                assertEquals(4, body.getJSONArray("messages").length())
                assertEquals("assistant", body.getJSONArray("messages").getJSONObject(2).getString("role"))
                if (provider == AiProvider.OpenRouter) assertEquals("Aurea", request.getHeader("X-OpenRouter-Title"))
            }
        }
    }
    @Test fun geminiUsesItsNativeSchemaAndJoinsTextParts() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"privado","thought":true},{"text":"Hola"},{"text":"mundo"}]}}]}"""))
            val client = AiClient(OkHttpClient(), server.url("/generateContent").toString())
            assertEquals("Hola\nmundo", client.execute(AiProvider.Gemini, client.call(AiProvider.Gemini, "gemini-key", "gemini-test", history)))
            val request = server.takeRequest()
            assertEquals("gemini-key", request.getHeader("x-goog-api-key")); assertNull(request.getHeader("Authorization"))
            assertFalse(request.path!!.contains("gemini-key"))
            val body = JSONObject(request.body.readUtf8())
            assertTrue(body.has("systemInstruction")); assertFalse(body.has("messages"))
            assertEquals("model", body.getJSONArray("contents").getJSONObject(1).getString("role"))
        }
    }
    @Test fun anthropicUsesNativeAuthenticationAndTextBlocks() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[{"type":"thinking","thinking":"privado"},{"type":"text","text":"Respuesta"}]}"""))
            val client = AiClient(OkHttpClient(), server.url("/messages").toString())
            assertEquals("Respuesta", client.execute(AiProvider.Anthropic, client.call(AiProvider.Anthropic, "anthropic-key", "claude-test", history)))
            val request = server.takeRequest()
            assertEquals("anthropic-key", request.getHeader("x-api-key")); assertEquals("2023-06-01", request.getHeader("anthropic-version"))
            val body = JSONObject(request.body.readUtf8())
            assertTrue(body.has("system")); assertEquals(3, body.getJSONArray("messages").length())
        }
    }
    @Test fun errorsDoNotLeakKeysOrProviderPayloads() {
        AiProvider.entries.forEach { provider ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(401).setBody("private provider response"))
                val client = AiClient(OkHttpClient(), server.url("/chat").toString())
                try { client.execute(provider, client.call(provider, "private-key", provider.defaultModel, history)); fail("Expected failure") }
                catch (e: AssistantException) { assertTrue(e.message!!.contains(provider.title)); assertFalse(e.message!!.contains("private")) }
            }
        }
    }
    @Test fun blockedOrEmptyAnswersDoNotBecomeSuccess() {
        AiProvider.entries.forEach { provider ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("{}"))
                val client = AiClient(OkHttpClient(), server.url("/chat").toString())
                try { client.execute(provider, client.call(provider, "test", provider.defaultModel, history)); fail("Expected failure") }
                catch (e: AssistantException) { assertTrue(e.message!!.contains("no devolvió texto")) }
            }
        }
    }
}
