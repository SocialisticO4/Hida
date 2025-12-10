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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Calculator : Screen("calculator_screen")
    object Gallery : Screen("gallery_screen/{mode}") {
        fun createRoute(mode: String) = "gallery_screen/$mode"
    }
    object Settings : Screen("settings_screen")
    object VideoPlayer : Screen("video_player/{filePath}") {
        fun createRoute(filePath: String): String {
            val encoded = URLEncoder.encode(filePath, StandardCharsets.UTF_8.toString())
            return "video_player/$encoded"
        }
    }
    object ImageViewer : Screen("image_viewer/{filePath}") {
        fun createRoute(filePath: String): String {
            val encoded = URLEncoder.encode(filePath, StandardCharsets.UTF_8.toString())
            return "image_viewer/$encoded"
        }
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
            val encodedPath = backStackEntry.arguments?.getString("filePath")
            if (encodedPath != null) {
                val filePath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
                VideoPlayerScreen(
                    filePath = filePath,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath")
            if (encodedPath != null) {
                val filePath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
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
