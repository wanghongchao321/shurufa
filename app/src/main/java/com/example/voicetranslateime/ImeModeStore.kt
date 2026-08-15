package com.example.voicetranslateime

import android.content.Context

class ImeModeStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "ime_state",
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

    private companion object {
        const val KEY_MODE = "mode"
    }
}
