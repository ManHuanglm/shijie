package com.shiping.app

import android.app.Application
import android.content.Context
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShipingApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        // 初始化默认 API 源和解析源
        appScope.launch {
            AppContainer.apiSourceRepository.initDefaultSource()
            AppContainer.parseSourceRepository.initDefaultSource()
        }
    }

    companion object {
        lateinit var context: Context
            private set
    }
}
