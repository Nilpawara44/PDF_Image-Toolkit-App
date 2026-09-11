package com.example.ui.imagetopdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ConversionRepository
import com.example.data.model.CompressionLevel
import com.example.data.model.ConversionRecord
import com.example.data.model.ConversionType
import com.example.data.model.ImageToPdfConfig
import com.example.data.model.PageSizeOption
import com.example.data.model.SelectedImageItem
import com.example.data.processing.FileUtils
import com.example.data.processing.ImageToPdfConverter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ImageProgressState(
    val isConverting: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val statusText: String = ""
)

class ImageToPdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ConversionRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ConversionRepository(db.conversionDao())
    }

    private val _selectedImages = MutableStateFlow<List<SelectedImageItem>>(emptyList())
    val selectedImages: StateFlow<List<SelectedImageItem>> = _selectedImages.asStateFlow()

    private val _config = MutableStateFlow(ImageToPdfConfig())
    val config: StateFlow<ImageToPdfConfig> = _config.asStateFlow()

    private val _progressState = MutableStateFlow(ImageProgressState())
    val progressState: StateFlow<ImageProgressState> = _progressState.asStateFlow()

    private val _successResult = MutableStateFlow<ImageToPdfConverter.ConversionResult?>(null)
    val successResult: StateFlow<ImageToPdfConverter.ConversionResult?> = _successResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var conversionJob: Job? = null

    fun addImages(uris: List<Uri>) {
        val context = getApplication<Application>()
        val currentList = _selectedImages.value.toMutableList()
        for (uri in uris) {
            val name = FileUtils.getFileName(context, uri)
            val size = FileUtils.getFileSize(context, uri)
            currentList.add(
                SelectedImageItem(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    name = name,
                    sizeBytes = size
                )
            )
        }
        _selectedImages.value = currentList
    }

    fun removeImage(id: String) {
        _selectedImages.value = _selectedImages.value.filter { it.id != id }
    }

    fun clearAllImages() {
        _selectedImages.value = emptyList()
    }

    fun rotateImage(id: String) {
        _selectedImages.value = _selectedImages.value.map {
            if (it.id == id) {
                val newRot = (it.rotationDegrees + 90) % 360
                it.copy(rotationDegrees = newRot)
            } else {
                it
            }
        }
    }

    fun moveImageUp(index: Int) {
        if (index <= 0 || index >= _selectedImages.value.size) return
        val list = _selectedImages.value.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        _selectedImages.value = list
    }

    fun moveImageDown(index: Int) {
        if (index < 0 || index >= _selectedImages.value.size - 1) return
        val list = _selectedImages.value.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        _selectedImages.value = list
    }

    fun setPageSize(option: PageSizeOption) {
        _config.value = _config.value.copy(pageSize = option)
    }

    fun setCompression(level: CompressionLevel) {
        _config.value = _config.value.copy(compression = level)
    }

    fun setOutputFileName(name: String) {
        _config.value = _config.value.copy(outputFileName = name)
    }

    fun startConversion() {
        val images = _selectedImages.value
        if (images.isEmpty()) {
            _errorMessage.value = "Please select at least one image"
            return
        }

        val context = getApplication<Application>()

        _progressState.value = ImageProgressState(
            isConverting = true,
            current = 0,
            total = images.size,
            statusText = "Compiling PDF document on device..."
        )

        conversionJob = viewModelScope.launch {
            val result = ImageToPdfConverter.convertImagesToPdf(
                context = context,
                images = images,
                config = _config.value,
                onProgress = { current, total, label ->
                    _progressState.value = ImageProgressState(
                        isConverting = true,
                        current = current,
                        total = total,
                        statusText = label
                    )
                }
            )

            result.onSuccess { convResult ->
                _progressState.value = ImageProgressState(isConverting = false)
                _successResult.value = convResult

                // Create thumbnail from first image URI if accessible
                val firstUriStr = images.firstOrNull()?.uri?.toString()

                val record = ConversionRecord(
                    title = convResult.outputFile.nameWithoutExtension,
                    type = ConversionType.IMAGE_TO_PDF,
                    sourceName = "${images.size} Photos",
                    outputPath = convResult.outputFile.absolutePath,
                    isDirectory = false,
                    itemCount = convResult.totalPages,
                    fileSizeBytes = convResult.totalBytes,
                    thumbnailUri = firstUriStr
                )
                repository.insertRecord(record)
            }.onFailure { ex ->
                _progressState.value = ImageProgressState(isConverting = false)
                _errorMessage.value = "PDF Creation failed: ${ex.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _progressState.value = ImageProgressState(isConverting = false)
    }

    fun dismissSuccess() {
        _successResult.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
