package com.kingzcheung.xime.ai

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterApiTest {
    @Test
    fun `OpenRouter app title is safe for an OkHttp header`() {
        assertTrue(OPENROUTER_APP_TITLE.all { it.code in 0x20..0x7e })

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/audio/transcriptions")
            .header("X-OpenRouter-Title", OPENROUTER_APP_TITLE)
            .build()

        assertEquals(OPENROUTER_APP_TITLE, request.header("X-OpenRouter-Title"))
    }
}
