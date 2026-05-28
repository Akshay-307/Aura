package com.aura.ui.anime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.aura.R
import com.aura.AuraApp
import com.aura.data.model.ContentType
import com.aura.data.model.SearchResult
import com.aura.databinding.FragmentAnimeBinding
import com.aura.ui.adapters.ContentCardAdapter
import com.aura.ui.base.ViewModelFactory
import com.aura.utils.NetworkResult
import com.aura.utils.hide
import com.aura.utils.show
import kotlinx.coroutines.launch

import com.aura.ui.base.OnTabReselectedListener

class AnimeFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentAnimeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AnimeViewModel
    private lateinit var adapter: ContentCardAdapter

    override fun onTabReselected() {
        binding.rvAnime.smoothScrollToPosition(0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[AnimeViewModel::class.java]

        adapter = ContentCardAdapter(isGridLayout = true) { result ->
            val bundle = bundleOf(
                "detailUrl" to result.detailUrl, 
                "contentType" to ContentType.NET_MIRROR.name,
                "title" to result.title, 
                "posterUrl" to result.posterUrl, 
                "netMirrorId" to result.detailUrl
            )
            findNavController().navigate(R.id.action_anime_to_detail, bundle)
        }
        binding.rvAnime.apply {
            this.adapter = this@AnimeFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), 3)
        }

        lifecycleScope.launch {
            viewModel.animeState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        binding.progressBar.show()
                        binding.rvAnime.hide()
                        binding.emptyState.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.hide()
                        if (state.data.isEmpty()) {
                            binding.emptyState.show()
                            binding.rvAnime.hide()
                        } else {
                            binding.emptyState.hide()
                            binding.rvAnime.show()
                            adapter.submitList(state.data.map { a ->
                                SearchResult(id = a.url, title = a.title, posterUrl = a.getPoster(),
                                    year = a.year, rating = a.rating, genre = a.genre,
                                    detailUrl = a.url, contentType = ContentType.ANIME)
                            })
                        }
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.hide()
                        binding.emptyState.show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

