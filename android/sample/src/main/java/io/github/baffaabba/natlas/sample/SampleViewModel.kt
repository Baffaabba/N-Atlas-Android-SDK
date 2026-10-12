package io.github.baffaabba.natlas.sample

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.baffaabba.natlas.ChatMessage
import io.github.baffaabba.natlas.Language
import io.github.baffaabba.natlas.LiveEvent
import io.github.baffaabba.natlas.LiveTranscription
import io.github.baffaabba.natlas.NAtlasClient
import io.github.baffaabba.natlas.NAtlasConfig
import io.github.baffaabba.natlas.NAtlasException
import io.github.baffaabba.natlas.VoiceEvent
import io.github.baffaabba.natlas.VoiceTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** The state of a one-shot task such as transcribing a file. */
sealed interface TaskState {
    data object Idle : TaskState
    data class Working(val label: String) : TaskState
    data class Done(val text: String) : TaskState
    data class Failed(val message: String) : TaskState
}

enum class VoicePhase { IDLE, LISTENING, THINKING, REPLYING }

/** Holds the client and all screen state. Every SDK call runs in viewModelScope. */
class SampleViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("sample", Context.MODE_PRIVATE)

    // ---- Settings ----
    var baseUrl by mutableStateOf(prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.NATLAS_BASE_URL)
        private set
    // Saved on the device only (SharedPreferences), so a key typed in Settings survives a restart.
    // Release builds ship with an empty BuildConfig key; the user types their own.
    var apiKey by mutableStateOf(prefs.getString(KEY_API_KEY, null) ?: BuildConfig.NATLAS_API_KEY)
        private set
    var language by mutableStateOf(Language.HAUSA)

    private var cachedClient: Pair<Pair<String, String>, NAtlasClient>? = null

    fun updateBaseUrl(value: String) {
        baseUrl = value.trim()
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
    }

    fun updateApiKey(value: String) {
        apiKey = value.trim()
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /** One client per server address and key; rebuilt when either changes. */
    private fun client(): NAtlasClient {
        val key = baseUrl to apiKey
        cachedClient?.let { (k, c) -> if (k == key) return c }
        return NAtlasClient(NAtlasConfig(baseUrl = baseUrl, apiKey = apiKey)).also { cachedClient = key to it }
    }

    // ---- Transcribe a file ----
    var fileState by mutableStateOf<TaskState>(TaskState.Idle)
        private set

    fun transcribeFile(uri: Uri) {
        val lang = language
        fileState = TaskState.Working("Writing down what was said…")
        viewModelScope.launch {
            var temp: File? = null
            fileState = try {
                temp = copyToTemp(uri)
                val t = client().asr.transcribe(temp, lang)
                TaskState.Done(t.text.ifBlank { "We didn't hear any speech in this file." })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TaskState.Failed(friendlyError(e))
            } finally {
                withContext(Dispatchers.IO) { temp?.delete() }
            }
        }
    }

    /** Copies the picked file to the cache so the SDK can upload it. Deleted right after. */
    private suspend fun copyToTemp(uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "audio"
        val ext = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 } ?: "wav"
        val file = File.createTempFile("natlas-upload-", ".$ext", getApplication<Application>().cacheDir)
        val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Can't open the file.")
        input.use { src -> file.outputStream().use { src.copyTo(it) } }
        file
    }

    // ---- Live transcription ----
    var liveRunning by mutableStateOf(false)
        private set
    var liveFinalText by mutableStateOf("")
        private set
    var livePartialText by mutableStateOf("")
        private set
    var liveStatus by mutableStateOf<String?>(null)
        private set
    var liveError by mutableStateOf<String?>(null)
        private set
    private var live: LiveTranscription? = null
    private var liveJob: Job? = null

    fun startLive() {
        if (liveRunning || voicePhase != VoicePhase.IDLE) return
        liveFinalText = ""
        livePartialText = ""
        liveError = null
        liveStatus = "Connecting…"
        liveRunning = true
        val session = try {
            client().asr.live(language)
        } catch (e: IllegalArgumentException) {
            liveError = friendlyError(e)
            liveStatus = null
            liveRunning = false
            return
        }
        live = session
        liveJob = viewModelScope.launch {
            try {
                session.events.collect { e ->
                    when (e) {
                        is LiveEvent.Ready -> liveStatus = "Listening… Speak now."
                        is LiveEvent.Partial -> livePartialText = e.text
                        is LiveEvent.Final -> {
                            liveFinalText = listOf(liveFinalText, e.text).filter { it.isNotBlank() }.joinToString(" ")
                            livePartialText = ""
                        }
                    }
                }
                liveStatus = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                liveError = friendlyError(e)
                liveStatus = null
            } finally {
                liveRunning = false
                live = null
            }
        }
    }

    fun stopLive() {
        liveStatus = "Finishing the last words…"
        live?.stop()
    }

    // ---- Chat ----
    val chatHistory = mutableStateListOf<ChatMessage>()
    var chatInput by mutableStateOf("")
    var chatReply by mutableStateOf("")
        private set
    var chatBusy by mutableStateOf(false)
        private set
    var chatError by mutableStateOf<String?>(null)
        private set

    fun sendChat() {
        val text = chatInput.trim()
        if (text.isEmpty() || chatBusy) return
        chatInput = ""
        chatError = null
        chatReply = ""
        chatBusy = true
        val messages = chatHistory.toList() + ChatMessage.user(text)
        chatHistory += ChatMessage.user(text)
        viewModelScope.launch {
            try {
                client().chat.stream(messages).collect { chatReply += it.text }
                chatHistory += ChatMessage.assistant(chatReply)
                chatReply = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chatError = friendlyError(e)
            } finally {
                chatBusy = false
            }
        }
    }

    fun clearChat() {
        if (chatBusy) return
        chatHistory.clear()
        chatReply = ""
        chatError = null
    }

    // ---- Voice turn ----
    var voicePhase by mutableStateOf(VoicePhase.IDLE)
        private set
    var voiceHeard by mutableStateOf("")
        private set
    var voiceReply by mutableStateOf("")
        private set
    var voiceError by mutableStateOf<String?>(null)
        private set
    var voiceAutoStop by mutableStateOf(true)
    private var turn: VoiceTurn? = null

    fun startVoice() {
        if (voicePhase != VoicePhase.IDLE || liveRunning) return
        voiceHeard = ""
        voiceReply = ""
        voiceError = null
        voicePhase = VoicePhase.LISTENING
        val t = try {
            client().voice.converse(language, history = chatHistory.toList(), autoStop = voiceAutoStop)
        } catch (e: IllegalArgumentException) {
            voiceError = friendlyError(e)
            voicePhase = VoicePhase.IDLE
            return
        }
        turn = t
        viewModelScope.launch {
            try {
                t.events.collect { e ->
                    when (e) {
                        VoiceEvent.Listening -> voicePhase = VoicePhase.LISTENING
                        is VoiceEvent.Heard -> voiceHeard = e.text
                        VoiceEvent.Thinking -> voicePhase = VoicePhase.THINKING
                        is VoiceEvent.Reply -> {
                            voicePhase = VoicePhase.REPLYING
                            voiceReply += e.delta
                        }
                        is VoiceEvent.Done -> {
                            if (e.userText.isBlank()) {
                                voiceError = "We didn't hear anything. Tap Talk to N-ATLAS and speak after \"Listening…\" appears."
                            } else {
                                chatHistory += ChatMessage.user(e.userText)
                                chatHistory += ChatMessage.assistant(e.replyText)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                voiceError = friendlyError(e)
            } finally {
                voicePhase = VoicePhase.IDLE
                turn = null
            }
        }
    }

    fun stopVoice() {
        turn?.stop()
    }

    /** Called when the app goes to the background: stop using the microphone. */
    fun stopMicSessions() {
        if (liveRunning) stopLive()
        if (voicePhase == VoicePhase.LISTENING) stopVoice()
    }

    /** Plain-English messages for app users. No jargon, always a next step. */
    private fun friendlyError(e: Throwable): String = when (e) {
        is NAtlasException.Unauthorized -> "The server didn't accept the API key. Check the key in Settings."
        is NAtlasException.UserCapReached ->
            "This app has reached its free limit of 1,000 people in 30 days. Please try again later."
        is NAtlasException.RateLimited ->
            "The server is busy. " + (e.retryAfterSeconds?.let { "Try again in $it seconds." } ?: "Try again in a moment.")
        is NAtlasException.ModelLoading ->
            "The server is still starting up. " + (e.retryAfterSeconds?.let { "Try again in $it seconds." } ?: "Try again in a minute.")
        is NAtlasException.UnsupportedAudio -> "We can't read this file. Try a wav, mp3, m4a, ogg or flac file."
        is NAtlasException.AudioTooLarge -> "This file is too big. Pick one under 25 MB."
        is NAtlasException.InvalidRequest -> "Something in the request was wrong: ${e.message}"
        is NAtlasException.Timeout -> "The server took too long to answer. Try again."
        is NAtlasException.Network -> "We couldn't reach the server. Check your connection and the server address in Settings."
        is NAtlasException.MicPermissionDenied -> "The app needs the microphone to hear you. Allow it and try again."
        is NAtlasException.MicUnavailable -> "We couldn't use the microphone. Close other apps that may be using it and try again."
        is NAtlasException.ServerError -> e.message ?: "The server had a problem. Try again later."
        is IllegalArgumentException -> "The server address doesn't look right. Check it in Settings."
        else -> "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
    }
}
