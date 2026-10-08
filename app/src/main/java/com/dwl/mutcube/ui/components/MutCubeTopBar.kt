package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.ui.RootDestination

@Composable
fun MutCubeTopBar(
    selected: RootDestination,
    onMenuClick: () -> Unit,
    onSurfaceSelected: (RootDestination) -> Unit,
    onContextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Menu, contentDescription = "打开侧边栏")
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(4.dp)) {
                SurfaceTab("聊天", selected == RootDestination.CHAT) {
                    onSurfaceSelected(RootDestination.CHAT)
                }
                SurfaceTab("模板", selected == RootDestination.TEMPLATES) {
                    onSurfaceSelected(RootDestination.TEMPLATES)
                }
            }
        }

        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = onContextClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (selected == RootDestination.TEMPLATES) Icons.Rounded.Search else Icons.Rounded.MoreHoriz,
                    contentDescription = if (selected == RootDestination.TEMPLATES) "搜索模板" else "更多功能",
                )
            }
        }
    }
}

@Composable
private fun SurfaceTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
