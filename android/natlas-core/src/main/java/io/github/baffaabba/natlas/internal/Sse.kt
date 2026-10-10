package io.github.baffaabba.natlas.internal

/**
 * A minimal Server-Sent Events parser: feed it lines, get back each event's `data`.
 * Only `data:` fields matter for the chat stream; comments and other fields are ignored.
 */
internal class SseParser {
    private val data = StringBuilder()
    private var hasData = false

    /** Feeds one line (without its line ending). Returns an event's data when [line] ends it. */
    fun feed(line: String): String? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null // Comment / keep-alive.
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        if (field == "data") {
            if (hasData) data.append('\n')
            data.append(value)
            hasData = true
        }
        return null
    }

    /** Call at end of input: returns a last event that had no blank line after it. */
    fun finish(): String? = dispatch()

    private fun dispatch(): String? {
        if (!hasData) return null
        val event = data.toString()
        data.setLength(0)
        hasData = false
        return event
    }
}
