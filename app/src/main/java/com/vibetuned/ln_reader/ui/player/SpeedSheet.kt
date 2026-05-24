package com.vibetuned.ln_reader.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    current: Float,
    onSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Playback speed",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            SPEEDS.forEach { speed ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpeed(speed) }
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatSpeed(speed),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (kotlin.math.abs(speed - current) < 0.01f) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            }
            Row(Modifier.height(8.dp)) {}
        }
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100f).toInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}.0×" else "${rounded}×"
}
