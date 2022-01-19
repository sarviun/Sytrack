package com.sytrack.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
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
import com.sytrack.utils.Constants.DEFAULT_BEARING_TOLERANCE
import com.sytrack.utils.Constants.DEFAULT_INTERVAL_POSITION_MAX_WAIT_MILLIS
import com.sytrack.utils.Constants.DEFAULT_INTERVAL_POSITION_UPDATE_MILLIS
import com.sytrack.utils.Constants.DEFAULT_INTERVAL_POSITION_UPDATE_FASTEST_MILLIS
import com.sytrack.utils.Constants.DEFAULT_SPEED_TOLERANCE_M_S
import com.sytrack.utils.Constants.NOTIFICATION_CHANNEL_ID
import com.sytrack.utils.Constants.NOTIFICATION_CHANNEL_NAME
import com.sytrack.utils.Constants.NOTIFICATION_ID
import com.sytrack.utils.Constants.UPDATE_INTERVAL_IN_MILLIS
import com.sytrack.utils.Constants.WALK_SPEED
import com.sytrack.utils.PermissionUtility
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecordingService : LifecycleService() {

    var lastSavedPosition: Location? = null

    companion object {
        val isRecordingOn = MutableLiveData<Boolean>()
        val recordedPoints = MutableLiveData<MutableList<RecordPosition>>()
        val currentPosition = MutableLiveData<RecordPosition>()
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

    private fun replaceLastPointInRecordingTrack(recordPosition: RecordPosition) {
        recordedPoints.value?.apply {
            if (this.isNotEmpty())
                removeLast()
            add(recordPosition)

            recordedPoints.postValue(this)
        }
    }

    private fun addPointToRecordingTrack(recordPosition: RecordPosition) {
        recordedPoints.value?.apply {
            add(recordPosition)
            recordedPoints.postValue(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(intervalInMillis: Long = DEFAULT_INTERVAL_POSITION_UPDATE_MILLIS) {

        if (PermissionUtility.hasLocationPermission(this)) {
            val request = LocationRequest.create().apply {
                interval = intervalInMillis
                fastestInterval = DEFAULT_INTERVAL_POSITION_UPDATE_FASTEST_MILLIS
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                maxWaitTime = DEFAULT_INTERVAL_POSITION_MAX_WAIT_MILLIS
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

                    if (isRecordingOn.value!!) {
                        saveOrReplacePositionIfNeeded(location)
                    }

                    sendPosition(location.toRecordPosition())

                }
            }
        }
    }

    private fun saveOrReplacePositionIfNeeded(location: Location) {
        val position = location.toRecordPosition()

        if (lastSavedPosition != null) {
            val twoLastPositionsDistance = lastSavedPosition!!.distanceTo(location)


            //NOT TESTED
            /*
            if (location.hasSimilarBearingTo(lastSavedPosition!!)
                && location.hasSimilarSpeedTo(lastSavedPosition!!)
            )
                return
            */

            if ((twoLastPositionsDistance < location.accuracy && location.speed < WALK_SPEED)) {
                if (location.accuracy < lastSavedPosition!!.accuracy) {
                    replaceLastPointInRecordingTrack(position)
                }
                return
            }

            addPointToRecordingTrack(position)
            lastSavedPosition = location

        } else {
            lastSavedPosition = location
            addPointToRecordingTrack(position)
        }
    }

    private fun sendPosition(recordPosition: RecordPosition) {
        currentPosition.postValue(recordPosition)
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
                    updateLocationTracking(
                        it.getLongExtra(
                            UPDATE_INTERVAL_IN_MILLIS,
                            DEFAULT_INTERVAL_POSITION_UPDATE_MILLIS
                        )
                    )
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

    private fun Location.toRecordPosition() =
        RecordPosition(
            latitude = this.latitude,
            longitude = this.longitude,
            accuracy = this.accuracy,
            time = this.time,
            provider = this.provider
        )

    private fun Location.hasSimilarBearingTo(location: Location): Boolean {

        val DEGREE_TOLERACE_FIRST = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.bearingAccuracyDegrees
        } else DEFAULT_BEARING_TOLERANCE

        val DEGREE_TOLERACE_SECOND = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.bearingAccuracyDegrees
        } else DEFAULT_BEARING_TOLERANCE

        val startFirst = this.bearing - DEGREE_TOLERACE_FIRST
        val endFirst = this.bearing + DEGREE_TOLERACE_FIRST

        val startSecond = location.bearing - DEGREE_TOLERACE_SECOND
        val endSecond = location.bearing + DEGREE_TOLERACE_SECOND

        return endFirst > startSecond || endSecond > startFirst
    }

    private fun Location.hasSimilarSpeedTo(location: Location): Boolean {
        val SPEED_TOLERACE_FIRST = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.speedAccuracyMetersPerSecond
        } else DEFAULT_SPEED_TOLERANCE_M_S

        val SPEED_TOLERACE_SECOND = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.speedAccuracyMetersPerSecond
        } else DEFAULT_SPEED_TOLERANCE_M_S

        val startFirst = this.speed - SPEED_TOLERACE_FIRST
        val endFirst = this.speed + SPEED_TOLERACE_FIRST

        val startSecond = location.speed - SPEED_TOLERACE_SECOND
        val endSecond = location.speed + SPEED_TOLERACE_SECOND

        return endFirst > startSecond || endSecond > startFirst
    }


}