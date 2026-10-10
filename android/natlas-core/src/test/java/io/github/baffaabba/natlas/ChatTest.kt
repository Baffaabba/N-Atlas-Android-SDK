package io.github.baffaabba.natlas

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatTest {
    private lateinit var server: MockWebServer
    private val messages = listOf(ChatMessage.system("Ka amsa da Hausa."), ChatMessage.user("Sannu! Yaya kake?"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun chunk(content: String?, finish: String? = null, role: Boolean = false): String {
        val delta = buildList {
            if (role) add("\"role\":\"assistant\"")
            if (content != null) add("\"content\":${Json.encodeToString(String.serializer(), content)}")
        }.joinToString(",")
        val fr = finish?.let { "\"$it\"" } ?: "null"
        return "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{$delta},\"finish_reason\":$fr}]}\n\n"
    }

    private fun sse(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    @Test
    fun `complete sends model card defaults and parses the reply`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"x","object":"chat.completion","choices":[{"index":0,
                  "message":{"role":"assistant","content":"Lafiya lau, na gode."},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":12,"completion_tokens":6,"total_tokens":18}}""",
            ),
        )
        val r = TestClient(server).client.chat.complete(messages)
        assertEquals("Lafiya lau, na gode.", r.text)
        assertEquals("stop", r.finishReason)
        assertEquals(Usage(12, 6, 18), r.usage)

        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        val json = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("NCAIR1/N-ATLaS", json["model"]!!.jsonPrimitive.content)
        assertEquals(false, json["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(0.1, json["temperature"]!!.jsonPrimitive.double, 0.0)
        assertEquals(512, json["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(1.12, json["repetition_penalty"]!!.jsonPrimitive.double, 0.0)
        assertFalse(json.containsKey("top_p"))
        assertFalse(json.containsKey("stop"))
        val sent = json["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), sent.map { it["role"]!!.jsonPrimitive.content })
        assertEquals("Sannu! Yaya kake?", sent[1]["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `options are sent`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"},"finish_reason":"length"}]}"""))
        val r = TestClient(server).client.chat.complete(
            messages,
            ChatOptions(temperature = 0.7, maxTokens = 20, repetitionPenalty = 1.0, topP = 0.9, stop = listOf("\n")),
        )
        assertEquals("length", r.finishReason)
        assertEquals(null, r.usage)
        val json: JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(0.9, json["top_p"]!!.jsonPrimitive.double, 0.0)
        assertEquals(20, json["max_tokens"]!!.jsonPrimitive.int)
        assertEquals("\n", json["stop"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `empty messages is InvalidRequest and nothing is sent`() = runBlocking {
        try {
            TestClient(server).client.chat.complete(emptyList()); fail()
        } catch (expected: NAtlasException.InvalidRequest) {
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `complete with malformed json is ServerError bad_response`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices": "nope"}"""))
        try {
            TestClient(server).client.chat.complete(messages); fail()
        } catch (e: NAtlasException.ServerError) {
            assertEquals("bad_response", e.code)
        }
    }

    @Test
    fun `complete with no choices is ServerError`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices": []}"""))
        try {
            TestClient(server).client.chat.complete(messages); fail()
        } catch (e: NAtlasException.ServerError) {
            assertEquals("bad_response", e.code)
        }
    }

    @Test
    fun `stream emits deltas in order and ends at DONE`() = runBlocking {
        server.enqueue(
            sse(
                ": keep-alive\n\n" +
                    chunk("", role = true) +
                    chunk("Lafiya") +
                    chunk(" lau,") +
                    chunk(" na gode.") +
                    chunk(null, finish = "stop") +
                    "data: [DONE]\n\n",
            ),
        )
        val deltas = withTimeout(10_000) { TestClient(server).client.chat.stream(messages).toList() }
        assertEquals(
            listOf(ChatDelta("Lafiya"), ChatDelta(" lau,"), ChatDelta(" na gode."), ChatDelta("", "stop")),
            deltas,
        )
        val json = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(true, json["stream"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `stream handles CRLF line endings and multi-line data`() = runBlocking {
        val body = chunk("A").replace("\n", "\r\n") + "data: [DONE]\r\n\r\n"
        server.enqueue(sse(body))
        val deltas = TestClient(server).client.chat.stream(messages).toList()
        assertEquals(listOf(ChatDelta("A")), deltas)
    }

    @Test
    fun `stream accepts a final DONE without a trailing blank line`() = runBlocking {
        server.enqueue(sse(chunk("A") + "data: [DONE]"))
        assertEquals(listOf(ChatDelta("A")), TestClient(server).client.chat.stream(messages).toList())
    }

    @Test
    fun `error event mid-stream throws after the text so far`() = runBlocking {
        server.enqueue(
            sse(chunk("Lafiya", role = true) + "data: " + errorJson("backend_unavailable", "GPU went away") + "\n\ndata: [DONE]\n\n"),
        )
        val got = mutableListOf<ChatDelta>()
        try {
            TestClient(server).client.chat.stream(messages).collect { got += it }
            fail()
        } catch (e: NAtlasException.ServerError) {
            assertEquals(503, e.httpCode)
            assertEquals("backend_unavailable", e.code)
        }
        assertEquals(listOf(ChatDelta("Lafiya")), got)
    }

    @Test
    fun `stream that drops mid-way throws Network`() = runBlocking {
        server.enqueue(sse(chunk("Lafiya") + chunk(" lau")).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        val got = mutableListOf<ChatDelta>()
        try {
            TestClient(server, maxRetries = 0).client.chat.stream(messages).collect { got += it }
            fail()
        } catch (e: NAtlasException.Network) {
            // expected
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `stream that ends without DONE throws Network`() = runBlocking {
        server.enqueue(sse(chunk("Lafiya")))
        val got = mutableListOf<ChatDelta>()
        try {
            TestClient(server).client.chat.stream(messages).collect { got += it }
            fail()
        } catch (e: NAtlasException.Network) {
            assertTrue(e.message!!.contains("dropped"))
        }
        assertEquals(listOf(ChatDelta("Lafiya")), got)
    }

    @Test
    fun `malformed chunk throws ServerError bad_response`() = runBlocking {
        server.enqueue(sse("data: {oops\n\n"))
        try {
            TestClient(server).client.chat.stream(messages).toList(); fail()
        } catch (e: NAtlasException.ServerError) {
            assertEquals("bad_response", e.code)
        }
    }

    @Test
    fun `stream open 401 is Unauthorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorJson("invalid_api_key")))
        try {
            TestClient(server).client.chat.stream(messages).toList(); fail()
        } catch (expected: NAtlasException.Unauthorized) {
        }
    }

    @Test
    fun `stream open 429 is retried before any event`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody(errorJson("rate_limited")))
        server.enqueue(sse(chunk("Sannu") + "data: [DONE]\n\n"))
        val tc = TestClient(server)
        assertEquals(listOf(ChatDelta("Sannu")), tc.client.chat.stream(messages).toList())
        assertEquals(2, server.requestCount)
        assertTrue(tc.sleeps.single() in 1000L..1250L)
    }

    @Test
    fun `first delta arrives before the reply is complete and cancelling stops the stream`() = runBlocking {
        // The body trickles out slowly; taking the first delta must not wait for all of it.
        val body = chunk("Sannu") + (1..50).joinToString("") { chunk(" kalma$it") } + "data: [DONE]\n\n"
        server.enqueue(sse(body).throttleBody(256, 100, TimeUnit.MILLISECONDS))
        val start = System.nanoTime()
        val first = withTimeout(5_000) { TestClient(server).client.chat.stream(messages).first() }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(ChatDelta("Sannu"), first)
        assertTrue("took $ms ms", ms < 3_000)
    }

    @Test
    fun `stream timeout is Timeout`() = runBlocking {
        server.enqueue(sse(chunk("A") + "data: [DONE]\n\n").setHeadersDelay(3, TimeUnit.SECONDS))
        try {
            TestClient(server, maxRetries = 0, readTimeout = kotlin.time.Duration.parse("1s"))
                .client.chat.stream(messages).toList()
            fail()
        } catch (expected: NAtlasException.Timeout) {
        }
    }
}
