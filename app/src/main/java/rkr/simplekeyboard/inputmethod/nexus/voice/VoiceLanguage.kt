package rkr.simplekeyboard.inputmethod.nexus.voice

enum class VoiceLanguage(
    val displayName: String,
    val whisperCode: String?
) {

    AUTO(
        displayName = "Auto Detect",
        whisperCode = null
    ),

    ENGLISH(
        displayName = "English",
        whisperCode = "en"
    ),

    HINDI(
        displayName = "Hindi",
        whisperCode = "hi"
    )
}
