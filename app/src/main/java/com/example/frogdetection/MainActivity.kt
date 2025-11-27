package com.example.frogdetection

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.frogdetection.screens.*
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 REQUIRED: OSMDroid initialization (fixes blank map)
        val ctx = applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        Configuration.getInstance().load(ctx, prefs)
        Configuration.getInstance().userAgentValue = ctx.packageName

        setContent {
            iFrogTheme {

                val navController = rememberNavController()

                // Use single shared ViewModel across entire app
                val historyViewModel: CapturedHistoryViewModel =
                    viewModel(factory = ViewModelProvider.AndroidViewModelFactory(application))

                Scaffold(
                    bottomBar = {
                        val route = navController.currentBackStackEntryAsState().value?.destination?.route
                        if (route !in listOf("splash", "welcome")) {
                            BottomNavBar(navController)
                        }
                    }
                ) { padding ->

                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(padding)
                    ) {

                        // ───────────────────────────────
                        // BASIC ROUTES
                        // ───────────────────────────────
                        composable("splash") { SplashScreen(navController) }
                        composable("welcome") { WelcomeScreen(navController) }
                        composable("home") { HomeScreen(navController) }
                        composable("about") { AboutScreen(navController) }

                        composable("dictionary") {
                            FrogDictionaryScreen(navController = navController, frogs = frogList)
                        }

                        composable(
                            "frogDetail/{frogName}",
                            listOf(navArgument("frogName") { type = NavType.StringType })
                        ) { backStack ->
                            val frogName = backStack.arguments?.getString("frogName")
                            val startIndex = frogList.indexOfFirst { it.name == frogName }
                            if (startIndex != -1)
                                FrogDetailScreen(navController, frogList, startIndex)
                        }

                        // ───────────────────────────────
                        // HISTORY
                        // ───────────────────────────────
                        composable("history") {
                            CapturedHistoryScreen(
                                navController = navController,
                                viewModel = historyViewModel
                            )
                        }

                        // ───────────────────────────────
                        // MAP ROUTE — NO PARAMETERS
                        // (Fix for previous crash)
                        // ───────────────────────────────
                        composable("map") {
                            DistributionMapScreen(navController = navController)
                        }

                        // ───────────────────────────────
                        // IMAGE PREVIEW
                        // ───────────────────────────────
                        composable(
                            "preview/{imageUri}/{lat}/{lon}",
                            arguments = listOf(
                                navArgument("imageUri") { type = NavType.StringType },
                                navArgument("lat") { type = NavType.FloatType },
                                navArgument("lon") { type = NavType.FloatType }
                            )
                        ) { backStack ->
                            val imageUri = Uri.parse(backStack.arguments?.getString("imageUri") ?: "")
                            val lat = backStack.arguments?.getFloat("lat")?.toDouble()
                            val lon = backStack.arguments?.getFloat("lon")?.toDouble()

                            ImagePreviewScreen(
                                navController = navController,
                                imageUri = imageUri,
                                latitude = lat,
                                longitude = lon,
                                viewModel = historyViewModel
                            )
                        }

                        // ───────────────────────────────
                        // RESULT SCREEN
                        // ───────────────────────────────
                        composable(
                            "resultScreen/{frogId}",
                            listOf(navArgument("frogId") { type = NavType.StringType })
                        ) { entry ->
                            val idLong = entry.arguments?.getString("frogId")?.toLongOrNull()
                            if (idLong != null) {
                                ResultScreen(
                                    navController = navController,
                                    frogId = idLong,
                                    viewModel = historyViewModel
                                )
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
fun iFrogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF98FB98),
            onPrimary = Color(0xFF006400),
            background = Color(0xFFE0FFE0),
            surface = Color(0xFFDFFFD9)
        ),
        content = content
    )
}
