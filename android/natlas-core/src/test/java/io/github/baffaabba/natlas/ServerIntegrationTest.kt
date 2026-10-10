package io.github.baffaabba.natlas

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * End-to-end checks against a running N-ATLAS server (for example `server/` with the fake
 * backend). Skipped unless both environment variables are set:
 *
 * ```
 * NATLAS_IT_BASE_URL=http://127.0.0.1:8000 NATLAS_IT_API_KEY=... ./gradlew :natlas-core:testDebugUnitTest
 * ```
 */
class ServerIntegrationTest {
    private val baseUrl: String? = System.getenv("NATLAS_IT_BASE_URL")
    private val apiKey: String? = System.getenv("NATLAS_IT_API_KEY")

    @Before
    fun requireServer() {
        assumeTrue("NATLAS_IT_BASE_URL / NATLAS_IT_API_KEY not set", !baseUrl.isNullOrBlank() && !apiKey.isNullOrBlank())
    }

    private fun client(audio: FakeAudioFactory = FakeAudioFactory()) = NAtlasClient(
        NAtlasConfig(baseUrl!!, apiKey!!, userId = "it-user"),
        "natlas-android/it",
        audio,
    ) { kotlinx.coroutines.delay(it) }

    /** 16 kHz mono PCM16 tone, [seconds] long. */
    private fun pcmTone(seconds: Double): ByteArray {
        val samples = (16_000 * seconds).toInt()
        val buf = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) buf.putShort((sin(2 * PI * 440 * i / 16_000) * 8000).toInt().toShort())
        return buf.array()
    }

    private fun wavFile(pcm: ByteArray): File {
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        out.write(header.array()); out.write(pcm)
        return File.createTempFile("it-clip", ".wav").apply { writeBytes(out.toByteArray()); deleteOnExit() }
    }

    @Test
    fun transcribeChatStreamLiveVoice() = runBlocking {
        val wav = wavFile(pcmTone(2.0))
        try {
            val t = client().asr.transcribe(wav, Language.HAUSA)
            println("transcribe -> ${t.text} (${t.model}, ${t.durationSeconds}s, ${t.segments.size} segments)")
            assertEquals(Language.HAUSA, t.language)
        } finally {
            wav.delete()
        }

        val r = client().chat.complete(listOf(ChatMessage.user("Sannu! Yaya kake?")))
        println("complete -> ${r.text} (${r.finishReason}, ${r.usage})")
        assertTrue(r.text.isNotEmpty())

        val deltas = withTimeout(60_000) { client().chat.stream(listOf(ChatMessage.user("Sannu!"))).toList() }
        println("stream -> ${deltas.size} deltas: ${deltas.joinToString("") { it.text }}")
        assertTrue(deltas.isNotEmpty())

        // Live: the fake mic sends 3 s of tone, then ends (acts like stop()).
        val liveAudio = FakeAudioFactory { FakeAudioSource(frames = 30, endAfterFrames = true, frameDelayMs = 100) }
        val events = withTimeout(60_000) { client(liveAudio).asr.live(Language.HAUSA).events.toList() }
        println("live -> $events")
        assertTrue(events.first() is LiveEvent.Ready)
        assertTrue(liveAudio.last.released)

        val voiceAudio = FakeAudioFactory { FakeAudioSource(frames = 20, endAfterFrames = true, frameDelayMs = 100) }
        val voice = withTimeout(90_000) { client(voiceAudio).voice.converse(Language.HAUSA).events.toList() }
        println("voice -> $voice")
        assertEquals(VoiceEvent.Listening, voice.first())
        assertTrue(voice.last() is VoiceEvent.Done)
    }

    @Test
    fun wrongKeyIsUnauthorized() = runBlocking {
        val bad = NAtlasClient(NAtlasConfig(baseUrl!!, "wrong-key", userId = "it-user"))
        try {
            bad.chat.complete(listOf(ChatMessage.user("Sannu")))
            throw AssertionError("expected Unauthorized")
        } catch (expected: NAtlasException.Unauthorized) {
        }
    }
}
