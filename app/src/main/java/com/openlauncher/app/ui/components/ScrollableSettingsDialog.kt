package com.openlauncher.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Screen-bounded settings form with a scrolling body and fixed action bar. */
@Composable
fun ScrollableSettingsDialog(
    title: String,
    isDayMode: Boolean,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val background = if (isDayMode) Color.White else Color(0xFF0C0C0C)
    val border = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF242424)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp)).background(background)
                        .border(1.dp, border, RoundedCornerShape(4.dp))
                ) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
                        Text(title, color = if (isDayMode) Color(0xFF111111) else Color.White,
                            fontSize = 13.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(12.dp))
                        content()
                    }
                    HorizontalDivider(color = border)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}
