package com.shiping.app.ui.screen.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.DownloadEntity
import com.shiping.app.data.repository.DownloadRepository
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 缓存管理页面 ViewModel。
 */
class DownloadViewModel(
    private val repository: DownloadRepository = AppContainer.downloadRepository,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = repository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalBytes: StateFlow<Long> = repository.totalBytes

    fun pause(contentId: String) {
        viewModelScope.launch { repository.pauseDownload(contentId) }
    }

    fun resume(contentId: String) {
        viewModelScope.launch { repository.resumeDownload(contentId) }
    }

    fun remove(contentId: String) {
        viewModelScope.launch { repository.removeDownload(contentId) }
    }

    fun removeAll(contentIds: List<String>) {
        viewModelScope.launch { repository.removeDownloads(contentIds) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.removeAll() }
    }

    fun retry(contentId: String) {
        viewModelScope.launch { repository.retryDownload(contentId) }
    }
}
