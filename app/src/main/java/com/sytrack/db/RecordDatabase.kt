package com.sytrack.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [(Record::class)],
    version = 1
)
@TypeConverters(Converters::class)
abstract class RecordDatabase: RoomDatabase() {
    abstract fun getRecordDAO(): RecordDAO
}