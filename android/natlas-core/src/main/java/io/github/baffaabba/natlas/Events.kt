package io.github.baffaabba.natlas

/** An event from [LiveTranscription.events]. */
public sealed class LiveEvent {
    /**
     * The server is ready and is receiving audio from the microphone.
     *
     * @property model The NCAIR model that transcribes, for example `NCAIR1/Hausa-ASR`.
     */
    public data class Ready(val model: String) : LiveEvent()

    /**
     * What has been heard so far in the current utterance. The next [Partial] or
     * [Final] replaces it.
     *
     * @property text The text so far.
     * @property start Start of the utterance, in seconds from the start of the session.
     * @property end End of the audio heard so far, in seconds.
     */
    public data class Partial(val text: String, val start: Double, val end: Double) : LiveEvent()

    /**
     * An utterance is finished (the speaker paused, or [LiveTranscription.stop] was
     * called). It never changes.
     *
     * @property text The text of the utterance.
     * @property start Start of the utterance, in seconds from the start of the session.
     * @property end End of the utterance, in seconds.
     */
    public data class Final(val text: String, val start: Double, val end: Double) : LiveEvent()
}

/** An event from [VoiceTurn.events], in this order: Listening, Heard…, Thinking, Reply…, Done. */
public sealed class VoiceEvent {
    /** The microphone is on and the server is listening. */
    public data object Listening : VoiceEvent()

    /**
     * What has been heard so far in this turn.
     *
     * @property text Everything heard so far in this turn. Replace the previous text with it.
     * @property isFinal `true` when [text] contains only finished utterances.
     */
    public data class Heard(val text: String, val isFinal: Boolean) : VoiceEvent()

    /** Listening is over and N-ATLaS is writing a reply. */
    public data object Thinking : VoiceEvent()

    /**
     * A piece of the reply.
     *
     * @property delta New text to append to the reply.
     */
    public data class Reply(val delta: String) : VoiceEvent()

    /**
     * The turn is complete. Add both messages to your history for the next turn.
     *
     * If nothing was heard, [userText] and [replyText] are empty and N-ATLaS was not asked.
     *
     * @property userText What the user said.
     * @property replyText The full reply from N-ATLaS.
     */
    public data class Done(val userText: String, val replyText: String) : VoiceEvent()
}
