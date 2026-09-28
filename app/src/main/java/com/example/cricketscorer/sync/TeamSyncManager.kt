package com.example.cricketscorer.sync

import android.content.Context
import com.example.cricketscorer.data.BackupDataScope
import com.example.cricketscorer.data.BackupSnapshot
import com.example.cricketscorer.data.CloudDeviceIdStore
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.RecentPlayersStore
import com.example.cricketscorer.data.TeamRole
import com.example.cricketscorer.data.UserProfileStore

/**
 * Team Sharing sync logic, shared by Home (automatic, on app open) and the Share Data page
 * ("Sync now").
 *  - pull: every phone whose mobile number is a team member downloads what's been published
 *    and merges it in (never deletes local data; duplicates are skipped) — this is what makes
 *    a freshly installed phone "automatically show all the data".
 *  - publish: Admin / Manager phones upload their matches and squads. Only matches that
 *    changed since the last upload are sent.
 */
class TeamSyncManager(
    private val repository: CricketRepository,
    private val context: Context
) {

    data class SyncResult(val downloaded: Int, val added: Int, val updated: Int, val published: Int) {
        fun describe(): String {
            val parts = mutableListOf<String>()
            if (added > 0) parts += "$added new match(es)"
            if (updated > 0) parts += "$updated updated match(es)"
            if (published > 0) parts += "$published match(es) uploaded"
            return if (parts.isEmpty()) "Everything is up to date." else parts.joinToString(", ") + "."
        }
    }

    /** Role of this phone: root admin login, else whatever the team list says for its number. */
    suspend fun effectiveRole(): TeamRole? {
        if (UserProfileStore.isAdminSession(context)) return TeamRole.ADMIN
        val mobile = UserProfileStore.get(context).mobile
        if (mobile.isBlank()) return null
        // Online: ask the team list and remember the answer. Offline: use the last answer.
        return runCatching { TeamHub.getMember(mobile)?.role }
            .onSuccess { UserProfileStore.setCachedRole(context, it) }
            .getOrElse { UserProfileStore.cachedRole(context) }
    }

    private fun publisherId(): String =
        UserProfileStore.get(context).mobile.ifBlank { "device-" + CloudDeviceIdStore.getDeviceId(context).take(8) }

    /** Full sync for [role]: publish (if allowed) then pull. */
    suspend fun sync(role: TeamRole?, fullPull: Boolean = false): SyncResult {
        if (role == null) return SyncResult(0, 0, 0, 0)
        val published = if (role.canPublish) publishChanged() else 0
        return pull(fullPull).copy(published = published)
    }

    suspend fun pull(full: Boolean = false): SyncResult {
        val startedAt = System.currentTimeMillis()
        // 10 minute overlap guards against phone clocks being slightly off; importing the
        // same data twice is harmless (duplicates are skipped).
        val since = if (full) null else UserProfileStore.lastTeamSync(context)?.minus(10 * 60_000L)
        val snapshots = TeamHub.fetchSince(since)
        var added = 0
        var updated = 0
        snapshots.forEach { snap ->
            val r = repository.importSharedSnapshot(snap, BackupDataScope.BOTH)
            added += r.matchesAdded
            updated += r.matchesUpdated
            RecentPlayersStore.addNames(context, snap.players.map { it.name })
        }
        UserProfileStore.setLastTeamSync(context, startedAt)
        return SyncResult(snapshots.size, added, updated, 0)
    }

    /** Uploads every local match whose content changed since this phone last uploaded it. */
    suspend fun publishChanged(): Int {
        val publisher = publisherId()
        val all = repository.getFullBackupSnapshot(BackupDataScope.BOTH)
        val done = UserProfileStore.publishedFingerprints(context).toMutableMap()
        var count = 0

        val squadsFp = fingerprintSquads(all)
        if (done["__squads"] != squadsFp && all.squads.isNotEmpty()) {
            TeamHub.publishSquads(
                BackupSnapshot(emptyList(), emptyList(), emptyList(), all.squads, all.players, all.playerMerges),
                publisher
            )
            done["__squads"] = squadsFp
        }

        val inningsByMatch = all.innings.groupBy { it.matchId }
        val ballCountByInnings = all.ballEvents.groupingBy { it.inningsId }.eachCount()
        for (match in all.matches) {
            val innings = inningsByMatch[match.matchId].orEmpty().sortedBy { it.inningsNumber }
            val balls = innings.sumOf { ballCountByInnings[it.inningsId] ?: 0 }
            if (balls == 0) continue // nothing scored yet — not worth sharing
            val key = TeamHub.matchKey(match.createdAt, match.teamAName, match.teamBName)
            val fp = "${match.isCompleted}|${match.resultSummary}|${match.currentInningsNumber}|$balls|" +
                innings.joinToString(",") { "${it.inningsNumber}:${it.totalRuns}/${it.wickets}/${it.completedOvers}.${it.ballsThisOver}" }
            if (done[key] == fp) continue
            val snapshot = repository.getSnapshotForMatch(match.matchId).copy(playerMerges = all.playerMerges)
            TeamHub.publishMatch(key, snapshot, publisher)
            done[key] = fp
            count++
        }
        UserProfileStore.setPublishedFingerprints(context, done)
        return count
    }

    private fun fingerprintSquads(s: BackupSnapshot): String =
        (s.squads.map { "${it.squadId}:${it.teamName}" }.sorted() +
            s.players.map { "${it.squadId}:${it.name}" }.sorted() +
            s.playerMerges.map { "${it.fromName}>${it.toName}" }.sorted()).hashCode().toString()
}
