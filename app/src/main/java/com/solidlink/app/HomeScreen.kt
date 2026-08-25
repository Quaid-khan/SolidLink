package com.solidlink.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "SolidLink",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Private, reliable file transfer over a local device-to-device link.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "The transfer engine is not connected yet. Your file bytes will stay local; actual speed will be measured per session.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
