package io.github.baffaabba.natlas

/**
 * A language N-ATLAS can transcribe. Each one is served by its own NCAIR speech model.
 *
 * | Value | [code] | [asrModel] |
 * | --- | --- | --- |
 * | [HAUSA] | `ha` | `NCAIR1/Hausa-ASR` |
 * | [IGBO] | `ig` | `NCAIR1/Igbo-ASR` |
 * | [YORUBA] | `yo` | `NCAIR1/Yoruba-ASR` |
 * | [ENGLISH] | `en` | `NCAIR1/NigerianAccentedEnglish` |
 *
 * @property code The code sent to the server as `language`.
 * @property asrModel The Hugging Face id of the speech model that serves this language.
 */
public enum class Language(public val code: String, public val asrModel: String) {
    /** Hausa (`ha`), served by `NCAIR1/Hausa-ASR`. */
    HAUSA("ha", "NCAIR1/Hausa-ASR"),

    /** Igbo (`ig`), served by `NCAIR1/Igbo-ASR`. */
    IGBO("ig", "NCAIR1/Igbo-ASR"),

    /** Yoruba (`yo`), served by `NCAIR1/Yoruba-ASR`. */
    YORUBA("yo", "NCAIR1/Yoruba-ASR"),

    /** Nigerian-accented English (`en`), served by `NCAIR1/NigerianAccentedEnglish`. */
    ENGLISH("en", "NCAIR1/NigerianAccentedEnglish");

    /** Lookup helpers. */
    public companion object {
        /** Returns the language with this [code] (`ha`, `ig`, `yo`, `en`), or `null`. */
        public fun fromCode(code: String): Language? = entries.firstOrNull { it.code == code }
    }
}
