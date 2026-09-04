package com.joshua.classquiet.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshua.classquiet.data.RuntimeState
import com.joshua.classquiet.data.RuntimeStatus
import com.joshua.classquiet.location.PermissionSnapshot
import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.model.CustomDndSettings
import com.joshua.classquiet.model.DndMode
import com.joshua.classquiet.model.formatAsTime
import com.joshua.classquiet.model.formatDays
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private enum class MainTab {
    CLASSES,
    WEEK,
    SETTINGS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassQuietRoot(
    viewModel: MainViewModel,
    requestForegroundLocation: () -> Unit,
    openBackgroundLocationSettings: () -> Unit,
    openDndSettings: () -> Unit,
    openExactAlarmSettings: () -> Unit,
    openLocationServices: () -> Unit,
    requestNotificationPermission: () -> Unit,
    exportConfiguration: () -> Unit,
    importConfiguration: () -> Unit,
    startOnSettings: Boolean = false,
) {
    val schedules by viewModel.schedules.collectAsState()
    val runtimeStatus by viewModel.runtimeStatus.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabName by rememberSaveable {
        mutableStateOf(if (startOnSettings) MainTab.SETTINGS.name else MainTab.CLASSES.name)
    }
    val selectedTab = MainTab.valueOf(selectedTabName)

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    BackHandler(enabled = editorOpen) {
        editorOpen = false
        editingId = null
    }
    BackHandler(enabled = !editorOpen && selectedTab != MainTab.CLASSES) {
        selectedTabName = MainTab.CLASSES.name
    }

    if (editorOpen) {
        ScheduleEditorScreenV2(
            existing = schedules.firstOrNull { it.id == editingId },
            permissions = permissions,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            onBack = {
                editorOpen = false
                editingId = null
            },
            onSaved = {
                editorOpen = false
                editingId = null
            },
            onDuplicate = { id ->
                if (viewModel.duplicate(id)) {
                    editorOpen = false
                    editingId = null
                }
            },
        )
    } else {
        val onAdd = {
            editingId = null
            editorOpen = true
        }
        val onEdit: (String) -> Unit = {
            editingId = it
            editorOpen = true
        }
        val bottomBar: @Composable () -> Unit = {
            MainNavigationBar(
                selected = selectedTab,
                onSelected = { selectedTabName = it.name },
            )
        }
        when (selectedTab) {
            MainTab.CLASSES -> HomeScreen(
                schedules = schedules,
                runtimeStatus = runtimeStatus,
                permissions = permissions,
                snackbarHostState = snackbarHostState,
                onAdd = onAdd,
                onEdit = onEdit,
                onDelete = viewModel::delete,
                onEnabledChange = viewModel::setEnabled,
                onCheckNow = viewModel::checkNow,
                requestForegroundLocation = requestForegroundLocation,
                openBackgroundLocationSettings = openBackgroundLocationSettings,
                openDndSettings = openDndSettings,
                openExactAlarmSettings = openExactAlarmSettings,
                openLocationServices = openLocationServices,
                requestNotificationPermission = requestNotificationPermission,
                bottomBar = bottomBar,
            )

            MainTab.WEEK -> WeekCalendarScreen(
                schedules = schedules,
                activeScheduleIds = runtimeStatus.activeScheduleIds,
                onEdit = onEdit,
                onAdd = onAdd,
                snackbarHostState = snackbarHostState,
                bottomBar = bottomBar,
            )

            MainTab.SETTINGS -> SettingsScreen(
                settings = appSettings,
                onSaveRuleName = viewModel::setDndRuleName,
                onExport = exportConfiguration,
                onImport = importConfiguration,
                snackbarHostState = snackbarHostState,
                bottomBar = bottomBar,
            )
        }
    }
}

@Composable
private fun MainNavigationBar(
    selected: MainTab,
    onSelected: (MainTab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == MainTab.CLASSES,
            onClick = { onSelected(MainTab.CLASSES) },
            icon = { Icon(Icons.Default.School, contentDescription = null) },
            label = { Text("Classes") },
        )
        NavigationBarItem(
            selected = selected == MainTab.WEEK,
            onClick = { onSelected(MainTab.WEEK) },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            label = { Text("Week") },
        )
        NavigationBarItem(
            selected = selected == MainTab.SETTINGS,
            onClick = { onSelected(MainTab.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    schedules: List<ClassSchedule>,
    runtimeStatus: RuntimeStatus,
    permissions: PermissionSnapshot,
    snackbarHostState: SnackbarHostState,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onCheckNow: () -> Unit,
    requestForegroundLocation: () -> Unit,
    openBackgroundLocationSettings: () -> Unit,
    openDndSettings: () -> Unit,
    openExactAlarmSettings: () -> Unit,
    openLocationServices: () -> Unit,
    requestNotificationPermission: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quiet Classes", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Schedule-aware DND",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCheckNow) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check now")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add a class")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RuntimeStatusCard(runtimeStatus) }
            if (!permissions.ready) {
                item {
                    SetupCard(
                        permissions = permissions,
                        requestForegroundLocation = requestForegroundLocation,
                        openBackgroundLocationSettings = openBackgroundLocationSettings,
                        openDndSettings = openDndSettings,
                        openExactAlarmSettings = openExactAlarmSettings,
                        openLocationServices = openLocationServices,
                        requestNotificationPermission = requestNotificationPermission,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Classes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAdd) { Text("Add class") }
                }
            }
            if (schedules.isEmpty()) {
                item { EmptyScheduleCard(onAdd) }
            } else {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        isActive = schedule.id in runtimeStatus.activeScheduleIds,
                        onEdit = { onEdit(schedule.id) },
                        onDelete = { onDelete(schedule.id) },
                        onEnabledChange = { onEnabledChange(schedule.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusCard(status: RuntimeStatus) {
    val colors = when (status.state) {
        RuntimeState.ACTIVE -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        RuntimeState.ERROR, RuntimeState.NEEDS_PERMISSION -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )

        else -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Card(colors = colors, shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            ) {
                Icon(
                    imageVector = if (status.state == RuntimeState.ACTIVE) {
                        Icons.Default.VolumeOff
                    } else {
                        Icons.Default.Shield
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    status.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(status.detail, style = MaterialTheme.typography.bodyMedium)
                if (status.activeScheduleNames.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        status.activeScheduleNames.joinToString(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    permissions: PermissionSnapshot,
    requestForegroundLocation: () -> Unit,
    openBackgroundLocationSettings: () -> Unit,
    openDndSettings: () -> Unit,
    openExactAlarmSettings: () -> Unit,
    openLocationServices: () -> Unit,
    requestNotificationPermission: () -> Unit,
) {
    var expanded by rememberSaveable(permissions.ready) { mutableStateOf(!permissions.ready) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Finish setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${permissions.completedCount} of ${PermissionSnapshot.REQUIREMENT_COUNT} requirements ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Review")
                }
            }

            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = {
                            permissions.completedCount / PermissionSnapshot.REQUIREMENT_COUNT.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    PermissionRow(
                        title = "Precise location",
                        detail = "Used to tell one campus building from another.",
                        granted = permissions.fineLocation,
                        actionLabel = "Allow",
                        onAction = requestForegroundLocation,
                    )
                    PermissionRow(
                        title = "Background location",
                        detail = "Open settings, choose Permissions → Location → Allow all the time.",
                        granted = permissions.backgroundLocation,
                        actionLabel = "Open settings",
                        actionEnabled = permissions.fineLocation,
                        onAction = openBackgroundLocationSettings,
                    )
                    PermissionRow(
                        title = "Do Not Disturb access",
                        detail = "Lets Quiet Classes activate its own Android Mode.",
                        granted = permissions.dndAccess,
                        actionLabel = "Allow",
                        onAction = openDndSettings,
                    )
                    PermissionRow(
                        title = "Alarms & reminders",
                        detail = "Makes class start and end checks run on time.",
                        granted = permissions.exactAlarms,
                        actionLabel = "Allow",
                        onAction = openExactAlarmSettings,
                    )
                    PermissionRow(
                        title = "Location Services",
                        detail = "The phone's system location switch must be on.",
                        granted = permissions.locationServices,
                        actionLabel = "Turn on",
                        onAction = openLocationServices,
                    )
                    PermissionRow(
                        title = "Active mode notification",
                        detail = "Shows a silent ongoing notification while a class DND mode is active.",
                        granted = permissions.notifications,
                        actionLabel = "Allow",
                        onAction = requestNotificationPermission,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    actionLabel: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            TextButton(enabled = actionEnabled, onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyScheduleCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("No classes yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add the days, time, and location of your first class.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add a class")
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ClassSchedule,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            schedule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape,
                            ) {
                                Text(
                                    "ACTIVE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Text(
                        schedule.days.formatDays(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Switch(checked = schedule.enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(12.dp))
            InfoLine(
                icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                text = "${schedule.startMinutes.formatAsTime()} – ${schedule.endMinutes.formatAsTime()}" +
                    (if (schedule.endMinutes <= schedule.startMinutes) " · next day" else "") +
                    (if (schedule.extendPastEnd) " · +30 sec" else ""),
            )
            InfoLine(
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                text = schedule.locationSummary,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        schedule.dndMode.displayName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${schedule.name}")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${schedule.name}")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${schedule.name}?") },
            text = { Text("Its alarms and location geofence will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InfoLine(icon: @Composable () -> Unit, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditorScreen(
    existing: ClassSchedule?,
    permissions: PermissionSnapshot,
    snackbarHostState: SnackbarHostState,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val key = existing?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(existing?.name.orEmpty()) }
    var locationLabel by rememberSaveable(key) { mutableStateOf(existing?.locationLabel.orEmpty()) }
    var addressQuery by rememberSaveable(key) {
        mutableStateOf(existing?.resolvedAddress.orEmpty())
    }
    var latitudeText by rememberSaveable(key) {
        mutableStateOf(existing?.let { formatCoordinate(it.latitude) }.orEmpty())
    }
    var longitudeText by rememberSaveable(key) {
        mutableStateOf(existing?.let { formatCoordinate(it.longitude) }.orEmpty())
    }
    var radius by rememberSaveable(key) { mutableStateOf(existing?.radiusMeters ?: 150f) }
    var selectedDays: List<Int> by rememberSaveable(key) {
        mutableStateOf(existing?.days?.map { it.value } ?: listOf(1, 3, 5))
    }
    var startMinutes by rememberSaveable(key) { mutableStateOf(existing?.startMinutes ?: 9 * 60) }
    var endMinutes by rememberSaveable(key) { mutableStateOf(existing?.endMinutes ?: 10 * 60) }
    var selectedModeName by rememberSaveable(key) {
        mutableStateOf((existing?.dndMode ?: DndMode.VISUAL_ONLY).name)
    }
    var customSettingsJson by rememberSaveable(key) {
        mutableStateOf(
            (existing?.customDndSettings ?: CustomDndSettings()).toJson().toString(),
        )
    }
    val customSettings = remember(customSettingsJson) {
        CustomDndSettings.fromJson(
            runCatching { JSONObject(customSettingsJson) }.getOrNull(),
        )
    }
    var enabled by rememberSaveable(key) { mutableStateOf(existing?.enabled ?: true) }
    var showCoordinates by rememberSaveable(key) { mutableStateOf(false) }
    var locating by remember(key) { mutableStateOf(false) }
    var validationAttempted by rememberSaveable(key) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val latitude = latitudeText.toDoubleOrNull()
    val longitude = longitudeText.toDoubleOrNull()
    val validationError = when {
        name.isBlank() -> "Enter a class name."
        selectedDays.isEmpty() -> "Choose at least one day."
        startMinutes == endMinutes -> "Start and end times cannot be the same."
        locationLabel.isBlank() -> "Enter a building or room name."
        latitude == null || latitude !in -90.0..90.0 -> "Choose a location or enter a valid latitude."
        longitude == null || longitude !in -180.0..180.0 -> "Choose a location or enter a valid longitude."
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add class" else "Edit class") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("Class details")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Class name") },
                placeholder = { Text("ECE 2514") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Days and time")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day.value in selectedDays,
                        onClick = {
                            selectedDays = selectedDays.toMutableList().apply {
                                if (day.value in this) remove(day.value) else add(day.value)
                                sort()
                            }
                        },
                        label = { Text(day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimeButton(
                    label = "Starts",
                    minutes = startMinutes,
                    modifier = Modifier.weight(1f),
                ) {
                    showTimePicker(context, startMinutes) { startMinutes = it }
                }
                TimeButton(
                    label = "Ends",
                    minutes = endMinutes,
                    modifier = Modifier.weight(1f),
                ) {
                    showTimePicker(context, endMinutes) { endMinutes = it }
                }
            }
            if (endMinutes <= startMinutes && startMinutes != endMinutes) {
                Text(
                    "The end time is treated as the following day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SectionTitle("Class location")
            OutlinedTextField(
                value = locationLabel,
                onValueChange = { locationLabel = it },
                label = { Text("Building / room label") },
                placeholder = { Text("McBryde Hall 100") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = addressQuery,
                onValueChange = { addressQuery = it },
                label = { Text("Address or building search") },
                placeholder = { Text("225 Stanger St, Blacksburg, VA") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = !locating && addressQuery.isNotBlank(),
                    onClick = {
                        scope.launch {
                            locating = true
                            val result = viewModel.resolveAddress(addressQuery)
                            locating = false
                            if (result == null) {
                                viewModel.showMessage("No location found. Try a full street address.")
                            } else {
                                latitudeText = formatCoordinate(result.latitude)
                                longitudeText = formatCoordinate(result.longitude)
                                addressQuery = result.address.ifBlank { addressQuery }
                                if (locationLabel.isBlank()) {
                                    locationLabel = addressQuery.substringBefore(',')
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Find address")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !locating,
                    onClick = {
                        if (!permissions.fineLocation) {
                            viewModel.showMessage("Allow precise location first, or search for an address.")
                        } else {
                            scope.launch {
                                locating = true
                                val result = viewModel.useCurrentLocation()
                                locating = false
                                if (result == null) {
                                    viewModel.showMessage("No GPS fix was available. Try again near a window.")
                                } else {
                                    latitudeText = formatCoordinate(result.latitude)
                                    longitudeText = formatCoordinate(result.longitude)
                                    if (result.address.isNotBlank()) addressQuery = result.address
                                    if (locationLabel.isBlank()) locationLabel = "Class location"
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Use here")
                }
            }
            if (locating) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (latitude != null && longitude != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Location selected", fontWeight = FontWeight.Medium)
                            Text(
                                "${formatCoordinate(latitude)}, ${formatCoordinate(longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            TextButton(onClick = { showCoordinates = !showCoordinates }) {
                Text(if (showCoordinates) "Hide coordinates" else "Enter coordinates manually")
            }
            AnimatedVisibility(showCoordinates) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = latitudeText,
                        onValueChange = { latitudeText = it },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitudeText,
                        onValueChange = { longitudeText = it },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                "Location radius: ${radius.roundToInt()} m",
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = radius,
                onValueChange = { radius = it },
                valueRange = 50f..500f,
                steps = 17,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "150–250 m usually works well for a campus building. GPS accuracy is allowed up to 30 m of extra margin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SectionTitle("Do Not Disturb level")
            DndMode.entries.forEach { mode ->
                DndModeChoice(
                    mode = mode,
                    selected = selectedModeName == mode.name,
                    onSelect = { selectedModeName = mode.name },
                )
            }
            AnimatedVisibility(selectedModeName == DndMode.CUSTOM.name) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CustomDndSettingsEditor(
                        value = customSettings,
                        onValueChange = { customSettingsJson = it.toJson().toString() },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Class enabled", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Disabled classes keep their settings but never change DND.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            if (validationAttempted && validationError != null) {
                Text(
                    validationError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = {
                    validationAttempted = true
                    if (validationError == null && latitude != null && longitude != null) {
                        val schedule = ClassSchedule(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            locationLabel = locationLabel.trim(),
                            resolvedAddress = addressQuery.trim(),
                            latitude = latitude,
                            longitude = longitude,
                            radiusMeters = radius,
                            days = selectedDays.mapTo(mutableSetOf()) { DayOfWeek.of(it) },
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            dndMode = DndMode.fromStored(selectedModeName),
                            customDndSettings = customSettings,
                            enabled = enabled,
                        )
                        if (viewModel.save(schedule)) onSaved()
                    }
                },
            ) {
                Text(if (existing == null) "Add class" else "Save changes")
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun TimeButton(
    label: String,
    minutes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(minutes.formatAsTime(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun DndModeChoice(mode: DndMode, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun showTimePicker(context: android.content.Context, initialMinutes: Int, onTime: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onTime(hour * 60 + minute) },
        initialMinutes / 60,
        initialMinutes % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
