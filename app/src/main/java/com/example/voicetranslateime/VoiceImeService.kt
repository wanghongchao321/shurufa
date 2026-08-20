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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File

class VoiceImeService : InputMethodService() {
    private enum class UiPhase { IDLE, RECORDING, SENDING, ERROR }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var recordButton: Button
    private lateinit var deleteButton: Button
    private lateinit var clearButton: Button
    private lateinit var sendButton: Button
    private lateinit var keyboardToggleButton: Button
    private lateinit var keyboardPanel: LinearLayout
    private lateinit var shiftButton: Button
    private val modeButtons = mutableMapOf<InputMode, Button>()
    private val keyboardButtons = mutableListOf<Button>()
    private val letterButtons = mutableMapOf<Char, Button>()
    private lateinit var modeStore: ImeModeStore
    private lateinit var recorder: ImeAudioRecorder
    private lateinit var openRouterApi: OpenRouterApi

    private var uiPhase = UiPhase.IDLE
    private var isRecording = false
    private var lastError = ""
    private var processingStage = "处理中"
    private var processingJob: Job? = null
    private var processingGeneration = 0L
    private var keyboardVisible = false
    private var shiftEnabled = false

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
        keyboardButtons.clear()
        letterButtons.clear()

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf(
            InputMode.CN,
            InputMode.EN,
            InputMode.FR,
            InputMode.ZH_EN,
            InputMode.ZH_FR
        )
            .forEach { mode ->
                modeRow.addView(createModeButton(mode), rowButtonParams())
            }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keyboardToggleButton = createActionButton(
            label = "ABC",
            description = "切换英文键盘或语音输入",
            color = COLOR_KEYBOARD_IDLE,
            onClick = ::toggleEnglishKeyboard
        )
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

        actionRow.addView(keyboardToggleButton, rowButtonParams())
        actionRow.addView(deleteButton, rowButtonParams())
        actionRow.addView(clearButton, rowButtonParams())
        actionRow.addView(sendButton, rowButtonParams())

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

