package rkr.simplekeyboard.inputmethod.nexus.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rkr.simplekeyboard.inputmethod.nexus.NexusImeBridge
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

class VoiceActivity : ComponentActivity() {

    private val activityScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate
        )

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisperEngine: WhisperEngine

    private var status by mutableStateOf("Loading Whisper model")
    private var isRecording by mutableStateOf(false)
    private var lastTranscription by mutableStateOf("")
    private var selectedLanguage by mutableStateOf(VoiceLanguage.AUTO)

    private var currentAudioFile: File? = null

    companion object {
        private const val MAX_RECORDING_MS = 30000L
        private const val SILENCE_THRESHOLD = 500
        private const val MIN_LOUD_SAMPLE_RATIO = 0.01
    }

    // Previous Nexus dark UI colors
    private val nexusBackground = Color(0xFF08090D)
    private val nexusSurface = Color(0xFF111318)
    private val nexusSurfaceRaised = Color(0xFF181A21)
    private val nexusAccent = Color(0xFF8B7CFF)
    private val nexusAccentBright = Color(0xFFA99DFF)
    private val nexusText = Color(0xFFF3F1F8)
    private val nexusSecondary = Color(0xFFAAA7B5)
    private val nexusSuccess = Color(0xFF7DDC9A)
    private val nexusWarning = Color(0xFFFFC46B)

    private val nexusDarkColors =
        darkColorScheme(
            primary = nexusAccent,
            onPrimary = Color.White,
            secondary = nexusAccentBright,
            background = nexusBackground,
            onBackground = nexusText,
            surface = nexusSurface,
            onSurface = nexusText,
            surfaceVariant = nexusSurfaceRaised,
            onSurfaceVariant = nexusSecondary
        )

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startRecording()
            } else {
                status = "Microphone permission denied"

                Toast.makeText(
                    this,
                    "Microphone permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        audioRecorder = AudioRecorder()
        whisperEngine = WhisperEngine(this)

        setContent {
            MaterialTheme(
                colorScheme = nexusDarkColors
            ) {
                VoiceScreen()
            }
        }

        // Preload Whisper in background.
        activityScope.launch(Dispatchers.IO) {

            try {

                whisperEngine.load()

                withContext(Dispatchers.Main) {
                    status = "Ready"
                }

            } catch (error: Exception) {

                withContext(Dispatchers.Main) {

                    status =
                        "Whisper model failed to load"

                    Toast.makeText(
                        this@VoiceActivity,
                        error.message
                            ?: "Unable to load Whisper model",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @Composable
    private fun VoiceScreen() {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = nexusBackground
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            horizontal = 20.dp,
                            vertical = 28.dp
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(18.dp)
            ) {

                // ---------------------------------------------------------
                // HEADER
                // ---------------------------------------------------------

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {

                    Text(
                        text = "NEXUS VOICE",
                        style =
                            MaterialTheme.typography.labelLarge,
                        color = nexusAccentBright,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Voice input",
                        style =
                            MaterialTheme.typography.headlineLarge,
                        color = nexusText,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            "Offline Whisper transcription",
                        style =
                            MaterialTheme.typography.bodyMedium,
                        color = nexusSecondary
                    )
                }

                // ---------------------------------------------------------
                // LANGUAGE
                // ---------------------------------------------------------

                Text(
                    text = "Language",
                    style =
                        MaterialTheme.typography.titleMedium,
                    color = nexusText,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    LanguageButton(
                        text = "Auto",
                        selected =
                            selectedLanguage ==
                                    VoiceLanguage.AUTO,
                        onClick = {
                            selectedLanguage =
                                VoiceLanguage.AUTO
                        }
                    )

                    LanguageButton(
                        text = "English",
                        selected =
                            selectedLanguage ==
                                    VoiceLanguage.ENGLISH,
                        onClick = {
                            selectedLanguage =
                                VoiceLanguage.ENGLISH
                        }
                    )

                    LanguageButton(
                        text = "Hindi",
                        selected =
                            selectedLanguage ==
                                    VoiceLanguage.HINDI,
                        onClick = {
                            selectedLanguage =
                                VoiceLanguage.HINDI
                        }
                    )
                }

                Text(
                    text =
                        "Selected: ${selectedLanguage.displayName}",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color = nexusSecondary
                )

                // ---------------------------------------------------------
                // STATUS
                // ---------------------------------------------------------

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(18.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                nexusSurface
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            text = "STATUS",
                            style =
                                MaterialTheme.typography.labelMedium,
                            color = nexusSecondary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = status,
                            style =
                                MaterialTheme.typography.titleMedium,
                            color =
                                when {

                                    status == "Complete" ->
                                        nexusSuccess

                                    status == "Ready" ||
                                            status == "Loading Whisper model" ||
                                            status == "Recording" ||
                                            status == "Processing" ||
                                            status == "Transcribing" ->
                                        nexusAccentBright

                                    status.contains(
                                        "failed",
                                        ignoreCase = true
                                    ) ||
                                            status.contains(
                                                "denied",
                                                ignoreCase = true
                                            ) ->
                                        nexusWarning

                                    else ->
                                        nexusText
                                },
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }
                }

                // ---------------------------------------------------------
                // RECORD BUTTON
                // ---------------------------------------------------------

                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                    enabled =
                        status != "Processing" &&
                                status != "Transcribing" &&
                                status != "Loading Whisper model" &&
                                status != "Whisper model failed to load",
                    shape =
                        RoundedCornerShape(18.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                if (isRecording)
                                    Color(0xFF9B4545)
                                else
                                    nexusAccent,
                            contentColor =
                                Color.White
                        ),
                    onClick = {

                        if (isRecording) {
                            stopRecording()
                        } else {
                            requestMicrophoneAndRecord()
                        }
                    }
                ) {

                    Text(
                        text =
                            if (isRecording) {
                                "⏹  Stop Recording"
                            } else {
                                "🎙  Start Recording"
                            },
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

                // ---------------------------------------------------------
                // TRANSCRIPTION
                // ---------------------------------------------------------

                if (lastTranscription.isNotBlank()) {

                    Text(
                        text = "TRANSCRIPTION",
                        style =
                            MaterialTheme.typography.labelMedium,
                        color = nexusSecondary,
                        fontWeight = FontWeight.Bold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(18.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    nexusSurfaceRaised
                            )
                    ) {

                        Text(
                            text = lastTranscription,
                            modifier =
                                Modifier.padding(18.dp),
                            style =
                                MaterialTheme.typography.bodyLarge,
                            color = nexusText
                        )
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        Button(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(52.dp),
                            enabled =
                                status == "Complete",
                            shape =
                                RoundedCornerShape(16.dp),
                            onClick = {
                                insertText()
                            }
                        ) {

                            Text(
                                text = "Insert",
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(52.dp),
                            shape =
                                RoundedCornerShape(16.dp),
                            onClick = {
                                finish()
                            }
                        ) {

                            Text("Close")
                        }
                    }

                } else {

                    OutlinedButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        shape =
                            RoundedCornerShape(16.dp),
                        onClick = {
                            finish()
                        }
                    ) {

                        Text("Close")
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Maximum recording time: 30 seconds",
                    modifier =
                        Modifier.fillMaxWidth(),
                    style =
                        MaterialTheme.typography.labelSmall,
                    color = nexusSecondary
                )
            }
        }
    }

    @Composable
    private fun RowScope.LanguageButton(
        text: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {

        if (selected) {

            Button(
                modifier =
                    Modifier.weight(1f),
                shape =
                    RoundedCornerShape(14.dp),
                onClick = onClick
            ) {

                Text(
                    text = text,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }

        } else {

            OutlinedButton(
                modifier =
                    Modifier.weight(1f),
                shape =
                    RoundedCornerShape(14.dp),
                onClick = onClick
            ) {

                Text(text)
            }
        }
    }

    private fun requestMicrophoneAndRecord() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )

        } else {

            startRecording()
        }
    }

    private fun startRecording() {

        if (
            isRecording ||
            status == "Loading Whisper model" ||
            status == "Whisper model failed to load" ||
            status == "Processing" ||
            status == "Transcribing"
        ) {
            return
        }

        lastTranscription = ""
        status = "Recording"
        isRecording = true

        val outputFile =
            File(
                cacheDir,
                "nexus_voice_${System.currentTimeMillis()}.wav"
            )

        currentAudioFile = outputFile

        Thread {

            try {

                audioRecorder.recordToWav(
                    outputFile = outputFile,
                    maxDurationMs =
                        MAX_RECORDING_MS
                )

                activityScope.launch {

                    isRecording = false

                    if (!outputFile.exists()) {

                        status = "Recording failed"
                        cleanupAudioFile()

                        return@launch
                    }

                    if (outputFile.length() <= 44) {

                        status = "No audio detected"
                        cleanupAudioFile()

                        return@launch
                    }

                    processAudioFile(outputFile)
                }

            } catch (error: Exception) {

                activityScope.launch {

                    isRecording = false
                    status = "Recording failed"

                    Toast.makeText(
                        this@VoiceActivity,
                        error.message
                            ?: "Unable to record audio",
                        Toast.LENGTH_LONG
                    ).show()

                    cleanupAudioFile()
                }
            }
        }.start()
    }

    private fun stopRecording() {

        if (!isRecording) {
            return
        }

        status = "Processing"

        // AudioRecorder finishes and finalizes the WAV.
        audioRecorder.stop()
    }

    private suspend fun processAudioFile(
        audioFile: File
    ) {

        try {

            status = "Processing"

            val hasSpeech =
                withContext(Dispatchers.IO) {
                    containsSpeech(audioFile)
                }

            if (!hasSpeech) {

                status = "No speech detected"

                Toast.makeText(
                    this,
                    "No speech detected",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            status = "Transcribing"

            val text =
                withContext(Dispatchers.IO) {

                    whisperEngine.transcribe(
                        audioFile = audioFile,
                        language =
                            selectedLanguage.whisperCode
                    )
                }

            val cleanText =
                text.trim()

            if (cleanText.isBlank()) {

                status = "No speech detected"
                return
            }

            // IMPORTANT:
            // Do not insert automatically.
            // The user must press Insert.
            lastTranscription = cleanText
            status = "Complete"

        } catch (error: Exception) {

            status = "Transcription failed"

            Toast.makeText(
                this,
                error.message
                    ?: "Whisper transcription failed",
                Toast.LENGTH_LONG
            ).show()

        } finally {

            cleanupAudioFile()
        }
    }

    private fun containsSpeech(
        audioFile: File
    ): Boolean {

        return try {

            if (audioFile.length() <= 44) {
                return false
            }

            RandomAccessFile(
                audioFile,
                "r"
            ).use { file ->

                file.seek(44)

                val buffer =
                    ByteArray(4096)

                var totalSamples = 0L
                var loudSamples = 0L
                var sumAmplitude = 0L

                while (true) {

                    val bytesRead =
                        file.read(buffer)

                    if (bytesRead <= 1) {
                        break
                    }

                    var index = 0

                    while (index + 1 < bytesRead) {

                        val low =
                            buffer[index]
                                .toInt() and 0xff

                        val high =
                            buffer[index + 1]
                                .toInt()

                        val sample =
                            (high shl 8) or low

                        val signedSample =
                            if (sample > 32767) {
                                sample - 65536
                            } else {
                                sample
                            }

                        val amplitude =
                            abs(signedSample)

                        sumAmplitude += amplitude
                        totalSamples++

                        if (
                            amplitude >=
                            SILENCE_THRESHOLD
                        ) {
                            loudSamples++
                        }

                        index += 2
                    }
                }

                if (totalSamples == 0L) {

                    false

                } else {

                    val averageAmplitude =
                        sumAmplitude.toDouble() /
                                totalSamples.toDouble()

                    val loudRatio =
                        loudSamples.toDouble() /
                                totalSamples.toDouble()

                    averageAmplitude >=
                            SILENCE_THRESHOLD ||
                            loudRatio >=
                            MIN_LOUD_SAMPLE_RATIO
                }
            }

        } catch (_: Exception) {

            // If silence analysis fails,
            // allow Whisper to make the decision.
            true
        }
    }

    private fun insertText() {

        val text =
            lastTranscription.trim()

        if (text.isBlank()) {

            Toast.makeText(
                this,
                "No transcription to insert",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        /*
         * Store the transcription for LatinIME.
         *
         * VoiceActivity is a normal Activity, so the IME's
         * current InputConnection may not be available while
         * this screen is open.
         *
         * LatinIME will commit the pending text after this
         * Activity closes and the keyboard becomes active again.
         */
        NexusImeBridge.setPendingText(text)

        Toast.makeText(
            this,
            "Text ready — returning to keyboard",
            Toast.LENGTH_SHORT
        ).show()

        finish()
    }

    private fun cleanupAudioFile() {

        try {

            currentAudioFile?.let { file ->

                if (file.exists()) {
                    file.delete()
                }
            }

        } catch (_: Exception) {
        }

        currentAudioFile = null
    }

    override fun onDestroy() {

        audioRecorder.stop()
        cleanupAudioFile()
        whisperEngine.release()
        activityScope.cancel()

        super.onDestroy()
    }
}