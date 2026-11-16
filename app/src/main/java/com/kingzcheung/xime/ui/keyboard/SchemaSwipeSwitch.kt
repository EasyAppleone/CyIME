package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 「左右滑动切换输入方式」的回调持有者（用户 11-16 需求）。
 *
 * 手势块只在键盘重组时启动一次，把回调写进这个稳定对象里，每次重组都刷新 [onSwitch]，
 * 手指抬起时执行的一定是现在这一份（否则换过一次输入方式后还会按旧列表算下一下）。
 */
internal class SchemaSwipeHandler {
    var onSwitch: ((forward: Boolean) -> Unit)? = null
}

/**
 * 在整块键盘（输入页面）上左右滑动切换输入方式：左划下一个、右划上一个。
 *
 * 判定放在 [PointerEventPass.Main]：父节点在这一趟里比按键、候选栏、删除键晚收到同一批事件，
 * 于是能看出这一次拖动有没有已经被它们接管（候选栏横滑翻候选、删除键横滑删字、底部留白横滑移光标、
 * 滑行输入画线都会 consume）。已经被接管就整次放弃，不影响它们原有手势——
 * 只有没人管的横滑才当成「换输入方式」。
 *
 * 判定成立后 consume 掉后续事件，按键抬手时不再补一个字符。
 *
 * @param enabled 关掉（或开着滑行输入）时完全不接事件。
 * @param handler 稳定的回调持有者，见 [SchemaSwipeHandler]。
 * @param thresholdDp 触发距离，默认 72dp（约两个键宽，误触不到）。
 */
internal fun Modifier.schemaSwipeSwitch(
    enabled: Boolean,
    handler: SchemaSwipeHandler,
    thresholdDp: Float = 72f,
): Modifier = if (!enabled) this else this.pointerInput(handler, thresholdDp) {
    val threshold = thresholdDp.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dx = 0f
        var dy = 0f
        // 这一次拖动已经被按键／候选栏／留白条接管，整次都不再算切换。
        var childOwns = false
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            // 第二根手指落下：当普通多指手势处理，不切换。
            if (event.changes.size > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.positionChangedIgnoreConsumed()) {
                if (change.isConsumed) {
                    childOwns = true
                } else if (!childOwns) {
                    val delta = change.positionChangeIgnoreConsumed()
                    dx += delta.x
                    dy += delta.y
                }
            }
            if (!fired && !childOwns && abs(dx) >= threshold && abs(dx) > abs(dy) * 1.5f) {
                fired = true
                handler.onSwitch?.invoke(dx < 0f)
            }
            if (fired) change.consume()
            if (!change.pressed) break
        }
    }
}
