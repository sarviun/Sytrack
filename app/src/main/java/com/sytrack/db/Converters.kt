package com.sytrack.db

import androidx.room.TypeConverter
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class Converters {

    @TypeConverter
    fun fromList(value : List<RecordPosition>) = Json.encodeToString(serializer(),value)

    @TypeConverter
    fun toList(value: String) = Json.decodeFromString<List<RecordPosition>>(serializer(), value)
}