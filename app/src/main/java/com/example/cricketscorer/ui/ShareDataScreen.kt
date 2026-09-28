package com.example.cricketscorer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.backup.ShareUtils
import com.example.cricketscorer.data.BackupDataScope
import com.example.cricketscorer.data.TeamRole
import com.example.cricketscorer.data.UserProfileStore
import com.example.cricketscorer.sync.TeamHub
import com.example.cricketscorer.viewmodel.HomeViewModel
import com.example.cricketscorer.viewmodel.ShareDataUiState
import com.example.cricketscorer.viewmodel.TeamViewModel
import kotlinx.coroutines.launch

/**
 * Share Data page.
 *
 * Team Sharing: add teammates by mobile number (from Contacts or typed). When they install the
 * app and enter that number, all the team's matches and squads appear automatically.
 * Access levels by mobile number:
 *  - Admin  — everything (log in with root/root, or be made Admin by another Admin)
 *  - Manager — add members + upload data
 *  - User   — read only
 *
 * "Send as a file" (the earlier WhatsApp file share / import) is kept at the bottom for
 * anyone who isn't in the team list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDataScreen(
    teamViewModel: TeamViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit
) {
    val state by teamViewModel.state.collectAsState()
    val shareState by homeViewModel.shareState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showAdminLogin by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<TeamHub.Member?>(null) }
    var fileScope by remember { mutableStateOf(BackupDataScope.BOTH) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) homeViewModel.openImportFile(uri)
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            teamViewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Share Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------------------------------------------------------- this phone
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.profile.name.ifBlank { "This phone" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                state.profile.mobile.ifBlank { "Mobile number not set" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RoleChip(role = state.role, loading = state.roleLoading, rootAdmin = state.isRootAdmin)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEditProfile = true }) { Text("Edit profile") }
                        if (state.isRootAdmin) {
                            OutlinedButton(onClick = { teamViewModel.adminLogout() }) { Text("Admin logout") }
                        } else if (state.role != TeamRole.ADMIN) {
                            OutlinedButton(onClick = { showAdminLogin = true }) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Admin login")
                            }
                        }
                    }
                }
            }

            if (!state.roleLoading && state.role == null) {
                InfoCard(
                    if (state.profile.mobile.isBlank())
                        "Add your mobile number (Edit profile). Once your team Admin or Manager adds that number, the team's matches and squads appear here automatically."
                    else
                        "${state.profile.mobile} isn't in the team yet. Ask your team Admin or Manager to add it — the team's matches and squads will then download automatically."
                )
            }

            // ---------------------------------------------------------------- team data
            if (state.role != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Team data", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.role!!.canPublish)
                                "Your matches and teams are uploaded for everyone, and theirs are downloaded to this phone. This also happens automatically when the app opens."
                            else
                                "The team's matches and squads download to this phone automatically when the app opens (read only).",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Last synced: " + (state.lastSync?.let { formatRelativeTime(it) } ?: "never"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { teamViewModel.syncNow() }, enabled = !state.busy) {
                                Icon(Icons.Default.Sync, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Sync now")
                            }
                            TextButton(onClick = { teamViewModel.syncNow(full = true) }, enabled = !state.busy) {
                                Text("Download everything")
                            }
                            if (state.busy) CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- members
            if (state.role != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Team members (${state.members.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.role!!.canAddMembers) {
                                Button(onClick = { showAddMember = true }, enabled = !state.busy) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add")
                                }
                            }
                        }
                        if (state.members.isEmpty()) {
                            Text("No members yet.", style = MaterialTheme.typography.bodySmall)
                        }
                        state.members.forEach { m ->
                            Divider()
                            MemberRow(
                                member = m,
                                isMe = m.mobile == state.profile.mobile,
                                canManage = state.role == TeamRole.ADMIN,
                                onChangeRole = { teamViewModel.changeRole(m, it) },
                                onRemove = { removeTarget = m }
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- file share (kept)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Send as a file", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "For someone who isn't in the team list: send a file on WhatsApp, they open it with this app.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    BackupScopeDropdown(label = "What to send", selected = fileScope, enabled = true, onSelect = { fileScope = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val file = homeViewModel.buildShareFile(fileScope) ?: return@launch
                                    ShareUtils.shareFile(context, file.first, "application/json", file.second, "Share Wickt data", preferWhatsApp = true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            modifier = Modifier.weight(1f)
                        ) { Text("WhatsApp") }
                        OutlinedButton(onClick = {
                            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                        }, modifier = Modifier.weight(1f)) { Text("Import file") }
                    }
                }
            }
        }
    }

    ShareDataStatusDialogs(
        state = shareState,
        onConfirmImport = { homeViewModel.confirmImport(it) },
        onDismiss = { homeViewModel.dismissShareState() }
    )

    if (showAdminLogin) {
        AdminLoginDialog(
            onLogin = { u, p -> if (teamViewModel.adminLogin(u, p)) showAdminLogin = false },
            onDismiss = { showAdminLogin = false }
        )
    }

    if (showAddMember) {
        AddMemberDialog(
            roles = state.role?.assignableRoles.orEmpty(),
            onAdd = { mobile, name, role ->
                teamViewModel.addMember(mobile, name, role)
                showAddMember = false
            },
            onDismiss = { showAddMember = false }
        )
    }

    if (showEditProfile) {
        ProfileDialog(
            title = "Your details",
            initial = state.profile,
            dismissLabel = "Cancel",
            onSave = { n, m, e ->
                teamViewModel.saveProfile(n, m, e)
                showEditProfile = false
            },
            onDismiss = { showEditProfile = false }
        )
    }

    removeTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove member?") },
            text = { Text("${m.name.ifBlank { m.mobile }} will no longer receive team data. Data already on their phone stays there.") },
            confirmButton = {
                Button(
                    onClick = { teamViewModel.removeMember(m); removeTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RoleChip(role: TeamRole?, loading: Boolean, rootAdmin: Boolean) {
    val (text, color) = when {
        loading -> "…" to MaterialTheme.colorScheme.surfaceVariant
        role == TeamRole.ADMIN -> (if (rootAdmin) "Admin (root)" else "Admin") to Color(0xFFFFE0B2)
        role == TeamRole.MANAGER -> "Manager" to Color(0xFFDCE8FB)
        role == TeamRole.USER -> "User" to Color(0xFFDCEFD9)
        else -> "Not in team" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun MemberRow(
    member: TeamHub.Member,
    isMe: Boolean,
    canManage: Boolean,
    onChangeRole: (TeamRole) -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                member.name.ifBlank { member.mobile } + if (isMe) " (you)" else "",
                fontWeight = FontWeight.SemiBold
            )
            Text(member.mobile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canManage) {
            Box {
                TextButton(onClick = { menu = true }) { Text(member.role.label + " ▾") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    TeamRole.entries.forEach { r ->
                        DropdownMenuItem(text = { Text(r.label) }, onClick = { menu = false; onChangeRole(r) })
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            Text(member.role.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun AdminLoginDialog(onLogin: (String, String) -> Unit, onDismiss: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        },
        confirmButton = { Button(onClick = { onLogin(user, pass) }, enabled = user.isNotBlank() && pass.isNotBlank()) { Text("Login") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Picks one phone number from Contacts. Uses the system picker, so no READ_CONTACTS
 *  permission is needed — the app only ever sees the one number the user picks. */
