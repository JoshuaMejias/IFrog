package com.example.frogdetection.screens

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Color(0xFFB0FFB0),
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        NavigationBarItem(
            icon = { Icon(painterResource(android.R.drawable.ic_menu_help), "Dictionary") },
            label = { Text("Dictionary") },
            selected = currentRoute == "dictionary",
            onClick = { navController.navigate("dictionary") }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(android.R.drawable.ic_menu_mapmode), "Map") },
            label = { Text("Map") },
            selected = currentRoute == "map",
            onClick = { navController.navigate("map") }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(android.R.drawable.ic_menu_myplaces), "Home") },
            label = { Text("Home") },
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(android.R.drawable.ic_menu_recent_history), "History") },
            label = { Text("History") },
            selected = currentRoute == "history",
            onClick = { navController.navigate("history") }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(android.R.drawable.ic_menu_info_details), "About") },
            label = { Text("About") },
            selected = currentRoute == "about",
            onClick = { navController.navigate("about") }
        )
    }
}