package com.example.data.local

import com.example.data.model.ConversionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ConversionRepository(private val conversionDao: ConversionDao) {

    val allRecords: Flow<List<ConversionRecord>> = conversionDao.getAllRecords()

    suspend fun insertRecord(record: ConversionRecord): Long = withContext(Dispatchers.IO) {
        conversionDao.insertRecord(record)
    }

    suspend fun deleteRecord(record: ConversionRecord) = withContext(Dispatchers.IO) {
        // Clean up physical file or directory
        try {
            val file = File(record.outputPath)
            if (file.exists()) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
        } catch (ignored: Exception) {
            // Ignore file deletion errors
        }
        conversionDao.deleteRecord(record)
    }

    suspend fun renameRecord(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        conversionDao.renameRecord(id, newTitle)
    }
}
