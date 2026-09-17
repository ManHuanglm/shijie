package com.shiping.app.di

import com.shiping.app.data.local.AppDatabase
import com.shiping.app.data.local.PreferencesManager
import com.shiping.app.data.remote.RetrofitClient
import com.shiping.app.data.repository.ApiSourceRepository
import com.shiping.app.data.repository.DownloadRepository
import com.shiping.app.data.repository.FavoriteRepository
import com.shiping.app.data.repository.ParseSourceRepository
import com.shiping.app.data.repository.PlayHistoryRepository
import com.shiping.app.data.repository.TvRepository
import com.shiping.app.data.repository.TvBoxConfigImporter
import com.shiping.app.data.repository.VodRepository
import com.shiping.app.ShipingApp

/**
 * 简易依赖注入容器
 */
object AppContainer {

    private val apiSourceDao by lazy { AppDatabase.getInstance().apiSourceDao() }
    private val parseSourceDao by lazy { AppDatabase.getInstance().parseSourceDao() }
    private val downloadDao by lazy { AppDatabase.getInstance().downloadDao() }
    private val playHistoryDao by lazy { AppDatabase.getInstance().playHistoryDao() }
    private val tvSourceDao by lazy { AppDatabase.getInstance().tvSourceDao() }
    private val favoriteDao by lazy { AppDatabase.getInstance().favoriteDao() }

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

    val downloadRepository by lazy {
        DownloadRepository(ShipingApp.context, downloadDao)
    }

    val playHistoryRepository by lazy {
        PlayHistoryRepository(playHistoryDao)
    }

    val favoriteRepository by lazy {
        FavoriteRepository(favoriteDao)
    }

    val tvRepository by lazy {
        TvRepository(tvSourceDao)
    }

    val tvBoxConfigImporter by lazy {
        TvBoxConfigImporter(apiSourceRepository, parseSourceRepository, tvRepository)
    }

    val preferences get() = preferencesManager
}
