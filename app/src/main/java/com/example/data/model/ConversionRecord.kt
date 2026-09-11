package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ConversionType {
    PDF_TO_IMAGE,
    IMAGE_TO_PDF
}

@Entity(tableName = "conversion_history")
data class ConversionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: ConversionType,
    val sourceName: String,
    val outputPath: String,
    val isDirectory: Boolean = false,
    val itemCount: Int,
    val fileSizeBytes: Long = 0,
    val thumbnailUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
