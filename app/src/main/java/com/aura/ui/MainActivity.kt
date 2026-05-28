package com.aura.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aura.R
import com.aura.databinding.ActivityMainBinding
import com.aura.utils.show
import com.aura.utils.hide
import com.aura.utils.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check for updates on startup
        lifecycleScope.launch {
            val updateInfo = UpdateChecker.checkForUpdates(this@MainActivity)
            if (updateInfo.isUpdateAvailable) {
                UpdateChecker.showUpdateDialog(this@MainActivity, updateInfo)
            }
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        binding.bottomNavigation.setOnItemReselectedListener { item ->
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val activeFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
            if (activeFragment is com.aura.ui.base.OnTabReselectedListener) {
                activeFragment.onTabReselected()
            }
        }

        // Hide bottom nav on detail and settings screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.detailFragment, R.id.settingsFragment -> {
                    binding.bottomNavigation.hide()
                    binding.navDivider.hide()
                }
                else -> {
                    binding.bottomNavigation.show()
                    binding.navDivider.show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}

