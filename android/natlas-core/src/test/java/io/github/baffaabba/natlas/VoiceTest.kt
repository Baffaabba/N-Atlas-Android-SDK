package io.github.baffaabba.natlas

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VoiceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    /** Sends [onOpen] messages; on `stop`, sends [onStop] then done and closes. */
    private class Script(private val onOpen: List<String>, private val onStop: List<String> = emptyList()) :
        WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            onOpen.forEach { webSocket.send(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.contains("\"stop\"")) {
                onStop.forEach { webSocket.send(it) }
                webSocket.send("""{"type":"done"}""")
                webSocket.close(1000, null)
            }
        }
    }

    private val ready = """{"type":"ready","model":"NCAIR1/Hausa-ASR","sample_rate":16000}"""

    private fun sseReply(vararg parts: String): MockResponse {
        val body = buildString {
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}\n\n")
            parts.forEach { append("data: {\"choices\":[{\"delta\":{\"content\":\"$it\"},\"finish_reason\":null}]}\n\n") }
            append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
            append("data: [DONE]\n\n")
        }
        return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
    }

    @Test
    fun `autoStop turn emits events in order and sends history plus what was heard`() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                Script(
                    onOpen = listOf(
                        ready,
                        """{"type":"partial","text":"Ina","start":0.0,"end":0.5}""",
                        """{"type":"final","text":"Ina kwana?","start":0.0,"end":1.2}""",
                    ),
                ),
            ),
        )
        server.enqueue(sseReply("Lafiya", " lau."))
        val tc = TestClient(server)
        val history = listOf(ChatMessage.system("Ka amsa da Hausa."))

        val events = withTimeout(10_000) {
            tc.client.voice.converse(Language.HAUSA, history = history, autoStop = true).events.toList()
        }

        assertEquals(
            listOf(
                VoiceEvent.Listening,
                VoiceEvent.Heard("Ina", isFinal = false),
                VoiceEvent.Heard("Ina kwana?", isFinal = true),
                VoiceEvent.Thinking,
                VoiceEvent.Reply("Lafiya"),
                VoiceEvent.Reply(" lau."),
                VoiceEvent.Done(userText = "Ina kwana?", replyText = "Lafiya lau."),
            ),
            events,
        )
        assertTrue(tc.audio.last.released)

        server.takeRequest() // WebSocket upgrade
        val chat = server.takeRequest()
        assertEquals("/v1/chat/completions", chat.path)
        val messages = Json.parseToJsonElement(chat.body.readUtf8()).jsonObject["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Ina kwana?", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `manual stop joins several utterances`() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                Script(
                    onOpen = listOf(ready, """{"type":"final","text":"Sannu.","start":0.0,"end":1.0}"""),
                    onStop = listOf("""{"type":"final","text":"Yaya aiki?","start":1.5,"end":2.4}"""),
                ),
            ),
        )
        server.enqueue(sseReply("Aiki yana tafiya."))
        val tc = TestClient(server)
        val turn = tc.client.voice.converse(Language.HAUSA)

        val events = withTimeout(10_000) {
            val got = mutableListOf<VoiceEvent>()
            turn.events.collect { e ->
                got += e
                if (e is VoiceEvent.Heard && e.isFinal && e.text == "Sannu.") turn.stop()
            }
            got
        }
        assertEquals(VoiceEvent.Heard("Sannu. Yaya aiki?", isFinal = true), events[2])
        assertEquals(VoiceEvent.Done("Sannu. Yaya aiki?", "Aiki yana tafiya."), events.last())
    }

    @Test
    fun `nothing heard ends with an empty Done and no chat call`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(Script(onOpen = listOf(ready))))
        val audio = FakeAudioFactory { FakeAudioSource(frames = 2, endAfterFrames = true) }
        val tc = TestClient(server, audio = audio)
        val events = withTimeout(10_000) { tc.client.voice.converse(Language.HAUSA).events.toList() }
        assertEquals(listOf(VoiceEvent.Listening, VoiceEvent.Done("", "")), events)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `chat failure after listening surfaces as NAtlasException`() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                Script(onOpen = listOf(ready, """{"type":"final","text":"Kedu","start":0.0,"end":0.6}""")),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorJson("invalid_api_key")))
        val tc = TestClient(server)
        val got = mutableListOf<VoiceEvent>()
        try {
            withTimeout(10_000) {
                tc.client.voice.converse(Language.IGBO, autoStop = true).events.collect { got += it }
            }
            fail()
        } catch (expected: NAtlasException.Unauthorized) {
        }
        assertEquals(VoiceEvent.Thinking, got.last())
        assertTrue(tc.audio.last.released)
    }
}
