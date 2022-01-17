package com.sytrack.utils

import android.content.Context
import android.os.Build
import com.mapbox.android.core.permissions.PermissionsManager

object PermissionUtility {

    fun hasLocationPermission(context: Context) =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            PermissionsManager.areLocationPermissionsGranted(context)
        } else {
            PermissionsManager.areLocationPermissionsGranted(context)
                    && PermissionsManager.isBackgroundLocationPermissionGranted(context)
        }

    //FU Google https://stackoverflow.com/q/66677217
    fun getStandardPermission() =
                android.Manifest.permission.ACCESS_FINE_LOCATION

    fun getExtraPermission() =
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION


}