package com.example.frogdetection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
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
                            // ✅ No factory needed
                            val historyViewModel: CapturedHistoryViewModel = viewModel()

                            CapturedHistoryScreen(
                                navController = navController,
                                viewModel = historyViewModel
                            )
                        }


                        composable("map/{frogId}?") { backStackEntry ->
                            val historyViewModel: CapturedHistoryViewModel = viewModel(
                                factory = ViewModelProvider.AndroidViewModelFactory(application)
                            )

                            val frogId = backStackEntry.arguments?.getString("frogId")

                            DistributionMapScreen(
                                navController = navController,
                                viewModel = historyViewModel,
                                focusedFrogId = frogId
                            )
                        }


                        composable("about") { AboutScreen(navController) }
                        composable("preview") { ImagePreviewScreen(navController) }
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
