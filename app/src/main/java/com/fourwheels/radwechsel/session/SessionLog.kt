package com.fourwheels.radwechsel.session

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
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
 * Hält die in der aktuellen Login-Session abgeschlossenen Radwechsel. Dient dem
 * Gegencheck und dem Excel-Versand und wird beim Logout geleert.
 *
 * Liegt zusätzlich als Datei vor (wie die Queue über [AtomicFile]): beendet
 * Android die App im Hintergrund, fehlten sonst die Wechsel des Vormittags im
 * Report.
 */
@Singleton
class SessionLog @Inject constructor(
    @ApplicationContext context: Context
) {
    private val gson = Gson()
    private val file = AtomicFile(File(context.filesDir, "radwechsel_session.json"))
    private val lock = Any()

    private val _items = MutableStateFlow<List<CompletedRadwechsel>>(emptyList())
    val items: StateFlow<List<CompletedRadwechsel>> = _items.asStateFlow()

    init {
        synchronized(lock) { load() }
    }

    fun add(item: CompletedRadwechsel) {
        synchronized(lock) {
            _items.value = _items.value + item
            saveLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            _items.value = emptyList()
            saveLocked()
        }
    }

    /** Nur unter [lock] aufrufen. */
    private fun saveLocked() {
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e("SessionLog", "Session-Datei nicht beschreibbar", e)
            return
        }
        try {
            out.write(gson.toJson(_items.value).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e("SessionLog", "Session konnte nicht gespeichert werden", e)
        }
    }

    /** Nur unter [lock] aufrufen. Eine kaputte Datei darf den Start nicht verhindern. */
    private fun load() {
        if (!file.baseFile.exists()) return
        runCatching {
            val json = String(file.readFully(), Charsets.UTF_8)
            val list: List<CompletedRadwechsel?> =
                gson.fromJson(json, object : TypeToken<List<CompletedRadwechsel?>>() {}.type) ?: emptyList()
            _items.value = list.filterNotNull()
        }.onFailure { Log.e("SessionLog", "Session-Datei nicht lesbar – wird ignoriert", it) }
    }
}
