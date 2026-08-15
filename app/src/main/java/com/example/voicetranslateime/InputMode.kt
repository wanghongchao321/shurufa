package com.example.voicetranslateime

enum class InputMode(
    val wireValue: String,
    val displayName: String
) {
    CN("CN", "中文"),
    EN("EN", "英文"),
    FR("FR", "法语");

    fun next(): InputMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }
}
