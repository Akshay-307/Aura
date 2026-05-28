package com.aura.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aura.R
import com.aura.AuraApp
import com.aura.data.model.ContentType
import com.aura.data.model.NetMirrorPost
import com.aura.data.model.SearchResult
import com.aura.databinding.FragmentHomeBinding
import com.aura.ui.adapters.ContentCardAdapter
import com.aura.ui.adapters.HeroBannerAdapter
import com.aura.ui.base.ViewModelFactory
import com.aura.utils.NetworkResult
import com.aura.utils.hide
import com.aura.utils.show
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.aura.ui.base.OnTabReselectedListener

class HomeFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel
    private lateinit var heroBannerAdapter: HeroBannerAdapter
    private lateinit var latestMoviesAdapter: ContentCardAdapter
    private lateinit var latestSeriesAdapter: ContentCardAdapter
    private lateinit var latestAnimeAdapter: ContentCardAdapter
    private lateinit var trendingAdapter: ContentCardAdapter

    private var bannerAutoScrollJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[HomeViewModel::class.java]

        setupAdapters()
        setupRecyclerViews()
        setupHeroBanner()
        observeViewModel()

        binding.btnSettingsShortcut.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.btnSearchShortcut.setOnClickListener {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
            bottomNav?.selectedItemId = R.id.searchFragment
        }

        binding.btnSeeAllMovies.setOnClickListener {
            val bundle = bundleOf(
                "sectionType" to "latest_movies",
                "sectionTitle" to getString(R.string.home_latest_movies)
            )
            findNavController().navigate(R.id.action_home_to_sectionDetail, bundle)
        }

        binding.btnSeeAllSeries.setOnClickListener {
            val bundle = bundleOf(
                "sectionType" to "popular_series",
                "sectionTitle" to "Popular Series"
            )
            findNavController().navigate(R.id.action_home_to_sectionDetail, bundle)
        }

        binding.btnSeeAllAnime.setOnClickListener {
            val bundle = bundleOf(
                "sectionType" to "latest_anime",
                "sectionTitle" to getString(R.string.home_latest_anime)
            )
            findNavController().navigate(R.id.action_home_to_sectionDetail, bundle)
        }

        binding.btnSeeAllTrending.setOnClickListener {
            val bundle = bundleOf(
                "sectionType" to "trending",
                "sectionTitle" to getString(R.string.home_trending)
            )
            findNavController().navigate(R.id.action_home_to_sectionDetail, bundle)
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAll() }
        binding.swipeRefresh.setColorSchemeColors(resources.getColor(R.color.primary, null))
    }

    override fun onTabReselected() {
        binding.nsvHome.smoothScrollTo(0, 0)
    }

    private fun NetMirrorPost.toSearchResult(): SearchResult {
        return SearchResult(
            id          = id,
            title       = title,
            posterUrl   = getPoster(),
            year        = year,
            genre       = genre,
            detailUrl   = url,
            contentType = ContentType.NET_MIRROR,
            netMirrorId = id
        )
    }

    private fun navigateToDetail(result: SearchResult, autoPlay: Boolean = false) {
        val bundle = bundleOf(
            "detailUrl"   to result.detailUrl,
            "contentType" to ContentType.NET_MIRROR.name,
            "title"       to result.title,
            "posterUrl"   to result.posterUrl,
            "netMirrorId" to result.netMirrorId,
            "autoPlay"    to autoPlay
        )
        findNavController().navigate(R.id.action_home_to_detail, bundle)
    }

    private fun setupAdapters() {
        heroBannerAdapter    = HeroBannerAdapter(
            onPlayClick = { navigateToDetail(it, autoPlay = true) },
            onInfoClick = { navigateToDetail(it, autoPlay = false) }
        )
        latestMoviesAdapter  = ContentCardAdapter(isGridLayout = false) { navigateToDetail(it) }
        latestSeriesAdapter  = ContentCardAdapter(isGridLayout = false) { navigateToDetail(it) }
        latestAnimeAdapter   = ContentCardAdapter(isGridLayout = false) { navigateToDetail(it) }
        trendingAdapter      = ContentCardAdapter(isGridLayout = false) { navigateToDetail(it) }
    }

    private fun setupHeroBanner() {
        binding.viewPagerHero.adapter = heroBannerAdapter
        binding.viewPagerHero.offscreenPageLimit = 3
        com.google.android.material.tabs.TabLayoutMediator(
            binding.dotsIndicator, binding.viewPagerHero
        ) { _, _ -> }.attach()
        startAutoScroll()
    }

    private fun setupRecyclerViews() {
        binding.rvMovies.apply {
            adapter = latestMoviesAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
        binding.rvSeries.apply {
            adapter = latestSeriesAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
        binding.rvAnime.apply {
            adapter = latestAnimeAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
        binding.rvTrending.apply {
            adapter = trendingAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }
    }

    private fun startAutoScroll() {
        bannerAutoScrollJob?.cancel()
        bannerAutoScrollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(4000)
                val count = heroBannerAdapter.itemCount
                if (count > 0) {
                    binding.viewPagerHero.currentItem =
                        (binding.viewPagerHero.currentItem + 1) % count
                }
            }
        }
    }

    private fun observeViewModel() {
        // Latest Movies
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.latestMoviesState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        binding.shimmerMovies.startShimmer(); binding.shimmerMovies.show()
                        binding.rvMovies.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.shimmerMovies.stopShimmer(); binding.shimmerMovies.hide()
                        binding.rvMovies.show()
                        binding.swipeRefresh.isRefreshing = false
                        latestMoviesAdapter.submitList(state.data.map { it.toSearchResult() })
                        updateHeroBanner()
                    }
                    is NetworkResult.Error -> {
                        binding.shimmerMovies.stopShimmer(); binding.shimmerMovies.hide()
                        binding.swipeRefresh.isRefreshing = false
                    }
                }
            }
        }

        // Latest Series
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.latestSeriesState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        binding.shimmerSeries.startShimmer(); binding.shimmerSeries.show()
                        binding.rvSeries.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.shimmerSeries.stopShimmer(); binding.shimmerSeries.hide()
                        binding.rvSeries.show()
                        latestSeriesAdapter.submitList(state.data.map { it.toSearchResult() })
                    }
                    is NetworkResult.Error -> {
                        binding.shimmerSeries.stopShimmer(); binding.shimmerSeries.hide()
                    }
                }
            }
        }

        // Latest Anime
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.latestAnimeState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        binding.shimmerAnime.startShimmer(); binding.shimmerAnime.show()
                        binding.rvAnime.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.shimmerAnime.stopShimmer(); binding.shimmerAnime.hide()
                        binding.rvAnime.show()
                        latestAnimeAdapter.submitList(state.data.map { it.toSearchResult() })
                        updateHeroBanner()
                    }
                    is NetworkResult.Error -> {
                        binding.shimmerAnime.stopShimmer(); binding.shimmerAnime.hide()
                    }
                }
            }
        }

        // Trending
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.trendingState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        binding.shimmerTrending.startShimmer(); binding.shimmerTrending.show()
                        binding.rvTrending.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.shimmerTrending.stopShimmer(); binding.shimmerTrending.hide()
                        binding.rvTrending.show()
                        trendingAdapter.submitList(state.data.map { it.toSearchResult() })
                    }
                    is NetworkResult.Error -> {
                        binding.shimmerTrending.stopShimmer(); binding.shimmerTrending.hide()
                    }
                }
            }
        }
    }

    private fun updateHeroBanner() {
        val movies  = (viewModel.latestMoviesState.value as? NetworkResult.Success)?.data
        val anime   = (viewModel.latestAnimeState.value as? NetworkResult.Success)?.data
        val heroItems = mutableListOf<SearchResult>()
        movies?.take(3)?.forEach { heroItems.add(it.toSearchResult()) }
        anime?.take(2)?.forEach  { heroItems.add(it.toSearchResult()) }
        if (heroItems.isNotEmpty()) {
            heroBannerAdapter.submitList(heroItems)
            binding.viewPagerHero.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bannerAutoScrollJob?.cancel()
        _binding = null
    }
}

