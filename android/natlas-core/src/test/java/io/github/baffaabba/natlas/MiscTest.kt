package io.github.baffaabba.natlas

import io.github.baffaabba.natlas.internal.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {
    @Test
    fun `config toString never shows the key`() {
        val s = NAtlasConfig("https://x.example", "sk-secret-123").toString()
        assertFalse(s.contains("sk-secret-123"))
        assertTrue(s.contains("<hidden>"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid baseUrl fails fast`() {
        NAtlasClient(NAtlasConfig("not a url", "k"))
    }

    @Test
    fun `languages map to the NCAIR models`() {
        assertEquals("NCAIR1/Hausa-ASR", Language.HAUSA.asrModel)
        assertEquals("NCAIR1/NigerianAccentedEnglish", Language.ENGLISH.asrModel)
        assertEquals(Language.YORUBA, Language.fromCode("yo"))
        assertNull(Language.fromCode("pcm"))
    }

    @Test
    fun `attribution is the licence text`() {
        assertEquals(
            "N-ATLAS is an initiative of the Federal Ministry of Communications, Innovation and " +
                "Digital Economy, and powered by Awarri Technologies.",
            NAtlasClient.ATTRIBUTION,
        )
    }

    @Test
    fun `sse parser joins data lines and ignores comments and other fields`() {
        val p = SseParser()
        assertNull(p.feed(": ping"))
        assertNull(p.feed("event: message"))
        assertNull(p.feed("id: 1"))
        assertNull(p.feed("data: line1"))
        assertNull(p.feed("data:line2"))
        assertEquals("line1\nline2", p.feed(""))
        assertNull(p.feed(""))
        assertNull(p.feed("data: [DONE]"))
        assertEquals("[DONE]", p.finish())
    }
}
