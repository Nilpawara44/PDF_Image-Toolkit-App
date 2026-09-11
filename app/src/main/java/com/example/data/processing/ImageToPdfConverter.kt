package com.example.data.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.data.model.CompressionLevel
import com.example.data.model.ImageToPdfConfig
import com.example.data.model.PageSizeOption
import com.example.data.model.SelectedImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageToPdfConverter {

    data class ConversionResult(
        val outputFile: File,
        val totalPages: Int,
        val totalBytes: Long
    )

    suspend fun convertImagesToPdf(
        context: Context,
        images: List<SelectedImageItem>,
        config: ImageToPdfConfig,
        onProgress: (current: Int, total: Int, currentLabel: String) -> Unit
    ): Result<ConversionResult> = withContext(Dispatchers.IO) {
        if (images.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No images selected for conversion"))
        }

        val cleanName = config.outputFileName.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifEmpty { "Converted_Document" }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "Documents"
        )
        if (!outputDir.exists()) outputDir.mkdirs()

        val targetPdfFile = File(outputDir, "${cleanName}_$timestamp.pdf")
        val pdfDocument = PdfDocument()

        try {
            val total = images.size
            for ((index, item) in images.withIndex()) {
                ensureActive()
                val pageNumber = index + 1
                onProgress(pageNumber, total, "Processing image $pageNumber of $total...")

                // 1. Decode bitmap with memory-safe downsampling
                val decodedBitmap = decodeSampledBitmapFromUri(
                    context = context,
                    item = item,
                    maxDimension = config.compression.maxDimension
                ) ?: continue

                // 2. Compress bitmap in-memory according to compression level
                val compressedBitmap = compressBitmapQuality(
                    source = decodedBitmap,
                    qualityPercent = config.compression.qualityPercent
                )
                if (compressedBitmap != decodedBitmap) {
                    decodedBitmap.recycle()
                }

                // 3. Determine PDF page dimensions
                val margin = config.marginPt
                val (pageWidth, pageHeight, drawRect) = calculatePageLayout(
                    bitmap = compressedBitmap,
                    pageSizeOption = config.pageSize,
                    margin = margin
                )

                // 4. Create and draw PDF page
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Fill clean white background
                canvas.drawColor(Color.WHITE)

                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(compressedBitmap, null, drawRect, paint)

                pdfDocument.finishPage(page)

                // Recycle intermediate bitmap
                compressedBitmap.recycle()
            }

            // Save PDF to file
            onProgress(total, total, "Writing PDF file to storage...")
            FileOutputStream(targetPdfFile).use { out ->
                pdfDocument.writeTo(out)
            }

            val fileSize = targetPdfFile.length()
            Result.success(
                ConversionResult(
                    outputFile = targetPdfFile,
                    totalPages = total,
                    totalBytes = fileSize
                )
            )
        } catch (e: Exception) {
            targetPdfFile.delete()
            Result.failure(e)
        } finally {
            try {
                pdfDocument.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun decodeSampledBitmapFromUri(
        context: Context,
        item: SelectedImageItem,
        maxDimension: Int
    ): Bitmap? {
        return try {
            // First decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inWidth = options.outWidth
            var inHeight = options.outHeight

            if (inWidth <= 0 || inHeight <= 0) return null

            // If rotation swaps width/height
            if (item.rotationDegrees % 180 != 0) {
                val temp = inWidth
                inWidth = inHeight
                inHeight = temp
            }

            var inSampleSize = 1
            if (inHeight > maxDimension || inWidth > maxDimension) {
                val halfHeight = inHeight / 2
                val halfWidth = inWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = context.contentResolver.openInputStream(item.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            if (item.rotationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(item.rotationDegrees.toFloat())
                }
                val rotated = Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    matrix,
                    true
                )
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun compressBitmapQuality(source: Bitmap, qualityPercent: Int): Bitmap {
        if (qualityPercent >= 95) return source
        return try {
            val bytes = ByteArrayOutputStream()
            source.compress(Bitmap.CompressFormat.JPEG, qualityPercent, bytes)
            val byteArray = bytes.toByteArray()
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size) ?: source
        } catch (e: Exception) {
            source
        }
    }

    private fun calculatePageLayout(
        bitmap: Bitmap,
        pageSizeOption: PageSizeOption,
        margin: Int
    ): Triple<Int, Int, RectF> {
        return when (pageSizeOption) {
            PageSizeOption.A4, PageSizeOption.LETTER -> {
                val pageWidth = pageSizeOption.widthPt
                val pageHeight = pageSizeOption.heightPt

                val availableWidth = (pageWidth - 2 * margin).toFloat()
                val availableHeight = (pageHeight - 2 * margin).toFloat()

                val imgWidth = bitmap.width.toFloat()
                val imgHeight = bitmap.height.toFloat()

                val scale = minOf(availableWidth / imgWidth, availableHeight / imgHeight)
                val targetWidth = imgWidth * scale
                val targetHeight = imgHeight * scale

                val left = margin + (availableWidth - targetWidth) / 2f
                val top = margin + (availableHeight - targetHeight) / 2f

                Triple(pageWidth, pageHeight, RectF(left, top, left + targetWidth, top + targetHeight))
            }
            PageSizeOption.FIT_TO_IMAGE -> {
                val ptWidth = (bitmap.width * 72f / 150f).toInt().coerceAtLeast(200)
                val ptHeight = (bitmap.height * 72f / 150f).toInt().coerceAtLeast(200)
                val pageWidth = ptWidth + 2 * margin
                val pageHeight = ptHeight + 2 * margin

                val left = margin.toFloat()
                val top = margin.toFloat()
                Triple(pageWidth, pageHeight, RectF(left, top, left + ptWidth, top + ptHeight))
            }
        }
    }
}
