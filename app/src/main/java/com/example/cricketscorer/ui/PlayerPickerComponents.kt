package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.data.SquadEntity

/**
 * Dropdown to optionally pick a saved squad for a team.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadDropdown(
    label: String,
    squads: List<SquadEntity>,
    selected: SquadEntity?,
    onSelect: (SquadEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.teamName ?: "No saved squad (type names)",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            DropdownMenuItem(
                text = { Text("No saved squad (type names)") },
                onClick = { onSelect(null); expanded = false }
            )
            squads.forEach { squad ->
                DropdownMenuItem(
                    text = { Text(squad.teamName) },
                    onClick = { onSelect(squad); expanded = false }
                )
            }
        }
    }
}

/**
 * A text field with an optional dropdown of player names to pick from.
 * Replaces the old horizontal-scrolling chip row — the dropdown scrolls
 * vertically so long squad lists are fully accessible without horizontal swiping.
 * Free-text entry is always available when the list is empty or the player isn't listed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    availablePlayerNames: List<String>,
    modifier: Modifier = Modifier
) {
    if (availablePlayerNames.isEmpty()) {
        // No squad linked — plain text field only
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it); expanded = false },
                label = { Text(label) },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 220.dp)
            ) {
                availablePlayerNames.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onValueChange(name); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}
