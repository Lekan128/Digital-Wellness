package io.github.lekan128.digital_wellness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lekan128.digital_wellness.ui.DashboardScreen
import io.github.lekan128.digital_wellness.ui.DashboardViewModel
import io.github.lekan128.digital_wellness.ui.theme.DigitalWellnessTheme

class MainActivity : ComponentActivity() {
    
    // We'll hold a reference to the VM to call checkPermissions manually if needed, 
    // but in Compose we often access it via viewModel().
    // A cleaner way in pure Compose is handling onResume in the Composable or Activity.
    // However, since we need to refresh state *when returning from settings*, 
    // overriding OnResume here is the most robust way if we can access the same VM instance or use a broad event.
    // But since the ViewModel is scoped to the Activity/Graph, calling checkPermissions in OnResume requires holding an instance or using the same scope.
    // To simplifiy, we will delegate this responsibility to the UI's Lifecycle effects, 
    // OR we can just use a simple OnRsume callback mechanism.
    
    // Actually, simply observing Lifecycle.Event.ON_RESUME inside DashboardScreen or just checking on `onResume` here is fine.
    // Let's rely on `DashboardScreen`'s `LaunchedEffect` and maybe add a `DisposableEffect` with lifecycle observer there, 
    // or just trigger re-check here if we can.
    
    // Better approach: Activity handles onResume -> broadcast or just let Compose Lifecycle handle it?
    // Let's implement a LifecycleEventObserver in Compose (cleaner).
    // Wait, the easiest way is to just keep the original request: "Ensure the UI refreshes when the user returns"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalWellnessTheme {
                // To ensure we share the same VM instance if we were to access it here, 
                // we would need to hoist it. But `viewModel()` in lower levels finds the Activity-scoped one.
                // We'll wrap it here to pass lifecycle events or just Handle it inside DashboardScreen.
                DashboardScreen()
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
