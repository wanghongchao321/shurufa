package com.example.voicetranslateime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class VoiceImeService : InputMethodService() {
    private enum class UiPhase { IDLE, RECORDING, SENDING }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var recordButton: Button
    private val modeButtons = mutableMapOf<InputMode, Button>()
    private lateinit var modeStore: ImeModeStore
    private lateinit var recorder: ImeAudioRecorder
    private lateinit var openRouterApi: OpenRouterApi

    private var uiPhase = UiPhase.IDLE
    private var isRecording = false

    private var recordingMode = InputMode.CN
    private var recordingEditorGeneration = 0L
    private var editorGeneration = 0L

    override fun onCreate() {
        super.onCreate()
        modeStore = ImeModeStore(this)
        recorder = ImeAudioRecorder(this)
        openRouterApi = OpenRouterApi(
            apiKey = BuildConfig.OPENROUTER_API_KEY,
            model = BuildConfig.OPENROUTER_MODEL
        )
    }

    override fun onCreateInputView(): View {
        modeButtons.clear()

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        InputMode.entries.forEach { mode ->
            val button = Button(this).apply {
                isAllCaps = false
                text = mode.displayName
                textSize = 15f
                minHeight = dp(52)
                contentDescription = "选择${mode.displayName}模式"
                setOnClickListener {
                    if (uiPhase == UiPhase.IDLE) {
                        modeStore.select(mode)
                        renderButtons()
                    }
                }
            }
            modeButtons[mode] = button
            modeRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                }
            )
        }

        recordButton = Button(this).apply {
            isAllCaps = false
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            minHeight = dp(88)
            background = roundedBackground(COLOR_IDLE)
            contentDescription = "按住说话，松开发送"

            setOnTouchListener { view, event ->
                handleRecordTouch(view, event)
            }
        }

        renderButtons()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            addView(
                modeRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(56)
                )
            )
            addView(
                recordButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(92)
                ).apply {
                    topMargin = dp(8)
                }
            )
        }
    }

    private fun handleRecordTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (uiPhase != UiPhase.IDLE) return true
                view.isPressed = true
                beginRecording()
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.isPressed = false
                if (isRecording) finishRecordingAndSubmit()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                if (isRecording) {
                    isRecording = false
                    recorder.cancel()
                    uiPhase = UiPhase.IDLE
                    renderButtons()
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
                renderButtons()
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
            renderButtons()
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
        renderButtons()

        serviceScope.launch {
            try {
                val text = openRouterApi.process(file, mode)

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
                if (::recordButton.isInitialized) renderButtons()
            }
        }
    }

    private fun renderButtons() {
        val mode = modeStore.current.displayName
        recordButton.text = when (uiPhase) {
            UiPhase.IDLE -> "按住说话 · $mode"
            UiPhase.RECORDING -> "松开发送 · $mode"
            UiPhase.SENDING -> "处理中… · $mode"
        }
        recordButton.background = roundedBackground(
            when (uiPhase) {
                UiPhase.IDLE -> COLOR_IDLE
                UiPhase.RECORDING -> COLOR_RECORDING
                UiPhase.SENDING -> COLOR_SENDING
            }
        )

        modeButtons.forEach { (buttonMode, button) ->
            val selected = buttonMode == modeStore.current
            button.isEnabled = uiPhase == UiPhase.IDLE
            button.setTextColor(if (selected) Color.WHITE else COLOR_MODE_TEXT)
            button.background = roundedBackground(
                if (selected) COLOR_MODE_SELECTED else COLOR_MODE_IDLE
            )
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        editorGeneration++
    }

    override fun onFinishInput() {
        editorGeneration++

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
        const val COLOR_IDLE = 0xFF3158D4.toInt()
        const val COLOR_RECORDING = 0xFFD43838.toInt()
        const val COLOR_SENDING = 0xFF6B7280.toInt()
        const val COLOR_MODE_SELECTED = 0xFF3158D4.toInt()
        const val COLOR_MODE_IDLE = 0xFFE5E7EB.toInt()
        const val COLOR_MODE_TEXT = 0xFF1F2937.toInt()
    }
}