private class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private fun readPickedContact(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
        null, null, null
    )?.use { c ->
        if (!c.moveToFirst()) null else (c.getString(0) ?: "") to (c.getString(1) ?: "")
    }
}.getOrNull()

@Composable
private fun AddMemberDialog(
    roles: List<TeamRole>,
    onAdd: (mobile: String, name: String, role: TeamRole) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mobile by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(if (TeamRole.USER in roles) TeamRole.USER else roles.firstOrNull()) }
    val contactLauncher = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        if (uri != null) {
            readPickedContact(context, uri)?.let { (number, displayName) ->
                mobile = number
                if (name.isBlank()) name = displayName
            }
        }
    }
    val valid = UserProfileStore.isValidMobile(mobile) && role != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add team member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { contactLauncher.launch(Unit) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Contacts, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick from Contacts")
                }
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = mobile.isNotBlank() && !UserProfileStore.isValidMobile(mobile),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = capitalizeFirstLetter(it) },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Access", style = MaterialTheme.typography.labelLarge)
                roles.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == r, onClick = { role = r })
                        Column {
                            Text(r.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (r) {
                                    TeamRole.ADMIN -> "All options"
                                    TeamRole.MANAGER -> "Can add members and upload data"
                                    TeamRole.USER -> "Read only"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { role?.let { onAdd(mobile, name, it) } }, enabled = valid) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Name / mobile / email form — used on first launch (Home) and from "Edit profile".
 * Mobile is required (it's how Team Sharing recognises the phone); email is optional and
 * registers the person for automatic app updates (Firebase App Distribution).
 */
@Composable
internal fun ProfileDialog(
    title: String,
    initial: UserProfileStore.Profile,
    dismissLabel: String,
    intro: String? = null,
    onSave: (name: String, mobile: String, email: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var mobile by remember { mutableStateOf(initial.mobile) }
    var email by remember { mutableStateOf(initial.email) }
    val mobileOk = UserProfileStore.isValidMobile(mobile)
    val emailOk = email.isBlank() || UserProfileStore.isValidEmail(email)

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                intro?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = name, onValueChange = { name = capitalizeFirstLetter(it) },
                    label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mobile, onValueChange = { mobile = it },
                    label = { Text("Mobile number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = mobile.isNotBlank() && !mobileOk,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim() },
                    label = { Text("Gmail / email (for app updates)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = !emailOk,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Use the Google account on this phone — new versions of the app are sent to it (Firebase App Distribution).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, mobile, email) }, enabled = mobileOk && emailOk) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
