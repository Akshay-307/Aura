package com.aura.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aura.AuraApp
import com.aura.data.local.AppDatabase
import com.aura.domain.usecase.GetAnimeUseCase
import com.aura.domain.usecase.GetMoviesUseCase
import com.aura.domain.usecase.GetStreamLinksUseCase
import com.aura.domain.usecase.SearchUseCase
import com.aura.ui.anime.AnimeViewModel
import com.aura.ui.detail.DetailViewModel
import com.aura.ui.home.HomeViewModel
import com.aura.ui.home.SectionDetailViewModel
import com.aura.ui.iptv.IptvViewModel
import com.aura.ui.player.PlayerViewModel
import com.aura.ui.search.SearchViewModel
import com.aura.ui.settings.SettingsViewModel
import com.aura.ui.library.LibraryViewModel

class ViewModelFactory(private val app: AuraApp) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val movieRepo = app.movieRepository
        val animeRepo = app.animeRepository
        val streamRepo = app.streamRepository
        val db = app.database

        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
                streamRepo, app.dataStore
            ) as T

            modelClass.isAssignableFrom(SectionDetailViewModel::class.java) -> SectionDetailViewModel(
                streamRepo, app.dataStore
            ) as T

            modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(
                SearchUseCase(movieRepo, animeRepo, streamRepo),
                db.searchHistoryDao(),
                app.dataStore
            ) as T

            modelClass.isAssignableFrom(DetailViewModel::class.java) -> DetailViewModel(
                movieRepo, animeRepo, streamRepo,
                db.watchlistDao(), db.watchHistoryDao()
            ) as T

            modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(
                GetStreamLinksUseCase(movieRepo, animeRepo, streamRepo),
                db.watchHistoryDao()
            ) as T

            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(
                db.watchlistDao(), db.watchHistoryDao(), db.iptvFavouritesDao()
            ) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                app.dataStore, db.watchHistoryDao(), db.watchlistDao()
            ) as T

            modelClass.isAssignableFrom(AnimeViewModel::class.java) -> AnimeViewModel(
                GetAnimeUseCase(animeRepo),
                app.dataStore
            ) as T

            modelClass.isAssignableFrom(IptvViewModel::class.java) -> IptvViewModel(
                app.dataStore, db.iptvFavouritesDao()
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}

