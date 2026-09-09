package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
            // req: the field itself should read "Select Squad" until something is picked,
            // matching the label — the old "No saved squad (type names)" text stuck around
            // even after the label above it was changed, which read as a leftover mismatch.
            value = selected?.teamName ?: "Select Squad",
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
                text = { Text("Select Squad") },
                onClick = { onSelect(null); expanded = false }
            )
            // req #4: "the border should be under each name" — a divider under every item
            // (including the last) instead of one border around the whole menu, so each name
            // in the list is visually separated from the next.
            DropdownItemDivider()
            squads.forEach { squad ->
                DropdownMenuItem(
                    text = { Text(squad.teamName) },
                    onClick = { onSelect(squad); expanded = false }
                )
                DropdownItemDivider()
            }
        }
    }
}

/** req #4/#6: the under-each-item border shared by every dropdown in the app (internal, not
 *  private, so other dropdowns in this package — e.g. HomeScreen's Backup scope pickers — use
 *  the exact same treatment instead of a slightly different one-off copy). */
@Composable
internal fun DropdownItemDivider() {
    Divider(color = MaterialTheme.colorScheme.outline)
}

/**
 * A dropdown to quick-pick a known player name, PLUS a separate, always-editable text field
 * to type one in by hand — deliberately mirroring the Select Squad dropdown + Team Name text
 * field pair used above on the Match Setup screen (two distinct fields, not one merged
 * autocomplete box), since a single combo field that both showed suggestions and accepted
 * typing read as "I can only pick from a list, I can't type a name" (req: "we need both
 * dropdown and field to enter batsman/bowler name just like we do it for squad").
 *
 * The dropdown only appears when there's actually something to offer — a squad linked to
 * this match, or a name typed here before elsewhere in the app (see RecentPlayersStore) — an
 * empty [availablePlayerNames] just shows the plain text field.
 *
 * req #2: whatever is typed by hand always starts with a capital letter.
 * req #4: picking a name from the dropdown automatically moves focus to
 * [nextFocusRequester] and closes the keyboard — so on the last field of a form, pass the
 * "confirm" button's own FocusRequester as [nextFocusRequester] to have it highlighted with
 * the keyboard dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    availablePlayerNames: List<String>,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    fun advanceFocus() {
        keyboardController?.hide()
        nextFocusRequester?.requestFocus()
    }

    fun changeAndCapitalize(raw: String) {
        onValueChange(capitalizeFirstLetter(raw))
    }

    val keyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Words,
        imeAction = if (nextFocusRequester != null) ImeAction.Next else ImeAction.Done
    )
    val keyboardActions = KeyboardActions(
        onNext = { advanceFocus() },
        onDone = { keyboardController?.hide() }
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (availablePlayerNames.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = value.ifBlank { "Select $label" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pick from list") },
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
                            onClick = {
                                onValueChange(name)
                                expanded = false
                                advanceFocus()
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                        // req #4: same under-each-item border treatment as SquadDropdown above.
                        DropdownItemDivider()
                    }
                }
            }
        }

        // The manual entry field — always present and always editable, regardless of
        // whether a dropdown is shown above it, so a name can always be typed by hand.
        OutlinedTextField(
            value = value,
            onValueChange = { changeAndCapitalize(it) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        )
    }
}
