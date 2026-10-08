package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compact floating notice shared by every app destination. */
@Composable
fun MutCubeNoticeHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { notice ->
        val isError = listOf("失败", "错误", "无法", "超时").any(notice.visuals.message::contains)
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 360.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = if (notice.visuals.actionLabel == null) 18.dp else 8.dp,
                    top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(notice.visuals.message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.widthIn(max = 220.dp))
                notice.visuals.actionLabel?.let { label ->
                    TextButton(onClick = notice::performAction) {
                        Text(label)
                    }
                }
            }
        }
    }
}
