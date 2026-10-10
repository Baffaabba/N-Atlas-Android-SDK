package io.github.baffaabba.natlas.internal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.baffaabba.natlas.NAtlasException

/** 16 kHz, mono, 16-bit little-endian PCM: what the server and Whisper expect. */
internal const val SAMPLE_RATE: Int = 16_000

/** 100 ms of audio at 16 kHz mono PCM16. */
internal const val FRAME_BYTES: Int = SAMPLE_RATE * 2 / 10

/**
 * Where live audio comes from. The real one wraps [AudioRecord]; tests use a fake.
 *
 * Calls happen in this order: [start], then [read] repeatedly from one thread, then [release].
 * [release] may be called more than once and must be safe to call even if [start] failed.
 */
internal interface AudioSource {
    /** Opens the microphone. Throws [NAtlasException.MicPermissionDenied] or [NAtlasException.MicUnavailable]. */
    fun start()

    /**
     * Blocks until [buffer] holds the next chunk of PCM16 audio and returns the number of
     * bytes written, or -1 when the source has no more audio. Throws
     * [NAtlasException.MicUnavailable] if recording fails.
     */
    fun read(buffer: ByteArray): Int

    /** Stops recording and frees the microphone. Idempotent. */
    fun release()
}

/** Creates a fresh [AudioSource] for each live session. */
internal fun interface AudioSourceFactory {
    fun create(): AudioSource
}

/** The real microphone, through [AudioRecord] at 16 kHz mono PCM16. */
internal class AudioRecordSource(private val context: Context?) : AudioSource {
    private var record: AudioRecord? = null
    private var released = false

    @SuppressLint("MissingPermission") // Checked just below when a Context is available.
    override fun start() {
        val ctx = context
        if (ctx != null &&
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            throw NAtlasException.MicPermissionDenied()
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            throw NAtlasException.MicUnavailable("This device can't record 16 kHz mono audio.")
        }
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_BYTES * 4),
            )
        } catch (e: SecurityException) {
            throw NAtlasException.MicPermissionDenied()
        } catch (e: IllegalArgumentException) {
            throw NAtlasException.MicUnavailable(cause = e)
        }
        record = recorder
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            release()
            throw NAtlasException.MicUnavailable()
        }
        try {
            recorder.startRecording()
        } catch (e: IllegalStateException) {
            release()
            throw NAtlasException.MicUnavailable(cause = e)
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            release()
            throw NAtlasException.MicUnavailable()
        }
    }

    override fun read(buffer: ByteArray): Int {
        val recorder = record ?: return -1
        var filled = 0
        while (filled < buffer.size) {
            val n = recorder.read(buffer, filled, buffer.size - filled)
            if (n < 0) {
                throw NAtlasException.MicUnavailable("The microphone stopped working (AudioRecord error $n).")
            }
            if (n == 0) break
            filled += n
        }
        return filled
    }

    override fun release() {
        if (released) return
        released = true
        val recorder = record ?: return
        record = null
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (expected: IllegalStateException) {
            // Already stopped.
        } finally {
            recorder.release()
        }
    }
}
