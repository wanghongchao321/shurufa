package com.kingzcheung.xime.ai

import android.content.Context

class ImeModeStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "ai_ime_state",
        Context.MODE_PRIVATE
    )

    var current: InputMode
        get() = runCatching {
            InputMode.valueOf(
                preferences.getString(KEY_MODE, InputMode.CN.name)!!
            )
        }.getOrDefault(InputMode.CN)
        private set(value) {
            preferences.edit().putString(KEY_MODE, value.name).apply()
        }

    fun moveToNext(): InputMode {
        current = current.next()
        return current
    }

    fun select(mode: InputMode) {
        current = mode
    }

    var chineseLayout: ChineseKeyboardLayout
        get() = runCatching {
            ChineseKeyboardLayout.valueOf(
                preferences.getString(KEY_CHINESE_LAYOUT, ChineseKeyboardLayout.T9.name)!!
            )
        }.getOrDefault(ChineseKeyboardLayout.T9)
        private set(value) {
            preferences.edit().putString(KEY_CHINESE_LAYOUT, value.name).apply()
        }

    fun toggleChineseLayout(): ChineseKeyboardLayout {
        chineseLayout = when (chineseLayout) {
            ChineseKeyboardLayout.T9 -> ChineseKeyboardLayout.FULL
            ChineseKeyboardLayout.FULL -> ChineseKeyboardLayout.T9
        }
        return chineseLayout
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_CHINESE_LAYOUT = "chinese_keyboard_layout"
    }
}
