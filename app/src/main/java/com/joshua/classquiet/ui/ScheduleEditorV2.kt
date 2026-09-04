package com.joshua.classquiet.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joshua.classquiet.location.PermissionSnapshot
import com.joshua.classquiet.model.ClassLocation
import com.joshua.classquiet.model.ClassSchedule
import com.joshua.classquiet.model.CustomDndSettings
import com.joshua.classquiet.model.DndMode
import com.joshua.classquiet.model.formatAsTime
import java.time.DayOfWeek
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleEditorScreenV2(
    existing: ClassSchedule?,
    permissions: PermissionSnapshot,
    snackbarHostState: SnackbarHostState,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDuplicate: (String) -> Unit,
) {
    val key = existing?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(existing?.name.orEmpty()) }
    var selectedDays: List<Int> by rememberSaveable(key) {
        mutableStateOf(existing?.days?.map { it.value } ?: listOf(1, 3, 5))
    }
    var startMinutes by rememberSaveable(key) {
        mutableStateOf(existing?.startMinutes ?: (9 * 60))
    }
    var endMinutes by rememberSaveable(key) {
        mutableStateOf(existing?.endMinutes ?: (10 * 60))
    }
    var extendPastEnd by rememberSaveable(key) {
        mutableStateOf(existing?.extendPastEnd ?: false)
    }
    var locationEnabled by rememberSaveable(key) {
        mutableStateOf(existing?.locationEnabled ?: true)
    }
    var locationsJson by rememberSaveable(key) {
        mutableStateOf(encodeLocations(existing?.savedLocations.orEmpty()))
    }
    val locations = remember(locationsJson) { decodeLocations(locationsJson) }
    var editingLocationId by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var addingLocation by rememberSaveable(key) { mutableStateOf(false) }
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
    var validationAttempted by rememberSaveable(key) { mutableStateOf(false) }
    val context = LocalContext.current

    val locationBeingEdited = editingLocationId?.let { id ->
        locations.firstOrNull { it.id == id }
    }
    if (addingLocation || locationBeingEdited != null) {
        LocationEditorScreen(
            existing = locationBeingEdited,
            permissions = permissions,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onBack = {
                addingLocation = false
                editingLocationId = null
            },
            onSave = { savedLocation ->
                val updated = locations.toMutableList()
                val index = updated.indexOfFirst { it.id == savedLocation.id }
                if (index >= 0) updated[index] = savedLocation else updated += savedLocation
                locationsJson = encodeLocations(updated)
                addingLocation = false
                editingLocationId = null
            },
        )
        return
    }

    val validationError = when {
        name.isBlank() -> "Enter a class name or label."
        selectedDays.isEmpty() -> "Choose at least one day."
        startMinutes == endMinutes -> "Start and end times cannot be the same."
        locationEnabled && locations.isEmpty() -> "Add at least one class location."
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
            EditorSectionTitle("Class details")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Class name / label") },
                placeholder = { Text("ECE 2514") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            EditorSectionTitle("Days and time")
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
                        label = {
                            Text(
                                day.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorTimeButton(
                    label = "Starts",
                    minutes = startMinutes,
                    modifier = Modifier.weight(1f),
                ) {
                    showEditorTimePicker(context, startMinutes) { startMinutes = it }
                }
                EditorTimeButton(
                    label = "Ends",
                    minutes = endMinutes,
                    modifier = Modifier.weight(1f),
                ) {
                    showEditorTimePicker(context, endMinutes) { endMinutes = it }
                }
            }
            if (endMinutes <= startMinutes && startMinutes != endMinutes) {
                Text(
                    "The end time is treated as the following day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsToggleRow(
                title = "Stay quiet 30 seconds after class",
                detail = "Prevents end-of-class reminder notifications from making sound immediately.",
                checked = extendPastEnd,
                onCheckedChange = { extendPastEnd = it },
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            EditorSectionTitle("Class locations")
            SettingsToggleRow(
                title = "Require a location",
                detail = if (locationEnabled) {
                    "DND starts when the time matches and you are inside any saved location."
                } else {
                    "DND is based only on the class day and time."
                },
                checked = locationEnabled,
                onCheckedChange = { locationEnabled = it },
            )
            AnimatedVisibility(locationEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    locations.forEach { location ->
                        LocationSummaryCard(
                            location = location,
                            onEdit = { editingLocationId = location.id },
                            onDelete = {
                                locationsJson = encodeLocations(
                                    locations.filterNot { it.id == location.id },
                                )
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { addingLocation = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(if (locations.isEmpty()) "Add location" else "Add another location")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            EditorSectionTitle("Do Not Disturb level")
            DndMode.entries.forEach { mode ->
                EditorDndModeChoice(
                    mode = mode,
                    selected = selectedModeName == mode.name,
                    onSelect = { selectedModeName = mode.name },
                )
            }
            AnimatedVisibility(selectedModeName == DndMode.CUSTOM.name) {
                CustomDndSettingsEditor(
                    value = customSettings,
                    onValueChange = { customSettingsJson = it.toJson().toString() },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SettingsToggleRow(
                title = "Class enabled",
                detail = "Disabled classes keep their settings but never change DND.",
                checked = enabled,
                onCheckedChange = { enabled = it },
            )

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
                    if (validationError == null) {
                        val primary = locations.firstOrNull() ?: ClassLocation(
                            label = "No location",
                            latitude = 0.0,
                            longitude = 0.0,
                        )
                        val schedule = ClassSchedule(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            locationLabel = primary.label,
                            resolvedAddress = primary.resolvedAddress,
                            latitude = primary.latitude,
                            longitude = primary.longitude,
                            radiusMeters = primary.radiusMeters,
                            days = selectedDays.mapTo(mutableSetOf()) { DayOfWeek.of(it) },
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            dndMode = DndMode.fromStored(selectedModeName),
                            customDndSettings = customSettings,
                            enabled = enabled,
                            locationEnabled = locationEnabled,
                            locations = locations,
                            extendPastEnd = extendPastEnd,
                        )
                        if (viewModel.save(schedule)) onSaved()
                    }
                },
            ) {
                Text(if (existing == null) "Add class" else "Save changes")
            }
            if (existing != null) {
                OutlinedButton(
                    onClick = { onDuplicate(existing.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Duplicate saved class")
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun LocationSummaryCard(
    location: ClassLocation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 5.dp))
            Column(Modifier.weight(1f)) {
                Text(location.label, fontWeight = FontWeight.SemiBold)
                if (location.resolvedAddress.isNotBlank()) {
                    Text(
                        location.resolvedAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${location.radiusMeters.roundToInt()} m radius",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${location.label}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${location.label}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationEditorScreen(
    existing: ClassLocation?,
    permissions: PermissionSnapshot,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: (ClassLocation) -> Unit,
) {
    val key = existing?.id ?: "new-location"
    var label by rememberSaveable(key) { mutableStateOf(existing?.label.orEmpty()) }
    var addressQuery by rememberSaveable(key) {
        mutableStateOf(existing?.resolvedAddress.orEmpty())
    }
    var latitudeText by rememberSaveable(key) {
        mutableStateOf(existing?.latitude?.let(::formatMapCoordinate).orEmpty())
    }
    var longitudeText by rememberSaveable(key) {
        mutableStateOf(existing?.longitude?.let(::formatMapCoordinate).orEmpty())
    }
    var radius by rememberSaveable(key) { mutableStateOf(existing?.radiusMeters ?: 150f) }
    var showCoordinates by rememberSaveable(key) { mutableStateOf(false) }
    var showMap by rememberSaveable(key) { mutableStateOf(false) }
    var locating by remember(key) { mutableStateOf(false) }
    var validationAttempted by rememberSaveable(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latitude = latitudeText.toDoubleOrNull()
    val longitude = longitudeText.toDoubleOrNull()
    val validationError = when {
        label.isBlank() -> "Enter a building, room, or location label."
        latitude == null || latitude !in -90.0..90.0 -> "Choose a valid latitude."
        longitude == null || longitude !in -180.0..180.0 -> "Choose a valid longitude."
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add location" else "Edit location") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
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
                                latitudeText = formatMapCoordinate(result.latitude)
                                longitudeText = formatMapCoordinate(result.longitude)
                                addressQuery = result.address.ifBlank { addressQuery }
                                if (label.isBlank()) label = addressQuery.substringBefore(',')
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Find")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !locating,
                    onClick = {
                        if (!permissions.fineLocation) {
                            viewModel.showMessage("Allow precise location first, or use the map.")
                        } else {
                            scope.launch {
                                locating = true
                                val result = viewModel.useCurrentLocation()
                                locating = false
                                if (result == null) {
                                    viewModel.showMessage("No GPS fix was available. Try again near a window.")
                                } else {
                                    latitudeText = formatMapCoordinate(result.latitude)
                                    longitudeText = formatMapCoordinate(result.longitude)
                                    if (result.address.isNotBlank()) addressQuery = result.address
                                    if (label.isBlank()) label = "Class location"
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text("Use here")
                }
            }
            if (locating) LinearProgressIndicator(Modifier.fillMaxWidth())

            OutlinedButton(
                onClick = { showMap = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Choose pin on 2D map")
            }

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
                        Spacer(Modifier.padding(horizontal = 5.dp))
                        Column {
                            Text("Location selected", fontWeight = FontWeight.Medium)
                            Text(
                                "${formatMapCoordinate(latitude)}, ${formatMapCoordinate(longitude)}",
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
                "The map displays this radius around the pin. 150–250 m usually works well for a campus building.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (validationAttempted && validationError != null) {
                Text(validationError, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    validationAttempted = true
                    if (validationError == null && latitude != null && longitude != null) {
                        onSave(
                            ClassLocation(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                label = label.trim(),
                                resolvedAddress = addressQuery.trim(),
                                latitude = latitude,
                                longitude = longitude,
                                radiusMeters = radius,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (existing == null) "Add location" else "Save location")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMap) {
        MapPickerDialog(
            initialLatitude = latitude,
            initialLongitude = longitude,
            radiusMeters = radius,
            onDismiss = { showMap = false },
            onUsePin = { selectedLatitude, selectedLongitude ->
                latitudeText = formatMapCoordinate(selectedLatitude)
                longitudeText = formatMapCoordinate(selectedLongitude)
                showMap = false
            },
        )
    }
}

@Composable
private fun MapPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    radiusMeters: Float,
    onDismiss: () -> Unit,
    onUsePin: (Double, Double) -> Unit,
) {
    var selectedLatitude by rememberSaveable { mutableStateOf(initialLatitude) }
    var selectedLongitude by rememberSaveable { mutableStateOf(initialLongitude) }
    val mapViewReference = remember { mutableStateOf<OsmMapView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapViewReference.value?.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Choose a class location",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Tap to place the pin. Drag the map, and pinch or double tap to zoom. " +
                        "The shaded circle is the saved radius.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context: android.content.Context ->
                        OsmMapView(context).apply {
                            mapViewReference.value = this
                            setInitialLocation(initialLatitude, initialLongitude)
                            setRadiusMeters(radiusMeters)
                            onLocationSelected = { latitude: Double, longitude: Double ->
                                selectedLatitude = latitude
                                selectedLongitude = longitude
                            }
                        }
                    },
                    update = { mapView: OsmMapView ->
                        mapView.setRadiusMeters(radiusMeters)
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val latitude = selectedLatitude
                            val longitude = selectedLongitude
                            if (latitude != null && longitude != null) {
                                onUsePin(latitude, longitude)
                            }
                        },
                        enabled = selectedLatitude != null && selectedLongitude != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Use this pin")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EditorTimeButton(
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
private fun EditorDndModeChoice(
    mode: DndMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.padding(horizontal = 4.dp))
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

private fun showEditorTimePicker(
    context: android.content.Context,
    initialMinutes: Int,
    onSelected: (Int) -> Unit,
) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(hour * 60 + minute) },
        initialMinutes / 60,
        initialMinutes % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

private fun encodeLocations(locations: List<ClassLocation>): String =
    JSONArray().apply { locations.forEach { put(it.toJson()) } }.toString()

private fun decodeLocations(raw: String): List<ClassLocation> = runCatching {
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(ClassLocation.fromJson(it)) }
        }
    }
}.getOrDefault(emptyList())

private fun formatMapCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)
