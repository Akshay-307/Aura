package com.aura.ui.search

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.aura.R
import com.aura.AuraApp
import com.aura.data.local.SearchHistoryEntity
import com.aura.databinding.FragmentSearchBinding
import com.aura.ui.adapters.ContentCardAdapter
import com.aura.ui.base.ViewModelFactory
import com.aura.utils.NetworkResult
import com.aura.utils.hide
import com.aura.utils.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.aura.ui.base.OnTabReselectedListener

class SearchFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchViewModel

    override fun onTabReselected() {
        val query = binding.etSearch.text?.toString() ?: ""
        if (query.isNotBlank()) {
            binding.etSearch.setText("")
        } else {
            binding.nsvSearchLanding.smoothScrollTo(0, 0)
        }
    }
    private lateinit var resultsAdapter: ContentCardAdapter
    private lateinit var app: AuraApp

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[SearchViewModel::class.java]

        setupAdapter()
        setupSearch()
        setupTabs()
        setupTrendingKeywords()
        observeViewModel()

        // Read arguments to pre-select tab if navigating from "See All"
        val initialTab = arguments?.getInt("initial_tab", 0) ?: 0
        if (initialTab > 0 && initialTab < binding.tabLayout.tabCount) {
            binding.tabLayout.post {
                binding.tabLayout.getTabAt(initialTab)?.select()
            }
        }
    }

    private fun setupAdapter() {
        resultsAdapter = ContentCardAdapter(isGridLayout = true) { result ->
            val bundle = bundleOf(
                "detailUrl" to result.detailUrl,
                "contentType" to result.contentType.name,
                "title" to result.title,
                "posterUrl" to result.posterUrl,
                "netMirrorId" to result.netMirrorId
            )
            findNavController().navigate(R.id.action_search_to_detail, bundle)
        }

        binding.rvResults.apply {
            adapter = resultsAdapter
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.isBlank()) {
                    binding.nsvSearchLanding.show()
                    binding.rvResults.hide()
                    binding.emptyState.hide()
                    binding.hsvSuggestions.hide()
                } else {
                    binding.nsvSearchLanding.hide()
                }
                viewModel.search(query, getSelectedTab())
                viewModel.fetchSuggestions(query)
            }
        })

        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val query = binding.etSearch.text?.toString()?.trim() ?: ""
            if (query.isNotBlank()) {
                viewModel.saveSearchHistory(query)
            }
            false
        }

        binding.btnClearHistory.setOnClickListener {
            viewModel.clearHistory()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.search(binding.etSearch.text?.toString() ?: "", getSelectedTab())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun getSelectedTab(): SearchTab = when (binding.tabLayout.selectedTabPosition) {
        1 -> SearchTab.MOVIES
        2 -> SearchTab.ANIME
        else -> SearchTab.ALL
    }

    private fun setupTrendingKeywords() {
        val trendingList = listOf(
            "Bhool Bhulaiyaa 3",
            "Pushpa 2",
            "Singham Again",
            "Demon Slayer",
            "Solo Leveling"
        )
        binding.chipGroupTrending.removeAllViews()
        trendingList.forEach { keyword ->
            val chip = Chip(requireContext()).apply {
                text = keyword
                isCheckable = false
                isClickable = true
                setTextColor(Color.WHITE)
                chipBackgroundColor = ColorStateList.valueOf(resources.getColor(R.color.surface, null))
                chipStrokeColor = ColorStateList.valueOf(resources.getColor(R.color.primary, null))
                chipStrokeWidth = 1.2f * resources.displayMetrics.density
                
                setOnClickListener {
                    binding.etSearch.setText(keyword)
                    binding.etSearch.setSelection(keyword.length)
                    viewModel.saveSearchHistory(keyword)
                }
            }
            binding.chipGroupTrending.addView(chip)
        }
    }

    private fun observeViewModel() {
        // Observe Search Results
        lifecycleScope.launch {
            viewModel.searchState.collect { state ->
                when (state) {
                    NetworkResult.Loading -> {
                        binding.progressBar.show()
                        binding.rvResults.hide()
                        binding.emptyState.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.hide()
                        val currentQuery = binding.etSearch.text?.toString() ?: ""
                        if (currentQuery.isBlank()) {
                            binding.nsvSearchLanding.show()
                            binding.rvResults.hide()
                            binding.emptyState.hide()
                        } else if (state.data.isEmpty()) {
                            binding.rvResults.hide()
                            binding.emptyState.show()
                        } else {
                            binding.emptyState.hide()
                            binding.rvResults.show()
                            resultsAdapter.submitList(state.data)
                        }
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.hide()
                        if (state.message != "idle") {
                            binding.rvResults.hide()
                        }
                    }
                }
            }
        }

        // Observe Recent Searches (History)
        viewModel.searchHistory.observe(viewLifecycleOwner) { history ->
            if (history.isNullOrEmpty()) {
                binding.rlRecentSearchesHeader.hide()
                binding.hsvRecentSearches.hide()
            } else {
                binding.rlRecentSearchesHeader.show()
                binding.hsvRecentSearches.show()
                binding.chipGroupHistory.removeAllViews()

                history.take(8).forEach { entity ->
                    val chip = Chip(requireContext()).apply {
                        text = entity.query
                        isCheckable = false
                        isCloseIconVisible = true
                        setTextColor(Color.WHITE)
                        chipBackgroundColor = ColorStateList.valueOf(resources.getColor(R.color.surface, null))
                        chipStrokeColor = ColorStateList.valueOf(resources.getColor(R.color.glass_stroke, null))
                        chipStrokeWidth = 1f * resources.displayMetrics.density
                        closeIconTint = ColorStateList.valueOf(resources.getColor(R.color.text_secondary, null))

                        setOnCloseIconClickListener {
                            lifecycleScope.launch(Dispatchers.IO) {
                                app.database.searchHistoryDao().delete(entity.query)
                            }
                        }

                        setOnClickListener {
                            binding.etSearch.setText(entity.query)
                            binding.etSearch.setSelection(entity.query.length)
                            viewModel.saveSearchHistory(entity.query)
                        }
                    }
                    binding.chipGroupHistory.addView(chip)
                }
            }
        }

        // Observe Search Suggestions
        lifecycleScope.launch {
            viewModel.suggestionsState.collect { suggestions ->
                val currentQuery = binding.etSearch.text?.toString() ?: ""
                if (suggestions.isEmpty() || currentQuery.isBlank()) {
                    binding.hsvSuggestions.visibility = View.GONE
                } else {
                    binding.hsvSuggestions.visibility = View.VISIBLE
                    binding.llSuggestionsContainer.removeAllViews()
                    suggestions.forEach { suggestion ->
                        val chip = android.widget.TextView(requireContext()).apply {
                            text = suggestion
                            setTextColor(Color.WHITE)
                            textSize = 13f
                            val paddingH = (14 * resources.displayMetrics.density).toInt()
                            val paddingV = (8 * resources.displayMetrics.density).toInt()
                            setPadding(paddingH, paddingV, paddingH, paddingV)
                            
                            val bg = android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = 18 * resources.displayMetrics.density
                                setColor(Color.parseColor("#1AFFFFFF"))
                                setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#33FFFFFF"))
                            }
                            background = bg
                            
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            ).also { lp ->
                                val margin = (4 * resources.displayMetrics.density).toInt()
                                lp.setMargins(margin, 0, margin, 0)
                            }
                            
                            setOnClickListener {
                                binding.etSearch.setText(suggestion)
                                binding.etSearch.setSelection(suggestion.length)
                                viewModel.saveSearchHistory(suggestion)
                                binding.hsvSuggestions.visibility = View.GONE
                                viewModel.fetchSuggestions("")
                            }
                        }
                        binding.llSuggestionsContainer.addView(chip)
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

