package com.example.cricketscorer.sync

import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.data.BackupSnapshot
import com.example.cricketscorer.data.TeamRole
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Team Sharing (Share Data page) on Cloud Firestore — the same Firebase project Rooms already use.
 *
 *  team_members/{mobile}      who is in the team and their role (Admin / Manager / User)
 *  team_matches/{matchKey}    one document per shared match (its innings, balls and squads)
 *  team_squads/{publisher}    each publisher's squads + players + player merges
 *  tester_requests/{email}    emails to register as Firebase App Distribution testers
 *                             (picked up by the GitHub Actions build — see README)
 *  app_release/latest         newest build number, written by the same build, for the
 *                             in-app "update available" banner
 *
 * Like Rooms, there is no Firebase login: the phone's mobile number (entered on first launch)
 * is how a member is recognised. See firestore.rules / README for what that means.
 */
object TeamHub {

    private fun db() = FirebaseFirestore.getInstance()
    private fun members() = db().collection("team_members")
    private fun matches() = db().collection("team_matches")
    private fun squads() = db().collection("team_squads")

    data class Member(
        val mobile: String,
        val name: String,
        val role: TeamRole,
        val addedBy: String
    )

    // ---------------------------------------------------------------- members

    suspend fun getMember(mobile: String): Member? {
        if (mobile.isBlank()) return null
        val doc = members().document(mobile).get().await()
        if (!doc.exists()) return null
        val role = TeamRole.parse(doc.getString("role")) ?: return null
        return Member(mobile, doc.getString("name") ?: "", role, doc.getString("addedBy") ?: "")
    }

    fun listenMembers(onChange: (List<Member>) -> Unit): ListenerRegistration =
        members().addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            onChange(
                snap.documents.mapNotNull { d ->
                    val role = TeamRole.parse(d.getString("role")) ?: return@mapNotNull null
                    Member(d.id, d.getString("name") ?: "", role, d.getString("addedBy") ?: "")
                }.sortedWith(compareBy({ it.role.ordinal }, { it.name.lowercase() }))
            )
        }

    suspend fun upsertMember(mobile: String, name: String, role: TeamRole, addedBy: String) {
        members().document(mobile).set(
            mapOf(
                "mobile" to mobile,
                "name" to name,
                "role" to role.name,
                "addedBy" to addedBy,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun removeMember(mobile: String) {
        members().document(mobile).delete().await()
    }

    // ---------------------------------------------------------------- data

    /** Stable id for a match on every phone: when it was created + both team names. */
    fun matchKey(createdAt: Long, teamA: String, teamB: String): String {
        val teams = (teamA.trim().lowercase() + "|" + teamB.trim().lowercase()).hashCode().toUInt().toString(16)
        return "${createdAt}_$teams"
    }

    suspend fun publishMatch(key: String, snapshot: BackupSnapshot, publisher: String) {
        matches().document(key).set(
            mapOf(
                "payload" to BackupSerializer.toJson(snapshot),
                "publishedBy" to publisher,
                "updatedAtMillis" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun publishSquads(snapshot: BackupSnapshot, publisher: String) {
        squads().document(publisher).set(
            mapOf(
                "payload" to BackupSerializer.toJson(snapshot),
                "updatedAtMillis" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * Everything published since [sinceMillis] (all of it when null), one snapshot per
     * document — each is imported separately because different publishers' local row ids
     * overlap and must not be mixed in one import.
     */
    suspend fun fetchSince(sinceMillis: Long?): List<BackupSnapshot> {
        fun query(col: com.google.firebase.firestore.CollectionReference): Query =
            if (sinceMillis == null) col else col.whereGreaterThan("updatedAtMillis", sinceMillis)

        val docs = query(squads()).get().await().documents + query(matches()).get().await().documents
        return docs.mapNotNull { d ->
            d.getString("payload")?.let { runCatching { BackupSerializer.fromJson(it) }.getOrNull() }
        }
    }

    // ---------------------------------------------------------------- app updates

    /** Queues [email] to be added as an App Distribution tester by the next CI build. */
    suspend fun requestTesterAccess(email: String, name: String, mobile: String) {
        val key = email.trim().lowercase()
        db().collection("tester_requests").document(key).set(
            mapOf(
                "email" to key,
                "name" to name,
                "mobile" to mobile,
                "status" to "pending",
                "requestedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    data class Release(val versionCode: Int, val versionName: String, val notes: String, val url: String)

    suspend fun latestRelease(): Release? {
        val doc = db().collection("app_release").document("latest").get().await()
        if (!doc.exists()) return null
        val code = doc.getLong("versionCode")?.toInt() ?: return null
        return Release(
            versionCode = code,
            versionName = doc.getString("versionName") ?: code.toString(),
            notes = doc.getString("notes") ?: "",
            url = doc.getString("url") ?: "https://appdistribution.firebase.google.com/testerapps"
        )
    }
}
