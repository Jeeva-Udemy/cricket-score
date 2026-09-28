package com.example.cricketscorer.stats

import com.example.cricketscorer.data.PlayerMergeEntity
import java.util.Locale

/**
 * How the app decides whether two name entries are "the same player".
 *
 * Bug fix (Rankings): players used to be grouped by name alone, so two different people
 * called e.g. "Ravi" in two different teams were combined into one row showing both teams.
 * A player is now identified by NAME + TEAM:
 *  - name and team are compared ignoring case and extra spaces ("ravi " == "Ravi")
 *  - the same name in a different team is a different player
 *  - anything else (spelling mistakes, the same person playing for two teams) is combined
 *    explicitly by the user with "Merge players", stored as [PlayerMergeEntity] rows.
 */
object PlayerIdentity {

    /** Trims and collapses inner whitespace — used for display. */
    fun clean(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    /** Case/space-insensitive form used for comparisons. */
    fun keyPart(value: String): String = clean(value).lowercase(Locale.ROOT)

    /** Stable identity for a player entry. */
    fun key(name: String, team: String): String = keyPart(name) + "|" + keyPart(team)
}

/** A player as seen by stats: [name] + [team] after applying merges. */
data class ResolvedPlayer(val name: String, val team: String, val wasMerged: Boolean) {
    val key: String get() = PlayerIdentity.key(name, team)
}

/**
 * Applies "Merge players" rows. Follows chains (A -> B, B -> C gives A -> C) and is safe
 * against cycles, so the order merges were made in never matters.
 */
class PlayerMergeResolver(merges: List<PlayerMergeEntity>) {

    // Latest merge for a given source wins (merges are passed oldest first).
    private val bySource: Map<String, PlayerMergeEntity> =
        merges.sortedBy { it.createdAt }.associateBy { PlayerIdentity.key(it.fromName, it.fromTeam) }

    fun resolve(name: String, team: String): ResolvedPlayer {
        var currentName = PlayerIdentity.clean(name)
        var currentTeam = PlayerIdentity.clean(team)
        var merged = false
        val seen = mutableSetOf(PlayerIdentity.key(currentName, currentTeam))
        repeat(32) {
            val merge = bySource[PlayerIdentity.key(currentName, currentTeam)] ?: return ResolvedPlayer(currentName, currentTeam, merged)
            currentName = PlayerIdentity.clean(merge.toName)
            currentTeam = PlayerIdentity.clean(merge.toTeam)
            merged = true
            // A merge that only fixes capitalisation ("ravi" -> "Ravi") maps a key onto
            // itself — stop there instead of looping.
            if (!seen.add(PlayerIdentity.key(currentName, currentTeam))) {
                return ResolvedPlayer(currentName, currentTeam, merged)
            }
        }
        return ResolvedPlayer(currentName, currentTeam, merged)
    }

    companion object {
        val NONE = PlayerMergeResolver(emptyList())
    }
}

/**
 * Suggests likely spelling-mismatch pairs for the Merge screen ("Jeeva" vs "Jeevaa",
 * "Rahul" vs "Raul"). Purely a hint — nothing is merged unless the user confirms.
 */
object DuplicateNameSuggester {

    data class Suggestion(val first: PlayerStatsCalculator.PlayerCareerStats, val second: PlayerStatsCalculator.PlayerCareerStats)

    fun suggest(players: List<PlayerStatsCalculator.PlayerCareerStats>, limit: Int = 10): List<Suggestion> {
        val result = mutableListOf<Suggestion>()
        for (i in players.indices) {
            for (j in i + 1 until players.size) {
                val a = players[i]
                val b = players[j]
                if (looksLikeSamePerson(a.playerName, b.playerName)) {
                    result += Suggestion(a, b)
                    if (result.size >= limit) return result
                }
            }
        }
        return result
    }

    fun looksLikeSamePerson(a: String, b: String): Boolean {
        val x = lettersOnly(a)
        val y = lettersOnly(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true // differs only by case/spaces/dots, e.g. "M.S Dhoni" / "MS Dhoni"
        val shorter = minOf(x.length, y.length)
        if (shorter < 3) return false
        // Short names: only a 1-letter slip with the same first letter ("Ravi"/"Ravii", not
        // "Ram"/"Sam"). Longer names allow 2 edits ("Jeeva"/"Jeevaa", "Rahul"/"Rahull").
        if (shorter <= 4) return x[0] == y[0] && levenshtein(x, y) <= 1
        return levenshtein(x, y) <= 2
    }

    private fun lettersOnly(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    internal fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }
}
