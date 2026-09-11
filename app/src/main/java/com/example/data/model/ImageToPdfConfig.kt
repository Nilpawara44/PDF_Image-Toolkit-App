package com.example.data.model

import android.net.Uri

enum class PageSizeOption(val title: String, val description: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 Format", "Standard international paper (595 × 842 pt)", 595, 842),
    LETTER("US Letter", "Standard American paper (612 × 792 pt)", 612, 792),
    FIT_TO_IMAGE("Fit to Image", "Adapts page dimensions to each image", 0, 0)
}

enum class CompressionLevel(val title: String, val description: String, val qualityPercent: Int, val maxDimension: Int) {
    HIGH_QUALITY("High Quality", "Minimal compression, maximum sharpness", 92, 2400),
    BALANCED("Balanced", "Recommended for sharing and readability", 78, 1800),
    COMPACT("Small File Size", "Aggressive compression for small emails", 55, 1200)
}

data class SelectedImageItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val rotationDegrees: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0
)

data class ImageToPdfConfig(
    val outputFileName: String = "Converted_Document",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val compression: CompressionLevel = CompressionLevel.BALANCED,
    val marginPt: Int = 20
)
