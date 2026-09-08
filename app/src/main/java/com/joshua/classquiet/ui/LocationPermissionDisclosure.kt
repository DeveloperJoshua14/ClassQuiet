package com.joshua.classquiet.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joshua.classquiet.R

/** Keeps the disclosure between each in-app location action and its Android permission step. */
@Composable
internal fun WithLocationPermissionDisclosure(
    requestForegroundLocation: () -> Unit,
    requestBackgroundLocation: () -> Unit,
    openPrivacyPolicy: () -> Unit,
    content: @Composable (
        requestForegroundLocation: () -> Unit,
        requestBackgroundLocation: () -> Unit,
    ) -> Unit,
) {
    // Save the pending step across recreation, but never launch a permission from composition.
    var pendingStep by rememberSaveable { mutableStateOf<String?>(null) }

    content(
        { pendingStep = FOREGROUND_STEP },
        { pendingStep = BACKGROUND_STEP },
    )

    val step = pendingStep ?: return
    val context = LocalContext.current
    val backgroundOption = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.backgroundPermissionOptionLabel.toString()
        } else {
            context.getString(R.string.location_disclosure_allow_all_the_time)
        }
    }

    AlertDialog(
        onDismissRequest = { pendingStep = null },
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
        title = { Text(stringResource(R.string.location_disclosure_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.location_disclosure_body))
                Text(stringResource(R.string.location_disclosure_processing))
                Text(
                    when {
                        step == FOREGROUND_STEP ->
                            stringResource(R.string.location_disclosure_foreground_step)
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                            stringResource(
                                R.string.location_disclosure_background_settings_step,
                                backgroundOption,
                            )
                        else -> stringResource(
                            R.string.location_disclosure_background_dialog_step,
                            backgroundOption,
                        )
                    },
                )
                Text(
                    stringResource(R.string.location_disclosure_optional),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = openPrivacyPolicy) {
                    Text(stringResource(R.string.location_disclosure_privacy_policy))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Consume the pending step first, so rapid taps cannot launch it twice.
                    if (pendingStep == step) {
                        pendingStep = null
                        if (step == FOREGROUND_STEP) {
                            requestForegroundLocation()
                        } else {
                            requestBackgroundLocation()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.location_disclosure_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingStep = null }) {
                Text(stringResource(R.string.location_disclosure_not_now))
            }
        },
    )
}

private const val FOREGROUND_STEP = "foreground"
private const val BACKGROUND_STEP = "background"
