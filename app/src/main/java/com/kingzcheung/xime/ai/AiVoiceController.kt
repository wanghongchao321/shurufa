package com.kingzcheung.xime.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class AiVoiceController(
    context: Context,
    private val scope: CoroutineScope,
    apiKey: String,
    model: String,
    private val onAmplitudeChanged: (Float) -> Unit = {},
) {
    private val recorder = ImeAudioRecorder(context)
    private val api = OpenRouterApi(apiKey = apiKey, model = model)

    private var processingJob: Job? = null
    private var amplitudeJob: Job? = null
    private var generation = 0L
    private var recording = false
    private var activeMode = InputMode.CN
    private var activeInputSessionId = 0L

    val isRecording: Boolean
        get() = recording

    val isProcessing: Boolean
        get() = processingJob?.isActive == true

    suspend fun start(mode: InputMode, inputSessionId: Long): Result<Unit> = try {
        check(!recording) { "录音已经开始" }
        processingJob?.cancel()
        generation++
        withContext(Dispatchers.IO) { recorder.start() }
        activeMode = mode
        activeInputSessionId = inputSessionId
        recording = true
        startAmplitudeMonitoring()
        Result.success(Unit)
    } catch (error: Throwable) {
        amplitudeJob?.cancel()
        amplitudeJob = null
        onAmplitudeChanged(0f)
        withContext(NonCancellable + Dispatchers.IO) { recorder.cancel() }
        Result.failure(error)
    }

    fun stopAndSubmit(
        onStage: (String) -> Unit,
        onSuccess: (text: String, inputSessionId: Long) -> Unit,
        onFailure: (String) -> Unit,
        onFinished: () -> Unit,
    ): Boolean {
        if (!recording) return false
        recording = false
        stopAmplitudeMonitoring()

        val requestGeneration = ++generation
        val mode = activeMode
        val inputSessionId = activeInputSessionId
        processingJob = scope.launch {
            var file: java.io.File? = null
            try {
                val audioFile = withContext(Dispatchers.IO) { recorder.stop() }
                file = audioFile
                if (audioFile == null) {
                    onFailure("录音过短或录音失败，请按住空格至少 1 秒")
                    return@launch
                }
                val timeoutMs = if (mode == InputMode.CN) {
                    CHINESE_TIMEOUT_MS
                } else {
                    OTHER_TIMEOUT_MS
                }
                val text = withTimeout(timeoutMs) {
                    api.process(audioFile, mode, onStage)
                }
                if (requestGeneration == generation) {
                    onSuccess(text, inputSessionId)
                }
            } catch (_: TimeoutCancellationException) {
                if (requestGeneration == generation) {
                    onFailure("处理超时，请检查网络后重试")
                }
            } catch (_: CancellationException) {
                // A cancelled request must never commit stale text.
            } catch (error: Exception) {
                if (requestGeneration == generation) {
                    onFailure(error.message ?: "网络异常")
                }
            } finally {
                file?.delete()
                if (requestGeneration == generation) {
                    processingJob = null
                    onFinished()
                }
            }
        }
        return true
    }

    fun cancel() {
        generation++
        stopAmplitudeMonitoring()
        if (recording) {
            recorder.cancel()
            recording = false
        }
        processingJob?.cancel()
        processingJob = null
    }

    private fun startAmplitudeMonitoring() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (recording) {
                val amplitude = withContext(Dispatchers.IO) { recorder.normalizedAmplitude() }
                onAmplitudeChanged(amplitude)
                delay(80L)
            }
        }
    }

    private fun stopAmplitudeMonitoring() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        onAmplitudeChanged(0f)
    }

    private companion object {
        const val CHINESE_TIMEOUT_MS = 32_000L
        const val OTHER_TIMEOUT_MS = 50_000L
    }
}
