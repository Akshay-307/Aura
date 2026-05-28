package com.aura.ui.iptv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.aura.R
import com.aura.AuraApp
import com.aura.databinding.FragmentIptvBinding
import com.aura.ui.base.ViewModelFactory
import com.aura.ui.player.PlayerActivity
import com.aura.utils.Constants
import com.aura.utils.NetworkResult
import com.aura.utils.hide
import com.aura.utils.show
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

import com.aura.ui.base.OnTabReselectedListener

class IptvFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentIptvBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: IptvViewModel
    private lateinit var adapter: IptvAdapter

    private var selectedCategory = "ALL"

    override fun onTabReselected() {
        val query = binding.etSearch.text?.toString() ?: ""
        if (query.isNotBlank()) {
            binding.etSearch.setText("")
        } else {
            binding.rvChannels.smoothScrollToPosition(0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIptvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[IptvViewModel::class.java]

        setupRecyclerView()
        setupSearch()
        observeViewModel()

        viewModel.loadChannels()
    }

    private fun setupRecyclerView() {
        adapter = IptvAdapter(
            onClick = { channel -> playInApp(channel) },
            onFavouriteToggle = { channel -> viewModel.toggleFavourite(channel) },
            isFavourite = { id -> viewModel.isFavourite(id) }
        )

        binding.rvChannels.apply {
            adapter = this@IptvFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), 3)
            setHasFixedSize(true)
        }
    }

    private fun playInApp(channel: com.aura.data.model.IptvChannel) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_STREAM_URL, channel.streamUrl)
            putExtra(Constants.EXTRA_STREAM_IS_HLS, channel.streamUrl.contains(".m3u8", ignoreCase = true))
            putExtra(Constants.EXTRA_TITLE, channel.name)
            putExtra("stream_type", "direct")
        }
        startActivity(intent)
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString() ?: "")
            }
        })
    }

    private fun observeViewModel() {
        // Observe Channels State
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.channelsState.collect { state ->
                when (state) {
                    is NetworkResult.Loading -> {
                        binding.progressBar.show()
                        binding.rvChannels.hide()
                        binding.emptyState.hide()
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.hide()
                        if (state.data.isEmpty()) {
                            binding.rvChannels.hide()
                            binding.emptyState.show()
                        } else {
                            binding.emptyState.hide()
                            binding.rvChannels.show()
                            adapter.submitList(state.data)
                        }
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.hide()
                        if (state.message != "idle") {
                            binding.rvChannels.hide()
                            binding.emptyState.show()
                            binding.tvEmptyTitle.text = "Error Loading Streams"
                            binding.tvEmptySubtitle.text = state.message
                        }
                    }
                }
            }
        }

        // Observe Categories
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { categories ->
                if (categories.isNotEmpty()) {
                    binding.chipGroupCategories.removeAllViews()
                    categories.forEach { category ->
                        val chip = Chip(requireContext()).apply {
                            text = category
                            isCheckable = true
                            isChecked = (category == selectedCategory)
                            
                            // Style the chips beautifully to match the neon dark theme
                            setTextColor(
                                android.content.res.ColorStateList.valueOf(
                                    if (category == selectedCategory) {
                                        android.graphics.Color.WHITE
                                    } else {
                                        resources.getColor(R.color.text_secondary, null)
                                    }
                                )
                            )
                            chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                                if (category == selectedCategory) {
                                    resources.getColor(R.color.primary, null)
                                } else {
                                    resources.getColor(R.color.surface, null)
                                }
                            )
                            chipStrokeColor = android.content.res.ColorStateList.valueOf(
                                if (category == selectedCategory) {
                                    resources.getColor(R.color.primary, null)
                                } else {
                                    resources.getColor(R.color.glass_stroke, null)
                                }
                            )
                            chipStrokeWidth = 1f * resources.displayMetrics.density

                            setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) {
                                    selectedCategory = category
                                    viewModel.setCategory(category)
                                    // Refresh categories view to update background colors for select state
                                    observeCategoriesRefresh(categories)
                                }
                            }
                        }
                        binding.chipGroupCategories.addView(chip)
                    }
                }
            }
        }
        // Observe favourite IDs â€” refresh adapter when stars change
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favouriteIds.collect {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun observeCategoriesRefresh(categories: List<String>) {
        for (i in 0 until binding.chipGroupCategories.childCount) {
            val chip = binding.chipGroupCategories.getChildAt(i) as? Chip ?: continue
            val category = categories.getOrNull(i) ?: continue
            val isCurrent = category == selectedCategory
            
            chip.isChecked = isCurrent
            chip.setTextColor(
                android.content.res.ColorStateList.valueOf(
                    if (isCurrent) android.graphics.Color.WHITE else resources.getColor(R.color.text_secondary, null)
                )
            )
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                if (isCurrent) resources.getColor(R.color.primary, null) else resources.getColor(R.color.surface, null)
            )
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                if (isCurrent) resources.getColor(R.color.primary, null) else resources.getColor(R.color.glass_stroke, null)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

