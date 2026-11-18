package com.fairland.fgroundcontrol

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.fairland.fgroundcontrol.ui.theme.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen()
        val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as GroundControlApp
        val model = androidx.lifecycle.ViewModelProvider(this)[FoundationViewModel::class.java]
        try { startForegroundService(android.content.Intent(this, ConnectionService::class.java)) }
        catch (error: Exception) { app.reportServiceError(error) }
        setContent {
            GroundControlTheme {
                Surface(Modifier.fillMaxSize()) {
                    var toastRequest by remember { mutableIntStateOf(0) }
                    var toastVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(toastRequest) {
                        if (toastRequest > 0) {
                            toastVisible = true
                            delay(AppLayout.toastDurationMs)
                            toastVisible = false
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        com.fairland.fgroundcontrol.ui.StartupFlow(model) { toastRequest++ }
                        if (toastVisible) Surface(
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                .padding(horizontal = AppSpace.lg, vertical = AppSpace.lg)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            color = AppColors.toast, shape = RoundedCornerShape(AppRadius.alternative)
                        ) {
                            Text(stringResource(R.string.startup_unsupported), color = AppColors.textInverse,
                                modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.sm))
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
