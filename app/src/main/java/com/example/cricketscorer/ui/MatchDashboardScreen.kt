package com.example.cricketscorer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.backup.ShareUtils
import com.example.cricketscorer.viewmodel.MatchDashboardViewModel
import kotlinx.coroutines.launch

/**
 * Match Dashboard: the whole match (result, both innings' scorecards, Player of the Match,
 * top batters/bowlers) as ONE image, previewed here and shared straight to WhatsApp — no
 * more stitching screenshots of each tab together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDashboardScreen(
    viewModel: MatchDashboardViewModel,
    matchId: Long,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(matchId) { viewModel.load(matchId) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun share(preferWhatsApp: Boolean) {
        scope.launch {
            val uri = viewModel.imageUri() ?: return@launch
            ShareUtils.shareFile(
                context = context,
                uri = uri,
                mimeType = "image/png",
                message = viewModel.shareMessage(),
                chooserTitle = "Share match summary",
                preferWhatsApp = preferWhatsApp
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Match Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load(matchId, force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { share(preferWhatsApp = false) }, enabled = state.image != null) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        },
        bottomBar = {
            if (state.image != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { share(preferWhatsApp = true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("WhatsApp")
                        }
                        OutlinedButton(onClick = { share(preferWhatsApp = false) }, modifier = Modifier.weight(1f)) {
                            Text("Other apps")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                state.error != null -> Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp)
                )
                state.image != null -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    Image(
                        bitmap = state.image!!.asImageBitmap(),
                        contentDescription = "Match summary image",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
