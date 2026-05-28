package com.aura.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aura.BuildConfig
import com.aura.R
import com.aura.AuraApp
import com.aura.databinding.FragmentSettingsBinding
import com.aura.ui.base.ViewModelFactory
import com.aura.utils.Constants
import com.aura.utils.showToast
import kotlinx.coroutines.launch

import com.aura.utils.UpdateChecker

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as AuraApp
        viewModel = ViewModelProvider(this, ViewModelFactory(app))[SettingsViewModel::class.java]

        val appVersionName = UpdateChecker.getAppVersionName(requireContext())
        binding.tvVersion.text = "Version $appVersionName"

        // Check for updates click listener
        binding.btnCheckUpdates.setOnClickListener {
            binding.btnCheckUpdates.isEnabled = false
            lifecycleScope.launch {
                val updateInfo = UpdateChecker.checkForUpdates(requireContext())
                binding.btnCheckUpdates.isEnabled = true
                if (updateInfo.error != null) {
                    showToast("Error checking updates: ${updateInfo.error}")
                } else if (updateInfo.isUpdateAvailable) {
                    UpdateChecker.showUpdateDialog(requireContext(), updateInfo)
                } else {
                    showToast("Your app is up to date!")
                }
            }
        }

        // Hide the API key section completely â€” no longer needed
        binding.tvApiHeader.visibility = View.GONE
        binding.cardApi.visibility = View.GONE

        // Back button
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Collect default quality flow to display initial quality selection
        lifecycleScope.launch {
            viewModel.defaultQuality.collect { quality ->
                binding.tvCurrentQuality.text = quality
            }
        }

        // Collect adult content preference
        lifecycleScope.launch {
            viewModel.adultContentEnabled.collect { enabled ->
                binding.switchAdultContent.isChecked = enabled
            }
        }



        // Adult content toggle with confirmation
        binding.switchAdultContent.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Show confirmation dialog
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.settings_adult_confirm_title))
                    .setMessage(getString(R.string.settings_adult_confirm_msg))
                    .setPositiveButton("Enable") { _, _ ->
                        viewModel.toggleAdultContent(true)
                        restartApp()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchAdultContent.isChecked = false
                    }
                    .setOnCancelListener {
                        binding.switchAdultContent.isChecked = false
                    }
                    .show()
            } else {
                viewModel.toggleAdultContent(false)
                restartApp()
            }
        }

        binding.btnClearHistory.setOnClickListener {
            viewModel.clearWatchHistory()
            showToast(getString(R.string.settings_clear_history_success))
        }

        binding.btnClearWatchlist.setOnClickListener {
            viewModel.clearWatchlist()
            showToast(getString(R.string.settings_clear_watchlist_success))
        }

        binding.btnQuality.setOnClickListener {
            val options = Constants.QUALITY_OPTIONS.toTypedArray()
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_quality))
                .setItems(options) { _, which ->
                    viewModel.saveDefaultQuality(options[which])
                }
                .show()
        }

        // Custom IPTV URL
        lifecycleScope.launch {
            viewModel.customIptvUrl.collect { url ->
                if (binding.etCustomIptvUrl.text.toString() != url) {
                    binding.etCustomIptvUrl.setText(url)
                }
            }
        }

        binding.btnSaveIptvUrl.setOnClickListener {
            val url = binding.etCustomIptvUrl.text?.toString()?.trim() ?: ""
            viewModel.saveCustomIptvUrl(url)
            showToast(if (url.isBlank()) "Custom playlist removed" else "Playlist URL saved")
        }
    }

    private fun restartApp() {
        val intent = requireActivity().packageManager
            .getLaunchIntentForPackage(requireActivity().packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        requireActivity().finishAffinity()
        if (intent != null) startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

