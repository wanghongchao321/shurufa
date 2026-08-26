package com.kingzcheung.xime.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModeTest {
    @Test
    fun `English and French use QWERTY while Chinese-source modes use T9`() {
        assertFalse(InputMode.CN.usesLatinKeyboard)
        assertTrue(InputMode.EN.usesLatinKeyboard)
        assertTrue(InputMode.FR.usesLatinKeyboard)
        assertFalse(InputMode.ZH_EN.usesLatinKeyboard)
        assertFalse(InputMode.ZH_FR.usesLatinKeyboard)
    }
}
