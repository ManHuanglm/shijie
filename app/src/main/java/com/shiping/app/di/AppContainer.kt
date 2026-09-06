package com.shiping.app.di

import com.shiping.app.data.local.AppDatabase
import com.shiping.app.data.local.PreferencesManager
import com.shiping.app.data.remote.RetrofitClient
import com.shiping.app.data.repository.ApiSourceRepository
import com.shiping.app.data.repository.ParseSourceRepository
import com.shiping.app.data.repository.VodRepository
import com.shiping.app.ShipingApp

/**
 * 简易依赖注入容器
 */
object AppContainer {

    private val apiSourceDao by lazy { AppDatabase.getInstance().apiSourceDao() }
    private val parseSourceDao by lazy { AppDatabase.getInstance().parseSourceDao() }

    private val preferencesManager by lazy { PreferencesManager(ShipingApp.context) }

    val apiSourceRepository by lazy {
        ApiSourceRepository(apiSourceDao, RetrofitClient.apiService, preferencesManager)
    }

    val parseSourceRepository by lazy {
        ParseSourceRepository(parseSourceDao, preferencesManager)
    }

    val vodRepository by lazy {
        VodRepository(RetrofitClient.apiService, apiSourceRepository)
    }

    val preferences get() = preferencesManager
}
