package com.example.cricketscorer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cricketscorer.ui.ComingSoonScreen
import com.example.cricketscorer.ui.HomeScreen
import com.example.cricketscorer.ui.MatchDashboardScreen
import com.example.cricketscorer.ui.MatchHistoryScreen
import com.example.cricketscorer.ui.MatchSetupScreen
import com.example.cricketscorer.ui.PlayerStatsScreen
import com.example.cricketscorer.ui.RankingsScreen
import com.example.cricketscorer.ui.RoomDetailScreen
import com.example.cricketscorer.ui.RoomsScreen
import com.example.cricketscorer.ui.ScoringScreen
import com.example.cricketscorer.ui.SquadScreen
import com.example.cricketscorer.viewmodel.HomeViewModel
import com.example.cricketscorer.viewmodel.MatchDashboardViewModel
import com.example.cricketscorer.viewmodel.MatchSetupViewModel
import com.example.cricketscorer.viewmodel.PlayerStatsViewModel
import com.example.cricketscorer.viewmodel.RoomsViewModel
import com.example.cricketscorer.viewmodel.ScoringViewModel
import com.example.cricketscorer.viewmodel.SquadViewModel
import com.example.cricketscorer.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {

    /** Share Data: a Wickt data file opened from WhatsApp etc. (VIEW / SEND intent). */
    private val pendingImportUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncomingIntent(intent)

        val app = application as CricketApplication
        val factory = ViewModelFactory(app.repository, applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CricketNavHost(
                        factory = factory,
                        pendingImportUri = pendingImportUri.value,
                        onImportConsumed = { pendingImportUri.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
        if (uri != null) pendingImportUri.value = uri
    }
}

@Composable
fun CricketNavHost(
    factory: ViewModelFactory,
    pendingImportUri: Uri? = null,
    onImportConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    // A shared data file was opened from outside the app — show the import prompt on Home.
    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null && navController.currentDestination?.route != "home") {
            navController.navigate("home") { popUpTo("home") { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = homeViewModel,
                onStartNewMatch = { navController.navigate("setup") },
                onOpenMatch = { matchId -> navController.navigate("scoring/$matchId/0") },
                onManageSquads = { navController.navigate("squads") },
                onPlayerStats = { navController.navigate("playerStats") },
                onRankings = { navController.navigate("rankings") },
                onTournaments = { navController.navigate("tournaments") },
                onMatchHistory = { navController.navigate("matchHistory") },
                onOpenRooms = { navController.navigate("rooms") },
                externalImportUri = pendingImportUri,
                onExternalImportConsumed = onImportConsumed
            )
        }

        composable("rooms") {
            val roomsViewModel: RoomsViewModel = viewModel(factory = factory)
            RoomsScreen(
                viewModel = roomsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenRoom = { code -> navController.navigate("roomDetail/$code") }
            )
        }

        composable(
            route = "roomDetail/{code}",
            arguments = listOf(navArgument("code") { type = NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code") ?: ""
            val roomsViewModel: RoomsViewModel = viewModel(factory = factory)
            RoomDetailScreen(
                viewModel = roomsViewModel,
                roomCode = code,
                onNavigateBack = { navController.popBackStack() },
                // req #5: a match started from inside a Room carries that room's code so it
                // becomes a Room match — unlike Home's plain "Start Match" below, which never
                // passes a roomCode and so always creates a purely local, single-device match.
                onStartMatch = { navController.navigate("setup?roomCode=$code") },
                onOpenMatch = { matchId -> navController.navigate("scoring/$matchId/0") }
            )
        }

        composable("matchHistory") {
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            MatchHistoryScreen(
                viewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenMatch = { matchId -> navController.navigate("scoring/$matchId/0") },
                onOpenDashboard = { matchId -> navController.navigate("dashboard/$matchId") }
            )
        }

        composable(
            route = "dashboard/{matchId}",
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: -1L
            val dashboardViewModel: MatchDashboardViewModel = viewModel(factory = factory)
            MatchDashboardScreen(
                viewModel = dashboardViewModel,
                matchId = matchId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("playerStats") {
            val playerStatsViewModel: PlayerStatsViewModel = viewModel(factory = factory)
            PlayerStatsScreen(
                viewModel = playerStatsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("rankings") {
            val playerStatsViewModel: PlayerStatsViewModel = viewModel(factory = factory)
            RankingsScreen(
                viewModel = playerStatsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("tournaments") {
            ComingSoonScreen(title = "Tournaments", onNavigateBack = { navController.popBackStack() })
        }

        composable("squads") {
            val squadViewModel: SquadViewModel = viewModel(factory = factory)
            SquadScreen(
                viewModel = squadViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            // req #5: roomCode is an optional query-style arg — Home's "Start Match" navigates
            // to plain "setup" (no roomCode, so this defaults to null and the match stays
            // local/single-device); Room Detail's "Start Match" navigates to
            // "setup?roomCode=<code>" so MatchSetupViewModel.configureForRoom ties the new
            // match to that room instead.
            route = "setup?roomCode={roomCode}",
            arguments = listOf(
                navArgument("roomCode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode")
            val setupViewModel: MatchSetupViewModel = viewModel(factory = factory)
            MatchSetupScreen(
                viewModel = setupViewModel,
                roomCode = roomCode,
                onNavigateBack = { navController.popBackStack() },
                onManageSquads = { navController.navigate("squads") },
                onMatchStarted = { matchId, inningsId ->
                    navController.navigate("scoring/$matchId/$inningsId") {
                        popUpTo("home")
                    }
                }
            )
        }

        composable(
            route = "scoring/{matchId}/{inningsId}",
            arguments = listOf(
                navArgument("matchId") { type = NavType.LongType },
                navArgument("inningsId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: -1L
            val inningsId = backStackEntry.arguments?.getLong("inningsId") ?: -1L
            val scoringViewModel: ScoringViewModel = viewModel(factory = factory)
            ScoringScreen(
                viewModel = scoringViewModel,
                matchId = matchId,
                inningsId = inningsId,
                onNavigateBack = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onOpenDashboard = { id -> navController.navigate("dashboard/$id") }
            )
        }
    }
}
