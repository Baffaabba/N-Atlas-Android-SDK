package io.github.baffaabba.natlas

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LiveTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    /** A scripted server side of `/v1/audio/live`. */
    private open class FakeLiveServer(
        val onOpenSend: List<String> = listOf("""{"type":"ready","model":"NCAIR1/Hausa-ASR","sample_rate":16000}"""),
        val onStopSend: List<String> = listOf(
            """{"type":"final","text":"Sannu da zuwa","start":0.0,"end":1.5}""",
            """{"type":"done"}""",
        ),
    ) : WebSocketListener() {
        val binaryBytes = AtomicInteger(0)
        val binaryFrames = AtomicInteger(0)
        val texts: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val stopReceived = CountDownLatch(1)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            onOpenSend.forEach { webSocket.send(it) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            binaryFrames.incrementAndGet()
            binaryBytes.addAndGet(bytes.size)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            texts += text
            if (text.contains("\"stop\"")) {
                stopReceived.countDown()
                onStopSend.forEach { webSocket.send(it) }
                webSocket.close(1000, null)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
    }

    @Test
    fun `live session streams audio and ends after stop`() = runBlocking {
        val fake = FakeLiveServer(
            onOpenSend = listOf(
                """{"type":"ready","model":"NCAIR1/Hausa-ASR","sample_rate":16000}""",
                """{"type":"partial","text":"Sannu","start":0.0,"end":0.8}""",
            ),
        )
        server.enqueue(MockResponse().withWebSocketUpgrade(fake))
        val tc = TestClient(server)
        val live = tc.client.asr.live(Language.HAUSA)

        val events = withTimeout(10_000) {
            val got = mutableListOf<LiveEvent>()
            live.events.collect { e ->
                got += e
                if (e is LiveEvent.Partial) {
                    Thread.sleep(50) // let a few frames go out
                    live.stop()
                }
            }
            got
        }

        assertEquals(
            listOf(
                LiveEvent.Ready("NCAIR1/Hausa-ASR"),
                LiveEvent.Partial("Sannu", 0.0, 0.8),
                LiveEvent.Final("Sannu da zuwa", 0.0, 1.5),
            ),
            events,
        )
        val req = server.takeRequest()
        assertEquals("/v1/audio/live?language=ha", req.path)
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        assertEquals("user-123", req.getHeader("X-NAtlas-User"))
        assertTrue(fake.stopReceived.await(5, TimeUnit.SECONDS))
        assertTrue("frames ${fake.binaryFrames}", fake.binaryFrames.get() > 0)
        assertEquals(0, fake.binaryBytes.get() % 3200) // whole 100 ms frames
        assertEquals("""{"type":"stop"}""", fake.texts.last())
        assertTrue(tc.audio.last.started.get())
        assertTrue(tc.audio.last.released)
    }

    @Test
    fun `audio recorded before ready is sent, then stop`() = runBlocking {
        // The source ends on its own after 5 frames, which acts like stop().
        val audio = FakeAudioFactory { FakeAudioSource(frames = 5, endAfterFrames = true) }
        val fake = FakeLiveServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(fake))
        val tc = TestClient(server, audio = audio)
        val events = withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.toList() }
        assertEquals(LiveEvent.Ready("NCAIR1/Hausa-ASR"), events.first())
        assertEquals(LiveEvent.Final("Sannu da zuwa", 0.0, 1.5), events.last())
        assertEquals(5, fake.binaryFrames.get())
        assertEquals(5 * 3200, fake.binaryBytes.get())
        assertTrue(audio.last.released)
    }

    @Test
    fun `cancelling the collector releases the microphone`() = runBlocking {
        val fake = FakeLiveServer(onStopSend = emptyList())
        server.enqueue(MockResponse().withWebSocketUpgrade(fake))
        val tc = TestClient(server)
        val ready = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            tc.client.asr.live(Language.HAUSA).events.collect { if (it is LiveEvent.Ready) ready.complete(Unit) }
        }
        withTimeout(10_000) { ready.await() }
        job.cancel()
        withTimeout(5_000) { job.join() }
        assertTrue(job.isCancelled)
        assertTrue(tc.audio.last.released)
        assertFalse(fake.stopReceived.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `error frame throws the mapped exception and releases the microphone`() = runBlocking {
        val fake = FakeLiveServer(
            onOpenSend = listOf(
                """{"type":"ready","model":"NCAIR1/Hausa-ASR","sample_rate":16000}""",
                """{"type":"error","error":{"message":"slow down","type":"error","code":"rate_limited"}}""",
            ),
        )
        server.enqueue(MockResponse().withWebSocketUpgrade(fake))
        val tc = TestClient(server)
        val got = mutableListOf<LiveEvent>()
        try {
            withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.collect { got += it } }
            fail()
        } catch (expected: NAtlasException.RateLimited) {
        }
        assertEquals(listOf<LiveEvent>(LiveEvent.Ready("NCAIR1/Hausa-ASR")), got)
        assertTrue(tc.audio.last.released)
    }

    @Test
    fun `rejected upgrade 401 is Unauthorized`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorJson("invalid_api_key")))
        val tc = TestClient(server)
        try {
            withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.toList() }
            fail()
        } catch (expected: NAtlasException.Unauthorized) {
        }
        assertTrue(tc.audio.last.released)
    }

    @Test
    fun `rejected upgrade 403 user cap is UserCapReached`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody(errorJson("user_cap_reached")))
        val tc = TestClient(server)
        try {
            withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.toList() }
            fail()
        } catch (expected: NAtlasException.UserCapReached) {
        }
    }

    @Test
    fun `mic permission denied fails before connecting`() = runBlocking {
        val audio = FakeAudioFactory { FakeAudioSource(startError = NAtlasException.MicPermissionDenied()) }
        val tc = TestClient(server, audio = audio)
        try {
            tc.client.asr.live(Language.HAUSA).events.toList(); fail()
        } catch (expected: NAtlasException.MicPermissionDenied) {
        }
        assertEquals(0, server.requestCount)
        assertTrue(audio.last.released)
    }

    @Test
    fun `mic failure mid-session is MicUnavailable and releases`() = runBlocking {
        val audio = FakeAudioFactory { FakeAudioSource(readErrorAfter = 2) }
        server.enqueue(MockResponse().withWebSocketUpgrade(FakeLiveServer(onStopSend = emptyList())))
        val tc = TestClient(server, audio = audio)
        try {
            withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.toList() }
            fail()
        } catch (expected: NAtlasException.MicUnavailable) {
        }
        assertTrue(audio.last.released)
    }

    @Test
    fun `server closing without done is Network`() = runBlocking {
        val fake = object : FakeLiveServer() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"ready","model":"NCAIR1/Hausa-ASR","sample_rate":16000}""")
                webSocket.close(1011, "crash")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(fake))
        val tc = TestClient(server)
        try {
            withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.toList() }
            fail()
        } catch (expected: NAtlasException.Network) {
        }
        assertTrue(tc.audio.last.released)
    }

    @Test
    fun `close code 4401 without an error frame is Unauthorized`() = runBlocking {
        val fake = object : FakeLiveServer() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.close(4401, "invalid_api_key")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(fake))
        val tc = TestClient(server)
        try {
            withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.toList() }
            fail()
        } catch (expected: NAtlasException.Unauthorized) {
        }
        assertTrue(tc.audio.last.released)
    }

    @Test
    fun `connect-time error frame before ready is mapped`() = runBlocking {
        val fake = object : FakeLiveServer() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"error","error":{"message":"cap","type":"permission_error","code":"user_cap_reached"}}""")
                webSocket.close(4403, "user_cap_reached")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(fake))
        val tc = TestClient(server)
        try {
            withTimeout(10_000) { tc.client.asr.live(Language.HAUSA).events.toList() }
            fail()
        } catch (expected: NAtlasException.UserCapReached) {
        }
    }

    @Test
    fun `server that never finishes after stop times out`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(FakeLiveServer(onStopSend = emptyList()).let { base ->
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = base.onOpen(webSocket, response)
                // Ignores stop entirely.
            }
        }))
        val tc = TestClient(server, readTimeout = kotlin.time.Duration.parse("1s"))
        val live = tc.client.asr.live(Language.HAUSA)
        try {
            withTimeout(10_000) {
                live.events.collect { if (it is LiveEvent.Ready) live.stop() }
            }
            fail()
        } catch (expected: NAtlasException.Timeout) {
        }
        assertTrue(tc.audio.last.released)
    }

    @Test
    fun `stop before collecting completes empty without opening anything`() = runBlocking {
        val tc = TestClient(server)
        val live = tc.client.asr.live(Language.HAUSA)
        live.stop()
        assertTrue(live.events.toList().isEmpty())
        assertEquals(0, server.requestCount)
        assertTrue(tc.audio.created.isEmpty())
    }

    @Test
    fun `events can be collected only once`() = runBlocking {
        val tc = TestClient(server)
        val live = tc.client.asr.live(Language.HAUSA)
        live.stop()
        live.events.toList()
        try {
            live.events.toList(); fail()
        } catch (expected: IllegalStateException) {
        }
    }
}
