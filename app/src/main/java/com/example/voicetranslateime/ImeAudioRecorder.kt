package com.example.voicetranslateime

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class ImeAudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        check(recorder == null) { "Recorder is already running" }

        val file = File.createTempFile("ime-voice-", ".m4a", context.cacheDir)
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioChannels(1)
                // 32 kbps remains compact (~4 KB/s) while preserving more
                // consonant detail for strongly accented English and French.
                setAudioEncodingBitRate(32_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = newRecorder
            outputFile = file
        } catch (error: Throwable) {
            runCatching { newRecorder.release() }
            file.delete()
            throw error
        }
    }

    fun stop(): File? {
        val activeRecorder = recorder ?: return null
        val file = outputFile

        return try {
            activeRecorder.stop()
            file?.takeIf { it.exists() && it.length() > 0L }
        } catch (_: RuntimeException) {
            file?.delete()
            null
        } finally {
            releaseRecorder()
        }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        outputFile?.delete()
        releaseRecorder()
    }

    private fun releaseRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
    }
}
