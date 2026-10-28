package com.fourwheels.radwechsel.ui.radwechsel

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fourwheels.radwechsel.R
import com.fourwheels.radwechsel.export.ReportSharer
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.session.CompletedRadwechsel
import com.fourwheels.radwechsel.ui.AppTopBar
import com.fourwheels.radwechsel.ui.QueueAction
import com.fourwheels.radwechsel.ui.login.Red4Wheels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadwechselScreen(
    wheelhotel: Wheelhotel?,
    username: String = "",
    onLoggedOut: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: RadwechselViewModel = hiltViewModel()
) {
    LaunchedEffect(wheelhotel) { viewModel.wheelhotel = wheelhotel }
    LaunchedEffect(username) { viewModel.username = username }

    // Nur solange dieser Screen sichtbar ist
    LaunchedEffect(Unit) { viewModel.scans.collect(viewModel::onScan) }

    val context = LocalContext.current
    val state = viewModel.uiState
    val failedItems by viewModel.failedItems.collectAsState()
    val pendingItems by viewModel.pendingItems.collectAsState()
    val sessionItems by viewModel.sessionItems.collectAsState()
    val sessionExpired by viewModel.sessionExpired.collectAsState()
    val hasOpenData = pendingItems.isNotEmpty() || failedItems.isNotEmpty()
    val running = state.phase == RadwechselPhase.LAUFEND

    var showQueue by remember { mutableStateOf(false) }
    var showSession by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Excel-Report an Outlook übergeben. null-Payload = keine Radwechsel, dann passiert nichts.
    val shareReport = {
        viewModel.buildReportPayload()?.let { p ->
            ReportSharer.shareToOutlook(
                context, p.xlsx, p.fileName, p.recipient, p.subject, p.body
            )
        }
    }

    // Beim Ausloggen den Report automatisch mitschicken, solange das Session-Log
    // noch gefüllt ist (Logout leert es). Outlook öffnet sich über dem Login.
    val logoutWithReport = { lockUsername: Boolean ->
        shareReport()
        viewModel.logout(lockUsername, onLoggedOut)
    }

    // Vor dem Öffnen von Outlook zeigen, wo „Senden" ist – sonst wird die Mail
    // einfach weggeklickt. null = kein Hinweis offen, sonst lockUsername für den Logout.
    var outlookHintFor by remember { mutableStateOf<Boolean?>(null) }
    val requestLogout = { lockUsername: Boolean ->
        if (sessionItems.isNotEmpty()) outlookHintFor = lockUsername
        else viewModel.logout(lockUsername, onLoggedOut)
    }

    outlookHintFor?.let { lockUsername ->
        OutlookHintDialog(
            onConfirm = {
                outlookHintFor = null
                logoutWithReport(lockUsername)
            },
            onDismiss = { outlookHintFor = null }
        )
    }

    BackHandler(enabled = showQueue || showSession) {
        showQueue = false
        showSession = false
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Offene Daten") },
            text = { Text("Es gibt noch ${pendingItems.size + failedItems.size} nicht übertragene Radwechsel. Trotzdem ausloggen?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    requestLogout(true)
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
            AppTopBar(
                username = username,
                wheelhotel = wheelhotel,
                actions = {
                    // Warteschlange: fehlgeschlagene Übertragungen sollen immer
                    // auffallen, nicht erst beim nächsten Radwechsel.
                    QueueAction(pendingItems.size, failedItems.size) {
                        showQueue = !showQueue
                        if (showQueue) showSession = false
                    }
                    // Protokoll und Logout braucht man selten – im Menü lassen sie
                    // der Kopfzeile Platz, und der Logout passiert nicht versehentlich.
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.focusProperties { canFocus = false }
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            // Erledigte Radwechsel zum Gegencheck + Excel-Versand. Bewusst
                            // im Menü: oben steht nur ein Symbol, die Übertragungen.
                            DropdownMenuItem(
                                text = { Text("Erledigte Radwechsel (${sessionItems.size})") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.FactCheck, contentDescription = null) },
                                enabled = !running,
                                onClick = {
                                    showMenu = false
                                    showSession = true
                                    showQueue = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Protokoll an IT senden") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_log), contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val intent = viewModel.buildLogMailIntent()
                                    if (intent == null) {
                                        Toast.makeText(context, "Protokoll konnte nicht erstellt werden", Toast.LENGTH_LONG).show()
                                    } else {
                                        // Ohne Chooser landet man je nach Gerät stumm in einer
                                        // App, die keine Anhänge kann.
                                        runCatching { context.startActivity(Intent.createChooser(intent, "Protokoll senden")) }
                                            .onFailure {
                                                Toast.makeText(context, "Kein Mail-Programm gefunden", Toast.LENGTH_LONG).show()
                                            }
                                    }
                                }
                            )
                            // Während eines laufenden Radwechsels gesperrt – der ginge sonst verloren
                            DropdownMenuItem(
                                text = { Text("Ausloggen") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                enabled = !running,
                                onClick = {
                                    showMenu = false
                                    if (hasOpenData) showLogoutConfirm = true
                                    else requestLogout(false)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->

        if (showSession) {
            SessionScreen(
                items = sessionItems,
                onShare = { shareReport() },
                viewModel = viewModel,
                onClose = { showSession = false },
                modifier = Modifier.padding(padding)
            )
        } else if (showQueue) {
            QueueScreen(
                failedItems = failedItems,
                pendingItems = pendingItems,
                onRetryAll = viewModel::retryAll,
                onRetryItem = viewModel::retryItem,
                onClose = { showQueue = false },
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (sessionExpired) {
                    // Bewusst kein automatischer Rauswurf: ein laufender Radwechsel
                    // ginge verloren. Gespeichert wird weiter lokal, gesendet nach dem Login.
                    SessionExpiredBanner(onRelogin = if (running) null else onSessionExpired)
                }
                when (state.phase) {
                    RadwechselPhase.EINGABE -> EingabeScreen(
                        state = state,
                        viewModel = viewModel
                    )
                    RadwechselPhase.LAUFEND -> LaufendScreen(
                        state = state,
                        viewModel = viewModel,
                        wheelhotel = wheelhotel
                    )
                    RadwechselPhase.ERFOLG -> ErfolgScreen(
                        state = state,
                        viewModel = viewModel,
                        wheelhotel = wheelhotel
                    )
                }
            }
        }
    }
}

/**
 * Hinweis vor dem Logout: gleich öffnet sich Outlook, oben rechts auf „Senden".
 * Bewusst wenig Text und ein Bild der Outlook-Kopfzeile – nicht jeder liest
 * gut Deutsch.
 */
@Composable
private fun OutlookHintDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = Red4Wheels, modifier = Modifier.size(36.dp)) },
        title = { Text("E-Mail senden!", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Outlook öffnet sich.\nOben rechts auf SENDEN tippen.",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                // Nachbau der Outlook-Kopfzeile, Senden-Symbol rot eingekreist
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .border(3.dp, Red4Wheels, MaterialTheme.shapes.extraLarge)
                                .padding(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Send, contentDescription = "Senden",
                                tint = Color(0xFF0F6CBD), modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Icon(
                        Icons.Outlined.ArrowUpward, contentDescription = null, tint = Red4Wheels,
                        modifier = Modifier.padding(end = 14.dp).size(32.dp)
                    )
                }
                Text(
                    "Outlook opens – tap SEND (top right).",
                    fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Red4Wheels),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Abbrechen", color = Color.Gray)
            }
        }
    )
}

/**
 * Hinweis bei abgelaufener Sitzung. [onRelogin] null = mitten in einem Radwechsel,
 * dann erst abschließen lassen statt ihn durch den Login zu verwerfen.
 */
@Composable
private fun SessionExpiredBanner(onRelogin: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFEF3C7))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Sitzung abgelaufen", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
            Text(
                if (onRelogin != null) "Radwechsel bleiben gespeichert und werden nach dem Anmelden gesendet."
                else "Radwechsel abschließen, danach neu anmelden. Es geht nichts verloren.",
                fontSize = 12.sp, color = Color(0xFF92400E)
            )
        }
        if (onRelogin != null) {
            TextButton(onClick = onRelogin) {
                Text("Anmelden", color = Red4Wheels, fontWeight = FontWeight.Bold)
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
    var radsatzFocused by remember { mutableStateOf(false) }
    val kennzeichenFocus = remember { FocusRequester() }
    val radsatzFocus = remember { FocusRequester() }

    // Beim Öffnen der Maske Kennzeichen fokussieren, damit ein Scan/eine Eingabe
    // sicher in einem Textfeld landet und NICHT eine TopBar-Aktion (Enter) auslöst.
    LaunchedEffect(Unit) { kennzeichenFocus.requestFocus() }

    // Nach vollständigem Radsatz-Scan: Fokus lösen -> Tastatur verschwindet,
    // der Start-Button ist direkt erreichbar.
    LaunchedEffect(viewModel.radsatzScanCompleted) {
        if (viewModel.radsatzScanCompleted) {
            focusManager.clearFocus()
            viewModel.consumeRadsatzScanCompleted()
        }
    }

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
                    .focusRequester(kennzeichenFocus)
                    .onFocusChanged { kennzeichenFocused = it.isFocused },
                singleLine = true,
                cursorBrush = SolidColor(Red4Wheels),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { radsatzFocus.requestFocus() }
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

        // Neue Radsatznummer (Barcode scannen, OPTIONAL – geht NICHT an die API)
        InputCard(
            label = "NEUE RADSATZNUMMER (OPTIONAL)",
            isFocused = radsatzFocused,
            leadingIcon = painterResource(R.drawable.ic_barcode)
        ) {
            BasicTextField(
                value = state.radsatznummer,
                onValueChange = viewModel::onRadsatznummerChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(radsatzFocus)
                    .onFocusChanged { radsatzFocused = it.isFocused },
                singleLine = true,
                cursorBrush = SolidColor(Red4Wheels),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
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
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.radsatznummer.isEmpty()) {
                                Text(
                                    "Barcode scannen (optional)",
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp
                                )
                            }
                            innerTextField()
                        }
                        if (state.radsatznummer.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "erfasst",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        }

        // Alter Radsatz: nur für Gegencheck/Excel, geht NICHT an die API
        InputCard(label = "KUNDE NIMMT ALTEN RS MIT?", isFocused = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceButton("Ja", state.alterRsMitgenommen == true, Modifier.weight(1f)) {
                    focusManager.clearFocus()
                    viewModel.onAlterRsMitgenommenChange(true)
                }
                ChoiceButton("Nein", state.alterRsMitgenommen == false, Modifier.weight(1f)) {
                    focusManager.clearFocus()
                    viewModel.onAlterRsMitgenommenChange(false)
                }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Color(0xFFF9FAFB))
                .border(
                    width = 1.5.dp,
                    color = if (torqueFocused) Red4Wheels else Color(0xFFE5E7EB),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Drehmoment",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7280),
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            BasicTextField(
                value = state.torque,
                onValueChange = viewModel::onTorqueChange,
                modifier = Modifier.onFocusChanged { torqueFocused = it.isFocused },
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
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                ),
                decorationBox = { innerTextField ->
                    Box {
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
            Text("Nm", color = Color.Gray, modifier = Modifier.padding(start = 6.dp),
                fontFamily = FontFamily.Monospace, fontSize = 18.sp)
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
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
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
            if (state.radsatznummer.isNotBlank()) {
                Text(
                    "Radsatz ${state.radsatznummer}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
            state.alterRsMitgenommen?.let {
                Text(
                    "Alter RS mitgenommen: ${if (it) "Ja" else "Nein"}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
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

@Composable
private fun QueueScreen(
    failedItems: List<QueueItem>,
    pendingItems: List<QueueItem>,
    onRetryAll: () -> Unit,
    onRetryItem: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Fehlgeschlagene zuerst zeigen, wenn es welche gibt – nur die brauchen eine Aktion
    var tab by rememberSaveable { mutableIntStateOf(if (failedItems.isNotEmpty()) TAB_FAILED else TAB_OPEN) }
    val shown = if (tab == TAB_OPEN) pendingItems else failedItems

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
            }
            Text("Übertragungen", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = Red4Wheels) {
            Tab(
                selected = tab == TAB_OPEN,
                onClick = { tab = TAB_OPEN },
                selectedContentColor = Red4Wheels,
                unselectedContentColor = Color.Gray,
                text = { Text("Offen (${pendingItems.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = tab == TAB_FAILED,
                onClick = { tab = TAB_FAILED },
                selectedContentColor = Red4Wheels,
                unselectedContentColor = Color.Gray,
                text = { Text("Fehlgeschlagen (${failedItems.size})", fontWeight = FontWeight.Bold) }
            )
        }
        // Offenes geht bei Netz automatisch raus – erneut senden gibt es nur für Fehlgeschlagene
        if (tab == TAB_FAILED && shown.isNotEmpty()) {
            Button(
                onClick = onRetryAll,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red4Wheels),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Alle erneut senden (${shown.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shown, key = { it.id }) { QueueItemCard(it, tab == TAB_FAILED) { onRetryItem(it.id) } }
            if (shown.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        if (tab == TAB_OPEN && failedItems.isEmpty()) {
                            Text("Alle Daten übertragen ✓", color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                        } else if (tab == TAB_OPEN) {
                            Text("Nichts offen", color = Color.Gray)
                        } else {
                            Text("Keine fehlgeschlagenen Übertragungen", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

private const val TAB_OPEN = 0
private const val TAB_FAILED = 1

@Composable
private fun QueueItemCard(item: QueueItem, isFailed: Boolean, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isFailed) Color(0xFFFEF2F2) else Color(0xFFF9FAFB)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.licensePlate, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                Text("KST ${item.wheelhotelId ?: "–"} · ${item.torque} Nm", fontSize = 12.sp, color = Color.Gray)
                Text(item.username?.uppercase() ?: "–", fontSize = 11.sp, color = Color.Gray)
                item.errorMessage?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
            }
            if (isFailed) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Sync, contentDescription = "Erneut senden", tint = Red4Wheels)
                }
            } else {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.padding(12.dp).size(20.dp))
            }
        }
    }
}

// ── Session-Ansicht (erledigte Radwechsel + Excel-Mail) ───────────────────────

@Composable
private fun SessionScreen(
    items: List<CompletedRadwechsel>,
    onShare: () -> Unit,
    viewModel: RadwechselViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
            }
            Text("Erledigt (Session)", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("${items.size}", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Noch keine Radwechsel in dieser Session.", color = Color.Gray)
                    }
                }
            } else {
                items(items.reversed(), key = { it.id }) { item ->
                    SessionItemCard(item = item, timerText = viewModel.formatTimer(item.durationSeconds))
                }
            }
        }

        // Excel-Versand (fester Empfänger radwechsel@4wheels.de über Outlook)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Email, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Text("An radwechsel@4wheels.de", fontSize = 12.sp, color = Color.Gray)
            }

            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = items.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Red4Wheels),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Als Excel per Outlook senden", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SessionItemCard(item: CompletedRadwechsel, timerText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.kennzeichen, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                Text(
                    buildString {
                        if (item.radsatznummer.isNotBlank()) append("Radsatz ${item.radsatznummer} · ")
                        item.alterRsMitgenommen?.let { append("Alter RS mit: ${if (it) "Ja" else "Nein"} · ") }
                        append("${item.torque} Nm · $timerText")
                    },
                    fontSize = 12.sp, color = Color.Gray
                )
            }
            Icon(
                Icons.Outlined.CheckCircle, contentDescription = null,
                tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Hilfs-Composables ────────────────────────────────────────────────────────

@Composable
private fun InputCard(
    label: String,
    isFocused: Boolean,
    leadingIcon: Painter? = null,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = Red4Wheels, modifier = Modifier.size(14.dp))
            }
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), letterSpacing = 1.sp)
        }
        content()
    }
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red4Wheels),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF374151))
        ) {
            Text(text, fontSize = 15.sp)
        }
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
