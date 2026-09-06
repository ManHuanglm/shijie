package com.shiping.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil.compose.rememberAsyncImagePainter

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onPrimary = Color.White,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F5),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F)
)

/**
 * 应用主题
 * @param darkTheme 是否暗色模式
 * @param backgroundType 背景类型: 0=默认, 1-5=渐变预设, 6=自定义图片
 * @param customBgUri 自定义背景图片URI
 */
@Composable
fun ShipingTheme(
    darkTheme: Boolean = true,
    backgroundType: Int = 0,
    customBgUri: String = "",
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    // 使用自定义背景时，将 background 设为透明，避免内容层遮挡渐变/图片背景
    val colorScheme = if (backgroundType != 0) {
        baseScheme.copy(background = Color.Transparent)
    } else baseScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (backgroundType != 0) baseScheme.background else colorScheme.background).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            when {
                // 渐变背景
                backgroundType in 1..BackgroundPresets.PRESET_COUNT -> {
                    val brush = BackgroundPresets.getGradientBrush(backgroundType, darkTheme)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                brush?.let { Modifier.background(it) }
                                ?: Modifier.background(colorScheme.background)
                            )
                    ) {
                        content()
                    }
                }
                // 自定义图片背景
                backgroundType == 6 && customBgUri.isNotEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = rememberAsyncImagePainter(customBgUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // 半透明遮罩，保证内容可读
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (darkTheme) Color.Black.copy(alpha = 0.5f)
                                    else Color.White.copy(alpha = 0.6f)
                                )
                        )
                        content()
                    }
                }
                // 默认
                else -> content()
            }
        }
    )
}
