package com.example.hida.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.hida.data.MediaRepository

sealed class Screen(val route: String) {
    object Calculator : Screen("calculator_screen")
    object Gallery : Screen("gallery_screen/{mode}") {
        fun createRoute(mode: String) = "gallery_screen/$mode"
    }
    object Settings : Screen("settings_screen")
    object ImageViewer : Screen("image_viewer/{filePath}") {
        fun createRoute(filePath: String) = "image_viewer/$filePath"
    }
}

@Composable
fun NavigationGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }

    NavHost(
        navController = navController,
        startDestination = Screen.Calculator.route
    ) {
        composable(route = Screen.Calculator.route) {
            CalculatorScreen(
                onUnlock = { isFake ->
                    val mode = if (isFake) "fake" else "real"
                    navController.navigate(Screen.Gallery.createRoute(mode)) {
                        popUpTo(Screen.Calculator.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Screen.Gallery.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "real"
            GalleryScreen(
                isFakeMode = mode == "fake",
                onLock = {
                    navController.navigate(Screen.Calculator.route) {
                        popUpTo(Screen.Gallery.route) { inclusive = true }
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onPlayVideo = { filePath ->
                    navController.navigate(Screen.VideoPlayer.createRoute(filePath))
                },
                onViewImage = { filePath ->
                    navController.navigate(Screen.ImageViewer.createRoute(filePath))
                }
            )
        }
        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath")
            if (filePath != null) {
                VideoPlayerScreen(filePath = filePath, repository = repository)
            }
        }
        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath")
            if (filePath != null) {
                ImageViewerScreen(
                    filePath = filePath,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
