package com.fourwheels.radwechsel.ui.rh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.ui.login.Red4Wheels
import com.fourwheels.radwechsel.ui.radwechsel.QueueManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RHAuswahlScreen(
    viewModel: RHAuswahlViewModel = hiltViewModel(),
    onWheelhotelSelected: (Wheelhotel) -> Unit,
    onLoggedOut: () -> Unit
) {
    val state = viewModel.uiState
    val pendingItems by viewModel.pendingItems.collectAsState()
    val failedItems by viewModel.failedItems.collectAsState()
    val hasOpenData = pendingItems.isNotEmpty() || failedItems.isNotEmpty()

    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Offene Daten") },
            text = { Text("Es gibt noch ${pendingItems.size + failedItems.size} nicht übertragene Radwechsel. Trotzdem ausloggen?") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; viewModel.logout(onLoggedOut) }) {
                    Text("Ausloggen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Standort wählen", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(onClick = {
                        if (hasOpenData) showLogoutConfirm = true
                        else viewModel.logout(onLoggedOut)
                    }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = "Ausloggen",
                            tint = if (hasOpenData) Color.White.copy(alpha = 0.35f) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Red4Wheels)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Red4Wheels)
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retry, colors = ButtonDefaults.buttonColors(containerColor = Red4Wheels)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Erneut versuchen")
                        }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text("Deine Räderhotels", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        items(state.wheelhotels, key = { it.id }) { hotel ->
                            WheelhotelItem(hotel = hotel, onClick = { onWheelhotelSelected(hotel) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelhotelItem(hotel: Wheelhotel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Red4Wheels, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(hotel.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("KST ${hotel.id} · ${hotel.city}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
