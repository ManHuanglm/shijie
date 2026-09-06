package com.shiping.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.shiping.app.di.AppContainer
import com.shiping.app.ui.navigation.AppNavigation
import com.shiping.app.ui.theme.ShipingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
}
