package io.github.baffaabba.natlas

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class TranscribeTest {
    private lateinit var server: MockWebServer
    private lateinit var audio: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        audio = File.createTempFile("clip", ".wav").apply { writeBytes(ByteArray(3200) { 1 }) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        audio.delete()
    }

    private inline fun <reified T : NAtlasException> expectError(
        client: NAtlasClient = TestClient(server, maxRetries = 0).client,
        file: File = audio,
    ): T = runBlocking {
        try {
            client.asr.transcribe(file, Language.HAUSA)
            fail("Expected ${T::class.simpleName}")
            throw AssertionError()
        } catch (e: NAtlasException) {
            assertTrue("Got $e", e is T)
            e as T
        }
    }

    @Test
    fun `transcribe sends multipart with language and parses verbose json`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"text":" Sannu, yaya kake? ","language":"ha","duration":2.5,
                   "model":"NCAIR1/Hausa-ASR","extra":1,
                   "segments":[{"id":0,"start":0.0,"end":2.5,"text":" Sannu, yaya kake?"}]}""",
            ),
        )
        val t = TestClient(server).client.asr.transcribe(audio, Language.HAUSA)

        assertEquals("Sannu, yaya kake?", t.text)
        assertEquals(Language.HAUSA, t.language)
        assertEquals(2.5, t.durationSeconds!!, 0.0)
        assertEquals("NCAIR1/Hausa-ASR", t.model)
        assertEquals(listOf(Segment(0.0, 2.5, "Sannu, yaya kake?")), t.segments)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/audio/transcriptions", req.path)
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        assertEquals("user-123", req.getHeader("X-NAtlas-User"))
        assertEquals("natlas-android/test", req.getHeader("User-Agent"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"${audio.name}\""))
        assertTrue(body.contains("Content-Type: audio/wav"))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("\r\n\r\nha\r\n"))
        assertTrue(body.contains("verbose_json"))
    }

    @Test
    fun `plain json response without segments works`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"Kedu"}"""))
        val t = TestClient(server).client.asr.transcribe(audio, Language.IGBO)
        assertEquals("Kedu", t.text)
        assertEquals(Language.IGBO, t.language)
        assertEquals("NCAIR1/Igbo-ASR", t.model)
        assertNull(t.durationSeconds)
        assertTrue(t.segments.isEmpty())
    }

    @Test
    fun `base url path prefix is kept`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"x"}"""))
        val client = NAtlasClient(NAtlasConfig(server.url("/natlas/").toString(), "k", userId = "u"))
        client.asr.transcribe(audio, Language.YORUBA)
        val req = server.takeRequest()
        assertEquals("/natlas/v1/audio/transcriptions", req.path)
        assertTrue(req.getHeader("User-Agent")!!.startsWith("natlas-android/"))
    }

    @Test
    fun `400 is InvalidRequest with the server message`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody(errorJson("invalid_request", "Unknown language")))
        val e = expectError<NAtlasException.InvalidRequest>()
        assertEquals("Unknown language", e.message)
    }

    @Test
    fun `401 is Unauthorized`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorJson("invalid_api_key")))
        expectError<NAtlasException.Unauthorized>()
    }

    @Test
    fun `401 without a body is still Unauthorized`() {
        server.enqueue(MockResponse().setResponseCode(401))
        expectError<NAtlasException.Unauthorized>()
    }

    @Test
    fun `403 user cap is UserCapReached`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody(errorJson("user_cap_reached")))
        expectError<NAtlasException.UserCapReached>()
    }

    @Test
    fun `413 is AudioTooLarge`() {
        server.enqueue(MockResponse().setResponseCode(413).setBody(errorJson("audio_too_large")))
        expectError<NAtlasException.AudioTooLarge>()
    }

    @Test
    fun `415 is UnsupportedAudio`() {
        server.enqueue(MockResponse().setResponseCode(415).setBody(errorJson("unsupported_audio")))
        expectError<NAtlasException.UnsupportedAudio>()
    }

    @Test
    fun `429 is RateLimited with Retry-After`() {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "7").setBody(errorJson("rate_limited")),
        )
        val e = expectError<NAtlasException.RateLimited>()
        assertEquals(7L, e.retryAfterSeconds)
    }

    @Test
    fun `503 model loading is ModelLoading`() {
        server.enqueue(
            MockResponse().setResponseCode(503).setHeader("Retry-After", "20").setBody(errorJson("model_loading")),
        )
        assertEquals(20L, expectError<NAtlasException.ModelLoading>().retryAfterSeconds)
    }

    @Test
    fun `503 backend unavailable is ServerError 503`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody(errorJson("backend_unavailable")))
        val e = expectError<NAtlasException.ServerError>()
        assertEquals(503, e.httpCode)
        assertEquals("backend_unavailable", e.code)
    }

    @Test
    fun `500 is ServerError`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorJson("internal_error")))
        val e = expectError<NAtlasException.ServerError>()
        assertEquals(500, e.httpCode)
        assertEquals("internal_error", e.code)
    }

    @Test
    fun `HTML 502 from a dead tunnel is ServerError mentioning baseUrl`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        val e = expectError<NAtlasException.ServerError>()
        assertEquals(502, e.httpCode)
        assertTrue(e.message!!.contains("baseUrl"))
    }

    @Test
    fun `malformed json is ServerError bad_response`() {
        server.enqueue(MockResponse().setBody("{not json"))
        val e = expectError<NAtlasException.ServerError>()
        assertEquals("bad_response", e.code)
    }

    @Test
    fun `slow server is Timeout`() {
        server.enqueue(MockResponse().setBody("""{"text":"x"}""").setHeadersDelay(3, TimeUnit.SECONDS))
        val client = TestClient(server, maxRetries = 0, readTimeout = 1.seconds).client
        expectError<NAtlasException.Timeout>(client)
    }

    @Test
    fun `server that drops the connection is Network`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        expectError<NAtlasException.Network>()
    }

    @Test
    fun `unreachable server is Network`() {
        val url = server.url("/").toString()
        server.shutdown()
        val client = NAtlasClient(NAtlasConfig(url, "k", maxRetries = 0, userId = "u"))
        expectError<NAtlasException.Network>(client)
    }

    @Test
    fun `missing file is InvalidRequest and nothing is sent`() {
        expectError<NAtlasException.InvalidRequest>(file = File(audio.parentFile, "does-not-exist.wav"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `file over 25 MB is AudioTooLarge and nothing is sent`() {
        val big = File.createTempFile("big", ".wav")
        try {
            RandomAccessFile(big, "rw").use { it.setLength(25L * 1024 * 1024 + 1) }
            expectError<NAtlasException.AudioTooLarge>(file = big)
            assertEquals(0, server.requestCount)
        } finally {
            big.delete()
        }
    }
}
