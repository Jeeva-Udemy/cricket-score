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
import com.example.cricketscorer.ui.MatchSetupScreen
import com.example.cricketscorer.ui.ScoringScreen
import com.example.cricketscorer.viewmodel.MatchSetupViewModel
import com.example.cricketscorer.viewmodel.ScoringViewModel
import com.example.cricketscorer.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as CricketApplication
        val factory = ViewModelFactory(app.repository)

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

    NavHost(navController = navController, startDestination = "setup") {

        composable("setup") {
            val setupViewModel: MatchSetupViewModel = viewModel(factory = factory)
            MatchSetupScreen(
                viewModel = setupViewModel,
                onMatchStarted = { matchId, inningsId ->
                    navController.navigate("scoring/$matchId/$inningsId")
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
            // Note: ScoringViewModel internally re-observes the 2nd innings once
            // it's created, so the nav route does not need to change mid-match.
            val scoringViewModel: ScoringViewModel = viewModel(factory = factory)
            ScoringScreen(
                viewModel = scoringViewModel,
                matchId = matchId,
                inningsId = inningsId
            )
        }
    }
}
