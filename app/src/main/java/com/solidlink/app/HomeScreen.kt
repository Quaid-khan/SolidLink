package com.solidlink.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.solidlink.files.FilePickerIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedUris by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var peerApprovalRequired by rememberSaveable { mutableStateOf(true) }
    var advancedSasEnabled by rememberSaveable { mutableStateOf(true) }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedUris = result.data?.selectedUris().orEmpty().map(Uri::toString)
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationPermissionRequested = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("SolidLink") },
            )
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose files, confirm a nearby peer, and transfer over an authenticated local connection. SolidLink does not inspect file contents or upload them to a server.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("1. Select files", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (selectedUris.isEmpty()) {
                                "No files selected. Access is limited to the files you choose."
                            } else {
                                "${selectedUris.size} file${if (selectedUris.size == 1) "" else "s"} selected and ready for peer confirmation."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { documentPicker.launch(FilePickerIntents.openDocuments()) }) {
                                Text("Choose files")
                            }
                            if (selectedUris.isNotEmpty()) {
                                OutlinedButton(onClick = { selectedUris = emptyList() }) {
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
                        Text("2. Nearby peer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Discovery and peer confirmation will appear here when the LAN transport is connected. No peer or transfer is fabricated in this state.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = {}, enabled = false) {
                            Text("Find nearby peers")
                        }
                        Text(
                            "Transport integration is the next functional slice.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Privacy controls", style = MaterialTheme.typography.titleMedium)
                        PrivacySwitchRow(
                            label = "Require peer approval",
                            checked = peerApprovalRequired,
                            onCheckedChange = { peerApprovalRequired = it },
                        )
                        PrivacySwitchRow(
                            label = "Allow advanced SAS confirmation",
                            checked = advancedSasEnabled,
                            onCheckedChange = { advancedSasEnabled = it },
                        )
                        HorizontalDivider()
                        PrivacySwitchRow(
                            label = "Local-only routing",
                            checked = true,
                            onCheckedChange = {},
                            enabled = false,
                        )
                        Text(
                            "This safety invariant cannot be disabled: public addresses, cloud fallback, and cellular fallback are rejected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionRequested) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Transfer notifications", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Allow visible progress and cancellation controls for transfers that continue while the app is not on screen.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Text("Enable notifications")
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Transfer history", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Completed transfers will appear here after durable history is connected. No placeholder records are shown.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Nothing transferred yet",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selectedUris.isEmpty()) "Select files to continue" else "Waiting for peer confirmation")
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun Intent.selectedUris(): List<Uri> {
    val clip = clipData
    return when {
        clip != null -> (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        data != null -> listOfNotNull(data)
        else -> emptyList()
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen()
    }
}
