package com.fourwheels.radwechsel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.fourwheels.radwechsel.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fourwheels.radwechsel.model.Wheelhotel
import com.fourwheels.radwechsel.ui.login.Red4Wheels

/**
 * Einheitlicher Kopf für alle Masken nach dem Login (gleich aufgebaut wie in der
 * RR-Inventur), damit der Mitarbeiter überall sieht, wo er gerade erfasst:
 *  1. Zeile: "Radwechsel"
 *  2. Zeile: Benutzer
 *  3. Zeile: Kostenstelle · Räderhotel
 *
 * Bewusst kein Material-TopAppBar: der hat eine feste Höhe und bietet nur Platz
 * für zwei Zeilen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    username: String,
    wheelhotel: Wheelhotel? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // Immer drei Zeilen, damit der Kopf in allen Masken gleich hoch ist – vor der
    // RH-Auswahl steht in der dritten Zeile deshalb ein Platzhalter.
    val location = wheelhotel?.let { "KST ${it.id} · ${it.displayName}" } ?: "Räderhotel wählen"

    Surface(color = Red4Wheels, contentColor = Color.White) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Der linke Platz ist immer belegt – ohne Zurück mit dem App-Symbol –,
                // damit der Titel beim Wechsel zwischen den Masken nicht springt.
                if (navigationIcon != null) {
                    navigationIcon()
                } else {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Handyman, contentDescription = null,
                            modifier = Modifier.size(28.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    Text("Radwechsel", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(username.uppercase(), fontSize = 12.sp, lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(location, fontSize = 12.sp, lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                actions()
            }
        }
    }
}

/**
 * Queue-Symbol zur Übertragungsansicht – in allen Masken gleich: weiß bei offenen
 * Daten, blass wenn alles übertragen ist, Zahl der fehlgeschlagenen als Badge.
 *
 * Nicht fokussierbar: ein Enter vom Scanner oder der Tastatur darf nicht
 * versehentlich die Übertragungsansicht öffnen.
 */
@Composable
fun QueueAction(pendingCount: Int, failedCount: Int, onClick: () -> Unit) {
    val hasOpenData = pendingCount + failedCount > 0
    BadgedBox(
        badge = {
            if (failedCount > 0) {
                Badge(containerColor = Color.White, contentColor = Red4Wheels) { Text("$failedCount") }
            }
        }
    ) {
        IconButton(onClick = onClick, modifier = Modifier.focusProperties { canFocus = false }) {
            Icon(
                painterResource(R.drawable.ic_queue),
                contentDescription = "Übertragungen",
                tint = if (hasOpenData) Color.White else Color.White.copy(alpha = 0.35f)
            )
        }
    }
}
