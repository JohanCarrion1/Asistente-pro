package org.asistentepro.asistentepro

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroqClientTest {
    @Test fun `sends conversation and extracts assistant answer`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Aquí tienes un plan."}}]}"""))
            val client = GroqClient(OkHttpClient(), server.url("/chat").toString())
            val history = listOf(Message("1", "Necesito un plan", true), Message("2", "¿Sobre qué?", false), Message("3", "Mi semana", true))
            assertEquals("Aquí tienes un plan.", client.execute(client.call("test-key", "model", history)))
            val request = server.takeRequest()
            assertEquals("Bearer test-key", request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals(4, body.getJSONArray("messages").length())
            assertEquals("assistant", body.getJSONArray("messages").getJSONObject(2).getString("role"))
        }
    }
    @Test fun `invalid key returns useful error without exposing key`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("private provider response"))
            val client = GroqClient(OkHttpClient(), server.url("/chat").toString())
            try { client.execute(client.call("private-key", "model", emptyList())); fail("Expected authentication failure") }
            catch (error: AssistantException) { assertTrue(error.message!!.contains("clave")); assertFalse(error.message!!.contains("private")) }
        }
    }
    @Test fun `empty answer does not produce fake success`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
            val client = GroqClient(OkHttpClient(), server.url("/chat").toString())
            try { client.execute(client.call("test", "model", emptyList())); fail("Expected empty response failure") }
            catch (error: AssistantException) { assertTrue(error.message!!.contains("vacía")) }
        }
    }
}
