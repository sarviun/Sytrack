package com.sytrack.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "record_table")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    val timeStamp: Long,
    val getTrackPositions: List<RecordPosition>
)
