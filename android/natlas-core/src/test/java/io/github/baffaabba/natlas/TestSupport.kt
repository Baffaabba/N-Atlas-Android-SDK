package io.github.baffaabba.natlas

import io.github.baffaabba.natlas.internal.AudioSource
import io.github.baffaabba.natlas.internal.AudioSourceFactory
import okhttp3.mockwebserver.MockWebServer
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A fake microphone: returns [frames] frames of silence, then either blocks like a real mic
 * (returning a frame every [frameDelayMs]) or reports end of audio.
 */
internal class FakeAudioSource(
    private val frames: Int = 3,
    private val endAfterFrames: Boolean = false,
    private val frameDelayMs: Long = 5,
    private val startError: NAtlasException? = null,
    private val readErrorAfter: Int? = null,
) : AudioSource {
    val started = AtomicBoolean(false)
    val releaseCount = AtomicInteger(0)
    val reads = AtomicInteger(0)
    val released: Boolean get() = releaseCount.get() > 0

    override fun start() {
        startError?.let { throw it }
        started.set(true)
    }

    override fun read(buffer: ByteArray): Int {
        check(!released) { "read after release" }
        val n = reads.incrementAndGet()
        if (readErrorAfter != null && n > readErrorAfter) throw NAtlasException.MicUnavailable()
        if (endAfterFrames && n > frames) return -1
        Thread.sleep(frameDelayMs)
        buffer.fill(n.toByte())
        return buffer.size
    }

    override fun release() {
        releaseCount.incrementAndGet()
    }
}

/** Records the factory's sources so a test can check them after the session. */
internal class FakeAudioFactory(private val make: () -> FakeAudioSource = { FakeAudioSource() }) :
    AudioSourceFactory {
    val created: MutableList<FakeAudioSource> = Collections.synchronizedList(mutableListOf())
    override fun create(): AudioSource = make().also { created += it }
    val last: FakeAudioSource get() = created.last()
}

/** A client against [server] whose retries record their waits instead of sleeping. */
internal class TestClient(
    server: MockWebServer,
    maxRetries: Int = 2,
    readTimeout: Duration = 10.seconds,
    val audio: FakeAudioFactory = FakeAudioFactory(),
) {
    val sleeps: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    val client = NAtlasClient(
        NAtlasConfig(
            baseUrl = server.url("/").toString(),
            apiKey = "test-key",
            maxRetries = maxRetries,
            readTimeout = readTimeout,
            userId = "user-123",
        ),
        "natlas-android/test",
        audio,
    ) { sleeps += it }
}

internal fun errorJson(code: String, message: String = "msg"): String =
    """{"error":{"message":"$message","type":"error","code":"$code"}}"""
