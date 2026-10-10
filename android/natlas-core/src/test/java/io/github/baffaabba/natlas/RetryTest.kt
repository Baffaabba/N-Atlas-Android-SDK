package io.github.baffaabba.natlas

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RetryTest {
    private lateinit var server: MockWebServer
    private val okChat = """{"choices":[{"message":{"role":"assistant","content":"Lafiya"},"finish_reason":"stop"}]}"""
    private val hello = listOf(ChatMessage.user("Sannu"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `429 with Retry-After waits that long then succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2").setBody(errorJson("rate_limited")))
        server.enqueue(MockResponse().setBody(okChat))
        val tc = TestClient(server)

        assertEquals("Lafiya", tc.client.chat.complete(hello).text)
        assertEquals(2, server.requestCount)
        assertEquals(1, tc.sleeps.size)
        assertTrue("slept ${tc.sleeps}", tc.sleeps[0] in 2000L..2250L)
    }

    @Test
    fun `503 model loading is retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "1").setBody(errorJson("model_loading")))
        server.enqueue(MockResponse().setResponseCode(503).setBody(errorJson("backend_unavailable")))
        server.enqueue(MockResponse().setBody(okChat))
        val tc = TestClient(server)

        assertEquals("Lafiya", tc.client.chat.complete(hello).text)
        assertEquals(3, server.requestCount)
        assertEquals(2, tc.sleeps.size)
        assertTrue(tc.sleeps[0] in 1000L..1250L)
        // Second wait has no Retry-After: exponential backoff with jitter, attempt 1 -> 500..1000 ms.
        assertTrue("slept ${tc.sleeps}", tc.sleeps[1] in 500L..1000L)
    }

    @Test
    fun `gives up after maxRetries and reports the last error`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429).setBody(errorJson("rate_limited"))) }
        val tc = TestClient(server, maxRetries = 2)
        try {
            tc.client.chat.complete(hello)
            fail()
        } catch (e: NAtlasException.RateLimited) {
            assertEquals(null, e.retryAfterSeconds)
        }
        assertEquals(3, server.requestCount)
        assertEquals(2, tc.sleeps.size)
    }

    @Test
    fun `long Retry-After is reported, not waited out`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120").setBody(errorJson("rate_limited")))
        val tc = TestClient(server)
        try {
            tc.client.chat.complete(hello)
            fail()
        } catch (e: NAtlasException.RateLimited) {
            assertEquals(120L, e.retryAfterSeconds)
        }
        assertEquals(1, server.requestCount)
        assertTrue(tc.sleeps.isEmpty())
    }

    @Test
    fun `401 and 400 are never retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorJson("invalid_api_key")))
        server.enqueue(MockResponse().setResponseCode(400).setBody(errorJson("invalid_request")))
        val tc = TestClient(server)
        try {
            tc.client.chat.complete(hello); fail()
        } catch (expected: NAtlasException.Unauthorized) {
        }
        try {
            tc.client.chat.complete(hello); fail()
        } catch (expected: NAtlasException.InvalidRequest) {
        }
        assertEquals(2, server.requestCount)
        assertTrue(tc.sleeps.isEmpty())
    }

    @Test
    fun `500 is not retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorJson("internal_error")))
        val tc = TestClient(server)
        try {
            tc.client.chat.complete(hello); fail()
        } catch (expected: NAtlasException.ServerError) {
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `transcribe is retried after 429`() = runBlocking {
        val file = java.io.File.createTempFile("clip", ".wav").apply { writeBytes(ByteArray(100)) }
        try {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody(errorJson("rate_limited")))
            server.enqueue(MockResponse().setBody("""{"text":"Bawo ni"}"""))
            val tc = TestClient(server)
            assertEquals("Bawo ni", tc.client.asr.transcribe(file, Language.YORUBA).text)
            assertEquals(2, server.requestCount)
            // The retried upload carries the whole file again.
            server.takeRequest()
            assertTrue(server.takeRequest().bodySize > 100)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `maxRetries 0 turns retries off`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody(errorJson("model_loading")))
        val tc = TestClient(server, maxRetries = 0)
        try {
            tc.client.chat.complete(hello); fail()
        } catch (expected: NAtlasException.ModelLoading) {
        }
        assertEquals(1, server.requestCount)
    }
}
