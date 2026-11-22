package com.cuscus.ffmpeggui.ui.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** The platform-neutral PanelCard from app/ui/expressive/Panels.kt. EUPL-1.2. */
@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier.fillMaxWidth()
        .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(16.dp), content = content)
}
