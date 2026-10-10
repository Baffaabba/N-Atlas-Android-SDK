package io.github.baffaabba.natlas

import io.github.baffaabba.natlas.internal.AudioSourceFactory
import io.github.baffaabba.natlas.internal.HttpCore
import io.github.baffaabba.natlas.internal.WireTranscript
import io.github.baffaabba.natlas.internal.decodeOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** Speech-to-text with the NCAIR speech models. Get it from [NAtlasClient.asr]. */
public class AsrApi internal constructor(
    private val core: HttpCore,
    private val audio: AudioSourceFactory,
) {
    /**
     * Transcribes an audio file.
     *
     * Formats: wav, mp3, m4a, ogg, flac, webm, up to 25 MB. Audio longer than 30 seconds is
     * split on silence by the server, and [Transcript.segments] carry times from the start
     * of the file. Retried after a 429, a 503 or a network failure (see [NAtlasConfig.maxRetries]).
     *
     * @param file The audio file. It is uploaded as is and never changed or deleted.
     * @param language The language spoken: `ha`, `ig`, `yo` or `en`.
     * @throws NAtlasException On any failure, for example [NAtlasException.UnsupportedAudio].
     */
    public suspend fun transcribe(file: File, language: Language): Transcript {
        val size = withContext(Dispatchers.IO) {
            if (!file.isFile || !file.canRead()) {
                throw NAtlasException.InvalidRequest("Can't read the audio file at ${file.path}.")
            }
            file.length()
        }
        if (size > MAX_UPLOAD_BYTES) throw NAtlasException.AudioTooLarge()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaTypeFor(file)))
            .addFormDataPart("language", language.code)
            .addFormDataPart("response_format", "verbose_json")
            .build()
        val request = Request.Builder().url(core.url("v1/audio/transcriptions")).post(body).build()

        val raw = core.retrying { core.execute(request) }
        val wire = decodeOrThrow<WireTranscript>(raw.body, raw.code)
        return Transcript(
            text = wire.text.trim(),
            language = wire.language?.let { Language.fromCode(it) } ?: language,
            durationSeconds = wire.duration,
            model = wire.model ?: language.asrModel,
            segments = wire.segments.orEmpty().map { Segment(it.start, it.end, it.text.trim()) },
        )
    }

    /**
     * Prepares a live transcription of the microphone. Nothing starts until you collect
     * [LiveTranscription.events]. The app must hold `RECORD_AUDIO`
     * (see [NAtlasClient.hasMicPermission]).
     *
     * How it works: the SDK records 16 kHz mono audio and sends it in 100 ms frames over a
     * WebSocket. The server finds pauses and re-transcribes the current utterance about once
     * a second. This is near-real-time chunked transcription, not true streaming.
     *
     * @param language The language spoken: `ha`, `ig`, `yo` or `en`.
     */
    public fun live(language: Language): LiveTranscription = LiveTranscription(core, language, audio)

    internal companion object {
        const val MAX_UPLOAD_BYTES: Long = 25L * 1024 * 1024

        fun mediaTypeFor(file: File): MediaType = when (file.extension.lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "ogg", "oga", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            "webm" -> "audio/webm"
            else -> "application/octet-stream"
        }.toMediaType()
    }
}
