package com.example.frogdetection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.frogdetection.screens.*

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
                        composable("splash") {
                            SplashScreen(navController)

                        }
                        composable("welcome") {
                            WelcomeScreen(navController)
                        }
                        composable("home") {
                            HomeScreen(navController)
                        }
                        composable("dictionary") {
                            FrogDictionaryScreen(navController)
                        }
                        // ✅ NEW frog detail route
                        composable("frogDetail/{frogName}") { backStackEntry ->
                            val frogName = backStackEntry.arguments?.getString("frogName")
                            // Frog data will be fetched/handled inside FrogDetailScreen
                            FrogDetailScreen(frogName ?: "", navController)
                        }

                        composable("map/{speciesName}") { backStackEntry ->
                            val speciesName = backStackEntry.arguments?.getString("speciesName")
                                ?: "Kaloula pulchra" // Fallback
                            DistributionMapScreen(navController, speciesName)
                        }
                        composable("history") {
                            CapturedHistoryScreen(navController)
                        }
                        composable("about") {
                            AboutScreen(navController)
                        }
                        composable("preview") {
                            ImagePreviewScreen(navController)
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