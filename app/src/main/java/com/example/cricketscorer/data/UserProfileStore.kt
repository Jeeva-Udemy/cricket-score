package com.example.cricketscorer.data

import android.content.Context
import java.security.MessageDigest

/** Team Sharing access levels (Share Data page). */
enum class TeamRole(val label: String) {
    /** Everything: add/remove members, change roles, publish team data. */
    ADMIN("Admin"),
    /** Can add members and publish their matches/squads to the team. */
    MANAGER("Manager"),
    /** Read only: receives the team's matches and squads automatically. */
    USER("User");

    val canAddMembers: Boolean get() = this == ADMIN || this == MANAGER
    val canManageMembers: Boolean get() = this == ADMIN
    val canPublish: Boolean get() = this == ADMIN || this == MANAGER

    /** Roles this role may give to someone it adds. */
    val assignableRoles: List<TeamRole>
        get() = when (this) {
            ADMIN -> listOf(ADMIN, MANAGER, USER)
            MANAGER -> listOf(MANAGER, USER)
            USER -> emptyList()
        }

    companion object {
        fun parse(value: String?): TeamRole? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Who is using this phone: name, mobile number and email, entered once on first launch.
 * The mobile number is how Team Sharing recognises the phone (a teammate's admin adds that
 * number, and the team's data then appears automatically); the email registers the person as
 * a Firebase App Distribution tester so new versions reach them automatically.
 */
object UserProfileStore {
    private const val PREFS = "user_profile"
    private const val KEY_NAME = "name"
    private const val KEY_MOBILE = "mobile"
    private const val KEY_EMAIL = "email"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_ADMIN_SESSION = "root_admin_session"
    private const val KEY_LAST_TEAM_SYNC = "last_team_sync"
    private const val KEY_PUBLISHED = "published_fingerprints"

    // "root" / "root" — stored as SHA-256 so the plain text isn't sitting in the source.
    // NOTE: anything shipped inside the app can still be extracted by a determined person;
    // change these (see README) and treat this as a convenience lock, not real security.
    private const val ADMIN_USER_HASH = "4813494d137e1631bba301d5acab6e7bb7aa74ce1185d456565ef51d737677b2"
    private const val ADMIN_PASS_HASH = "4813494d137e1631bba301d5acab6e7bb7aa74ce1185d456565ef51d737677b2"

    data class Profile(val name: String, val mobile: String, val email: String)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(c: Context): Profile = prefs(c).let {
        Profile(it.getString(KEY_NAME, "") ?: "", it.getString(KEY_MOBILE, "") ?: "", it.getString(KEY_EMAIL, "") ?: "")
    }

    fun save(c: Context, name: String, mobile: String, email: String) {
        prefs(c).edit()
            .putString(KEY_NAME, name.trim())
            .putString(KEY_MOBILE, normalizeMobile(mobile))
            .putString(KEY_EMAIL, email.trim())
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    fun isOnboarded(c: Context): Boolean = prefs(c).getBoolean(KEY_ONBOARDED, false)

    /** Lets "Skip" dismiss the first-launch prompt without asking again every time. */
    fun markOnboarded(c: Context) = prefs(c).edit().putBoolean(KEY_ONBOARDED, true).apply()

    // ---- root admin login ----

    fun checkAdminCredentials(username: String, password: String): Boolean =
        sha256(username.trim()) == ADMIN_USER_HASH && sha256(password) == ADMIN_PASS_HASH

    fun isAdminSession(c: Context): Boolean = prefs(c).getBoolean(KEY_ADMIN_SESSION, false)
    fun setAdminSession(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_ADMIN_SESSION, on).apply()

    // ---- last known Team Sharing role (so Admin/Manager-only features work offline) ----

    fun cachedRole(c: Context): TeamRole? = TeamRole.parse(prefs(c).getString("cached_role", null))
    fun setCachedRole(c: Context, role: TeamRole?) =
        prefs(c).edit().putString("cached_role", role?.name).apply()

    // ---- sync bookkeeping ----

    fun lastTeamSync(c: Context): Long? = prefs(c).getLong(KEY_LAST_TEAM_SYNC, 0L).takeIf { it > 0 }
    fun setLastTeamSync(c: Context, t: Long) = prefs(c).edit().putLong(KEY_LAST_TEAM_SYNC, t).apply()

    /** matchKey -> content fingerprint of what this phone last published. */
    fun publishedFingerprints(c: Context): Map<String, String> =
        (prefs(c).getStringSet(KEY_PUBLISHED, emptySet()) ?: emptySet())
            .mapNotNull { e -> e.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()

    fun setPublishedFingerprints(c: Context, map: Map<String, String>) =
        prefs(c).edit().putStringSet(KEY_PUBLISHED, map.map { "${it.key}=${it.value}" }.toSet()).apply()

    /** Digits only, last 10 digits (drops +91 / leading 0), so "+91 98765 43210" == "09876543210". */
    fun normalizeMobile(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun isValidMobile(raw: String): Boolean = normalizeMobile(raw).length == 10

    fun isValidEmail(raw: String): Boolean =
        Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(raw.trim())

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
