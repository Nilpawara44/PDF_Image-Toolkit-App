package com.example.data.model

enum class ImageFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

enum class ImageResolution(val title: String, val subtitle: String, val scale: Float, val dpi: Int) {
    STANDARD("Standard (150 DPI)", "150 DPI • Balanced file size", 2.083f, 150),
    HIGH_QUALITY("High Quality (300 DPI)", "300 DPI • Crisp clarity (Recommended)", 4.167f, 300),
    ULTRA_HD("Ultra HD (400 DPI)", "400 DPI • Maximum detail & print grade", 5.556f, 400)
}

enum class PageRangeOption {
    ALL_PAGES,
    CUSTOM_RANGE,
    SELECTED_PAGES
}

data class PdfToImageConfig(
    val format: ImageFormat = ImageFormat.JPG,
    val resolution: ImageResolution = ImageResolution.HIGH_QUALITY,
    val rangeOption: PageRangeOption = PageRangeOption.ALL_PAGES,
    val startPage: Int = 1,
    val endPage: Int = 1,
    val selectedPages: Set<Int> = emptySet(),
    val saveToGallery: Boolean = true
)
