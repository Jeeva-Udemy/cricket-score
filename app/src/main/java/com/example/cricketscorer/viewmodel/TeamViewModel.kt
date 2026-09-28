package com.example.cricketscorer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.TeamRole
import com.example.cricketscorer.data.UserProfileStore
import com.example.cricketscorer.sync.TeamHub
import com.example.cricketscorer.sync.TeamSyncManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Share Data page: Team Sharing by mobile number with Admin / Manager / User access.
 *  - Admin (root/root login, or a number the team made Admin): add/remove members, change
 *    roles, upload data.
 *  - Manager: add members (as Manager or User) and upload data.
 *  - User: read only — sees the team list and receives data automatically.
 */
class TeamViewModel(
    private val repository: CricketRepository,
    private val appContext: Context
) : ViewModel() {

    data class UiState(
        val profile: UserProfileStore.Profile = UserProfileStore.Profile("", "", ""),
        val role: TeamRole? = null,
        val isRootAdmin: Boolean = false,
        val roleLoading: Boolean = true,
        val members: List<TeamHub.Member> = emptyList(),
        val busy: Boolean = false,
        val message: String? = null,
        val error: String? = null,
        val lastSync: Long? = null
    )

    private val sync = TeamSyncManager(repository, appContext)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var membersListener: ListenerRegistration? = null

    init {
        refreshRole()
        membersListener = runCatching {
            TeamHub.listenMembers { list ->
                _state.value = _state.value.copy(members = list)
                // Role changes made by an admin on another phone apply here straight away.
                val mine = list.firstOrNull { it.mobile == _state.value.profile.mobile }
                if (!_state.value.isRootAdmin) _state.value = _state.value.copy(role = mine?.role, roleLoading = false)
            }
        }.getOrNull()
    }

    override fun onCleared() {
        membersListener?.remove()
    }

    fun refreshRole() {
        val profile = UserProfileStore.get(appContext)
        _state.value = _state.value.copy(
            profile = profile,
            isRootAdmin = UserProfileStore.isAdminSession(appContext),
            lastSync = UserProfileStore.lastTeamSync(appContext),
            roleLoading = true
        )
        viewModelScope.launch {
            val role = sync.effectiveRole()
            _state.value = _state.value.copy(role = role, roleLoading = false)
        }
    }

    fun saveProfile(name: String, mobile: String, email: String) {
        val old = UserProfileStore.get(appContext)
        UserProfileStore.save(appContext, name, mobile, email)
        if (email.isNotBlank() && !email.equals(old.email, ignoreCase = true)) {
            viewModelScope.launch {
                runCatching { TeamHub.requestTesterAccess(email, name, UserProfileStore.normalizeMobile(mobile)) }
            }
        }
        refreshRole()
        toast("Profile saved.")
    }

    /** "root" / "root" — turns this phone into an Admin until logged out. */
    fun adminLogin(username: String, password: String): Boolean {
        if (!UserProfileStore.checkAdminCredentials(username, password)) {
            _state.value = _state.value.copy(error = "Wrong username or password.")
            return false
        }
        UserProfileStore.setAdminSession(appContext, true)
        refreshRole()
        toast("Logged in as Admin.")
        return true
    }

    fun adminLogout() {
        UserProfileStore.setAdminSession(appContext, false)
        refreshRole()
        toast("Logged out of Admin.")
    }

    fun addMember(mobileRaw: String, name: String, role: TeamRole) {
        val me = _state.value.role
        if (me == null || role !in me.assignableRoles) {
            _state.value = _state.value.copy(error = "You don't have permission to add a ${role.label}.")
            return
        }
        val mobile = UserProfileStore.normalizeMobile(mobileRaw)
        if (mobile.length != 10) {
            _state.value = _state.value.copy(error = "Enter a valid 10-digit mobile number.")
            return
        }
        val existing = _state.value.members.firstOrNull { it.mobile == mobile }
        if (existing != null && me != TeamRole.ADMIN) {
            _state.value = _state.value.copy(error = "$mobile is already in the team as ${existing.role.label}.")
            return
        }
        runAction("Added ${name.ifBlank { mobile }} as ${role.label}.") {
            TeamHub.upsertMember(mobile, name.trim(), role, addedBy())
        }
    }

    fun changeRole(member: TeamHub.Member, role: TeamRole) {
        if (_state.value.role != TeamRole.ADMIN) return
        runAction("${member.name.ifBlank { member.mobile }} is now ${role.label}.") {
            TeamHub.upsertMember(member.mobile, member.name, role, member.addedBy)
        }
    }

    fun removeMember(member: TeamHub.Member) {
        if (_state.value.role != TeamRole.ADMIN) return
        runAction("Removed ${member.name.ifBlank { member.mobile }}.") { TeamHub.removeMember(member.mobile) }
    }

    fun syncNow(full: Boolean = false) {
        val role = _state.value.role ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { sync.sync(role, fullPull = full) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        busy = false,
                        message = it.describe(),
                        lastSync = UserProfileStore.lastTeamSync(appContext)
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(busy = false, error = "Sync failed — check your internet connection. (${it.message})")
                }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    private fun addedBy(): String =
        _state.value.profile.mobile.ifBlank { if (_state.value.isRootAdmin) "root" else "" }

    private fun toast(msg: String) {
        _state.value = _state.value.copy(message = msg)
    }

    private fun runAction(success: String, block: suspend () -> Unit) {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _state.value = _state.value.copy(busy = false, message = success) }
                .onFailure {
                    _state.value = _state.value.copy(busy = false, error = "Couldn't save — check your internet connection. (${it.message})")
                }
        }
    }
}
