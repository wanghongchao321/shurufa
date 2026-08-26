package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.ui.theme.KeyboardThemes

/** 老版本式的大状态卡片；避免音量采样触发多组动画和整键盘高频重组。 */
@Composable
fun VoiceKeyboardLayout(
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    keyboardBackgroundColor: Color,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    themeId: String = "ocean_blue",
    bottomActive: Boolean = false,
    leftActive: Boolean = false,
    rightActive: Boolean = false,
    pluginName: String = "",
    recognitionState: RecognitionState = RecognitionState.IDLE,
    recognizedText: String = "",
    amplitude: Float = 0f,
) {
    val accentColor = KeyboardThemes.getAccentColor(themeId, isDarkTheme)
    // Directly map recorder amplitude to the microphone halo.  This avoids
    // allocating per-bar animations while still giving immediate feedback that
    // the microphone is receiving the user's voice.
    val volumeLevel = amplitude.coerceIn(0f, 1f)
    val cardColor = when (recognitionState) {
        RecognitionState.IDLE -> accentColor
        RecognitionState.LISTENING -> Color(0xFFD43838)
        RecognitionState.PROCESSING -> Color(0xFF6B7280)
        RecognitionState.ERROR -> Color(0xFFD97706)
    }
    val defaultStatus = when (recognitionState) {
        RecognitionState.IDLE -> "按住空格开始说话"
        RecognitionState.LISTENING -> "正在聆听，松开后识别"
        RecognitionState.PROCESSING -> "正在识别…"
        RecognitionState.ERROR -> "识别失败，请重试"
    }
    val helper = when (recognitionState) {
        RecognitionState.LISTENING -> "松开空格立即提交录音"
        RecognitionState.PROCESSING -> "正在处理，请稍候"
        RecognitionState.ERROR -> "返回键盘后可重新录音"
        RecognitionState.IDLE -> ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(keyboardBackgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pluginName.isNotBlank()) {
            Text(
                text = pluginName,
                color = keyTextColor.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = cardColor,
            contentColor = Color.White,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size((52f + volumeLevel * 12f).dp)
                        .background(Color.White.copy(alpha = 0.18f + volumeLevel * 0.36f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size((28f + volumeLevel * 7f).dp),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = recognizedText.ifBlank { defaultStatus },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (helper.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = helper,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 候选栏常驻语音模式使用的轻量频谱；直接绘制，不创建逐条动画状态。 */
@Composable
fun AudioSpectrumAnimation(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    amplitude: Float = 0f,
    spectrum: FloatArray = FloatArray(16),
    barWidthFactor: Float = 2.2f,
    barCount: Int = 9,
    spacingRatio: Float = 0.6f,
    heightScale: Float = 0.85f,
) {
    Canvas(modifier = modifier) {
        if (!isActive || barCount <= 0) return@Canvas
        val barWidth = size.width / (barCount * barWidthFactor)
        val spacing = barWidth * spacingRatio
        val totalWidth = barCount * barWidth + (barCount - 1) * spacing
        val startX = (size.width - totalWidth) / 2f
        repeat(barCount) { index ->
            val source = spectrum.getOrNull(index % spectrum.size.coerceAtLeast(1))
                ?: amplitude
            val value = source.coerceIn(0.08f, 1f)
            val barHeight = size.height * value * heightScale
            drawRoundRect(
                color = Color(0xFF54A0FF),
                topLeft = Offset(startX + index * (barWidth + spacing), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
