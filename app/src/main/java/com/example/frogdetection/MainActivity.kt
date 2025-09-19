package com.example.frogdetection

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
import com.example.frogdetection.screens.*
import com.example.frogdetection.viewmodel.CapturedHistoryViewModel
import android.net.Uri
import com.google.android.libraries.places.api.Places
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        setContent {
            iFrogTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val historyViewModel: CapturedHistoryViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory(application)
                )

                LaunchedEffect(Unit) {
                    historyViewModel.migrateMissingLocations()
                }

                Scaffold(
                    bottomBar = {
                        if (currentRoute !in listOf("splash", "welcome")) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->

                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") { SplashScreen(navController) }
                        composable("welcome") { WelcomeScreen(navController) }
                        composable("home") { HomeScreen(navController) }
                        composable("dictionary") { FrogDictionaryScreen(navController, frogs = frogList) }

                        composable("frogDetail/{frogName}") { backStackEntry ->
                            val frogName = backStackEntry.arguments?.getString("frogName")
                            val startIndex = frogList.indexOfFirst { it.name == frogName }
                            if (startIndex != -1) {
                                FrogDetailScreen(navController, frogs = frogList, startIndex = startIndex)
                            }
                        }

                        composable("history") {
                            CapturedHistoryScreen(
                                navController = navController,
                                viewModel = historyViewModel
                            )
                        }

                        composable("map/{frogId}?") { backStackEntry ->
                            val frogId = backStackEntry.arguments?.getString("frogId")
                            DistributionMapScreen(
                                navController = navController,
                                viewModel = historyViewModel,
                                focusedFrogId = frogId
                            )
                        }

                        composable("about") { AboutScreen(navController) }

                        // ✅ Updated: preview now accepts lat & lon
                        composable("preview/{imageUri}/{lat}/{lon}") { backStackEntry ->
                            val encodedUri = backStackEntry.arguments?.getString("imageUri")
                            val decodedUri = encodedUri?.let { Uri.decode(it) }
                            val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull()
                            val lon = backStackEntry.arguments?.getString("lon")?.toDoubleOrNull()

                            ImagePreviewScreen(
                                navController = navController,
                                imageUri = decodedUri,
                                latitude = lat,
                                longitude = lon
                            )
                        }


                        composable("resultScreen/{frogId}") { backStackEntry ->
                            val frogId = backStackEntry.arguments?.getString("frogId")?.toLongOrNull()
                            if (frogId != null) {
                                ResultScreen(
                                    navController = navController,
                                    frogId = frogId,
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
