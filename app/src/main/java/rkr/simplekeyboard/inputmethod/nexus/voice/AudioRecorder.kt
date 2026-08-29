package rkr.simplekeyboard.inputmethod.nexus.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000

        private const val CHANNEL_CONFIG =
            AudioFormat.CHANNEL_IN_MONO

        private const val AUDIO_FORMAT =
            AudioFormat.ENCODING_PCM_16BIT

        private const val DEFAULT_MAX_DURATION_MS = 30000L
    }

    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun recordToWav(
        outputFile: File,
        maxDurationMs: Long = DEFAULT_MAX_DURATION_MS
    ) {

        val minBufferSize =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

        if (
            minBufferSize == AudioRecord.ERROR ||
            minBufferSize == AudioRecord.ERROR_BAD_VALUE
        ) {
            throw IllegalStateException(
                "Unable to initialize microphone"
            )
        }

        val bufferSize =
            maxOf(
                minBufferSize,
                SAMPLE_RATE / 2
            )

        val recorder =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

        audioRecord = recorder
        isRecording = true

        val buffer =
            ShortArray(bufferSize / 2)

        var totalAudioBytes = 0

        try {

            FileOutputStream(outputFile).use { output ->

                writeWavHeader(output, 0)

                recorder.startRecording()

                val startTime =
                    System.currentTimeMillis()

                while (
                    isRecording &&
                    System.currentTimeMillis() - startTime <
                    maxDurationMs
                ) {

                    val count =
                        recorder.read(
                            buffer,
                            0,
                            buffer.size
                        )

                    if (count > 0) {

                        val byteBuffer =
                            ByteBuffer
                                .allocate(count * 2)
                                .order(ByteOrder.LITTLE_ENDIAN)

                        for (i in 0 until count) {
                            byteBuffer.putShort(buffer[i])
                        }

                        output.write(byteBuffer.array())
                        totalAudioBytes += count * 2
                    }
                }

                output.flush()
            }

        } finally {

            stop()

            if (outputFile.exists()) {
                updateWavHeader(
                    outputFile,
                    totalAudioBytes
                )
            }
        }
    }

    fun stop() {

        isRecording = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null
    }

    fun isRecording(): Boolean {
        return isRecording
    }

    private fun writeWavHeader(
        output: FileOutputStream,
        audioLength: Int
    ) {

        val header =
            ByteBuffer
                .allocate(44)
                .order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray())
        header.putInt(36 + audioLength)
        header.put("WAVE".toByteArray())

        header.put("fmt ".toByteArray())
        header.putInt(16)

        header.putShort(1)
        header.putShort(1)

        header.putInt(SAMPLE_RATE)

        val byteRate =
            SAMPLE_RATE * 2

        header.putInt(byteRate)
        header.putShort(2)
        header.putShort(16)

        header.put("data".toByteArray())
        header.putInt(audioLength)

        output.write(header.array())
    }

    private fun updateWavHeader(
        file: File,
        audioLength: Int
    ) {

        RandomAccessFile(file, "rw").use {

            it.seek(4)
            it.writeIntLE(36 + audioLength)

            it.seek(40)
            it.writeIntLE(audioLength)
        }
    }

    private fun RandomAccessFile.writeIntLE(
        value: Int
    ) {

        write(
            byteArrayOf(
                (value and 0xff).toByte(),
                ((value shr 8) and 0xff).toByte(),
                ((value shr 16) and 0xff).toByte(),
                ((value shr 24) and 0xff).toByte()
            )
        )
    }
}
