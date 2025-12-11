package com.example.hida.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.hida.data.MediaRepository
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.HidaTheme
import com.example.hida.ui.theme.md3_dark_background
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome_screen")
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

// Smooth Material 3 transitions - no fade to prevent white flash
private const val TRANSITION_DURATION = 300

private val enterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(TRANSITION_DURATION)
    )
}

private val exitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutHorizontally(
        targetOffsetX = { -it / 4 },
        animationSpec = tween(TRANSITION_DURATION)
    )
}

private val popEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = tween(TRANSITION_DURATION)
    )
}

private val popExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(TRANSITION_DURATION)
    )
}

@Composable
fun NavigationGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }
    val prefs = remember { PreferencesManager(context) }
    
    // Determine start destination based on first launch OR missing PIN
    // If no PIN is set, force Welcome screen (prevents default PIN bypass)
    val startDestination = if (prefs.isFirstLaunch() || !prefs.hasPin()) {
        Screen.Welcome.route
    } else {
        Screen.Calculator.route
    }

    // Wrap in HidaTheme with dark background to prevent white flash
    HidaTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(md3_dark_background)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Calculator.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }
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
    }
}
