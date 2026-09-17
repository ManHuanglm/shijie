package com.shiping.app

import android.app.Application
import android.content.Context
import com.shiping.app.di.AppContainer
import com.shiping.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShipingApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        AppLog.init(this)
        // 初始化默认 API 源、解析源和电视直播源
        appScope.launch {
            runCatching { AppContainer.apiSourceRepository.initDefaultSource() }
            runCatching { AppContainer.parseSourceRepository.initDefaultSource() }
            runCatching { AppContainer.tvRepository.initDefaultSource() }
        }
    }

    companion object {
        lateinit var context: Context
            private set
    }
}
