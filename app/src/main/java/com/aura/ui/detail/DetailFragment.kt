package com.aura.ui.detail

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import com.aura.data.model.StreamEpisode
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aura.R
import com.aura.AuraApp
import com.aura.data.local.WatchlistEntity
import com.aura.data.model.ContentType
import com.aura.data.model.StreamLink
import com.aura.databinding.FragmentDetailBinding
import com.aura.ui.base.ViewModelFactory
import com.aura.ui.player.PlayerActivity
import com.aura.utils.*
import kotlinx.coroutines.launch

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DetailViewModel

    // Args read from bundle
    private var argDetailUrl: String = ""
    private var argContentType: String = "MOVIE"
    private var argTitle: String = ""
    private var argPosterUrl: String = ""
    private var argNetMirrorId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Read arguments
        arguments?.let { args ->
            argDetailUrl = args.getString("detailUrl", "")
            argContentType = args.getString("contentType", "MOVIE")
            argTitle = args.getString("title", "")
            argPosterUrl = args.getString("posterUrl", "")
            argNetMirrorId = args.getString("netMirrorId", "")
        }

        val app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[DetailViewModel::class.java]

        setupToolbar()
        loadDetails()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
    }

    private fun loadDetails() {
        val contentType = ContentType.valueOf(argContentType)
        // All movie/series content routes through TMDB+embed pipeline (NET_MIRROR path)
        val effectiveType = when (contentType) {
            ContentType.MOVIE -> ContentType.NET_MIRROR
            else -> contentType
        }
        // netMirrorId carries the composite ID: "tv:1396", "movie:603", or bare IMDb "tt..."
        // detailUrl also carries the same composite when coming from home screen
        val idToLoad = when {
            argNetMirrorId.isNotBlank() -> argNetMirrorId
            argDetailUrl.isNotBlank()   -> argDetailUrl
            else -> ""
        }
        viewModel.loadDetails(idToLoad, effectiveType, idToLoad)

        if (argPosterUrl.isNotBlank()) {
            binding.ivPosterBlur.loadImage(argPosterUrl)
            binding.ivPoster.loadImage(argPosterUrl)
        }
        if (argTitle.isNotBlank()) binding.tvDetailTitle.text = argTitle
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.detailState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        binding.progressBar.show(); binding.contentGroup.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.hide(); binding.contentGroup.show()
                        populateDetail(state.data)

                        // Check if we should auto-play
                        if (arguments?.getBoolean("autoPlay", false) == true) {
                            // Reset the autoPlay argument so it doesn't trigger again on configuration changes
                            arguments?.putBoolean("autoPlay", false)

                            val playId = when (val detail = state.data) {
                                is ContentDetail.MovieDetail -> argDetailUrl
                                is ContentDetail.AnimeDetail -> argDetailUrl
                                is ContentDetail.NetMirrorDetail -> detail.data.episodes.firstOrNull()?.id ?: argNetMirrorId
                            }
                            if (playId.startsWith("tv:") && playId.split(":").size < 4) {
                                showToast("No stream episodes available for this series")
                            } else {
                                viewModel.getStreamLinks(playId)
                            }
                        }
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.hide(); showToast(state.message)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.streamState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> binding.btnPlay.isEnabled = false
                    is NetworkResult.Success -> {
                        binding.btnPlay.isEnabled = true
                        if (state.data.isNotEmpty()) launchPlayer(state.data.first())
                        else showToast(getString(R.string.error_no_streams))
                    }
                    is NetworkResult.Error -> {
                        binding.btnPlay.isEnabled = true
                        if (state.message != "idle") showToast(state.message)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isInWatchlist.collect { inWatchlist ->
                binding.btnWatchlist.text = if (inWatchlist)
                    getString(R.string.detail_remove_watchlist) else getString(R.string.detail_add_watchlist)
                binding.btnWatchlist.setIconResource(
                    if (inWatchlist) R.drawable.ic_check else R.drawable.ic_add
                )
            }
        }
    }

    private fun populateDetail(detail: ContentDetail) {
        when (detail) {
            is ContentDetail.MovieDetail -> {
                val d = detail.data
                binding.ivPoster.loadImage(d.poster); binding.ivPosterBlur.loadImage(d.poster)
                binding.tvDetailTitle.text = d.title
                binding.tvDetailMeta.text = "${d.year} • ${d.language} • ${d.rating}"
                binding.tvDetailGenre.text = d.genre
                binding.tvDetailDescription.text = d.getDescription()
                binding.btnPlay.isEnabled = true
                setupWatchlistButton(d.poster, d.title, ContentType.MOVIE)
                setupPlayButton(argDetailUrl)
            }
            is ContentDetail.AnimeDetail -> {
                val d = detail.data
                binding.ivPoster.loadImage(d.getPoster()); binding.ivPosterBlur.loadImage(d.getPoster())
                binding.tvDetailTitle.text = d.title
                binding.tvDetailMeta.text = "${d.year} • ${d.type} • ${d.rating}"
                binding.tvDetailGenre.text = d.genre
                binding.tvDetailDescription.text = d.getDescription()
                binding.btnPlay.isEnabled = true
                setupWatchlistButton(d.getPoster(), d.title, ContentType.ANIME)
                setupPlayButton(argDetailUrl)
            }
            is ContentDetail.NetMirrorDetail -> {
                val d = detail.data
                binding.ivPoster.loadImage(d.getPoster()); binding.ivPosterBlur.loadImage(d.getPoster())
                binding.tvDetailTitle.text = d.title
                binding.tvDetailMeta.text = "${d.year} • ${d.rating}"
                binding.tvDetailGenre.text = d.genre
                binding.tvDetailDescription.text = d.getDescription()
                binding.btnPlay.isEnabled = true
                setupWatchlistButton(d.getPoster(), d.title, ContentType.NET_MIRROR)
                // Pass the first episode ID so getStreamLinks knows exactly what to play
                val firstEpId = d.episodes.firstOrNull()?.id ?: argNetMirrorId
                setupPlayButton(firstEpId)

                // TV episodes section
                val hasMultipleEpisodes = d.episodes.size > 1 || d.episodes.any { it.id.startsWith("tv:") }
                if (hasMultipleEpisodes) {
                    binding.episodesDivider.visibility = View.VISIBLE
                    binding.tvEpisodesTitle.visibility = View.VISIBLE
                    binding.seasonsScroll.visibility = View.VISIBLE

                    val episodesBySeason = d.episodes.groupBy {
                        val parts = it.id.split(":")
                        if (parts.size >= 4) parts[2].toIntOrNull() ?: 1 else 1
                    }
                    val sortedSeasons = episodesBySeason.keys.sorted()

                    binding.seasonsChipGroup.removeAllViews()
                    sortedSeasons.forEach { season ->
                        val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                            text = "Season $season"
                            isCheckable = true
                            id = season
                            setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) {
                                    showEpisodesForSeason(season, episodesBySeason[season] ?: emptyList())
                                }
                            }
                        }
                        binding.seasonsChipGroup.addView(chip)
                    }

                    // Auto-select first season
                    sortedSeasons.firstOrNull()?.let { firstSeason ->
                        binding.seasonsChipGroup.check(firstSeason)
                    }
                } else {
                    binding.episodesDivider.visibility = View.GONE
                    binding.tvEpisodesTitle.visibility = View.GONE
                    binding.seasonsScroll.visibility = View.GONE
                    binding.episodesList.removeAllViews()
                }
            }
        }
    }

    private fun setupPlayButton(url: String) {
        binding.btnPlay.setOnClickListener {
            if (url.startsWith("tv:") && url.split(":").size < 4) {
                showToast("No stream episodes available for this series")
            } else {
                viewModel.getStreamLinks(url)
            }
        }
    }

    private fun setupWatchlistButton(poster: String, title: String, type: ContentType) {
        binding.btnWatchlist.setOnClickListener {
            viewModel.toggleWatchlist(WatchlistEntity(
                id = argDetailUrl, title = title, posterUrl = poster,
                detailUrl = argDetailUrl, contentType = type, netMirrorId = argNetMirrorId
            ))
        }
    }

    private fun launchPlayer(link: StreamLink) {
        // Collect all available stream links for the source switcher
        val allLinks = viewModel.streamState.value.let {
            if (it is NetworkResult.Success) it.data else listOf(link)
        }
        startActivity(Intent(requireActivity(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_STREAM_URL, link.getUrl())
            putExtra(Constants.EXTRA_STREAM_IS_HLS, link.isHls())
            putExtra(Constants.EXTRA_TITLE, binding.tvDetailTitle.text.toString())
            putExtra(Constants.EXTRA_DETAIL_URL, argDetailUrl)
            putExtra("stream_type", link.type)
            putExtra("poster_url", argPosterUrl)
            putExtra("content_type", argContentType)
            // Pass all sources for the in-player source switcher
            putExtra("all_stream_urls", allLinks.map { it.getUrl() }.toTypedArray())
            putExtra("all_stream_names", allLinks.mapIndexed { index, _ -> "Stream ${index + 1}" }.toTypedArray())
        })
    }

    private fun showEpisodesForSeason(season: Int, episodes: List<StreamEpisode>) {
        binding.episodesList.removeAllViews()
        episodes.forEach { episode ->
            // Create a beautiful premium card or container for each episode
            val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { lp ->
                    lp.setMargins(0, 8, 0, 8)
                }
                cardElevation = 4f
                radius = 16f
                setCardBackgroundColor(android.graphics.Color.parseColor("#141414"))
                strokeColor = android.graphics.Color.parseColor("#33FFFFFF")
                strokeWidth = 2
                
                // Add a gorgeous custom ripple selection effect
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = requireContext().getDrawable(outValue.resourceId)
                
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(32, 32, 32, 32)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                
                // Premium Play Icon
                val icon = ImageView(requireContext()).apply {
                    setImageResource(R.drawable.ic_play)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.primary)
                    )
                    layoutParams = LinearLayout.LayoutParams(48, 48).also { lp ->
                        lp.marginEnd = 24
                    }
                }
                row.addView(icon)
                
                // Title and Episode details
                val textLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                
                val epNumText = TextView(requireContext()).apply {
                    text = "EPISODE ${episode.episode}"
                    textSize = 11f
                    setTextColor(requireContext().getColor(R.color.primary))
                    typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
                }
                textLayout.addView(epNumText)
                
                val cleanTitle = episode.title.substringAfter(" – ").substringAfter(" - ")
                val epTitleText = TextView(requireContext()).apply {
                    text = cleanTitle
                    textSize = 14f
                    setTextColor(android.graphics.Color.WHITE)
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { lp ->
                        lp.topMargin = 4
                    }
                }
                textLayout.addView(epTitleText)
                
                row.addView(textLayout)
                addView(row)
                
                setOnClickListener {
                    viewModel.getStreamLinks(episode.id)
                }
            }
            binding.episodesList.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

