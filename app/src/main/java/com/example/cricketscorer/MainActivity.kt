package com.example.cricketscorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cricketscorer.ui.ComingSoonScreen
import com.example.cricketscorer.ui.HomeScreen
import com.example.cricketscorer.ui.MatchSetupScreen
import com.example.cricketscorer.ui.ScoringScreen
import com.example.cricketscorer.ui.SquadScreen
import com.example.cricketscorer.viewmodel.HomeViewModel
import com.example.cricketscorer.viewmodel.MatchSetupViewModel
import com.example.cricketscorer.viewmodel.ScoringViewModel
import com.example.cricketscorer.viewmodel.SquadViewModel
import com.example.cricketscorer.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as CricketApplication
        val factory = ViewModelFactory(app.repository, applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CricketNavHost(factory)
                }
            }
        }
    }
}

@Composable
fun CricketNavHost(factory: ViewModelFactory) {
    val navController = rememberNavController()

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
                onTournaments = { navController.navigate("tournaments") }
            )
        }

        composable("playerStats") {
            ComingSoonScreen(title = "Player Stats", onNavigateBack = { navController.popBackStack() })
        }

        composable("rankings") {
            ComingSoonScreen(title = "Rankings", onNavigateBack = { navController.popBackStack() })
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

        composable("setup") {
            val setupViewModel: MatchSetupViewModel = viewModel(factory = factory)
            MatchSetupScreen(
                viewModel = setupViewModel,
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
                }
            )
        }
    }
}
