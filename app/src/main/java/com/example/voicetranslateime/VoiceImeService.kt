package com.example.voicetranslateime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class VoiceImeService : InputMethodService() {
    private enum class UiPhase { IDLE, RECORDING, SENDING }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var modeButton: Button
    private lateinit var modeStore: ImeModeStore
    private lateinit var recorder: ImeAudioRecorder
    private lateinit var backendApi: ImeBackendApi

    private var uiPhase = UiPhase.IDLE
    private var isRecording = false
    private var longPressTriggered = false
    private var longPressJob: Job? = null
    private var downAtMillis = 0L

    private var recordingMode = InputMode.CN
    private var recordingEditorGeneration = 0L
    private var editorGeneration = 0L

    override fun onCreate() {
        super.onCreate()
        modeStore = ImeModeStore(this)
        recorder = ImeAudioRecorder(this)
        backendApi = ImeBackendApi(
            baseUrl = BuildConfig.IME_BACKEND_BASE_URL,
            sharedToken = BuildConfig.IME_SHARED_TOKEN
        )
    }

    override fun onCreateInputView(): View {
        modeButton = Button(this).apply {
            isAllCaps = false
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minHeight = dp(72)
            background = roundedBackground(COLOR_IDLE)
            contentDescription = "输入模式。短按切换模式，长按录音"

            setOnClickListener {
                if (uiPhase == UiPhase.IDLE) {
                    modeStore.moveToNext()
                    renderButton()
                }
            }

            setOnTouchListener { view, event ->
                handleButtonTouch(view, event)
            }
        }

        renderButton()
        return modeButton
    }

    private fun handleButtonTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (uiPhase != UiPhase.IDLE) return true

                downAtMillis = SystemClock.elapsedRealtime()
                longPressTriggered = false
                longPressJob?.cancel()
                longPressJob = serviceScope.launch {
                    delay(LONG_PRESS_MILLIS)
                    longPressTriggered = true
                    beginRecording()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                longPressJob?.cancel()

                when {
                    isRecording -> finishRecordingAndSubmit()
                    !longPressTriggered &&
                        SystemClock.elapsedRealtime() - downAtMillis < LONG_PRESS_MILLIS -> {
                        view.performClick()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                if (isRecording) {
                    isRecording = false
                    recorder.cancel()
                    uiPhase = UiPhase.IDLE
                    renderButton()
                }
                return true
            }

            else -> return true
        }
    }

    private fun beginRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "请先授予麦克风权限", Toast.LENGTH_LONG).show()
            runCatching {
                startActivity(
                    Intent(this, PermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }

        recordingMode = modeStore.current
        recordingEditorGeneration = editorGeneration

        runCatching { recorder.start() }
            .onSuccess {
                isRecording = true
                uiPhase = UiPhase.RECORDING
                renderButton()
            }
            .onFailure {
                Toast.makeText(
                    this,
                    "无法启动录音：${it.message ?: "未知错误"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun finishRecordingAndSubmit() {
        isRecording = false
        val file = recorder.stop()

        if (file == null) {
            uiPhase = UiPhase.IDLE
            renderButton()
            Toast.makeText(this, "录音过短，请重试", Toast.LENGTH_SHORT).show()
            return
        }

        submitAudio(file, recordingMode, recordingEditorGeneration)
    }

    private fun submitAudio(
        file: File,
        mode: InputMode,
        requestGeneration: Long
    ) {
        uiPhase = UiPhase.SENDING
        renderButton()

        serviceScope.launch {
            try {
                val text = backendApi.process(file, mode)

                if (requestGeneration == editorGeneration) {
                    currentInputConnection?.commitText(text, 1)
                } else {
                    Toast.makeText(
                        this@VoiceImeService,
                        "输入框已变化，结果未上屏",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: Exception) {
                Toast.makeText(
                    this@VoiceImeService,
                    "处理失败：${error.message ?: "网络异常"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                file.delete()
                uiPhase = UiPhase.IDLE
                if (::modeButton.isInitialized) renderButton()
            }
        }
    }

    private fun renderButton() {
        val mode = modeStore.current.displayName
        modeButton.text = when (uiPhase) {
            UiPhase.IDLE -> mode
            UiPhase.RECORDING -> "$mode · 松开发送"
            UiPhase.SENDING -> "$mode · 处理中…"
        }
        modeButton.background = roundedBackground(
            when (uiPhase) {
                UiPhase.IDLE -> COLOR_IDLE
                UiPhase.RECORDING -> COLOR_RECORDING
                UiPhase.SENDING -> COLOR_SENDING
            }
        )
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        editorGeneration++
    }

    override fun onFinishInput() {
        editorGeneration++
        longPressJob?.cancel()

        if (isRecording) {
            isRecording = false
            recorder.cancel()
        }

        uiPhase = UiPhase.IDLE
        super.onFinishInput()
    }

    override fun onDestroy() {
        recorder.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(color)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val LONG_PRESS_MILLIS = 350L
        const val COLOR_IDLE = 0xFF3158D4.toInt()
        const val COLOR_RECORDING = 0xFFD43838.toInt()
        const val COLOR_SENDING = 0xFF6B7280.toInt()
    }
}
