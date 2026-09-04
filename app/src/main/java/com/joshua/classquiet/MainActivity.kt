package com.joshua.classquiet

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.joshua.classquiet.ui.ClassQuietRoot
import com.joshua.classquiet.ui.MainViewModel
import com.joshua.classquiet.ui.theme.ClassQuietTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val foregroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.refreshPermissionsOnly()
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            viewModel.showMessage("Precise location is required to recognize individual class buildings.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClassQuietTheme {
                ClassQuietRoot(
                    viewModel = viewModel,
                    requestForegroundLocation = ::requestForegroundLocation,
                    openBackgroundLocationSettings = ::openAppSettings,
                    openDndSettings = ::openDndSettings,
                    openExactAlarmSettings = ::openExactAlarmSettings,
                    openLocationServices = ::openLocationServices,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAfterResume()
    }

    private fun requestForegroundLocation() {
        foregroundLocationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun openDndSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppSettings()
        }
    }

    private fun openLocationServices() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}
