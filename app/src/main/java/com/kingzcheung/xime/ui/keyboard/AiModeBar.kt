package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ai.ImeModeStore
import com.kingzcheung.xime.ai.InputMode

@Composable
internal fun AiModeBar(
    visuals: CandidateBarVisuals,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { ImeModeStore(context.applicationContext) }
    var selectedMode by remember { mutableStateOf(store.current) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Surface(
                onClick = {
                    store.select(mode)
                    selectedMode = mode
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    visuals.accentColor
                } else {
                    visuals.textColor.copy(alpha = if (visuals.isDarkTheme) 0.14f else 0.08f)
                },
                contentColor = if (selected) Color.White else visuals.textColor,
                tonalElevation = if (selected) 2.dp else 0.dp,
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = mode.displayName,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
