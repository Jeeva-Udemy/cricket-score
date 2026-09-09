package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
 * to type one in by hand — laid out side by side, exactly like the Select Squad dropdown +
 * Team Name text field pair on the Match Setup screen (req: "Keep the batsman/bowler dropdown
 * and manual entry field side by side just like we did it for Squad selection in Start
 * Match" — this had been stacked in a Column instead of a Row, the one visible difference from
 * that pattern).
 *
 * The dropdown only appears when there's actually something to offer — a squad linked to
 * this match, or a name typed here before elsewhere in the app (see RecentPlayersStore) — an
 * empty [availablePlayerNames] just shows the plain text field at full width, same as the
 * Squad row would if there were no squads to list.
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

    // req: side by side, matching the Squad row's Row(spacedBy(12.dp)) { ...weight(1f)...
    // weight(1f) } exactly — only fall back to a single full-width field when there's no
    // dropdown to put next to it (nothing to list in availablePlayerNames).
    if (availablePlayerNames.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
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

            // The manual entry field — always present and always editable, regardless of
            // whether a dropdown sits next to it, so a name can always be typed by hand.
            OutlinedTextField(
                value = value,
                onValueChange = { changeAndCapitalize(it) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .weight(1f)
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            )
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = { changeAndCapitalize(it) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        )
    }
}
