package com.example.voicetranslateime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
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
    private enum class UiPhase { IDLE, RECORDING, SENDING, ERROR }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var recordButton: Button
    private lateinit var deleteButton: Button
    private lateinit var clearButton: Button
    private lateinit var sendButton: Button
    private val modeButtons = mutableMapOf<InputMode, Button>()
    private lateinit var modeStore: ImeModeStore
    private lateinit var recorder: ImeAudioRecorder
    private lateinit var openRouterApi: OpenRouterApi

    private var uiPhase = UiPhase.IDLE
    private var isRecording = false
    private var lastError = ""

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

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        deleteButton = createActionButton(
            label = "\u5220\u9664",
            description = "\u5220\u9664\u5149\u6807\u524d\u7684\u4e00\u4e2a\u5b57\u7b26",
            color = COLOR_ACTION_IDLE,
            onClick = ::deletePreviousCharacter
        )
        clearButton = createActionButton(
            label = "\u6e05\u7a7a",
            description = "\u6e05\u7a7a\u5f53\u524d\u8f93\u5165\u6846",
            color = COLOR_CLEAR_IDLE,
            onClick = ::clearCurrentField
        )
        sendButton = createActionButton(
            label = "\u53d1\u9001",
            description = "\u53d1\u9001\u6216\u786e\u8ba4\u5f53\u524d\u8f93\u5165",
            color = COLOR_SEND_IDLE,
            onClick = ::sendCurrentInput
        )

        actionRow.addView(
            deleteButton,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                marginEnd = dp(3)
            }
        )
        actionRow.addView(
            clearButton,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        )
        actionRow.addView(
            sendButton,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                marginStart = dp(3)
            }
        )

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
                actionRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(50)
                ).apply {
                    topMargin = dp(6)
                }
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

    private fun createActionButton(
        label: String,
        description: String,
        color: Int,
        onClick: () -> Unit
    ) = Button(this).apply {
        isAllCaps = false
        text = label
        textSize = 17f
        minHeight = dp(46)
        setTextColor(COLOR_MODE_TEXT)
        background = roundedBackground(color)
        contentDescription = description
        setOnClickListener { onClick() }
    }

    private fun deletePreviousCharacter() {
        val connection = currentInputConnection ?: return
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        val hasSelection = extracted != null &&
            extracted.selectionStart >= 0 &&
            extracted.selectionEnd > extracted.selectionStart

        if (hasSelection) {
            connection.commitText("", 1)
            return
        }

        if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    private fun clearCurrentField() {
        val connection = currentInputConnection ?: return
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        val textLength = extracted?.text?.length ?: 0

        if (textLength > 0 && connection.setSelection(0, textLength)) {
            connection.commitText("", 1)
        } else {
            connection.performContextMenuAction(android.R.id.selectAll)
            connection.commitText("", 1)
        }
    }

    private fun sendCurrentInput() {
        val connection = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val editorAction = editorInfo?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        val packageName = editorInfo?.packageName.orEmpty()

        val handledByAdvertisedAction = when {
            editorInfo != null && editorInfo.actionId != 0 ->
                connection.performEditorAction(editorInfo.actionId)

            editorAction != EditorInfo.IME_ACTION_NONE &&
                editorAction != EditorInfo.IME_ACTION_UNSPECIFIED ->
                connection.performEditorAction(editorAction)

            else -> false
        }

        if (handledByAdvertisedAction) return

        if (packageName in SEND_ACTION_PACKAGES &&
            connection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        ) {
            return
        }

        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun handleRecordTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (uiPhase == UiPhase.SENDING || uiPhase == UiPhase.RECORDING) {
                    return true
                }
                // A new press dismisses the previous visible error and retries.
                if (uiPhase == UiPhase.ERROR) uiPhase = UiPhase.IDLE
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
                showFailure("无法启动录音：${it.message ?: "未知错误"}")
            }
    }

    private fun finishRecordingAndSubmit() {
        isRecording = false
        val file = recorder.stop()

        if (file == null) {
            showFailure("录音过短或设备录音失败，请按住至少 1 秒")
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
                showFailure(error.message ?: "网络异常")
            } finally {
                file.delete()
                if (uiPhase != UiPhase.ERROR) {
                    uiPhase = UiPhase.IDLE
                    if (::recordButton.isInitialized) renderButtons()
                }
            }
        }
    }

    private fun showFailure(message: String) {
        lastError = message.take(80)
        uiPhase = UiPhase.ERROR
        if (::recordButton.isInitialized) renderButtons()
        Toast.makeText(this, "处理失败：$lastError", Toast.LENGTH_LONG).show()
    }

    private fun renderButtons() {
        val mode = modeStore.current.displayName
        recordButton.text = when (uiPhase) {
            UiPhase.IDLE -> "按住说话 · $mode"
            UiPhase.RECORDING -> "松开发送 · $mode"
            UiPhase.SENDING -> "处理中… · $mode"
            UiPhase.ERROR -> "失败：$lastError · 按住重试"
        }
        recordButton.background = roundedBackground(
            when (uiPhase) {
                UiPhase.IDLE -> COLOR_IDLE
                UiPhase.RECORDING -> COLOR_RECORDING
                UiPhase.SENDING -> COLOR_SENDING
                UiPhase.ERROR -> COLOR_ERROR
            }
        )

        modeButtons.forEach { (buttonMode, button) ->
            val selected = buttonMode == modeStore.current
            button.isEnabled = uiPhase == UiPhase.IDLE || uiPhase == UiPhase.ERROR
            button.setTextColor(if (selected) Color.WHITE else COLOR_MODE_TEXT)
            button.background = roundedBackground(
                if (selected) COLOR_MODE_SELECTED else COLOR_MODE_IDLE
            )
        }

        val actionsEnabled = uiPhase == UiPhase.IDLE || uiPhase == UiPhase.ERROR
        deleteButton.isEnabled = actionsEnabled
        clearButton.isEnabled = actionsEnabled
        sendButton.isEnabled = actionsEnabled
        deleteButton.alpha = if (actionsEnabled) 1f else 0.55f
        clearButton.alpha = if (actionsEnabled) 1f else 0.55f
        sendButton.alpha = if (actionsEnabled) 1f else 0.55f
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
        const val COLOR_ERROR = 0xFFD97706.toInt()
        const val COLOR_MODE_SELECTED = 0xFF3158D4.toInt()
        const val COLOR_MODE_IDLE = 0xFFE5E7EB.toInt()
        const val COLOR_MODE_TEXT = 0xFF1F2937.toInt()
        const val COLOR_ACTION_IDLE = 0xFFDDE5F5.toInt()
        const val COLOR_CLEAR_IDLE = 0xFFF7DADA.toInt()
        const val COLOR_SEND_IDLE = 0xFFD9F0E1.toInt()

        val SEND_ACTION_PACKAGES = setOf(
            "com.tencent.mm",
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
    }
}
