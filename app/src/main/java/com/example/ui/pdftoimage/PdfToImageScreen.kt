package com.example.ui.pdftoimage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ConversionType
import com.example.data.model.ImageFormat
import com.example.data.model.ImageResolution
import com.example.data.model.PageRangeOption
import com.example.data.model.PdfToImageConfig
import com.example.data.processing.FileUtils
import com.example.ui.components.ConversionSuccessDialog
import com.example.ui.components.InterstitialAdManager
import com.example.ui.components.ProcessingDialog
import com.example.ui.components.findActivity
import com.example.ui.theme.BrandTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    viewModel: PdfToImageViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val successResult by viewModel.successResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val interstitialAdManager = remember { InterstitialAdManager() }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var hasHandledAdForSuccess by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectPdf(it) }
    }

    // When conversion completes, show the Interstitial Ad, then route to the success screen on ad dismissal
    LaunchedEffect(successResult) {
        val result = successResult
        if (result != null && !hasHandledAdForSuccess) {
            hasHandledAdForSuccess = true
            val activity = context.findActivity()
            interstitialAdManager.showAd(activity) {
                // Route user to final success screen after ad dismissal
                showSuccessDialog = true
            }
        }
    }

    LaunchedEffect(previewState.error) {
        previewState.error?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PDF to Image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("pdf_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Document Selection Area
            if (previewState.uri == null) {
                item {
                    PdfPickerEmptyCard(
                        onSelectPdf = { pdfPickerLauncher.launch("application/pdf") }
                    )
                }
            } else {
                item {
                    SelectedPdfCard(
                        fileName = previewState.fileName,
                        fileSizeBytes = previewState.fileSizeBytes,
                        totalPages = previewState.totalPages,
                        isLoading = previewState.isLoadingMetadata,
                        onChangePdf = { pdfPickerLauncher.launch("application/pdf") }
                    )
                }

                // Page Range Configuration Card
                item {
                    PageRangeConfigCard(
                        totalPages = previewState.totalPages,
                        config = config,
                        thumbnails = previewState.thumbnails,
                        onRangeOptionChange = { viewModel.setRangeOption(it) },
                        onCustomRangeChange = { start, end -> viewModel.setCustomRange(start, end) },
                        onTogglePageSelection = { viewModel.togglePageSelection(it) }
                    )
                }

                // Output Format & Quality Settings Card
                item {
                    OutputSettingsCard(
                        config = config,
                        onFormatChange = { viewModel.setFormat(it) },
                        onResolutionChange = { viewModel.setResolution(it) },
                        onSaveToGalleryChange = { viewModel.setSaveToGallery(it) }
                    )
                }

                // Convert Button
                item {
                    Button(
                        onClick = {
                            hasHandledAdForSuccess = false
                            showSuccessDialog = false
                            // Pre-load Interstitial Ad when conversion begins
                            interstitialAdManager.preloadAd(context)
                            viewModel.startConversion()
                        },
                        enabled = previewState.totalPages > 0 && !previewState.isLoadingMetadata,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_pdf_conversion_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Extract Pages to Images",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Processing Overlay Dialog
    if (progressState.isConverting) {
        ProcessingDialog(
            current = progressState.current,
            total = progressState.total,
            statusText = progressState.statusText,
            onCancel = { viewModel.cancelConversion() }
        )
    }

    // Success Screen Dialog (routed to user after ad dismissal)
    if (showSuccessDialog) {
        successResult?.let { result ->
            ConversionSuccessDialog(
                title = previewState.fileName.substringBeforeLast(".") + " Images",
                type = ConversionType.PDF_TO_IMAGE,
                itemCount = result.totalPagesConverted,
                fileSizeBytes = result.totalBytes,
                outputPath = result.outputDirectory.absolutePath,
                onOpen = {
                    FileUtils.openFile(context, result.firstImageFile, "image/*")
                },
                onShare = {
                    FileUtils.shareDirectoryImages(context, result.outputDirectory)
                },
                onDismiss = {
                    showSuccessDialog = false
                    viewModel.dismissSuccess()
                    onNavigateBack()
                }
            )
        }
    }
}

@Composable
fun PdfPickerEmptyCard(onSelectPdf: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectPdf)
            .testTag("select_pdf_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandTertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileOpen,
                    contentDescription = null,
                    tint = BrandTertiary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Select PDF Document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose any .pdf file from your device to extract high-resolution JPG or PNG images.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSelectPdf,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTertiary)
            ) {
                Icon(imageVector = Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Storage")
            }
        }
    }
}

@Composable
fun SelectedPdfCard(
    fileName: String,
    fileSizeBytes: Long,
    totalPages: Int,
    isLoading: Boolean,
    onChangePdf: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandTertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = BrandTertiary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = BrandTertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLoading) "Reading document..." else "$totalPages Pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FileUtils.formatFileSize(fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onChangePdf,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Change", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun PageRangeConfigCard(
    totalPages: Int,
    config: PdfToImageConfig,
    thumbnails: Map<Int, android.graphics.Bitmap>,
    onRangeOptionChange: (PageRangeOption) -> Unit,
    onCustomRangeChange: (start: Int, end: Int) -> Unit,
    onTogglePageSelection: (Int) -> Unit
) {
    var startInput by remember(config.startPage) { mutableStateOf(config.startPage.toString()) }
    var endInput by remember(config.endPage) { mutableStateOf(config.endPage.toString()) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Page Extraction Range",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = config.rangeOption == PageRangeOption.ALL_PAGES,
                    onClick = { onRangeOptionChange(PageRangeOption.ALL_PAGES) },
                    label = { Text("All Pages ($totalPages)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                FilterChip(
                    selected = config.rangeOption == PageRangeOption.CUSTOM_RANGE,
                    onClick = { onRangeOptionChange(PageRangeOption.CUSTOM_RANGE) },
                    label = { Text("Page Range") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                FilterChip(
                    selected = config.rangeOption == PageRangeOption.SELECTED_PAGES,
                    onClick = { onRangeOptionChange(PageRangeOption.SELECTED_PAGES) },
                    label = { Text("Select Pages") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            // Custom Range inputs
            if (config.rangeOption == PageRangeOption.CUSTOM_RANGE) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startInput,
                        onValueChange = {
                            startInput = it
                            val s = it.toIntOrNull() ?: 1
                            val e = endInput.toIntOrNull() ?: totalPages
                            onCustomRangeChange(s, e)
                        },
                        label = { Text("From Page") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Text(text = "to", style = MaterialTheme.typography.bodyMedium)

                    OutlinedTextField(
                        value = endInput,
                        onValueChange = {
                            endInput = it
                            val s = startInput.toIntOrNull() ?: 1
                            val e = it.toIntOrNull() ?: totalPages
                            onCustomRangeChange(s, e)
                        },
                        label = { Text("To Page") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Thumbnail Preview List
            if (thumbnails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Page Previews",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(totalPages) { pageIdx ->
                        val isSelected = when (config.rangeOption) {
                            PageRangeOption.ALL_PAGES -> true
                            PageRangeOption.CUSTOM_RANGE -> (pageIdx + 1) in config.startPage..config.endPage
                            PageRangeOption.SELECTED_PAGES -> config.selectedPages.contains(pageIdx)
                        }

                        PageThumbnailItem(
                            pageNumber = pageIdx + 1,
                            bitmap = thumbnails[pageIdx],
                            isSelected = isSelected,
                            isSelectable = config.rangeOption == PageRangeOption.SELECTED_PAGES,
                            onClick = {
                                if (config.rangeOption == PageRangeOption.SELECTED_PAGES) {
                                    onTogglePageSelection(pageIdx)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PageThumbnailItem(
    pageNumber: Int,
    bitmap: android.graphics.Bitmap?,
    isSelected: Boolean,
    isSelectable: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(enabled = isSelectable, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page $pageNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Page $pageNumber",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OutputSettingsCard(
    config: PdfToImageConfig,
    onFormatChange: (ImageFormat) -> Unit,
    onResolutionChange: (ImageResolution) -> Unit,
    onSaveToGalleryChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Output Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Output Format: JPG vs PNG
            Text(
                text = "File Format",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = config.format == ImageFormat.JPG,
                    onClick = { onFormatChange(ImageFormat.JPG) },
                    label = { Text("JPG (Smaller size)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = config.format == ImageFormat.PNG,
                    onClick = { onFormatChange(ImageFormat.PNG) },
                    label = { Text("PNG (Lossless)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Resolution Selection: Standard vs High Quality vs Ultra HD
            Text(
                text = "Rendering Resolution (DPI)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImageResolution.entries.forEach { res ->
                    val isSelected = config.resolution == res
                    Surface(
                        onClick = { onResolutionChange(res) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onResolutionChange(res) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = res.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save to Device Gallery Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Save to Device Photos / Gallery",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Saved in Pictures/PDF-Image Toolkit album",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = config.saveToGallery,
                    onCheckedChange = onSaveToGalleryChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
