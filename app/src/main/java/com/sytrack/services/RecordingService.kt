package com.sytrack.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import com.sytrack.R
import com.sytrack.db.RecordPosition
import com.sytrack.ui.MainActivity
import com.sytrack.utils.Constants.ACTION_SHOW_RECORDING_FRAGMENT
import com.sytrack.utils.Constants.ACTION_START_FOREGROUND_SERVICE
import com.sytrack.utils.Constants.ACTION_STOP_FOREGROUND_SERVICE
import com.sytrack.utils.Constants.ACTION_UPDATE_INTERVAL
import com.sytrack.utils.Constants.INTERVAL_POSITION_MAX_WAIT
import com.sytrack.utils.Constants.INTERVAL_POSITION_UPDATE
import com.sytrack.utils.Constants.INTERVAL_POSITION_UPDATE_FASTEST
import com.sytrack.utils.Constants.NOTIFICATION_CHANNEL_ID
import com.sytrack.utils.Constants.NOTIFICATION_CHANNEL_NAME
import com.sytrack.utils.Constants.NOTIFICATION_ID
import com.sytrack.utils.PermissionUtility
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    companion object {
        val isRecordingOn = MutableLiveData<Boolean>()
        val recordedPoints = MutableLiveData<MutableList<RecordPosition>>()
    }

    private fun postInitialValues() {
        isRecordingOn.postValue(false)
        recordedPoints.postValue(mutableListOf())
    }

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        postInitialValues()
        fusedLocationProviderClient = FusedLocationProviderClient(this)

        updateLocationTracking()
    }

    private fun stopForegroundService() {
        isRecordingOn.postValue(false)
        stopForeground(true)
    }

    private fun addPointToRecordingTrack(location: Location?) {
        location?.let {
            val position = RecordPosition(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                time = location.time,
                provider = location.provider
            )
            recordedPoints.value?.apply {
                add(position)
                recordedPoints.postValue(this)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking() {

        val savedIntervalValueInSeconds = try {
            sharedPreferences.getString(getString(R.string.updates_interval_key), "")?.toLong()?.times(1000)
                ?: INTERVAL_POSITION_UPDATE
        } catch (ex: NumberFormatException) {
            INTERVAL_POSITION_UPDATE
        }

        if (PermissionUtility.hasLocationPermission(this)) {
            val request = LocationRequest.create().apply {
                interval = savedIntervalValueInSeconds
                fastestInterval = INTERVAL_POSITION_UPDATE_FASTEST
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                maxWaitTime = INTERVAL_POSITION_MAX_WAIT
            }
            fusedLocationProviderClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            locationResult.locations.let { locations ->
                for (location in locations) {
                    addPointToRecordingTrack(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_FOREGROUND_SERVICE -> {
                    startForegroundService()
                }
                ACTION_STOP_FOREGROUND_SERVICE -> {
                    stopForegroundService()
                }
                ACTION_UPDATE_INTERVAL -> {
                    fusedLocationProviderClient.removeLocationUpdates(locationCallback)
                    updateLocationTracking()
                }
                else -> {}
            }

        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    private fun startForegroundService() {

        isRecordingOn.postValue(true)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        startForeground(NOTIFICATION_ID, generateNotification())

    }

    private fun generateNotification(): Notification {

        // Notification Channel Id is ignored for Android pre O (26).
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setContentTitle(getString(R.string.track_recording))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(getMainActivityPendingIntent())
            .build()
    }

    private fun getMainActivityPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).also {
            it.action = ACTION_SHOW_RECORDING_FRAGMENT
        },
        FLAG_UPDATE_CURRENT
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}