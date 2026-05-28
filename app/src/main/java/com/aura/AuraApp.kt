package com.aura

import android.app.Application
import com.aura.data.datastore.AppDataStore
import com.aura.data.local.AppDatabase
import com.aura.data.remote.RemoteConfigManager
import com.aura.data.repository.AnimeRepository
import com.aura.data.repository.MovieRepository
import com.aura.data.repository.StreamRepository
import com.aura.data.scraper.AnimeSaltScraper
import com.aura.data.scraper.KMMoviesScraper
import com.aura.data.scraper.NetMirrorScraper
import com.aura.data.scraper.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AuraApp : Application() {

    lateinit var dataStore: AppDataStore
    lateinit var database: AppDatabase
    lateinit var movieRepository: MovieRepository
    lateinit var animeRepository: AnimeRepository
    lateinit var streamRepository: StreamRepository

    // Scrapers (singleton instances â€” no API key required)
    private val kmMoviesScraper by lazy { KMMoviesScraper() }
    private val animeSaltScraper by lazy { AnimeSaltScraper() }
    private val netMirrorScraper by lazy { NetMirrorScraper() }
    private val providerManager by lazy { ProviderManager() }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        dataStore = AppDataStore(this)
        database = AppDatabase.getInstance(this)

        // Initialize remote config without blocking the main thread
        GlobalScope.launch(Dispatchers.IO) {
            RemoteConfigManager.init()
        }

        // Repositories are always ready â€” no API key needed
        movieRepository = MovieRepository(kmMoviesScraper, providerManager)
        animeRepository = AnimeRepository(animeSaltScraper, providerManager, netMirrorScraper)
        streamRepository = StreamRepository(netMirrorScraper, providerManager)
    }

    companion object {
        @Volatile
        private var INSTANCE: AuraApp? = null

        fun getInstance(): AuraApp = INSTANCE!!
    }
}

