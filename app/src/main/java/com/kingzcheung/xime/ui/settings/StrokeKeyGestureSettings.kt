package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.keyboard.GestureAction
import com.kingzcheung.xime.settings.StrokeKeyGestures

/**
 * 「键盘布局 › 笔画键手势」（用户 11-16 需求）：笔画布局上每个键上滑、下滑分别做什么。
 *
 * 一页九行，每行一个笔画键，点「上滑」「下滑」就地挑动作，挑完立刻存、键盘立刻生效，没有保存按钮。
 * 没设过的方向仍跟上滑输入数字；「恢复默认」把这个键两个方向一起交还给 YAML／内置默认。
 */
@Composable
internal fun StrokeKeyGestureSettings() {
    val context = LocalContext.current
    val version = StrokeKeyGestures.version
    val gestures = remember(version) { StrokeKeyGestures.load(context) }
    var editing by remember { mutableStateOf<Editing?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(title = "怎么用") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("只对「笔画」布局有效。点每个键的「上滑」「下滑」挑动作，挑完马上生效，不用保存。",
                    style = MaterialTheme.typography.bodyMedium)
                Text("没设过的方向仍跟内置一样：上滑输入对应数字，下滑没有动作。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("复制、剪切、粘贴、全选这些要用到输入框里的内容，只有光标在输入框里时才有反应；" +
                        "清空、回车、退格、空格和普通按键一样。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsSection(title = "笔画键") {
            StrokeKeyGestures.keys.forEachIndexed { index, key ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                StrokeKeyGestureRow(
                    key = key,
                    gestures = gestures[key],
                    onEdit = { direction -> editing = Editing(key, direction) },
                    onReset = { StrokeKeyGestures.reset(context, key) },
                )
            }
        }
    }

    editing?.let { target ->
        StrokeSwipeDialog(
            keyLabel = target.key,
            direction = target.direction,
            current = gestures[target.key]?.let {
                if (target.direction == StrokeKeyGestures.Direction.UP) it.up else it.down
            },
            onDismiss = { editing = null },
            onPick = { swipe ->
                StrokeKeyGestures.set(context, target.key, target.direction, swipe)
                editing = null
            },
        )
    }
}

private data class Editing(val key: String, val direction: StrokeKeyGestures.Direction)

@Composable
private fun StrokeKeyGestureRow(
    key: String,
    gestures: StrokeKeyGestures.KeyGestures?,
    onEdit: (StrokeKeyGestures.Direction) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (gestures == null) "内置：上滑输入数字" else "已自定义",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (gestures != null) {
                TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("恢复默认")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionChip("上滑", gestures?.up, Modifier.weight(1f)) { onEdit(StrokeKeyGestures.Direction.UP) }
            DirectionChip("下滑", gestures?.down, Modifier.weight(1f)) { onEdit(StrokeKeyGestures.Direction.DOWN) }
        }
    }
}

@Composable
private fun DirectionChip(
    title: String,
    swipe: StrokeKeyGestures.Swipe?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(swipe?.label ?: "未设置", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StrokeSwipeDialog(
    keyLabel: String,
    direction: StrokeKeyGestures.Direction,
    current: StrokeKeyGestures.Swipe?,
    onDismiss: () -> Unit,
    onPick: (StrokeKeyGestures.Swipe?) -> Unit,
) {
    var commitText by remember(keyLabel, direction) {
        mutableStateOf(if (current?.action == GestureAction.COMMIT) current.value else "")
    }
    val directionLabel = if (direction == StrokeKeyGestures.Direction.UP) "上滑" else "下滑"
    val defaultLabel = "跟随默认（" +
        (if (direction == StrokeKeyGestures.Direction.UP) "上滑输入数字" else "无动作") + "）"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$keyLabel · $directionLabel") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OptionRow(
                    label = defaultLabel,
                    selected = current == null,
                ) { onPick(null) }
                StrokeKeyGestures.choices.forEach { action ->
                    OptionRow(
                        label = StrokeKeyGestures.actionLabel(action),
                        selected = current?.action == action,
                    ) { onPick(StrokeKeyGestures.Swipe(action)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("上屏文字", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = commitText,
                    onValueChange = { commitText = it },
                    singleLine = true,
                    placeholder = { Text("滑动后上屏的文字") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                TextButton(
                    onClick = { onPick(StrokeKeyGestures.Swipe(GestureAction.COMMIT, commitText.trim())) },
                    enabled = commitText.isNotBlank(),
                ) { Text("用这段文字") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
