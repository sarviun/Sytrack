package com.sytrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: Record)

    @Query("DELETE FROM record_table")
    suspend fun deleteRecords()

    @Query("SELECT * FROM record_table ORDER BY timeStamp DESC")
    fun getRecords(): Flow<List<Record>>

    @Query("SELECT * FROM record_table WHERE id = :id")
    fun getRecord (id: Int): Record

}