package com.shiping.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 渐变背景预设
 */
object BackgroundPresets {

    /** 预设数量（不含默认和自定义） */
    const val PRESET_COUNT = 5

    data class GradientPreset(
        val name: String,
        val darkColors: List<Color>,
        val lightColors: List<Color>
    )

    val presets = listOf(
        // 1. 落日余晖
        GradientPreset(
            name = "落日余晖",
            darkColors = listOf(Color(0xFF1A0A2E), Color(0xFF2D1B4E), Color(0xFF4A1942), Color(0xFF6B2D5C)),
            lightColors = listOf(Color(0xFFFFE5B4), Color(0xFFFFC9A0), Color(0xFFFFA589), Color(0xFFFF7E5F))
        ),
        // 2. 深海蓝调
        GradientPreset(
            name = "深海蓝调",
            darkColors = listOf(Color(0xFF001F3F), Color(0xFF003366), Color(0xFF004D80), Color(0xFF0066A0)),
            lightColors = listOf(Color(0xFFB3E5FC), Color(0xFF81D4FA), Color(0xFF4FC3F7), Color(0xFF29B6F6))
        ),
        // 3. 极光紫
        GradientPreset(
            name = "极光紫",
            darkColors = listOf(Color(0xFF0D0221), Color(0xFF1B0E3D), Color(0xFF2E1A5D), Color(0xFF4B2E83)),
            lightColors = listOf(Color(0xFFE1BEE7), Color(0xFFCE93D8), Color(0xFFBA68C8), Color(0xFF9C27B0))
        ),
        // 4. 暗夜森林
        GradientPreset(
            name = "暗夜森林",
            darkColors = listOf(Color(0xFF0A1A0A), Color(0xFF1B3A1B), Color(0xFF2D5A2D), Color(0xFF3D6B3D)),
            lightColors = listOf(Color(0xFFC8E6C9), Color(0xFFA5D6A7), Color(0xFF81C784), Color(0xFF66BB6A))
        ),
        // 5. 烈焰红
        GradientPreset(
            name = "烈焰红",
            darkColors = listOf(Color(0xFF1A0000), Color(0xFF330000), Color(0xFF4D0000), Color(0xFF660000)),
            lightColors = listOf(Color(0xFFFFCDD2), Color(0xFFEF9A9A), Color(0xFFE57373), Color(0xFFEF5350))
        )
    )

    /**
     * 获取渐变 Brush
     * @param type 背景类型: 0=默认, 1-5=渐变预设, 6=自定义图片
     * @param darkTheme 是否暗色模式
     */
    fun getGradientBrush(type: Int, darkTheme: Boolean): Brush? {
        if (type < 1 || type > PRESET_COUNT) return null
        val preset = presets[type - 1]
        val colors = if (darkTheme) preset.darkColors else preset.lightColors
        return Brush.verticalGradient(colors)
    }

    /** 获取预设名称 */
    fun getPresetName(type: Int): String {
        if (type < 1 || type > PRESET_COUNT) return "默认"
        return presets[type - 1].name
    }
}
