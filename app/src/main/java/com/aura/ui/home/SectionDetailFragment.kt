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
import androidx.recyclerview.widget.GridLayoutManager
import com.aura.R
import com.aura.AuraApp
import com.aura.data.model.ContentType
import com.aura.data.model.SearchResult
import com.aura.databinding.FragmentSectionDetailBinding
import com.aura.ui.adapters.ContentCardAdapter
import com.aura.ui.base.ViewModelFactory
import com.aura.utils.NetworkResult
import com.aura.utils.hide
import com.aura.utils.show
import kotlinx.coroutines.launch

class SectionDetailFragment : Fragment() {

    private var _binding: FragmentSectionDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SectionDetailViewModel
    private lateinit var resultsAdapter: ContentCardAdapter

    private var sectionType: String = ""
    private var sectionTitle: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSectionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { args ->
            sectionType = args.getString("sectionType", "")
            sectionTitle = args.getString("sectionTitle", "")
        }

        binding.tvTitle.text = sectionTitle
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        val app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[SectionDetailViewModel::class.java]

        setupRecyclerView()
        observeViewModel()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSection(sectionType)
        }
        binding.swipeRefresh.setColorSchemeColors(resources.getColor(R.color.primary, null))

        viewModel.loadSection(sectionType)
    }

    private fun setupRecyclerView() {
        resultsAdapter = ContentCardAdapter(isGridLayout = true) { result ->
            val bundle = bundleOf(
                "detailUrl" to result.detailUrl,
                "contentType" to ContentType.NET_MIRROR.name,
                "title" to result.title,
                "posterUrl" to result.posterUrl,
                "netMirrorId" to result.netMirrorId
            )
            findNavController().navigate(R.id.action_sectionDetail_to_detail, bundle)
        }

        binding.rvItems.apply {
            adapter = resultsAdapter
            layoutManager = GridLayoutManager(requireContext(), 2)
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.itemsState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        if (!binding.swipeRefresh.isRefreshing) {
                            binding.progressBar.show()
                        }
                        binding.rvItems.hide()
                        binding.emptyState.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.hide()
                        binding.swipeRefresh.isRefreshing = false
                        
                        if (state.data.isEmpty()) {
                            binding.rvItems.hide()
                            binding.emptyState.show()
                            binding.tvEmptyTitle.text = "No content available"
                            binding.tvEmptySubtitle.text = "Try refreshing or check again later."
                        } else {
                            binding.emptyState.hide()
                            binding.rvItems.show()
                            
                            val searchResults = state.data.map { post ->
                                SearchResult(
                                    id = post.id,
                                    title = post.title,
                                    posterUrl = post.getPoster(),
                                    year = post.year,
                                    genre = post.genre,
                                    detailUrl = post.url,
                                    contentType = ContentType.NET_MIRROR,
                                    netMirrorId = post.id
                                )
                            }
                            resultsAdapter.submitList(searchResults)
                        }
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.hide()
                        binding.swipeRefresh.isRefreshing = false
                        binding.rvItems.hide()
                        binding.emptyState.show()
                        binding.tvEmptyTitle.text = "Failed to load content"
                        binding.tvEmptySubtitle.text = state.message
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

