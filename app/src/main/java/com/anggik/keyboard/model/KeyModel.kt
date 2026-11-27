package com.anggik.keyboard.model

enum class KeyType {
    CHAR,
    SHIFT,
    BACKSPACE,
    MODE_SYMBOLS,
    MODE_SYMBOLS_ALT,
    MODE_EMOJI,
    COMMA,
    SPACE,
    PERIOD,
    ENTER
}

enum class KeyboardLayer {
    QWERTY,
    SYMBOLS,
    SYMBOLS_ALT,
    EMOJI
}

enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS_LOCK
}

data class KeySpec(
    val label: String,
    val hint: String = "",
    val type: KeyType = KeyType.CHAR,
    val weight: Float = 1.0f,
    val longPressText: String? = null
)

object KeyboardLayouts {

    val qwertyRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("q", "1", longPressText = "1"),
            KeySpec("w", "2", longPressText = "2"),
            KeySpec("e", "3", longPressText = "3"),
            KeySpec("r", "4", longPressText = "4"),
            KeySpec("t", "5", longPressText = "5"),
            KeySpec("y", "6", longPressText = "6"),
            KeySpec("u", "7", longPressText = "7"),
            KeySpec("i", "8", longPressText = "8"),
            KeySpec("o", "9", longPressText = "9"),
            KeySpec("p", "0", longPressText = "0")
        ),
        listOf(
            KeySpec("a", longPressText = "@"),
            KeySpec("s", longPressText = "#"),
            KeySpec("d", longPressText = "$"),
            KeySpec("f", longPressText = "_"),
            KeySpec("g", longPressText = "&"),
            KeySpec("h", longPressText = "-"),
            KeySpec("j", longPressText = "+"),
            KeySpec("k", longPressText = "("),
            KeySpec("l", longPressText = ")")
        ),
        listOf(
            KeySpec("", type = KeyType.SHIFT, weight = 1.38f),
            KeySpec("z", longPressText = "*"),
            KeySpec("x", longPressText = "\""),
            KeySpec("c", longPressText = "'"),
            KeySpec("v", longPressText = ":"),
            KeySpec("b", longPressText = ";"),
            KeySpec("n", longPressText = "!"),
            KeySpec("m", longPressText = "?"),
            KeySpec("", type = KeyType.BACKSPACE, weight = 1.38f)
        ),
        listOf(
            KeySpec("?123", type = KeyType.MODE_SYMBOLS, weight = 1.38f),
            KeySpec("", type = KeyType.MODE_EMOJI, weight = 1.0f),
            KeySpec(",", type = KeyType.COMMA, weight = 1.0f),
            KeySpec("English", type = KeyType.SPACE, weight = 3.38f),
            KeySpec(".", hint = "!?", type = KeyType.PERIOD, weight = 1.0f, longPressText = "?"),
            KeySpec("", type = KeyType.ENTER, weight = 1.38f)
        )
    )

    val symbolsRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("1"), KeySpec("2"), KeySpec("3"), KeySpec("4"), KeySpec("5"),
            KeySpec("6"), KeySpec("7"), KeySpec("8"), KeySpec("9"), KeySpec("0")
        ),
        listOf(
            KeySpec("@"), KeySpec("#"), KeySpec("$"), KeySpec("_"), KeySpec("&"),
            KeySpec("-"), KeySpec("+"), KeySpec("("), KeySpec(")"), KeySpec("/")
        ),
        listOf(
            KeySpec("=\\<", type = KeyType.MODE_SYMBOLS_ALT, weight = 1.38f),
            KeySpec("*"), KeySpec("\""), KeySpec("'"), KeySpec(":"),
            KeySpec(";"), KeySpec("!"), KeySpec("?"),
            KeySpec("", type = KeyType.BACKSPACE, weight = 1.38f)
        ),
        listOf(
            KeySpec("ABC", type = KeyType.MODE_SYMBOLS, weight = 1.38f),
            KeySpec("", type = KeyType.MODE_EMOJI, weight = 1.0f),
            KeySpec(",", type = KeyType.COMMA, weight = 1.0f),
            KeySpec("English", type = KeyType.SPACE, weight = 3.38f),
            KeySpec(".", type = KeyType.PERIOD, weight = 1.0f),
            KeySpec("", type = KeyType.ENTER, weight = 1.38f)
        )
    )

    val symbolsAltRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("~"), KeySpec("`"), KeySpec("|"), KeySpec("•"), KeySpec("√"),
            KeySpec("π"), KeySpec("÷"), KeySpec("×"), KeySpec("§"), KeySpec("Δ")
        ),
        listOf(
            KeySpec("£"), KeySpec("€"), KeySpec("¥"), KeySpec("^"), KeySpec("°"),
            KeySpec("="), KeySpec("{"), KeySpec("}"), KeySpec("\\"), KeySpec("%")
        ),
        listOf(
            KeySpec("?123", type = KeyType.MODE_SYMBOLS, weight = 1.38f),
            KeySpec("©"), KeySpec("®"), KeySpec("™"), KeySpec("✓"),
            KeySpec("["), KeySpec("]"), KeySpec("<"), KeySpec(">"),
            KeySpec("", type = KeyType.BACKSPACE, weight = 1.38f)
        ),
        listOf(
            KeySpec("ABC", type = KeyType.MODE_SYMBOLS, weight = 1.38f),
            KeySpec("", type = KeyType.MODE_EMOJI, weight = 1.0f),
            KeySpec(",", type = KeyType.COMMA, weight = 1.0f),
            KeySpec("English", type = KeyType.SPACE, weight = 3.38f),
            KeySpec(".", type = KeyType.PERIOD, weight = 1.0f),
            KeySpec("", type = KeyType.ENTER, weight = 1.38f)
        )
    )

    val emojiRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("😀"), KeySpec("😂"), KeySpec("🥰"), KeySpec("😎"), KeySpec("🤔"),
            KeySpec("😭"), KeySpec("😡"), KeySpec("👍"), KeySpec("🙏"), KeySpec("🔥")
        ),
        listOf(
            KeySpec("❤️"), KeySpec("✨"), KeySpec("🎉"), KeySpec("💯"), KeySpec("✅"),
            KeySpec("⚡"), KeySpec("🚀"), KeySpec("💻"), KeySpec("📱"), KeySpec("🛠️")
        ),
        listOf(
            KeySpec("ABC", type = KeyType.MODE_SYMBOLS, weight = 1.38f),
            KeySpec("🤝"), KeySpec("👏"), KeySpec("🙌"), KeySpec("💪"),
            KeySpec("👀"), KeySpec("💡"), KeySpec("📌"),
            KeySpec("", type = KeyType.BACKSPACE, weight = 1.38f)
        ),
        listOf(
            KeySpec("?123", type = KeyType.MODE_SYMBOLS, weight = 1.38f),
            KeySpec("ABC", type = KeyType.MODE_EMOJI, weight = 1.0f),
            KeySpec(",", type = KeyType.COMMA, weight = 1.0f),
            KeySpec("English", type = KeyType.SPACE, weight = 3.38f),
            KeySpec(".", type = KeyType.PERIOD, weight = 1.0f),
            KeySpec("", type = KeyType.ENTER, weight = 1.38f)
        )
    )
}
