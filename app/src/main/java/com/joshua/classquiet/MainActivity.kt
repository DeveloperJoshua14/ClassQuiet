package com.joshua.classquiet

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissionsOnly()
        if (!it) {
            viewModel.showMessage("Notification access is needed to show when class mode is active.")
        }
    }

    private val exportConfigurationLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(viewModel::exportConfiguration)
    }

    private val importConfigurationLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importConfiguration)
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
                    requestNotificationPermission = ::requestNotificationPermission,
                    exportConfiguration = ::exportConfiguration,
                    importConfiguration = ::importConfiguration,
                    startOnSettings = intent?.action == NotificationManager.ACTION_AUTOMATIC_ZEN_RULE,
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

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private fun exportConfiguration() {
        exportConfigurationLauncher.launch(
            "quiet-classes-backup-${java.time.LocalDate.now()}.json",
        )
    }

    private fun importConfiguration() {
        importConfigurationLauncher.launch(arrayOf("application/json", "text/*"))
    }
}
