package com.shiping.app.data.player

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import com.shiping.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 画中画（Picture-in-Picture）控制器单例。
 *
 * 跨层桥接：MainActivity 与 Compose 层（AppNavigation）的 NavBackstack / PlayerViewModel /
 * PreferencesManager 之间通过 provider lambda 通信，避免 Activity 持有 Compose 内部引用。
 *
 * 触发时机：用户按 Home 键 → Activity.onUserLeaveHint → [tryEnterPip]。
 * 满足以下条件才进入：
 * 1. SDK >= 26 (O)
 * 2. 设置开关开启（[pipEnabledProvider]）
 * 3. 当前路由包含播放页（[isOnPlayerRouteProvider]）
 * 4. ExoPlayer 正在播放（[isPlayingProvider]）
 *
 * UI 联动：AppNavigation 收集 [isPipMode] 在 PiP 模式下隐藏底部导航栏、
 * 跳过 player 释放、覆盖透明触摸吞层（避免 Compose 手势层拦截系统 PiP 控件）。
 */
object PipController {

    private const val TAG = "PipController"

    private val _isPipMode = MutableStateFlow(false)
    val isPipMode: StateFlow<Boolean> = _isPipMode.asStateFlow()

    // 由 AppNavigation 在启动时设置，lambda 不持有 Activity 引用
    var pipEnabledProvider: () -> Boolean = { false }
    var isPlayingProvider: () -> Boolean = { false }
    var isOnPlayerRouteProvider: () -> Boolean = { false }
    var aspectProvider: () -> Pair<Int, Int> = { 0 to 0 }

    /**
     * 尝试进入画中画模式。由 Activity.onUserLeaveHint 调用。
     * 满足全部前置条件才会进入，避免非播放场景误触发。
     */
    fun tryEnterPip(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!pipEnabledProvider()) {
            AppLog.d(TAG, "PiP 开关未开启，跳过")
            return
        }
        if (!isOnPlayerRouteProvider()) {
            AppLog.d(TAG, "当前不在播放页路由，跳过")
            return
        }
        if (!isPlayingProvider()) {
            AppLog.d(TAG, "player 未在播放，跳过")
            return
        }
        val (w, h) = aspectProvider()
        // 首帧前 videoWidth/Height 为 0，Rational(0,0) 非法，需回退 16:9
        val rational = if (w <= 0 || h <= 0) {
            Rational(16, 9)
        } else {
            // 系统要求纵横比在 [0.418410, 2.390000] 范围内
            Rational(w, h).coerceIn(
                Rational(41841, 100000),
                Rational(239000, 100000),
            )
        }
        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 关闭无缝缩放，避免部分设备切换闪烁
            builder.setSeamlessResizeEnabled(false)
        }
        runCatching {
            activity.enterPictureInPictureMode(builder.build())
        }.onFailure {
            AppLog.w(TAG, "进入画中画失败：${it.message}")
        }
    }

    /**
     * 由 Activity.onPictureInPictureModeChanged 调用，同步当前 PiP 状态给 UI 层。
     */
    fun onPipModeChanged(isInPipMode: Boolean) {
        _isPipMode.value = isInPipMode
        AppLog.i(TAG, if (isInPipMode) "已进入画中画" else "已退出画中画")
    }

    /**
     * Activity 销毁时清理状态，避免残留。
     */
    fun reset() {
        _isPipMode.value = false
    }
}
