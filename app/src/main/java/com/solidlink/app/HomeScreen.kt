package com.solidlink.app

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solidlink.files.FilePickerIntents

private val SolidLinkNavy = androidx.compose.ui.graphics.Color(0xFF071B3A)
private val SolidLinkTeal = androidx.compose.ui.graphics.Color(0xFF208C9A)
private val SolidLinkIconBackground = androidx.compose.ui.graphics.Color(0xFFE9F4F5)

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
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> solidLinkViewModel.setNotificationsEnabled(granted) }

    LaunchedEffect(Unit) {
        solidLinkViewModel.startLocalDiscovery()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("SolidLink", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { /* Navigation drawer follows in the next increment. */ }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Open navigation")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings screen follows the same state model. */ }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Private transfers. No cloud relay.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SolidLinkNavy,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Choose files, confirm a nearby peer, and transfer over an authenticated local connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }

            item {
                WorkflowCard(
                    step = "1",
                    title = "Select files",
                    icon = Icons.Outlined.FolderOpen,
                    iconDescription = "Files",
                ) {
                    Text(
                        text = if (state.selectedUris.isEmpty()) {
                            "No files selected. Access is limited to the files you choose."
                        } else {
                            "${state.selectedUris.size} file${if (state.selectedUris.size == 1) "" else "s"} selected and ready."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { documentPicker.launch(FilePickerIntents.openDocuments()) },
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Choose files")
                    }
                }
            }

            item {
                WorkflowCard(
                    step = "2",
                    title = "Nearby peer",
                    icon = Icons.Outlined.PeopleOutline,
                    iconDescription = "Nearby peer",
                ) {
                    Text(
                        text = if (state.peers.isEmpty()) {
                            "Discovery and peer confirmation will appear here when the LAN transport is connected."
                        } else {
                            state.status
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.peers.isEmpty()) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = solidLinkViewModel::startLocalDiscovery,
                        ) {
                            if (state.isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(if (state.isRunning) "Searching nearby" else "Find nearby peers")
                        }
                    } else {
                        state.peers.forEach { peerRow ->
                            PeerRowItem(peerRow, onConnect = { solidLinkViewModel.connect(peerRow) })
                        }
                    }
                    state.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                SettingsCard(
                    title = "Privacy controls",
                    icon = Icons.Outlined.Security,
                ) {
                    ToggleRow(
                        title = "Require peer approval",
                        checked = true,
                        onCheckedChange = {},
                    )
                    ToggleRow(
                        title = "Allow advanced SAS confirmation",
                        checked = true,
                        onCheckedChange = {},
                    )
                    ToggleRow(
                        title = "Local-only routing",
                        checked = true,
                        enabled = false,
                        onCheckedChange = {},
                    )
                }
            }

            item {
                SettingsCard(
                    title = "Transfer notifications",
                    icon = Icons.Outlined.NotificationsNone,
                ) {
                    Text(
                        "Get notified when a long transfer completes or needs attention.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                solidLinkViewModel.setNotificationsEnabled(true)
                            }
                        },
                    ) {
                        Text(if (state.notificationsEnabled) "Notifications enabled" else "Enable notifications")
                    }
                }
            }

            item {
                SettingsCard(
                    title = "Transfer history",
                    icon = Icons.Outlined.History,
                ) {
                    Text("Nothing transferred yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 16.dp),
                    enabled = false,
                    onClick = {},
                ) {
                    Text(if (state.selectedUris.isEmpty()) "Select files to continue" else "Waiting for peer confirmation")
                }
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    step: String,
    title: String,
    icon: ImageVector,
    iconDescription: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconBubble(icon, iconDescription)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = {
                    Text("$step. $title", style = MaterialTheme.typography.titleMedium, color = SolidLinkNavy)
                    content()
                },
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconBubble(icon, title)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, description: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(SolidLinkIconBackground, MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = SolidLinkTeal, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = if (enabled) SolidLinkNavy else MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PeerRowItem(peerRow: PeerRow, onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(peerRow.peer.displayName, style = MaterialTheme.typography.titleSmall, color = SolidLinkNavy)
                Text(
                    text = "${peerRow.peer.endpoint.hostAddress}:${peerRow.peer.endpoint.port} · LAN",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peerRow.isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                OutlinedButton(onClick = onConnect) { Text("Connect") }
            }
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
