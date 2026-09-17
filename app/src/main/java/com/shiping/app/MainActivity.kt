package com.shiping.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.shiping.app.data.player.PipController
import com.shiping.app.di.AppContainer
import com.shiping.app.ui.navigation.AppNavigation
import com.shiping.app.ui.navigation.PlayerArgsHolder
import com.shiping.app.ui.theme.ShipingTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PLAY_URL = "extra_play_url"
        const val EXTRA_PLAY_TITLE = "extra_play_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 处理下载完成通知跳转：将 URL/标题放入 PlayerArgsHolder
        intent?.getStringExtra(EXTRA_PLAY_URL)?.let { url ->
            val title = intent.getStringExtra(EXTRA_PLAY_TITLE) ?: "已下载视频"
            PlayerArgsHolder.setPendingPlay(url, title)
        }

        setContent {
            val themeMode by AppContainer.preferences.themeMode.collectAsState(initial = 0)
            val bgType by AppContainer.preferences.backgroundType.collectAsState(initial = 0)
            val customBgUri by AppContainer.preferences.customBgUri.collectAsState(initial = "")
            ShipingTheme(
                darkTheme = themeMode == 0,
                backgroundType = bgType,
                customBgUri = customBgUri
            ) {
                AppNavigation()
            }
        }
    }

    /**
     * 用户按 Home 键或切换到其他 App 时触发。
     * 若设置开启画中画且当前在播放页且视频正在播放，则自动进入 PiP 小窗。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipController.tryEnterPip(this)
    }

    /**
     * PiP 模式切换回调：通知 UI 层隐藏底部导航栏/控制层。
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.onPipModeChanged(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        PipController.reset()
    }
}
