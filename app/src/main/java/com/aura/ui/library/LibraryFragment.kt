package com.aura.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.aura.R
import com.aura.AuraApp
import com.aura.data.model.SearchResult
import com.aura.databinding.FragmentLibraryBinding
import com.aura.ui.adapters.ContentCardAdapter
import com.aura.ui.adapters.WatchHistoryAdapter
import com.aura.ui.base.OnTabReselectedListener
import com.aura.ui.base.ViewModelFactory
import com.aura.utils.hide
import com.aura.utils.show

import android.content.Intent
import com.aura.ui.adapters.IptvFavouriteHorizontalAdapter
import com.aura.ui.player.PlayerActivity
import com.aura.utils.Constants

class LibraryFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LibraryViewModel

    private lateinit var watchlistAdapter: ContentCardAdapter
    private lateinit var historyAdapter: WatchHistoryAdapter
    private lateinit var iptvAdapter: IptvFavouriteHorizontalAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[LibraryViewModel::class.java]

        setupAdapters()
        observeViewModel()
    }

    private fun navigateToDetail(detailUrl: String, contentType: String, title: String, posterUrl: String, netMirrorId: String) {
        val bundle = bundleOf(
            "detailUrl" to detailUrl, "contentType" to contentType,
            "title" to title, "posterUrl" to posterUrl, "netMirrorId" to netMirrorId
        )
        findNavController().navigate(R.id.action_library_to_detail, bundle)
    }

    private fun playIptvChannel(channel: com.aura.data.local.IptvFavouriteEntity) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_STREAM_URL, channel.streamUrl)
            putExtra(Constants.EXTRA_STREAM_IS_HLS, channel.streamUrl.contains(".m3u8", ignoreCase = true))
            putExtra(Constants.EXTRA_TITLE, channel.name)
            putExtra("stream_type", "direct")
        }
        startActivity(intent)
    }

    private fun setupAdapters() {
        watchlistAdapter = ContentCardAdapter { result ->
            navigateToDetail(result.detailUrl, result.contentType.name, result.title, result.posterUrl, result.netMirrorId)
        }
        binding.rvWatchlist.apply {
            adapter = watchlistAdapter
            layoutManager = GridLayoutManager(requireContext(), 3)
        }

        historyAdapter = WatchHistoryAdapter { entity ->
            navigateToDetail(entity.detailUrl, entity.contentType.name, entity.title, entity.posterUrl, entity.netMirrorId)
        }
        binding.rvHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        iptvAdapter = IptvFavouriteHorizontalAdapter(
            onClick = { channel -> playIptvChannel(channel) },
            onFavouriteToggle = { entity -> viewModel.removeFromIptvFavourites(entity.id) }
        )
        binding.rvIptvFavourites.apply {
            adapter = iptvAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun observeViewModel() {
        viewModel.watchlist.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) { binding.emptyState.show(); binding.rvWatchlist.hide() }
            else {
                binding.emptyState.hide(); binding.rvWatchlist.show()
                watchlistAdapter.submitList(list.map { e ->
                    SearchResult(id = e.id, title = e.title, posterUrl = e.posterUrl,
                        year = e.year, genre = e.genre, detailUrl = e.detailUrl,
                        contentType = e.contentType, netMirrorId = e.netMirrorId)
                })
            }
        }

        viewModel.watchHistory.observe(viewLifecycleOwner) { history ->
            if (history.isEmpty()) binding.sectionHistory.hide()
            else { binding.sectionHistory.show(); historyAdapter.submitList(history) }
        }

        viewModel.iptvFavourites.observe(viewLifecycleOwner) { favourites ->
            if (favourites.isEmpty()) {
                binding.sectionIptvFavourites.hide()
            } else {
                binding.sectionIptvFavourites.show()
                iptvAdapter.submitList(favourites)
            }
        }
    }

    override fun onTabReselected() {
        binding.nsvLibrary.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

