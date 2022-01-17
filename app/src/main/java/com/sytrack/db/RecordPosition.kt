package com.sytrack.db

data class RecordPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val provider: String?,
    val time: Long?
)

