package com.sytrack.repositories

import com.sytrack.db.Record
import com.sytrack.db.RecordDAO
import javax.inject.Inject

class MainRepository @Inject constructor(val recordDAO: RecordDAO) {

    suspend fun insertRecord(record: Record) = recordDAO.insertRecord(record)
    suspend fun deleteRecords() = recordDAO.deleteRecords()
    fun getRecords() = recordDAO.getRecords()
    fun getRecord(id: Int) = recordDAO.getRecord(id)

}