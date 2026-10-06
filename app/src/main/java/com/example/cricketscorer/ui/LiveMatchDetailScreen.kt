package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.data.BackupSnapshot
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType
import com.example.cricketscorer.stats.ScorecardCalculator
import com.example.cricketscorer.sync.CloudSync

/**
 * Read-only view of a match from the Live Match tab: the same innings details (Scorecard and
 * Overs) as the scoring screen, but with no scoring controls at all. Updates in real time while
 * the match is being scored on another phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMatchDetailScreen(code: String, onNavigateBack: () -> Unit) {
    var snapshot by remember { mutableStateOf<BackupSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedInnings by remember { mutableStateOf<Int?>(null) }
    var subTab by remember { mutableStateOf(0) }

    DisposableEffect(code) {
        val reg = runCatching {
            CloudSync.listenLiveSnapshot(
                code = code,
                onUpdate = { snapshot = it; error = null },
                onError = { error = it }
            )
        }.getOrNull()
        onDispose { reg?.remove() }
    }

    val match = snapshot?.matches?.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (match != null) "${match.teamAName} vs ${match.teamBName}" else "Live Match") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        when {
            snapshot == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            snapshot == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                val snap = snapshot!!
                val innings = snap.innings.filter { it.matchId == match?.matchId }.sortedBy { it.inningsNumber }
                if (match == null || innings.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Waiting for the first innings to start…")
                    }
                    return@Column
                }
                val activeIdx = selectedInnings?.takeIf { it in innings.indices }
                    ?: innings.indexOfLast { !it.isCompleted }.takeIf { it >= 0 }
                    ?: innings.lastIndex
                val inn = innings[activeIdx]
                val balls = snap.ballEvents.filter { it.inningsId == inn.inningsId }.sortedBy { it.ballId }

                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            if (match.isCompleted) (match.resultSummary ?: "Match completed") else "● LIVE  (view only)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        innings.forEach { i ->
                            Text(
                                "${i.battingTeam}${if (i.isSuperOver) " (Super Over)" else ""}: " +
                                    "${i.totalRuns}/${i.wickets} (${i.completedOvers}.${i.ballsThisOver})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (innings.size > 1) {
                    ScrollableTabRow(selectedTabIndex = activeIdx, edgePadding = 0.dp) {
                        innings.forEachIndexed { idx, i ->
                            Tab(
                                selected = idx == activeIdx,
                                onClick = { selectedInnings = idx },
                                text = {
                                    Text(if (i.isSuperOver) "Super Over: ${i.battingTeam}" else "Inn ${i.inningsNumber}: ${i.battingTeam}")
                                }
                            )
                        }
                    }
                }
                TabRow(selectedTabIndex = subTab) {
                    Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Scorecard") })
                    Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Overs") })
                }
                if (subTab == 0) LiveScorecard(inn.battingTeam, inn.bowlingTeam, inn, balls)
                else LiveOvers(balls)
            }
        }
    }
}

@Composable
private fun LiveScorecard(
    battingTeam: String,
    bowlingTeam: String,
    innings: com.example.cricketscorer.data.InningsEntity,
    balls: List<BallEventEntity>
) {
    val batting = ScorecardCalculator.battingLines(innings, balls)
    val bowling = ScorecardCalculator.bowlingLines(balls)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Batting — $battingTeam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Batsman", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                    Text("R", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("B", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("4s", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("6s", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("SR", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Divider()
                batting.forEach { b ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(2f)) {
                            Text(b.name, fontWeight = FontWeight.SemiBold)
                            Text(b.dismissal, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${b.runs}", modifier = Modifier.weight(0.7f))
                        Text("${b.balls}", modifier = Modifier.weight(0.7f))
                        Text("${b.fours}", modifier = Modifier.weight(0.7f))
                        Text("${b.sixes}", modifier = Modifier.weight(0.7f))
                        Text("%.1f".format(b.strikeRate), modifier = Modifier.weight(1f))
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Extras", fontWeight = FontWeight.SemiBold)
                    Text("${ScorecardCalculator.extrasTotal(innings)}")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(
                        "${innings.totalRuns}/${innings.wickets} (${innings.completedOvers}.${innings.ballsThisOver})",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text("Bowling — $bowlingTeam", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bowler", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                    Text("O", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("M", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("R", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("W", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("Econ", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Divider()
                bowling.forEach { bw ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bw.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f))
                        Text(bw.overs, modifier = Modifier.weight(0.7f))
                        Text("${bw.maidens}", modifier = Modifier.weight(0.7f))
                        Text("${bw.runs}", modifier = Modifier.weight(0.7f))
                        Text("${bw.wickets}", modifier = Modifier.weight(0.7f))
                        Text("%.1f".format(bw.economy), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveOvers(balls: List<BallEventEntity>) {
    if (balls.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No balls bowled yet in this innings.")
        }
        return
    }
    val grouped = balls.groupBy { it.overNumber }.toSortedMap().entries.toList()
    var runAcc = 0
    var wktAcc = 0
    val overs = grouped.map { (overNum, overBalls) ->
        val runs = overBalls.sumOf { it.runsScored + it.extraRuns }
        val wkts = overBalls.count { it.isWicket }
        runAcc += runs; wktAcc += wkts
        LiveOver(overNum + 1, overBalls.firstOrNull()?.bowlerName ?: "Bowler", runs, wkts, runAcc, wktAcc, overBalls)
    }.reversed() // newest over first, so the latest action is on top

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(overs) { o ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Over ${o.number}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Total: ${o.cumRuns}/${o.cumWkts}", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Bowler: ${o.bowler}  •  ${o.runs} runs, ${o.wkts} wkts",
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Divider()
                    o.balls.forEach { ball ->
                        val label = when {
                            ball.isWicket -> {
                                val out = ScorecardCalculator.dismissedName(ball)
                                val how = ball.wicketType.name.replace("_", " ").lowercase()
                                val extra = if (ball.wicketType == WicketType.RUN_OUT && ball.runsScored > 0) " +${ball.runsScored}" else ""
                                "Wicket: $out ($how)$extra"
                            }
                            ball.extraType == ExtraType.WIDE -> "Wide (+${ball.runsScored + ball.extraRuns} runs)"
                            ball.extraType == ExtraType.NO_BALL -> "No Ball (+${ball.runsScored + ball.extraRuns} runs)"
                            ball.extraType == ExtraType.BYE -> "Bye (${ball.runsScored} runs)"
                            ball.extraType == ExtraType.LEG_BYE -> "Leg Bye (${ball.runsScored} runs)"
                            ball.extraType == ExtraType.PENALTY -> "Penalty (+${ball.extraRuns} runs)"
                            ball.isOverthrow -> "${ball.runsScored} run(s) incl. overthrow"
                            else -> "${ball.runsScored} run(s)"
                        }
                        val batsman = ball.strikerName.ifBlank { "Batsman ${ball.strikerBatsmanNumber}" }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ball ${o.number - 1}.${ball.ballNumberInOver}: $batsman", fontSize = 13.sp)
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class LiveOver(
    val number: Int,
    val bowler: String,
    val runs: Int,
    val wkts: Int,
    val cumRuns: Int,
    val cumWkts: Int,
    val balls: List<BallEventEntity>
)
