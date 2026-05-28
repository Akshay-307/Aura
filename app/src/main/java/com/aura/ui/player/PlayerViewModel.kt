package com.aura.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.local.WatchHistoryDao
import com.aura.data.local.WatchHistoryEntity
import com.aura.data.model.ContentType
import com.aura.domain.usecase.GetStreamLinksUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val getStreamLinksUseCase: GetStreamLinksUseCase,
    private val watchHistoryDao: WatchHistoryDao
) : ViewModel() {

    private var savePositionJob: Job? = null

    suspend fun getSavedPosition(detailUrl: String): Long {
        return watchHistoryDao.getHistoryById(detailUrl)?.lastPosition ?: 0L
    }

    fun startSavingPosition(
        detailUrl: String,
        title: String,
        posterUrl: String,
        contentType: ContentType,
        getPosition: () -> Long,
        getDuration: () -> Long
    ) {
        savePositionJob?.cancel()
        savePositionJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                val position = getPosition()
                val duration = getDuration()
                if (position > 0) {
                    val existing = watchHistoryDao.getHistoryById(detailUrl)
                    if (existing != null) {
                        watchHistoryDao.updatePosition(detailUrl, position)
                    } else {
                        watchHistoryDao.insert(
                            WatchHistoryEntity(
                                id = detailUrl,
                                title = title,
                                posterUrl = posterUrl,
                                detailUrl = detailUrl,
                                contentType = contentType,
                                lastPosition = position,
                                duration = duration
                            )
                        )
                    }
                }
            }
        }
    }

    fun stopSavingPosition() {
        savePositionJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        savePositionJob?.cancel()
    }
}

