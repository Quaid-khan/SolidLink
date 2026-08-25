package com.solidlink.app

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solidlink.files.FilePickerIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun HomeScreen(
    modifier: Modifier = Modifier,
    solidLinkViewModel: SolidLinkViewModel = viewModel(),
) {
    val state by solidLinkViewModel.state.collectAsStateWithLifecycle()
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            solidLinkViewModel.setSelectedUris(result.data?.selectedUris().orEmpty())
        }
    }

    LaunchedEffect(Unit) {
        solidLinkViewModel.startLocalDiscovery()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("SolidLink") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Private transfers. No cloud relay.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Move files over the same local Wi-Fi network. SolidLink does not upload your files or use cellular fallback.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (state.isRunning) "Local discovery is active" else "Local discovery is stopped",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(state.status, style = MaterialTheme.typography.bodyMedium)
                        state.error?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.isRunning) {
                                OutlinedButton(onClick = solidLinkViewModel::stopLocalDiscovery) {
                                    Text("Stop")
                                }
                            } else {
                                Button(onClick = solidLinkViewModel::startLocalDiscovery) {
                                    Text("Find nearby peers")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("1. Select files", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (state.selectedUris.isEmpty()) {
                                "Files stay in your control. Choose them only when you are ready to send."
                            } else {
                                "${state.selectedUris.size} file${if (state.selectedUris.size == 1) "" else "s"} ready to send."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { documentPicker.launch(FilePickerIntents.openDocuments()) }) {
                                Text("Choose files")
                            }
                            if (state.selectedUris.isNotEmpty()) {
                                OutlinedButton(onClick = { solidLinkViewModel.setSelectedUris(emptyList()) }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("2. Nearby peers", style = MaterialTheme.typography.titleMedium)
                        if (state.peers.isEmpty()) {
                            Text(
                                "Open SolidLink on the other device and keep both devices on the same Wi-Fi network. Discovery is local-only.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (state.peers.isNotEmpty()) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            state.peers.forEach { peerRow ->
                                PeerRowItem(peerRow, onConnect = { solidLinkViewModel.connect(peerRow) })
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Local-only protection", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "SolidLink accepts only peers discovered on the local link. Public addresses, Internet routes, cloud relays, and cellular fallback are rejected before file bytes are sent.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Peer-to-peer smoke path",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "A successful connection performs a Protobuf HELLO exchange. Full authenticated transfer and iPhone transport remain separate native implementation work and are not represented as completed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PeerRowItem(peerRow: PeerRow, onConnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(peerRow.peer.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${peerRow.peer.endpoint.hostAddress}:${peerRow.peer.endpoint.port} · LAN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (peerRow.isConnecting) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else {
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

private fun android.content.Intent.selectedUris(): List<Uri> {
    val clip = clipData
    return when {
        clip != null -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        data != null -> listOfNotNull(data)
        else -> emptyList()
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme { HomeScreen(solidLinkViewModel = viewModel()) }
}
