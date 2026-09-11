package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ConversionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversion_history ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<ConversionRecord>>

    @Query("SELECT * FROM conversion_history WHERE id = :id")
    suspend fun getRecordById(id: Long): ConversionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ConversionRecord): Long

    @Update
    suspend fun updateRecord(record: ConversionRecord)

    @Delete
    suspend fun deleteRecord(record: ConversionRecord)

    @Query("DELETE FROM conversion_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE conversion_history SET title = :newTitle WHERE id = :id")
    suspend fun renameRecord(id: Long, newTitle: String)
}
