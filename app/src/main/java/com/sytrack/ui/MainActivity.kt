package com.sytrack.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.sytrack.R
import com.sytrack.databinding.ActivityMainBinding
import com.sytrack.utils.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navHostFragment: NavHostFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Sytrack)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setSupportActionBar(binding.topAppBar)

        navHostFragment =
            supportFragmentManager
                .findFragmentById(R.id.navHostFragment) as NavHostFragment

        binding.bottomNavigation.setupWithNavController(navHostFragment.navController)

        navHostFragment.findNavController().addOnDestinationChangedListener{
            _, destination, _ ->
            when (destination.id) {
                R.id.settingsFragment -> binding.bottomNavigation.visibility = GONE
                else -> binding.bottomNavigation.visibility = VISIBLE
            }
        }

        navigateToRecordFragment(intent)
    }

    private fun navigateToRecordFragment(intent: Intent?) {
        if (intent?.action == Constants.ACTION_SHOW_RECORDING_FRAGMENT) {
            navHostFragment.findNavController().navigate(R.id.action_global_recording_fragment)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        navigateToRecordFragment(intent)
    }
}