package com.kingzcheung.xime.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.keyboard.GestureAction

/**
 * 「键盘布局 › 笔画键手势」（用户 11-16 需求）：笔画布局上每个键上滑、下滑分别做什么。
 *
 * 存在 SharedPreferences（[PREFS_KEY]），按「键 + 方向」覆盖 xime.yaml 的 keyboard.stroke.keys：
 * - 设过的方向用这里的动作；
 * - 没设过的方向继续跟 YAML／内置默认（上滑输入对应数字）。
 *
 * 存 prefs 而不是改写 YAML：改 YAML 要重解析整份配置，还会碰到用户自己写过的内容；
 * 这里只记用户在这一页真正改过的那几格。
 */
object StrokeKeyGestures {

    /** 笔画布局的九个键，键面标签就是 StrokeKeyboardLayout 里的键 id。 */
    val keys = listOf("一", "丨", "丿", "丶", "乛", "*", "分词", "，", "。")

    const val PREFS_KEY = "stroke_key_gestures_v1"
    private const val RECORD = '\u001E'
    private const val FIELD = '\u001F'

    enum class Direction { UP, DOWN }

    /** 一个方向的动作；[value] 只有 [GestureAction.COMMIT] 用得上。 */
    data class Swipe(val action: GestureAction, val value: String = "") {
        /** 气泡上写的字：上屏文本就是它本身，其余动作写动作名。 */
        val label: String
            get() = if (action == GestureAction.COMMIT) value.ifBlank { "上屏" } else actionLabel(action)

        /** 交给布局层的手势定义：默认只在滑动气泡里显示，不挤占键面。 */
        fun toDef(): GestureDef = GestureDef(
            label = label,
            action = action,
            value = if (action == GestureAction.COMMIT) value else "",
            display = DisplayMode.BUBBLE,
        )
    }

    data class KeyGestures(val up: Swipe? = null, val down: Swipe? = null)

    /** 改一次就 +1：键盘那边读它决定重读（同一个进程，设置页存完键盘立刻生效）。 */
    var version by mutableIntStateOf(0)
        private set

    private var cachedRaw: String? = null
    private var cached: Map<String, KeyGestures> = emptyMap()

    /** 读全部设置；没改过的键不在表里。 */
    fun load(context: Context): Map<String, KeyGestures> {
        val raw = SettingsPreferences.getPrefsPublic(context).getString(PREFS_KEY, "").orEmpty()
        if (raw == cachedRaw) return cached
        cached = parse(raw)
        cachedRaw = raw
        return cached
    }

    fun swipe(context: Context, key: String, direction: Direction): Swipe? =
        load(context)[key]?.let { if (direction == Direction.UP) it.up else it.down }

    /** 设一个方向的动作；[swipe] 为 null 表示恢复默认（这一方向重新跟 YAML／内置）。 */
    fun set(context: Context, key: String, direction: Direction, swipe: Swipe?) {
        val current = load(context).toMutableMap()
        val old = current[key] ?: KeyGestures()
        val next = if (direction == Direction.UP) old.copy(up = swipe) else old.copy(down = swipe)
        if (next.up == null && next.down == null) current.remove(key) else current[key] = next
        write(context, current)
    }

    /** 这个键两个方向都恢复默认。 */
    fun reset(context: Context, key: String) {
        val current = load(context).toMutableMap()
        current.remove(key)
        write(context, current)
    }

    private fun parse(raw: String): Map<String, KeyGestures> {
        if (raw.isEmpty()) return emptyMap()
        val ups = mutableMapOf<String, Swipe>()
        val downs = mutableMapOf<String, Swipe>()
        raw.split(RECORD).forEach { record ->
            val fields = record.split(FIELD)
            if (fields.size < 3 || fields[0] !in keys) return@forEach
            val action = GestureAction.entries.firstOrNull { it.name == fields[2] } ?: return@forEach
            val swipe = Swipe(action, fields.getOrElse(3) { "" })
            when (fields[1]) {
                "up" -> ups[fields[0]] = swipe
                "down" -> downs[fields[0]] = swipe
            }
        }
        return (ups.keys + downs.keys).associateWith { KeyGestures(ups[it], downs[it]) }
    }

    private fun write(context: Context, all: Map<String, KeyGestures>) {
        val records = mutableListOf<String>()
        keys.forEach { key ->
            val gestures = all[key] ?: return@forEach
            gestures.up?.let { records += record(key, "up", it) }
            gestures.down?.let { records += record(key, "down", it) }
        }
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(PREFS_KEY, records.joinToString(RECORD.toString()))
            .apply()
        cachedRaw = null
        version++
    }

    private fun record(key: String, direction: String, swipe: Swipe): String {
        val value = swipe.value.filter { it != RECORD && it != FIELD }
        return listOf(key, direction, swipe.action.name, value).joinToString(FIELD.toString())
    }

    /** 设置页里可选的动作，顺序就是弹框里的顺序（上屏文字单独在最后处理）。 */
    val choices: List<GestureAction> = listOf(
        GestureAction.NONE,
        GestureAction.COPY,
        GestureAction.CUT,
        GestureAction.PASTE,
        GestureAction.SELECT_ALL,
        GestureAction.UNDO,
        GestureAction.LINE_START,
        GestureAction.LINE_END,
        GestureAction.DELETE,
        GestureAction.SPACE,
        GestureAction.ENTER,
        GestureAction.CLEAR_ALL,
        GestureAction.UNDO_CLEAR,
        GestureAction.REPEAT,
        GestureAction.TOGGLE_SYMBOLS,
        GestureAction.TOGGLE_ASCII,
    )

    fun actionLabel(action: GestureAction): String = when (action) {
        GestureAction.NONE -> "无操作"
        GestureAction.COMMIT -> "上屏文字"
        GestureAction.COMMAND -> "内置命令"
        GestureAction.COPY -> "复制"
        GestureAction.CUT -> "剪切"
        GestureAction.PASTE -> "粘贴"
        GestureAction.SELECT_ALL -> "全选"
        GestureAction.UNDO -> "撤销"
        GestureAction.LINE_START -> "光标到行首"
        GestureAction.LINE_END -> "光标到行尾"
        GestureAction.DELETE -> "退格"
        GestureAction.SPACE -> "空格"
        GestureAction.ENTER -> "回车"
        GestureAction.CLEAR_ALL -> "清空输入"
        GestureAction.UNDO_CLEAR -> "撤回清空"
        GestureAction.REPEAT -> "重复上次输入"
        GestureAction.SWITCH_ROUTE -> "切换面板"
        GestureAction.TOGGLE_ASCII -> "切换中／英"
        GestureAction.TOGGLE_SYMBOLS -> "打开符号"
        GestureAction.TOGGLE_SHIFT -> "切换大小写"
    }
}
