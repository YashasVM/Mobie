package dev.yashasvm.mobie.data.cloud

import dev.yashasvm.mobie.core.model.CloudModel
import dev.yashasvm.mobie.core.security.CloudConnection
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OpenCodeCloudProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var provider: OpenCodeCloudProvider
    private val text = CloudModel("big-pickle", "Big Pickle", false, "Provider terms apply.")
    private val vision = CloudModel("glm-5.3-flash-free", "GLM 5.3 Flash Free", true, "Provider terms apply.")

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        provider = OpenCodeCloudProvider(client) { CloudConnection(server.url("/inference").toString(), "test-pairing-key") }
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `catalog includes every gateway offering with capabilities and no fixed allowlist`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"big-pickle","title":"Big Pickle","images":false},{"id":"glm-5.3-flash-free","title":"GLM","images":true,"privacy_notice":"Provider terms"},{"id":"new-free"},{"id":"paid-model"},{"id":"new-free"},{"id":"../unsafe"}]}"""))
        val models = provider.models()
        assertEquals(listOf("big-pickle", "glm-5.3-flash-free", "new-free", "paid-model"), models.map { it.id })
        assertTrue(models.first { it.id == "glm-5.3-flash-free" }.supportsImages)
        assertEquals("Provider terms", models.first { it.id == "glm-5.3-flash-free" }.privacyNotice)
        assertFalse(models.first { it.id == "new-free" }.supportsImages)
        val request = server.takeRequest()
        assertEquals("/inference/v1/models", request.path)
        assertEquals("Bearer test-pairing-key", request.getHeader("Authorization"))
    }

    @Test fun `new gateway offering generates without an app update`() = runBlocking {
        server.enqueue(stream("data: [DONE]\n\n"))
        val fresh = CloudModel("new-free", "New", false, "Provider terms")
        assertEquals(InferenceEvent.Complete, provider.generate(fresh, listOf(RuntimeMessage(true, "Hi"))).toList().last())
        assertEquals("new-free", Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["model"]!!.jsonPrimitive.content)
    }

    @Test fun `streams reasoning and answer with conversation and paired gateway credentials`() = runBlocking {
        server.enqueue(stream("""data: {"choices":[{"delta":{"reasoning_content":"Thinking"}}]}

data: {"choices":[{"delta":{"content":"Hello"}}]}

data: {"choices":[{"delta":{"content":" there"},"finish_reason":"stop"}]}

