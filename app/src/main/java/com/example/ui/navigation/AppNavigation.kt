package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.imagetopdf.ImageToPdfScreen
import com.example.ui.imagetopdf.ImageToPdfViewModel
import com.example.ui.pdftoimage.PdfToImageScreen
import com.example.ui.pdftoimage.PdfToImageViewModel

object Destinations {
    const val HOME = "home"
    const val PDF_TO_IMAGE = "pdf_to_image"
    const val IMAGE_TO_PDF = "image_to_pdf"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME
    ) {
        composable(Destinations.HOME) {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToPdfToImage = {
                    navController.navigate(Destinations.PDF_TO_IMAGE)
                },
                onNavigateToImageToPdf = {
                    navController.navigate(Destinations.IMAGE_TO_PDF)
                }
            )
        }

        composable(Destinations.PDF_TO_IMAGE) {
            val pdfViewModel: PdfToImageViewModel = viewModel()
            PdfToImageScreen(
                viewModel = pdfViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Destinations.IMAGE_TO_PDF) {
            val imageViewModel: ImageToPdfViewModel = viewModel()
            ImageToPdfScreen(
                viewModel = imageViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
