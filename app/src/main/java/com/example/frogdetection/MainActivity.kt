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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            iFrogTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val historyViewModel: CapturedHistoryViewModel = viewModel(
                    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
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

                        composable("dictionary") {
                            FrogDictionaryScreen(navController, frogs = frogList)
                        }

                        composable("frogDetail/{frogName}",
                            arguments = listOf(
                                navArgument("frogName") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val frogName = backStackEntry.arguments?.getString("frogName")
                            val startIndex = frogList.indexOfFirst { it.name == frogName }
                            if (startIndex != -1) {
                                FrogDetailScreen(
                                    navController,
                                    frogs = frogList,
                                    startIndex = startIndex
                                )
                            }
                        }

                        composable("history") {
                            CapturedHistoryScreen(navController, historyViewModel)
                        }

                        // FIXED — valid route
                        composable(
                            route = "map/{frogId}",
                            arguments = listOf(
                                navArgument("frogId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val frogId = backStackEntry.arguments?.getString("frogId")
                            DistributionMapScreen(
                                navController,
                                viewModel = historyViewModel,
                                focusedFrogId = frogId
                            )
                        }

                        composable("about") { AboutScreen(navController) }

                        composable(
                            route = "preview/{imageUri}/{lat}/{lon}",
                            arguments = listOf(
                                navArgument("imageUri") { type = NavType.StringType },
                                navArgument("lat") { type = NavType.FloatType },
                                navArgument("lon") { type = NavType.FloatType }
                            )
                        ) { backStackEntry ->

                            val imageUriStr = backStackEntry.arguments?.getString("imageUri")
                            val decodedUri = Uri.parse(imageUriStr ?: "")

                            val lat = backStackEntry.arguments?.getFloat("lat")?.toDouble()
                            val lon = backStackEntry.arguments?.getFloat("lon")?.toDouble()

                            val historyViewModel: CapturedHistoryViewModel = viewModel()

                            ImagePreviewScreen(
                                navController = navController,
                                imageUri = decodedUri,
                                latitude = lat,
                                longitude = lon,
                                viewModel = historyViewModel
                            )
                        }






                        composable(
                            route = "resultScreen/{frogId}",
                            arguments = listOf(
                                navArgument("frogId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
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