"""))
        val events = provider.generate(text, listOf(RuntimeMessage(true, "Earlier"), RuntimeMessage(false, "Answer"), RuntimeMessage(true, "Hi"))).toList()
        assertEquals(listOf(InferenceEvent.Token("Thinking", true), InferenceEvent.Token("Hello"), InferenceEvent.Token(" there"), InferenceEvent.Complete), events)
        val request = server.takeRequest()
        assertEquals("/inference/v1/chat/completions", request.path)
        assertEquals("Bearer test-pairing-key", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("big-pickle", body["model"]?.jsonPrimitive?.content)
        assertEquals(listOf("Earlier", "Answer", "Hi"), body["messages"]!!.jsonArray.map { it.jsonObject["content"]!!.jsonPrimitive.content })
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
    }

    @Test fun `encodes both restored and new photos as inline image content`() = runBlocking {
        val photo = File.createTempFile("cloud-photo", ".png")
        try {
            photo.writeBytes(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            server.enqueue(stream("data: [DONE]\n\n"))
            assertEquals(InferenceEvent.Complete, provider.generate(vision,
                listOf(RuntimeMessage(true, "Old photo", imagePath = photo.path), RuntimeMessage(false, "A photo"),
                    RuntimeMessage(true, "New photo", imagePath = photo.path))).toList().last())
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val messages = body["messages"]!!.jsonArray
            for (index in listOf(0, 2)) {
                val content = messages[index].jsonObject["content"]!!.jsonArray
                assertEquals("image_url", content[1].jsonObject["type"]!!.jsonPrimitive.content)
                assertEquals("data:image/png;base64,iVBORw0KGgo=", content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
            }
        } finally { photo.delete() }
    }

    @Test fun `text model refuses photos before any network request`() = runBlocking {
        val events = provider.generate(text, listOf(RuntimeMessage(true, "Photo", imagePath = "/missing.png"))).toList()
        assertEquals(listOf(InferenceEvent.Error("This cloud model can't read photos.")), events)
        assertEquals(0, server.requestCount)
    }

    @Test fun `rate limit reports retry time without echoing provider body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120").setBody("secret prompt/token"))
        assertEquals(listOf(InferenceEvent.Error("OpenCode's usage limit was reached. Try again in 120 seconds.")),
            provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).toList())
    }

    @Test fun `denial model removal and server failures remain explicit`() = runBlocking {
        for (code in listOf(403, 404, 503)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("private details"))
            val events = provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).toList()
            assertTrue(events.single() is InferenceEvent.Error)
            assertFalse((events.single() as InferenceEvent.Error).message.contains("private details"))
        }
    }

    @Test fun `free tier app restriction explains why Mobie cannot access the model`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("Content-Type", "application/json").setBody(
            """{"type":"FreeTierError","message":"Error from provider (Console): OpenCode's free tier can only be used from within OpenCode","details":"private prompt and token"}"""
        ))
        assertEquals(listOf(InferenceEvent.Error("OpenCode restricts this free model to its own app. Direct access from Mobie is unavailable.")),
            provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).toList())
    }

    @Test fun `security denials explain access restriction without claiming a rate limit`() = runBlocking {
        for (body in listOf(
            "<html><title>Access denied</title><body>Cloudflare access denied. private prompt and token</body></html>",
            "error code: 1010\nprivate prompt and token"
        )) {
            server.enqueue(MockResponse().setResponseCode(403).setBody(body))
            assertEquals(listOf(InferenceEvent.Error("OpenCode's security service blocked this request (HTTP 403). This is an access restriction, not a confirmed rate limit.")),
                provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).toList())
        }
    }

    @Test fun `authentication denial asks for the current pairing key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("private prompt and token"))
        assertEquals(listOf(InferenceEvent.Error("Your Linux box rejected the pairing key. Save the current key in Settings.")),
            provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).toList())
    }

    @Test fun `unknown forbidden response makes no promise of free access`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private prompt and token"))
        assertEquals(listOf(InferenceEvent.Error("Your Linux box refused access (HTTP 403). Check its OpenCode connection.")),
            provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).toList())
    }

    @Test fun `redirect never forwards conversation to another destination`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "https://example.invalid/collect"))
        val events = provider.generate(text, listOf(RuntimeMessage(true, "Private"))).toList()
        assertTrue(events.single() is InferenceEvent.Error)
        assertEquals(1, server.requestCount)
    }

    @Test fun `truncated and malformed streams fail instead of claiming completion`() = runBlocking {
        for (body in listOf("data: not-json\n\n", "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n")) {
            server.enqueue(stream(body))
            val events = provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).toList()
            assertTrue(events.last() is InferenceEvent.Error)
            assertFalse(events.contains(InferenceEvent.Complete))
        }
    }

    @Test fun `cancellation closes the active network call`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val work = launch(Dispatchers.IO) { provider.generate(text, listOf(RuntimeMessage(true, "Hi"))).collect() }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
        work.cancelAndJoin()
        withTimeout(5_000) {
            while (client.dispatcher.runningCallsCount() > 0) delay(10)
        }
    }

    @Test fun `unpaired gateway emits an actionable error without sending a request`() = runBlocking {
        val unpaired = OpenCodeCloudProvider(client) { null }
        assertEquals(listOf(InferenceEvent.Error("Connect your Linux box in Settings first.")),
            unpaired.generate(text, listOf(RuntimeMessage(true, "Private"))).toList())
        try {
            unpaired.models()
            fail("An unpaired gateway must not fetch a catalog")
        } catch (error: IllegalArgumentException) {
            assertEquals("Connect your Linux box in Settings first.", error.message)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `pairing key and prompt are excluded from gateway error messages`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("test-pairing-key private prompt"))
        val events = provider.generate(text, listOf(RuntimeMessage(true, "private prompt"))).toList()
        assertEquals(listOf(InferenceEvent.Error("Your Linux box could not complete this request (HTTP 500). Check its OpenCode service.")), events)
    }

    private fun stream(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
}
