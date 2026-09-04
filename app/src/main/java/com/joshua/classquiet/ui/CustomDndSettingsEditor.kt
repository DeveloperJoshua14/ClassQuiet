package com.joshua.classquiet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshua.classquiet.model.ConversationAudience
import com.joshua.classquiet.model.CustomDndSettings
import com.joshua.classquiet.model.PeopleAudience

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomDndSettingsEditor(
    value: CustomDndSettings,
    onValueChange: (CustomDndSettings) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
    Text(
        "Custom DND settings",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        "Allowed items may make sound or appear while this class is active. Other notifications are intercepted by Android.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    CustomSettingsGroup("Sounds and exceptions") {
        CustomSwitch(
            title = "Alarms",
            detail = "Allow the alarm audio stream.",
            checked = value.allowAlarms,
            onCheckedChange = { onValueChange(value.copy(allowAlarms = it)) },
        )
        CustomSwitch(
            title = "Media",
            detail = "Allow music, videos, and other media audio.",
            checked = value.allowMedia,
            onCheckedChange = { onValueChange(value.copy(allowMedia = it)) },
        )
        CustomSwitch(
            title = "System sounds",
            detail = "Allow touch, charging, and other system sounds.",
            checked = value.allowSystemSounds,
            onCheckedChange = { onValueChange(value.copy(allowSystemSounds = it)) },
        )
        CustomSwitch(
            title = "Reminders",
            checked = value.allowReminders,
            onCheckedChange = { onValueChange(value.copy(allowReminders = it)) },
        )
        CustomSwitch(
            title = "Calendar events",
            checked = value.allowEvents,
            onCheckedChange = { onValueChange(value.copy(allowEvents = it)) },
        )
        CustomSwitch(
            title = "Repeat callers",
            detail = "Allow someone who calls again within Android's repeat-caller window.",
            checked = value.allowRepeatCallers,
            onCheckedChange = { onValueChange(value.copy(allowRepeatCallers = it)) },
        )
        CustomSwitch(
            title = "Priority app channels",
            detail = "Allow channels you marked as priority in Android 15 or newer.",
            checked = value.allowPriorityChannels,
            onCheckedChange = { onValueChange(value.copy(allowPriorityChannels = it)) },
        )
    }

    CustomSettingsGroup("People") {
        PeopleAudienceSelector(
            title = "Calls",
            selected = value.calls,
            onSelected = { onValueChange(value.copy(calls = it)) },
        )
        PeopleAudienceSelector(
            title = "Messages",
            selected = value.messages,
            onSelected = { onValueChange(value.copy(messages = it)) },
        )
        ConversationAudienceSelector(
            selected = value.conversations,
            onSelected = { onValueChange(value.copy(conversations = it)) },
        )
    }

    CustomSettingsGroup("Visual notifications") {
        CustomSwitch(
            title = "Notification shade and lock screen",
            checked = value.showNotificationList,
            onCheckedChange = { onValueChange(value.copy(showNotificationList = it)) },
        )
        CustomSwitch(
            title = "Status bar icons",
            checked = value.showStatusBarIcons,
            onCheckedChange = { onValueChange(value.copy(showStatusBarIcons = it)) },
        )
        CustomSwitch(
            title = "Heads-up notifications",
            checked = value.showPeeking,
            onCheckedChange = { onValueChange(value.copy(showPeeking = it)) },
        )
        CustomSwitch(
            title = "Notification dots",
            checked = value.showBadges,
            onCheckedChange = { onValueChange(value.copy(showBadges = it)) },
        )
        CustomSwitch(
            title = "Always-on display",
            checked = value.showAmbientDisplay,
            onCheckedChange = { onValueChange(value.copy(showAmbientDisplay = it)) },
        )
        CustomSwitch(
            title = "Notification lights",
            checked = value.showLights,
            onCheckedChange = { onValueChange(value.copy(showLights = it)) },
        )
        CustomSwitch(
            title = "Full-screen notifications",
            checked = value.showFullScreenIntents,
            onCheckedChange = { onValueChange(value.copy(showFullScreenIntents = it)) },
        )
    }
    }
}

@Composable
private fun CustomSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun CustomSwitch(
    title: String,
    detail: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleAudienceSelector(
    title: String,
    selected: PeopleAudience,
    onSelected: (PeopleAudience) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 7.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PeopleAudience.entries.forEach { audience ->
            FilterChip(
                selected = audience == selected,
                onClick = { onSelected(audience) },
                label = { Text(audience.displayName) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationAudienceSelector(
    selected: ConversationAudience,
    onSelected: (ConversationAudience) -> Unit,
) {
    Text(
        "Conversations",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 7.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ConversationAudience.entries.forEach { audience ->
            FilterChip(
                selected = audience == selected,
                onClick = { onSelected(audience) },
                label = { Text(audience.displayName) },
            )
        }
    }
}
