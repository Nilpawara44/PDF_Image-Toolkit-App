package com.example.data.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.example.data.model.ImageFormat
import com.example.data.model.PageRangeOption
import com.example.data.model.PdfToImageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat

object PdfToImageConverter {

    data class PdfMetadata(
        val totalPages: Int,
        val pageDimensions: List<Pair<Int, Int>>
    )

    data class ConversionResult(
        val outputDirectory: File,
        val generatedFiles: List<File>,
        val firstImageFile: File,
        val totalPagesConverted: Int,
        val totalBytes: Long
    )

    suspend fun inspectPdf(context: Context, uri: Uri): Result<PdfMetadata> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempFile: File? = null
        try {
            // First try opening direct PFD
            pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                // If direct opening fails, copy to cache
                tempFile = FileUtils.copyUriToTempFile(context, uri, "inspect_temp.pdf")
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            if (pfd == null) {
                return@withContext Result.failure(IllegalStateException("Could not open PDF file"))
            }

            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val dimensions = mutableListOf<Pair<Int, Int>>()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                dimensions.add(Pair(page.width, page.height))
                page.close()
            }

            Result.success(PdfMetadata(pageCount, dimensions))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Password-protected PDF files cannot be processed."))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Failed to read PDF: ${e.localizedMessage ?: "Corrupted file"}"))
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            tempFile?.delete()
        }
    }

    suspend fun renderPageThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 200
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempFile: File? = null
        try {
            pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: Exception) {
                tempFile = FileUtils.copyUriToTempFile(context, uri, "thumb_temp_${System.currentTimeMillis()}.pdf")
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            if (pfd == null) return@withContext null
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            val page = renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            tempFile?.delete()
        }
    }

    suspend fun convertPdfToImages(
        context: Context,
        pdfUri: Uri,
        originalPdfName: String,
        config: PdfToImageConfig,
        onProgress: (current: Int, total: Int, currentLabel: String) -> Unit
    ): Result<ConversionResult> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempFile: File? = null

        val cleanBaseName = originalPdfName
            .substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifEmpty { "PDF_Export" }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val outputDir = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "Conversions/${cleanBaseName}_$timestamp"
        )
        if (!outputDir.exists()) outputDir.mkdirs()

        val generatedFiles = mutableListOf<File>()
        var totalBytes = 0L

        try {
            pfd = try {
                context.contentResolver.openFileDescriptor(pdfUri, "r")
            } catch (e: Exception) {
                tempFile = FileUtils.copyUriToTempFile(context, pdfUri, "convert_temp.pdf")
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            if (pfd == null) {
                return@withContext Result.failure(IllegalStateException("Could not open PDF file descriptor"))
            }

            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            if (totalPages == 0) {
                return@withContext Result.failure(IllegalStateException("PDF document has 0 pages"))
            }

            // Determine page indices to convert (0-based)
            val pageIndices: List<Int> = when (config.rangeOption) {
                PageRangeOption.ALL_PAGES -> (0 until totalPages).toList()
                PageRangeOption.CUSTOM_RANGE -> {
                    val start = (config.startPage - 1).coerceIn(0, totalPages - 1)
                    val end = (config.endPage - 1).coerceIn(start, totalPages - 1)
                    (start..end).toList()
                }
                PageRangeOption.SELECTED_PAGES -> {
                    config.selectedPages.filter { it in 0 until totalPages }.sorted()
                }
            }.ifEmpty { (0 until totalPages).toList() }

            val totalToProcess = pageIndices.size

            for ((stepIndex, pageIndex) in pageIndices.withIndex()) {
                ensureActive()
                val pageNumber = pageIndex + 1
                onProgress(stepIndex + 1, totalToProcess, "Rendering page $pageNumber of $totalPages...")

                val page = renderer.openPage(pageIndex)

                // High fidelity scaling based on configured DPI scale
                val scale = config.resolution.scale
                var destWidth = (page.width * scale).toInt()
                var destHeight = (page.height * scale).toInt()

                // Safe upper cap to prevent pathological out-of-memory while preserving full 300/400 DPI fidelity
                val maxDimension = 5000
                if (destWidth > maxDimension || destHeight > maxDimension) {
                    val factor = maxDimension.toFloat() / maxOf(destWidth, destHeight)
                    destWidth = (destWidth * factor).toInt()
                    destHeight = (destHeight * factor).toInt()
                }

                val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                // Render in PRINT mode for maximum sharpness, anti-aliased text, and crisp vector curves
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val extension = config.format.extension
                val fileName = "${cleanBaseName}_page_${String.format(java.util.Locale.US, "%03d", pageNumber)}.$extension"
                val targetFile = File(outputDir, fileName)

                val compressFormat = if (config.format == ImageFormat.JPG) {
                    Bitmap.CompressFormat.JPEG
                } else {
                    Bitmap.CompressFormat.PNG
                }
                // High-fidelity compression: 98% for near-lossless JPEG, 100% for PNG
                val quality = if (config.format == ImageFormat.JPG) 98 else 100

                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(compressFormat, quality, out)
                }

                totalBytes += targetFile.length()
                generatedFiles.add(targetFile)

                // Save to Gallery if enabled
                if (config.saveToGallery) {
                    saveToDeviceGallery(context, targetFile, fileName, config.format.mimeType)
                }

                // Immediately recycle to prevent memory bloat
                bitmap.recycle()
            }

            if (generatedFiles.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No pages were extracted"))
            }

            Result.success(
                ConversionResult(
                    outputDirectory = outputDir,
                    generatedFiles = generatedFiles,
                    firstImageFile = generatedFiles.first(),
                    totalPagesConverted = generatedFiles.size,
                    totalBytes = totalBytes
                )
            )
        } catch (e: Exception) {
            // Clean up files if failed
            outputDir.deleteRecursively()
            Result.failure(e)
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            tempFile?.delete()
        }
    }

    private fun saveToDeviceGallery(
        context: Context,
        imageFile: File,
        displayName: String,
        mimeType: String
    ) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PDF-Image Toolkit")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    imageFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            // Non-fatal if gallery insert fails
        }
    }
}
