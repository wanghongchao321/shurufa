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

    /** 中文语音来源模式允许用地球键切换拼音九键/全键。 */
    val usesChineseKeyboard: Boolean
        get() = this == CN || this == ZH_EN || this == ZH_FR

    fun next(): InputMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }
}

enum class ChineseKeyboardLayout {
    T9,
    FULL,
}
