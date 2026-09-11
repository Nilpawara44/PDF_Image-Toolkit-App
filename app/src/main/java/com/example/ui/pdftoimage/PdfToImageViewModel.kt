package com.example.ui.pdftoimage

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ConversionRepository
import com.example.data.model.ConversionRecord
import com.example.data.model.ConversionType
import com.example.data.model.ImageFormat
import com.example.data.model.ImageResolution
import com.example.data.model.PageRangeOption
import com.example.data.model.PdfToImageConfig
import com.example.data.processing.FileUtils
import com.example.data.processing.PdfToImageConverter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PdfPreviewState(
    val uri: Uri? = null,
    val fileName: String = "",
    val fileSizeBytes: Long = 0,
    val totalPages: Int = 0,
    val isLoadingMetadata: Boolean = false,
    val error: String? = null,
    val thumbnails: Map<Int, Bitmap> = emptyMap() // pageIndex to bitmap
)

data class ConversionProgressState(
    val isConverting: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val statusText: String = ""
)

class PdfToImageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ConversionRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ConversionRepository(db.conversionDao())
    }

    private val _previewState = MutableStateFlow(PdfPreviewState())
    val previewState: StateFlow<PdfPreviewState> = _previewState.asStateFlow()

    private val _config = MutableStateFlow(PdfToImageConfig())
    val config: StateFlow<PdfToImageConfig> = _config.asStateFlow()

    private val _progressState = MutableStateFlow(ConversionProgressState())
    val progressState: StateFlow<ConversionProgressState> = _progressState.asStateFlow()

    private val _successResult = MutableStateFlow<PdfToImageConverter.ConversionResult?>(null)
    val successResult: StateFlow<PdfToImageConverter.ConversionResult?> = _successResult.asStateFlow()

    private var conversionJob: Job? = null

    fun selectPdf(uri: Uri) {
        val context = getApplication<Application>()
        val name = FileUtils.getFileName(context, uri)
        val size = FileUtils.getFileSize(context, uri)

        _previewState.value = PdfPreviewState(
            uri = uri,
            fileName = name,
            fileSizeBytes = size,
            isLoadingMetadata = true,
            error = null
        )

        viewModelScope.launch {
            val result = PdfToImageConverter.inspectPdf(context, uri)
            result.onSuccess { metadata ->
                _previewState.value = _previewState.value.copy(
                    totalPages = metadata.totalPages,
                    isLoadingMetadata = false,
                    error = null
                )
                // Initialize range config
                _config.value = _config.value.copy(
                    startPage = 1,
                    endPage = metadata.totalPages,
                    selectedPages = (0 until metadata.totalPages).toSet()
                )
                // Load first few thumbnails
                loadThumbnails(uri, metadata.totalPages)
            }.onFailure { ex ->
                _previewState.value = _previewState.value.copy(
                    isLoadingMetadata = false,
                    error = ex.localizedMessage ?: "Failed to open PDF file"
                )
            }
        }
    }

    private fun loadThumbnails(uri: Uri, totalPages: Int) {
        viewModelScope.launch {
            val countToLoad = minOf(totalPages, 12)
            val thumbs = mutableMapOf<Int, Bitmap>()
            for (i in 0 until countToLoad) {
                val bmp = PdfToImageConverter.renderPageThumbnail(getApplication(), uri, i, targetWidth = 180)
                if (bmp != null) {
                    thumbs[i] = bmp
                    _previewState.value = _previewState.value.copy(thumbnails = thumbs.toMap())
                }
            }
        }
    }

    fun setFormat(format: ImageFormat) {
        _config.value = _config.value.copy(format = format)
    }

    fun setResolution(resolution: ImageResolution) {
        _config.value = _config.value.copy(resolution = resolution)
    }

    fun setRangeOption(option: PageRangeOption) {
        _config.value = _config.value.copy(rangeOption = option)
    }

    fun setCustomRange(start: Int, end: Int) {
        val total = _previewState.value.totalPages
        val safeStart = start.coerceIn(1, total.coerceAtLeast(1))
        val safeEnd = end.coerceIn(safeStart, total.coerceAtLeast(safeStart))
        _config.value = _config.value.copy(startPage = safeStart, endPage = safeEnd)
    }

    fun togglePageSelection(pageIndex: Int) {
        val current = _config.value.selectedPages.toMutableSet()
        if (current.contains(pageIndex)) {
            current.remove(pageIndex)
        } else {
            current.add(pageIndex)
        }
        _config.value = _config.value.copy(selectedPages = current)
    }

    fun setSaveToGallery(save: Boolean) {
        _config.value = _config.value.copy(saveToGallery = save)
    }

    fun startConversion() {
        val uri = _previewState.value.uri ?: return
        val context = getApplication<Application>()

        _progressState.value = ConversionProgressState(
            isConverting = true,
            current = 0,
            total = _previewState.value.totalPages,
            statusText = "Starting on-device extraction..."
        )

        conversionJob = viewModelScope.launch {
            val result = PdfToImageConverter.convertPdfToImages(
                context = context,
                pdfUri = uri,
                originalPdfName = _previewState.value.fileName,
                config = _config.value,
                onProgress = { current, total, label ->
                    _progressState.value = ConversionProgressState(
                        isConverting = true,
                        current = current,
                        total = total,
                        statusText = label
                    )
                }
            )

            result.onSuccess { convResult ->
                _progressState.value = ConversionProgressState(isConverting = false)
                _successResult.value = convResult

                // Save record to Room database
                val record = ConversionRecord(
                    title = _previewState.value.fileName.substringBeforeLast(".") + " Images",
                    type = ConversionType.PDF_TO_IMAGE,
                    sourceName = _previewState.value.fileName,
                    outputPath = convResult.outputDirectory.absolutePath,
                    isDirectory = true,
                    itemCount = convResult.totalPagesConverted,
                    fileSizeBytes = convResult.totalBytes,
                    thumbnailUri = convResult.firstImageFile.absolutePath
                )
                repository.insertRecord(record)
            }.onFailure { ex ->
                _progressState.value = ConversionProgressState(isConverting = false)
                _previewState.value = _previewState.value.copy(
                    error = "Conversion failed: ${ex.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _progressState.value = ConversionProgressState(isConverting = false)
    }

    fun dismissSuccess() {
        _successResult.value = null
    }

    fun clearError() {
        _previewState.value = _previewState.value.copy(error = null)
    }
}
