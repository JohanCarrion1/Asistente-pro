package org.asistentepro.asistentepro

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiClientTest {
    @Test fun everyProviderReceivesCurrentDeviceTimeAndSystemTools() {
        AiProvider.entries.forEach { provider ->
            MockWebServer().use { server ->
                val client = AiClient(OkHttpClient(), server.url("/probe").toString())
                server.enqueue(MockResponse().setBody("{}"))
                client.call(provider, "not-real", provider.defaultModel, history).execute().close()
                val body = JSONObject(server.takeRequest().body.readUtf8())
                val instruction = when (provider) {
                    AiProvider.Gemini -> body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
                    AiProvider.Anthropic -> body.getString("system")
                    else -> body.getJSONArray("messages").getJSONObject(0).getString("content")
                }
                assertTrue(instruction.contains("Datos actuales del dispositivo"))
                assertTrue(instruction.contains(java.time.ZoneId.systemDefault().id))
                assertTrue(body.getJSONArray("tools").toString().contains("brightness"))
                assertTrue(body.getJSONArray("tools").toString().contains("current_time"))
            }
        }
    }
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
                if (provider == AiProvider.OpenRouter) assertEquals("Xylon", request.getHeader("X-OpenRouter-Title"))
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
    @Test fun naturalLanguageUsesNativeToolCallingWithEveryProvider() {
        AiProvider.entries.forEach { provider ->
            MockWebServer().use { server ->
                val args = JSONObject().put("action", "open_app").put("app_name", "YouTube")
                val response = when (provider) {
                    AiProvider.Gemini -> JSONObject().put("candidates", org.json.JSONArray().put(JSONObject().put("content", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("functionCall", JSONObject().put("name", PhoneTools.name).put("args", args)))))))
                    AiProvider.Anthropic -> JSONObject().put("content", org.json.JSONArray().put(JSONObject().put("type", "tool_use").put("id", "test-tool").put("name", PhoneTools.name).put("input", args)))
                    else -> JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("tool_calls", org.json.JSONArray().put(JSONObject().put("id", "test-tool").put("type", "function").put("function", JSONObject().put("name", PhoneTools.name).put("arguments", args.toString())))))))
                }
                server.enqueue(MockResponse().setBody(response.toString()))
                val client = AiClient(OkHttpClient(), server.url("/chat").toString())
                val question = "Me apetece ver mis vídeos; llévame a YouTube, por favor."
                val result = client.reply(provider, client.call(provider, "test-key", provider.defaultModel, listOf(Message("1", question, true))))
                assertEquals(DeviceAction.OpenApp("YouTube"), result.action)
                assertEquals("", result.text)
                val body = JSONObject(server.takeRequest().body.readUtf8())
                assertTrue(body.has("tools")); assertTrue(body.toString().contains(question))
                val definition = body.getJSONArray("tools").getJSONObject(0)
                when (provider) {
                    AiProvider.Gemini -> assertEquals(PhoneTools.name, definition.getJSONArray("functionDeclarations").getJSONObject(0).getString("name"))
                    AiProvider.Anthropic -> assertEquals(PhoneTools.name, definition.getString("name"))
                    else -> assertEquals(PhoneTools.name, definition.getJSONObject("function").getString("name"))
                }
            }
        }
    }
    @Test fun plainAssistantTextCannotExecutePhoneActions() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Abre YouTube"}}]}"""))
            val client = AiClient(OkHttpClient(), server.url("/chat").toString())
            val result = client.reply(AiProvider.Groq, client.call(AiProvider.Groq, "test", "test-model", history))
            assertNull(result.action); assertEquals("Abre YouTube", result.text)
        }
    }
}
