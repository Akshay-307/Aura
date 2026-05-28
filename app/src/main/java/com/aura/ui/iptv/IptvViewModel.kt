package com.aura.ui.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.data.datastore.AppDataStore
import com.aura.data.local.IptvFavouriteEntity
import com.aura.data.local.IptvFavouritesDao
import com.aura.data.model.IptvChannel
import com.aura.utils.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

/**
 * Manages loading, caching, filtering, and favouriting for live IPTV streams.
 */
class IptvViewModel(
    private val dataStore: AppDataStore,
    private val iptvFavouritesDao: IptvFavouritesDao
) : ViewModel() {

    private val inPlaylistUrl = "https://iptv-org.github.io/iptv/countries/in.m3u"
    private val sportsPlaylistUrl = "https://iptv-org.github.io/iptv/categories/sports.m3u"
    private val hindiPlaylistUrl = "https://iptv-org.github.io/iptv/languages/hin.m3u"
    private val entertainmentPlaylistUrl = "https://iptv-org.github.io/iptv/categories/entertainment.m3u"
    private val newsPlaylistUrl = "https://iptv-org.github.io/iptv/categories/news.m3u"

    private val _channelsState = MutableStateFlow<NetworkResult<List<IptvChannel>>>(NetworkResult.Error("idle"))
    val channelsState: StateFlow<NetworkResult<List<IptvChannel>>> = _channelsState

    private val _categories = MutableStateFlow<List<String>>(listOf("ALL", "⭐ Favourites"))
    val categories: StateFlow<List<String>> = _categories

    // Tracks which channel IDs are favourited (for live badge display)
    private val _favouriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favouriteIds: StateFlow<Set<String>> = _favouriteIds

    // Cache the full parsed list in memory for instant local filtering
    private var cachedChannels = emptyList<IptvChannel>()

    private var currentSearchQuery = ""
    private var currentCategory = "ALL"

    init {
        // Load persisted favourite IDs from Room on startup
        viewModelScope.launch {
            val favs = iptvFavouritesDao.getAllFavouritesSync()
            _favouriteIds.value = favs.map { it.id }.toSet()
        }
    }

    fun loadChannels(forceRefresh: Boolean = false) {
        if (!forceRefresh && cachedChannels.isNotEmpty()) {
            applyFilters()
            return
        }

        _channelsState.value = NetworkResult.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inDeferred = async { IptvParser.fetchAndParse(inPlaylistUrl) }
                val sportsDeferred = async { IptvParser.fetchAndParse(sportsPlaylistUrl) }
                val hindiDeferred = async { IptvParser.fetchAndParse(hindiPlaylistUrl) }
                val entertainmentDeferred = async { IptvParser.fetchAndParse(entertainmentPlaylistUrl) }
                val newsDeferred = async { IptvParser.fetchAndParse(newsPlaylistUrl) }

                val inChannels = try { inDeferred.await() } catch (e: Exception) { emptyList() }
                val sportsChannels = try { sportsDeferred.await() } catch (e: Exception) { emptyList() }
                val hindiChannels = try { hindiDeferred.await() } catch (e: Exception) { emptyList() }
                val entertainmentChannels = try { entertainmentDeferred.await() } catch (e: Exception) { emptyList() }
                val newsChannels = try { newsDeferred.await() } catch (e: Exception) { emptyList() }

                // Fetch custom URL if set
                val customUrl = dataStore.customIptvUrl.first()
                val customChannels = if (customUrl.isNotBlank()) {
                    try { IptvParser.fetchAndParse(customUrl) } catch (e: Exception) { emptyList() }
                } else {
                    emptyList()
                }

                val mappedSportsChannels = sportsChannels.map { channel ->
                    if (channel.category.equals("General", ignoreCase = true) || channel.category.isBlank()) {
                        channel.copy(category = "Sports")
                    } else {
                        channel
                    }
                }

                val mappedEntertainmentChannels = entertainmentChannels.map { channel ->
                    if (channel.category.isBlank()) {
                        channel.copy(category = "Entertainment")
                    } else {
                        channel
                    }
                }

                val mappedNewsChannels = newsChannels.map { channel ->
                    if (channel.category.isBlank()) {
                        channel.copy(category = "News")
                    } else {
                        channel
                    }
                }

                val mappedCustomChannels = customChannels.map { channel ->
                    if (channel.category.equals("General", ignoreCase = true) || channel.category.isBlank()) {
                        channel.copy(category = "Custom")
                    } else {
                        channel
                    }
                }

                val publicMerged = (inChannels + mappedSportsChannels + hindiChannels + mappedEntertainmentChannels + mappedNewsChannels).distinctBy { it.streamUrl }

                // Filter out DRM-protected channels from public playlists (they never work)
                val publicFiltered = publicMerged.filter { channel ->
                    val nameLower = channel.name.lowercase()
                    DRM_BLOCKED_PATTERNS.none { pattern -> nameLower.contains(pattern) }
                }

                // Custom channels bypass DRM filter (user's own subscription proxy)
                val merged = (publicFiltered + mappedCustomChannels).distinctBy { it.streamUrl }

                if (merged.isNotEmpty()) {
                    cachedChannels = merged

                    // Extract unique sorted categories
                    val uniqCats = merged.map { it.category }
                        .distinct()
                        .sorted()
                        .toMutableList()

                    // Add special tabs at the beginning
                    uniqCats.add(0, "ALL")
                    uniqCats.add(1, "⭐ Favourites")
                    _categories.value = uniqCats

                    applyFilters()
                } else {
                    _channelsState.value = NetworkResult.Error("No channels found in playlist.")
                }
            } catch (e: Exception) {
                _channelsState.value = NetworkResult.Error(e.message ?: "Failed to retrieve live streams.")
            }
        }
    }

    fun setCategory(category: String) {
        currentCategory = category
        applyFilters()
    }

    fun search(query: String) {
        currentSearchQuery = query
        applyFilters()
    }

    fun toggleFavourite(channel: IptvChannel) {
        viewModelScope.launch {
            val isFav = iptvFavouritesDao.isFavourite(channel.id)
            if (isFav) {
                iptvFavouritesDao.deleteById(channel.id)
                _favouriteIds.value = _favouriteIds.value - channel.id
            } else {
                iptvFavouritesDao.insert(
                    IptvFavouriteEntity(
                        id = channel.id,
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        streamUrl = channel.streamUrl,
                        category = channel.category
                    )
                )
                _favouriteIds.value = _favouriteIds.value + channel.id
            }
            // Refresh list if we're viewing favourites
            if (currentCategory == "⭐ Favourites") applyFilters()
        }
    }

    fun isFavourite(channelId: String): Boolean = _favouriteIds.value.contains(channelId)

    private fun applyFilters() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseList = if (currentCategory == "⭐ Favourites") {
                val favs = iptvFavouritesDao.getAllFavouritesSync()
                favs.map { entity ->
                    IptvChannel(
                        id = entity.id,
                        name = entity.name,
                        logoUrl = entity.logoUrl,
                        streamUrl = entity.streamUrl,
                        category = entity.category
                    )
                }
            } else {
                if (cachedChannels.isEmpty()) {
                    if (_channelsState.value !is NetworkResult.Loading) {
                        _channelsState.value = NetworkResult.Error("No channels loaded")
                    }
                    return@launch
                }
                cachedChannels
            }

            var filtered = baseList

            // 1. Filter by category if not viewing favourites and not ALL
            if (currentCategory != "⭐ Favourites" && currentCategory != "ALL") {
                filtered = filtered.filter {
                    it.category.equals(currentCategory, ignoreCase = true)
                }
            }

            // 2. Filter by search query
            if (currentSearchQuery.isNotBlank()) {
                filtered = filtered.filter {
                    it.name.contains(currentSearchQuery, ignoreCase = true) ||
                    it.category.contains(currentSearchQuery, ignoreCase = true)
                }
            }

            _channelsState.value = NetworkResult.Success(filtered)
        }
    }

    companion object {
        // DRM-protected networks â€” streams from these never work in standard players
        private val DRM_BLOCKED_PATTERNS = listOf(
            "zee ", "zee5", "zeetv", "zee tv", "zee cinema", "zee news",
            "zing", "zee anmol", "zee bangla", "zee marathi", "zee kannada",
            "zee telugu", "zee tamil", "zee keralam", "zee sarthak",
            "star plus", "star bharat", "star sports", "star gold",
            "star movies", "star pravah", "star maa", "star vijay",
            "star jalsha", "star suvarna", "star utsav",
            "hotstar", "disney channel india",
            "sony max", "sony sab", "sony pix", "sony ten",
            "set max", "sony liv", "sony pal", "sony aath",
            "sony marathi", "sony yay",
            "colors tv", "colors rishtey", "colors cineplex",
            "colors bangla", "colors marathi", "colors kannada",
            "colors gujarati", "colors tamil",
            "sun tv", "sun news", "sun music", "sun life",
            "sun bangla", "udaya tv", "gemini tv", "surya tv",
            "jio cinema", "jiocinema",
            "voot",
            "&tv", "andtv",
            "mtv india", "nick india", "nickelodeon india",
            "vh1 india", "comedy central india",
            "discovery india", "animal planet india", "tlc india",
            "history tv18"
        )
    }
}

