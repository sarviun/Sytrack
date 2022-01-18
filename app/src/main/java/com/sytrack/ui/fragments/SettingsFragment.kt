package com.sytrack.ui.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.sytrack.R
import com.sytrack.services.RecordingService
import com.sytrack.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)


        val intervalPreference: EditTextPreference? = findPreference("interval_GPS")
        intervalPreference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, key: String?) {
        if (key.equals(getString(R.string.updates_interval_key)))
            sendCommandToService(Constants.ACTION_UPDATE_INTERVAL)
    }

    private fun sendCommandToService(action: String) {
        Intent(requireContext(), RecordingService::class.java).also {
            it.action = action
            when (action) {
                Constants.ACTION_UPDATE_INTERVAL -> {
                    val savedIntervalValueInMillis = try {
                        sharedPreferences.getString(getString(R.string.updates_interval_key), "")?.toLong()?.times(1000)
                            ?: Constants.DEFAULT_INTERVAL_POSITION_UPDATE_MILLIS
                    } catch (ex: NumberFormatException) {
                        Constants.DEFAULT_INTERVAL_POSITION_UPDATE_MILLIS
                    }
                    it.putExtra(Constants.UPDATE_INTERVAL_IN_MILLIS, savedIntervalValueInMillis)
                }
            }

            requireContext().startService(it)
        }
    }
}