package io.github.lekan128.digital_wellness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.lekan128.digital_wellness.ui.DashboardScreen
import io.github.lekan128.digital_wellness.ui.SettingsScreen

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "dashboard"
            ) {
                composable("dashboard") {
                    DashboardScreen(
                        onNavigateToSettings = {
                            navController.navigate("settings")
                        }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
   
    override fun onResume() {
        super.onResume()
        // If we could access the VM here, we would call checkPermissions().
        // Since we don't hold the reference easily without lateinit var (which is fine), 
        // We'll actually modify the DashboardScreen to listen to lifecycle events using `LifecycleEventEffect` 
        // (compose 1.7+) or `DisposableEffect` with observer.
        // Since we are using standard compose, I'll add the observer in DashboardScreen.kt instead of here.
    }
}
