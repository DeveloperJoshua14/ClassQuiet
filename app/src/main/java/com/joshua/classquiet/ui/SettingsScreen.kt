package com.joshua.classquiet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshua.classquiet.data.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    onSaveRuleName: (String) -> Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var ruleName by rememberSaveable(settings.dndRuleName) {
        mutableStateOf(settings.dndRuleName)
    }
    var confirmImport by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quiet Classes", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsCard(title = "Android Mode") {
                    Text(
                        "This is the name shown in Android Settings → Modes when Quiet Classes is active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = {
                            if (it.length <= AppSettings.MAX_RULE_NAME_LENGTH) ruleName = it
                        },
                        label = { Text("Mode name") },
                        supportingText = {
                            Text("${ruleName.length}/${AppSettings.MAX_RULE_NAME_LENGTH}")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onSaveRuleName(ruleName) },
                        enabled = ruleName.isNotBlank() && ruleName.trim() != settings.dndRuleName,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Mode name")
                    }
                }
            }
            item {
                SettingsCard(title = "Backup and transfer") {
                    Text(
                        "Export every class, location, schedule, DND choice, custom policy, and app setting to one JSON file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export backup")
                    }
                    Spacer(Modifier.height(9.dp))
                    OutlinedButton(
                        onClick = { confirmImport = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import backup")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Android permissions are device-specific and must be granted again after transferring.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsCard(title = "Links") {
                    ExternalLinkRow("App website") {
                        uriHandler.openUri("https://classquiet.nafzigers.us")
                    }
                    ExternalLinkRow("Privacy policy") {
                        uriHandler.openUri("https://classquiet.nafzigers.us/privacy")
                    }
                    ExternalLinkRow("Terms and conditions") {
                        uriHandler.openUri("https://classquiet.nafzigers.us/terms")
                    }
                    ExternalLinkRow("Buy me a coffee") {
                        uriHandler.openUri("https://buymeacoffee.com/joshua.nafziger")
                    }
                }
            }
            item {
                SettingsCard(title = "About") {
                    Row(Modifier.fillMaxWidth()) {
                        Text("App", modifier = Modifier.weight(1f))
                        Text("Quiet Classes")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Version", modifier = Modifier.weight(1f))
                        Text(com.joshua.classquiet.BuildConfig.VERSION_NAME)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Class schedules and coordinates remain on this device unless you explicitly export a backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import backup?") },
            text = {
                Text(
                    "The imported backup will replace every class and app setting currently stored on this device.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmImport = false
                        onImport()
                    },
                ) { Text("Choose backup") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ExternalLinkRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Icon(Icons.Default.OpenInNew, contentDescription = "Open $label")
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}
