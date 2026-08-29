package rkr.simplekeyboard.inputmethod.nexus.voice

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import java.io.File

class WhisperEngine(
    private val context: Context
) {

    private var model: dev.ffmpegkit.whisper.WhisperModel? = null

    suspend fun load() {

        if (model != null) {
            return
        }

        val modelFile =
            File(
                context.filesDir,
                "ggml-tiny.bin"
            )

        if (!modelFile.exists()) {

            context.assets
                .open("ggml-tiny.bin")
                .use { input ->

                    modelFile.outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }
        }

        model =
            Whisper.loadModel(
                context,
                modelFile.absolutePath
            )
    }

    suspend fun transcribe(
        audioFile: File,
        language: String? = null
    ): String {

        load()

        val whisperModel =
            model
                ?: throw IllegalStateException(
                    "Whisper model not loaded"
                )

        val config =
            if (language.isNullOrBlank()) {
                WhisperConfig()
            } else {
                WhisperConfig(
                    language = language
                )
            }

        val result =
            Whisper.transcribe(
                whisperModel,
                audioFile.absolutePath,
                config
            )

        return result.text.trim()
    }

    fun release() {

        model?.let {
            Whisper.releaseModel(it)
        }

        model = null
    }
}
