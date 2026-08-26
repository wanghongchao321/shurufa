package com.kingzcheung.xime.ai

enum class InputMode(
    val wireValue: String,
    val displayName: String
) {
    CN("CN", "中文"),
    EN("EN", "英文"),
    FR("FR", "法语"),
    ZH_EN("ZH_EN", "中英"),
    ZH_FR("ZH_FR", "中法");

    /** 英文和法语文字输入使用 26 键，其余模式使用中文拼音九宫格。 */
    val usesLatinKeyboard: Boolean
        get() = this == EN || this == FR

    fun next(): InputMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }
}
