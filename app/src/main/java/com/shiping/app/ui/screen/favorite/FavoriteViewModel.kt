package com.shiping.app.ui.screen.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiping.app.data.model.FavoriteEntity
import com.shiping.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteUiState(
    val favorites: List<FavoriteEntity> = emptyList(),
    val isLoading: Boolean = true,
)

class FavoriteViewModel : ViewModel() {

    private val favoriteRepository = AppContainer.favoriteRepository

    private val _uiState = MutableStateFlow(FavoriteUiState())
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteRepository.favoritesFlow.collect { favorites ->
                _uiState.update { it.copy(favorites = favorites, isLoading = false) }
            }
        }
    }

    /** 取消收藏 */
    fun remove(vodId: Int, sourceUrl: String) {
        viewModelScope.launch {
            favoriteRepository.delete(vodId, sourceUrl)
        }
    }
}
