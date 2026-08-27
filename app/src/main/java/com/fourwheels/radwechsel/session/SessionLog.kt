package com.fourwheels.radwechsel.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Ein in dieser Session abgeschlossener Radwechsel (inkl. Radsatznummer – die
 *  geht NICHT an die API, sondern dient nur dem Gegencheck / Excel-Report). */
data class CompletedRadwechsel(
    val id: String = UUID.randomUUID().toString(),
    val kennzeichen: String,
    val radsatznummer: String,
    val torque: Int,
    val kostenstelle: String,   // ID des gewählten Räderhotels
    val standortName: String,   // Name des Standorts
    val benutzer: String,       // eingeloggter Username
    val startedAtIso: String,
    val finishedAtIso: String,
    val durationSeconds: Int,
    val recordedAt: Long = System.currentTimeMillis()
)

/**
 * Hält die in der aktuellen Login-Session abgeschlossenen Radwechsel – nur im
 * Speicher, wird beim Logout geleert. Dient dem Gegencheck und dem Excel-Versand.
 */
@Singleton
class SessionLog @Inject constructor() {

    private val _items = MutableStateFlow<List<CompletedRadwechsel>>(emptyList())
    val items: StateFlow<List<CompletedRadwechsel>> = _items.asStateFlow()

    fun add(item: CompletedRadwechsel) {
        _items.value = _items.value + item
    }

    fun clear() {
        _items.value = emptyList()
    }
}
