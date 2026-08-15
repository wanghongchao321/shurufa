package com.example.voicetranslateime

enum class InputMode(
    val wireValue: String,
    val displayName: String
) {
    CN("CN", "中文"),
    EN("EN", "英文"),
    FR("FR", "法语"),
    TRANSLATE("TRANSLATE", "中→法");

    fun next(): InputMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }
}