        keyboardPanel = createEnglishKeyboard()
        renderButtons()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            addView(
                modeRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(54)
                )
            )
            addView(
                actionRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(54)
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
            addView(
                keyboardPanel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            )
        }
    }

    private fun createModeButton(mode: InputMode) = Button(this).apply {
        isAllCaps = false
        text = mode.displayName
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        minHeight = dp(50)
        contentDescription = "选择${mode.displayName}模式"
        setOnClickListener {
            if (uiPhase == UiPhase.IDLE || uiPhase == UiPhase.ERROR) {
                modeStore.select(mode)
                uiPhase = UiPhase.IDLE
                lastError = ""
                renderButtons()
            }
        }
        modeButtons[mode] = this
    }

    private fun rowButtonParams() =
        LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }

    private fun createEnglishKeyboard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(createLetterRow("qwertyuiop"), keyboardRowParams())
        addView(createLetterRow("asdfghjkl", insetWeight = 0.5f), keyboardRowParams())

        addView(
            createKeyboardRow().apply {
                shiftButton = createKeyboardKey("⇧", "切换英文大小写") {
                    shiftEnabled = !shiftEnabled
                    renderKeyboardKeys()
                }
                addView(shiftButton, keyboardKeyParams(1.5f))
                "zxcvbnm".forEach { letter -> addLetterKey(this, letter) }
                addView(
                    createKeyboardKey("⌫", "删除光标前的一个字符") {
                        deletePreviousCharacter()
                    },
                    keyboardKeyParams(1.5f)
                )
            },
            keyboardRowParams()
        )

        addView(
            createKeyboardRow().apply {
                addView(createKeyboardKey("'", "输入英文撇号") { commitKeyText("'") }, keyboardKeyParams(1f))
                addView(createKeyboardKey(",", "输入逗号") { commitKeyText(",") }, keyboardKeyParams(1f))
                addView(createKeyboardKey("空格", "输入空格") { commitKeyText(" ") }, keyboardKeyParams(5f))
                addView(createKeyboardKey(".", "输入句号") { commitKeyText(".") }, keyboardKeyParams(1f))
                addView(createKeyboardKey("发送", "发送或确认当前输入") { sendCurrentInput() }, keyboardKeyParams(2f))
            },
            keyboardRowParams()
        )
    }

    private fun createLetterRow(
        letters: String,
        insetWeight: Float = 0f
    ) = createKeyboardRow().apply {
        if (insetWeight > 0f) {
            addView(View(this@VoiceImeService), keyboardKeyParams(insetWeight))
        }
        letters.forEach { letter -> addLetterKey(this, letter) }
        if (insetWeight > 0f) {
            addView(View(this@VoiceImeService), keyboardKeyParams(insetWeight))
        }
    }

    private fun createKeyboardRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun addLetterKey(row: LinearLayout, letter: Char) {
        val button = createKeyboardKey(letter.toString(), "输入字母 $letter") {
            commitLetter(letter)
        }
        letterButtons[letter] = button
        row.addView(button, keyboardKeyParams(1f))
    }

    private fun createKeyboardKey(
        label: String,
        description: String,
        onClick: () -> Unit
    ) = Button(this).apply {
        isAllCaps = false
        text = label
        textSize = 16f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setTextColor(COLOR_MODE_TEXT)
        background = roundedBackground(COLOR_KEY_IDLE, dp(8).toFloat())
        contentDescription = description
        setOnClickListener { onClick() }
        keyboardButtons.add(this)
    }

    private fun keyboardRowParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(48)
    ).apply {
        topMargin = dp(3)
    }

    private fun keyboardKeyParams(weight: Float) = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.MATCH_PARENT,
        weight
    ).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
    }

    private fun toggleEnglishKeyboard() {
        keyboardVisible = !keyboardVisible
        shiftEnabled = false
        renderButtons()
    }

    private fun commitLetter(letter: Char) {
        val output = if (shiftEnabled) letter.uppercaseChar() else letter
        commitKeyText(output.toString())
        if (shiftEnabled) {
            shiftEnabled = false
            renderKeyboardKeys()
        }
    }

    private fun commitKeyText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun renderKeyboardKeys() {
        letterButtons.forEach { (letter, button) ->
            button.text = if (shiftEnabled) {
                letter.uppercaseChar().toString()
            } else {
                letter.toString()
            }
        }
        if (::shiftButton.isInitialized) {
            shiftButton.setTextColor(if (shiftEnabled) Color.WHITE else COLOR_MODE_TEXT)
            shiftButton.background = roundedBackground(
                if (shiftEnabled) COLOR_MODE_SELECTED else COLOR_KEY_IDLE,
                dp(8).toFloat()
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
        textSize = 15f
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
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
                if (uiPhase == UiPhase.SENDING) {
                    cancelProcessing()
                    return true
                }
                if (uiPhase == UiPhase.RECORDING) {
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
        val processId = ++processingGeneration
        processingStage = "准备处理"
        uiPhase = UiPhase.SENDING
        renderButtons()

        processingJob = serviceScope.launch {
            try {
                val timeoutMs = if (mode == InputMode.CN) {
                    CHINESE_PROCESS_TIMEOUT_MS
                } else {
                    OTHER_PROCESS_TIMEOUT_MS
                }
                val text = withTimeout(timeoutMs) {
                    openRouterApi.process(file, mode) { stage ->
                        if (processId == processingGeneration) {
                            processingStage = stage
                            renderButtons()
                        }
                    }
                }

                if (requestGeneration == editorGeneration) {
                    currentInputConnection?.commitText(text, 1)
                } else {
                    Toast.makeText(
                        this@VoiceImeService,
                        "输入框已变化，结果未上屏",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: TimeoutCancellationException) {
                showFailure("处理超时，请检查网络后重试")
            } catch (_: CancellationException) {
                // User cancellation is intentionally silent and must not commit a stale result.
            } catch (error: Exception) {
                showFailure(error.message ?: "网络异常")
            } finally {
                file.delete()
                if (processId == processingGeneration) {
                    processingJob = null
                }
                if (processId == processingGeneration && uiPhase != UiPhase.ERROR) {
                    uiPhase = UiPhase.IDLE
                    if (::recordButton.isInitialized) renderButtons()
                }
            }
        }
    }

    private fun cancelProcessing() {
        processingGeneration++
        processingJob?.cancel()
        processingJob = null
        uiPhase = UiPhase.IDLE
        processingStage = "处理中"
        renderButtons()
        Toast.makeText(this, "已取消处理，可重新录音", Toast.LENGTH_SHORT).show()
    }

    private fun showFailure(message: String) {
        lastError = message.take(80)
        uiPhase = UiPhase.ERROR
        if (::recordButton.isInitialized) renderButtons()
        Toast.makeText(this, "处理失败：$lastError", Toast.LENGTH_LONG).show()
    }

    private fun renderButtons() {
        val mode = modeStore.current.displayName
        recordButton.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        keyboardPanel.visibility = if (keyboardVisible) View.VISIBLE else View.GONE
        keyboardToggleButton.text = if (keyboardVisible) "语音" else "ABC"
        keyboardToggleButton.contentDescription = if (keyboardVisible) {
            "切换到语音输入"
        } else {
            "切换到英文键盘"
        }
        renderKeyboardKeys()

        recordButton.text = when (uiPhase) {
            UiPhase.IDLE -> "按住说话 · $mode"
            UiPhase.RECORDING -> "松开发送 · $mode"
            UiPhase.SENDING -> "$processingStage… · $mode\n点按取消"
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
        keyboardToggleButton.isEnabled = actionsEnabled
        deleteButton.isEnabled = actionsEnabled
        clearButton.isEnabled = actionsEnabled
        sendButton.isEnabled = actionsEnabled
        keyboardToggleButton.alpha = if (actionsEnabled) 1f else 0.55f
        deleteButton.alpha = if (actionsEnabled) 1f else 0.55f
        clearButton.alpha = if (actionsEnabled) 1f else 0.55f
        sendButton.alpha = if (actionsEnabled) 1f else 0.55f
        keyboardButtons.forEach { button ->
            button.isEnabled = actionsEnabled
            button.alpha = if (actionsEnabled) 1f else 0.55f
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

        if (uiPhase == UiPhase.SENDING) {
            processingGeneration++
            processingJob?.cancel()
            processingJob = null
        }

        uiPhase = UiPhase.IDLE
        super.onFinishInput()
    }

    override fun onDestroy() {
        recorder.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun roundedBackground(
        color: Int,
        radius: Float = dp(18).toFloat()
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
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
        const val COLOR_KEYBOARD_IDLE = 0xFFE0E7FF.toInt()
        const val COLOR_KEY_IDLE = 0xFFF3F4F6.toInt()
        const val CHINESE_PROCESS_TIMEOUT_MS = 32_000L
        const val OTHER_PROCESS_TIMEOUT_MS = 50_000L

        val SEND_ACTION_PACKAGES = setOf(
            "com.tencent.mm",
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
    }
}
