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

    private companion object {
        const val KEY_MODE = "mode"
    }
}
