package com.fourwheels.radwechsel.ui.radwechsel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.ui.login.Red4Wheels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadwechselScreen(
    wheelhotel: Wheelhotel?,
    username: String = "",
    onLoggedOut: () -> Unit,
    viewModel: RadwechselViewModel = hiltViewModel()
) {
    LaunchedEffect(wheelhotel) { viewModel.wheelhotel = wheelhotel }
    LaunchedEffect(username)   { viewModel.setUsername(username) }

    val state = viewModel.uiState
    val failedItems by viewModel.failedItems.collectAsState()
    val pendingItems by viewModel.pendingItems.collectAsState()
    val hasOpenData = pendingItems.isNotEmpty() || failedItems.isNotEmpty()

    var showQueue by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Offene Daten") },
            text = { Text("Es gibt noch ${pendingItems.size + failedItems.size} nicht übertragene Radwechsel. Trotzdem ausloggen?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout(lockUsername = true, onLoggedOut)
                }) {
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
                title = {
                    Column {
                        Text("Radwechsel", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            "RH: ${wheelhotel?.id ?: "–"}  ·  ${username.uppercase()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    // Fehlgeschlagen-Badge
                    if (failedItems.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text("${failedItems.size}") } }) {
                            IconButton(onClick = { showQueue = !showQueue }) {
                                Icon(
                                    Icons.Outlined.Sync,
                                    contentDescription = "Fehlgeschlagene Übertragungen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else if (pendingItems.isNotEmpty()) {
                        IconButton(onClick = { showQueue = !showQueue }) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Red4Wheels
                            )
                        }
                    }
                    // Logout-Button
                    if (state.phase != RadwechselPhase.LAUFEND) {
                        IconButton(onClick = {
                            if (hasOpenData) showLogoutConfirm = true
                            else viewModel.logout(lockUsername = false, onLoggedOut)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Ausloggen",
                                tint = if (hasOpenData) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->

        if (showQueue) {
            QueueScreen(
                failedItems = failedItems,
                pendingItems = pendingItems,
                onRetryAll = { viewModel.retryAll() },
                onRetryItem = { viewModel.retryItem(it) },
                onClose = { showQueue = false },
                modifier = Modifier.padding(padding)
            )
        } else {
            when (state.phase) {
                RadwechselPhase.EINGABE -> EingabeScreen(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
                RadwechselPhase.LAUFEND -> LaufendScreen(
                    state = state,
                    viewModel = viewModel,
                    wheelhotel = wheelhotel,
                    modifier = Modifier.padding(padding)
                )
                RadwechselPhase.ERFOLG -> ErfolgScreen(
                    state = state,
                    viewModel = viewModel,
                    wheelhotel = wheelhotel,
                    pendingCount = pendingItems.size,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

// ── Phase 1: Eingabe ─────────────────────────────────────────────────────────

@Composable
private fun EingabeScreen(
    state: RadwechselUiState,
    viewModel: RadwechselViewModel,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var kennzeichenFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Kennzeichen / Auftragsnummer
        InputCard(label = "KENNZEICHEN / AUFTRAGSNUMMER", isFocused = kennzeichenFocused) {
            BasicTextField(
                value = state.kennzeichen,
                onValueChange = viewModel::onKennzeichenChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { kennzeichenFocused = it.isFocused },
                singleLine = true,
                cursorBrush = SolidColor(Red4Wheels),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (state.kennzeichen.isEmpty()) {
                            Text(
                                "z.B. MÜ-AB 123",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        if (state.error != null) {
            Text(
                text = state.error,
                color = Red4Wheels,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.startRadwechsel()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red4Wheels),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Radwechsel starten", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ── Phase 2: Laufend ─────────────────────────────────────────────────────────

@Composable
private fun LaufendScreen(
    state: RadwechselUiState,
    viewModel: RadwechselViewModel,
    wheelhotel: Wheelhotel?,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var torqueFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timer-Block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Red4Wheels)
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("LAUFZEIT", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(
                    viewModel.formatTimer(state.timerSeconds),
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text("Gestartet: ${viewModel.formatStartzeit(state.startedAt)}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        InfoChip(label = "KENNZEICHEN / AUFTRAGSNUMMER", value = state.kennzeichen, stacked = true)
        InfoChip(label = "STANDORT", value = wheelhotel?.id ?: "–")

        // Drehmoment-Eingabe
        InputCard(label = "DREHMOMENT (NM)", isFocused = torqueFocused) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = state.torque,
                    onValueChange = viewModel::onTorqueChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { torqueFocused = it.isFocused },
                    singleLine = true,
                    cursorBrush = SolidColor(Red4Wheels),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (state.torque.isEmpty()) {
                                Text(
                                    "110",
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Text("Nm", color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (state.error != null) {
            Text(
                text = state.error,
                color = Red4Wheels,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Buttons mit explizitem Abstand
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { focusManager.clearFocus(); viewModel.abschliessen() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Radwechsel abschließen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = viewModel::abbrechen,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abbrechen", fontSize = 15.sp)
            }
        }
    }
}

// ── Phase 3: Erfolg ──────────────────────────────────────────────────────────

@Composable
private fun ErfolgScreen(
    state: RadwechselUiState,
    viewModel: RadwechselViewModel,
    wheelhotel: Wheelhotel?,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Color(0xFFD1FAE5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(48.dp))
        }

        Text("Radwechsel erfasst!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111827))

        // Drei Werte untereinander
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                state.kennzeichen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF374151)
            )
            Text(
                viewModel.formatTimer(state.timerSeconds),
                fontSize = 14.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "${state.torque} Nm",
                fontSize = 14.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
        }

        // Sync-Hinweis
        if (pendingCount > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF92400E))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "$pendingCount Datensatz/Datensätze werden im Hintergrund synchronisiert.",
                        fontSize = 13.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = viewModel::neuerWechsel,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red4Wheels),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nächster Wechsel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ── Queue-Ansicht ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueScreen(
    failedItems: List<QueueItem>,
    pendingItems: List<QueueItem>,
    onRetryAll: () -> Unit,
    onRetryItem: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück")
            }
            Text("Übertragungen", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (failedItems.isNotEmpty()) {
                IconButton(onClick = onRetryAll) {
                    Icon(Icons.Outlined.Sync, contentDescription = "Alle erneut senden", tint = Red4Wheels)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (failedItems.isNotEmpty()) {
                item {
                    Text("FEHLGESCHLAGEN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
                items(failedItems, key = { it.id }) { item ->
                    QueueItemCard(item = item, isFailed = true, onRetry = { onRetryItem(item.id) })
                }
            }

            if (pendingItems.isNotEmpty()) {
                item {
                    Text("AUSSTEHEND", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(pendingItems, key = { it.id }) { item ->
                    QueueItemCard(item = item, isFailed = false, onRetry = {})
                }
            }

            if (failedItems.isEmpty() && pendingItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Alle Daten übertragen ✓", color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(item: QueueItem, isFailed: Boolean, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed) Color(0xFFFEF2F2) else Color(0xFFF9FAFB)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.licensePlate, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                Text("${item.wheelhotelName} · ${item.torque} Nm", fontSize = 12.sp, color = Color.Gray)
                if (item.errorMessage != null) {
                    Text(item.errorMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            if (isFailed) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Sync, contentDescription = "Erneut senden", tint = Red4Wheels)
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Gray)
            }
        }
    }
}

// ── Hilfs-Composables ────────────────────────────────────────────────────────

@Composable
private fun InputCard(
    label: String,
    isFocused: Boolean,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFFF9FAFB))
            .border(
                width = 1.5.dp,
                color = if (isFocused) Red4Wheels else Color(0xFFE5E7EB),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun InfoChip(label: String, value: String, stacked: Boolean = false) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(Color(0xFFF3F4F6))
        .padding(horizontal = 16.dp, vertical = 12.dp)

    if (stacked) {
        Column(modifier = baseModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), letterSpacing = 1.sp)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    } else {
        Row(
            modifier = baseModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
