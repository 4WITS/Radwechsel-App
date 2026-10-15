package com.fourwheels.radwechsel.ui.radwechsel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.fourwheels.radwechsel.api.FourWheelsApi
import com.fourwheels.radwechsel.auth.TokenStore
import com.fourwheels.radwechsel.logging.AppLog
import com.fourwheels.radwechsel.model.WheelChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class QueueItemStatus { PENDING, FAILED }

/**
 * Ein abgeschlossener Radwechsel in der Offline-Queue.
 *
 * Audit-Trail: Die fachlichen Felder (inkl. [username] und der Zeitstempel)
 * werden beim Einreihen einmal gesetzt und danach nie mehr geändert – nur die
 * Queue-Verwaltungsfelder ([status], [errorMessage], [attemptCount]).
 */
data class QueueItem(
    val id: String = UUID.randomUUID().toString(),
    // ── fachliche Daten = 1:1 der API-Payload ──
    // Nullable: Einträge aus Versionen, die Wheelhotel/Benutzer nicht mitgeschickt
    // haben, kommen aus Gson ohne diese Felder zurück.
    val wheelhotelId: String?,
    val username: String?,
    val licensePlate: String,
    val torque: Int,
    val startedAt: String,               // ISO 8601 UTC mit ms
    val finishedAt: String,              // ISO 8601 UTC mit ms
    // ── nur Anzeige, geht nicht in die API ──
    val wheelhotelName: String,
    // ── Queue-Verwaltung ──
    // Heißt hier "status" (nicht "queueStatus" wie in der RR-Inventur): so lesen
    // sich die Einträge aus der alten SharedPreferences-Queue ohne Umbau ein.
    val status: QueueItemStatus = QueueItemStatus.PENDING,
    val errorMessage: String? = null,
    val attemptCount: Int = 0
)

/**
 * Offline-Queue für Radwechsel.
 *
 * Datensicherheit:
 * - Jeder Radwechsel wird beim Einreihen *synchron* auf Platte geschrieben, bevor
 *   der Nutzer die Bestätigung sieht. Stürzt die App eine Sekunde später ab, ist er da.
 * - Geschrieben wird über [AtomicFile] (neue Datei schreiben, fsync, umbenennen):
 *   ein Absturz mitten im Schreiben hinterlässt die alte, vollständige Datei statt
 *   einer halben.
 * - Erfolgreich gesendete Einträge verlassen Speicher und Datei sofort; die Queue
 *   wächst also nur, solange kein Netz da ist.
 * - Nichts wird je automatisch gelöscht, das nicht vom Server bestätigt wurde.
 */
@Singleton
class QueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fourWheelsApi: FourWheelsApi,
    private val tokenStore: TokenStore,
    private val appLog: AppLog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val file = AtomicFile(File(context.filesDir, "radwechsel_queue.json"))
    private val isProcessing = AtomicBoolean(false)

    // Schützt alle Lese-Ändere-Schreibe-Zugriffe auf die beiden Listen. enqueue()
    // läuft auf dem Main-Thread, der Sync-Worker auf IO – ohne Lock gingen bei
    // vielen Radwechseln am Stück Einträge durch Races verloren.
    private val lock = Any()

    private val _pendingItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val pendingItems: StateFlow<List<QueueItem>> = _pendingItems.asStateFlow()

    private val _failedItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val failedItems: StateFlow<List<QueueItem>> = _failedItems.asStateFlow()

    val hasPendingOrFailed: Boolean
        get() = _pendingItems.value.isNotEmpty() || _failedItems.value.isNotEmpty()

    /**
     * Weckt den Sync-Worker sofort. Conflated: mehrere Anstöße kurz hintereinander
     * ergeben einen Durchlauf, nicht fünf. Muss vor [init] stehen – sonst greift
     * der dort gestartete Worker auf ein noch nicht gesetztes Feld zu.
     */
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    init {
        synchronized(lock) { loadFromDisk() }
        appLog.log(TAG, "App gestartet · Queue geladen: " +
            "${_pendingItems.value.size} ausstehend, ${_failedItems.value.size} fehlgeschlagen")
        startWorker()
        watchConnectivity()
    }

    fun enqueue(item: QueueItem) {
        synchronized(lock) {
            _pendingItems.value = _pendingItems.value + item
            saveToDiskLocked()
        }
        wakeUp.trySend(Unit)   // frisch erfasst: sofort raus, nicht erst beim Intervall
        Log.d("QueueManager", "Enqueued: ${item.licensePlate}")
        appLog.log(TAG, "eingereiht: ${item.licensePlate} · KST ${item.wheelhotelId ?: "–"} · " +
            "${item.torque} Nm · ${item.startedAt} – ${item.finishedAt} · " +
            "Benutzer ${item.username ?: "–"} · id=${item.id}")
    }

    /**
     * Nach dem Login: was wegen fehlender Anmeldung liegen geblieben ist, sofort
     * senden statt bis zum nächsten Intervall zu warten.
     */
    fun syncNow() {
        wakeUp.trySend(Unit)
    }

    fun retryFailed() {
        synchronized(lock) {
            val toRetry = _failedItems.value.map {
                it.copy(status = QueueItemStatus.PENDING, errorMessage = null)
            }
            _failedItems.value = emptyList()
            _pendingItems.value = _pendingItems.value + toRetry
            saveToDiskLocked()
        }
        // Der Nutzer hat auf "erneut senden" getippt – darauf soll er nicht bis
        // zum nächsten Intervall warten müssen.
        wakeUp.trySend(Unit)
    }

    fun retryItem(id: String) {
        synchronized(lock) {
            val item = _failedItems.value.find { it.id == id } ?: return
            _failedItems.value = _failedItems.value.filter { it.id != id }
            _pendingItems.value = _pendingItems.value + item.copy(
                status = QueueItemStatus.PENDING,
                errorMessage = null
            )
            saveToDiskLocked()
        }
        wakeUp.trySend(Unit)
    }

    /**
     * Der Worker läuft nicht im Sekundentakt, sondern wartet auf einen Anlass:
     * neuer Radwechsel, manueller Wiederholversuch, Login oder Netz ist wieder da.
     * Nur als Sicherheitsnetz kommt er zusätzlich alle [RETRY_INTERVAL_MS] von selbst.
     */
    private fun startWorker() {
        scope.launch {
            while (true) {
                // Ein unerwarteter Fehler darf den Worker nicht dauerhaft beenden
                runCatching { processQueue() }
                    .onFailure { Log.e("QueueManager", "Sync-Durchlauf fehlgeschlagen", it) }
                withTimeoutOrNull(RETRY_INTERVAL_MS) { wakeUp.receive() }
            }
        }
    }

    /**
     * Sobald wieder ein Netz da ist, geht die Queue sofort raus statt bis zum
     * nächsten Intervall zu warten. Die Registrierung gilt für die Lebensdauer der
     * App – [QueueManager] ist ein Singleton, es gibt nichts abzumelden.
     */
    private fun watchConnectivity() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (hasPendingOrFailed) {
                        appLog.log(TAG, "Netz wieder verfügbar – Übertragung wird angestoßen")
                        wakeUp.trySend(Unit)
                    }
                }
            })
        }.onFailure { Log.w("QueueManager", "Netz-Überwachung nicht möglich", it) }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun processQueue() {
        if (!isProcessing.compareAndSet(false, true)) return
        try {
            val pending = _pendingItems.value.toList()
            if (pending.isEmpty()) return

            if (!isOnline()) return

            // Nicht eingeloggt → Queue pausieren. Bearer + 401-Refresh übernimmt
            // beim Senden der AuthInterceptor/TokenAuthenticator automatisch.
            if (tokenStore.accessToken == null) return

            for (item in pending) {
                // Sitzung ist unterwegs abgelaufen → Rest liegen lassen bis zum Login
                if (tokenStore.accessToken == null) return

                // Einträge aus Versionen ohne Wheelhotel/Benutzer lassen sich nicht
                // mehr korrekt zuordnen – sichtbar fehlschlagen statt sie mit
                // geratenen Werten zu senden.
                val wheelhotelId = item.wheelhotelId
                val username = item.username
                if (wheelhotelId.isNullOrBlank() || username.isNullOrBlank()) {
                    val missing = listOfNotNull(
                        "Wheelhotel".takeIf { wheelhotelId.isNullOrBlank() },
                        "Benutzer".takeIf { username.isNullOrBlank() }
                    )
                    val errorMsg = missing.joinToString(" und ") + if (missing.size > 1) " fehlen" else " fehlt"
                    Log.w("QueueManager", "✗ Unvollständig: ${item.licensePlate} – $errorMsg")
                    appLog.log(TAG, "fehlgeschlagen: ${item.licensePlate} · $errorMsg · " +
                        "nicht gesendet · id=${item.id}")
                    synchronized(lock) {
                        markFailedLocked(item, errorMsg)
                        saveToDiskLocked()
                    }
                    continue
                }

                try {
                    // Netzwerk-Call bewusst OHNE Lock (darf nicht blockieren)
                    val response = fourWheelsApi.postWheelChange(
                        body = WheelChangeRequest(
                            wheelhotel   = wheelhotelId,
                            username     = username,
                            licensePlate = item.licensePlate,
                            torque       = item.torque,
                            startedAt    = item.startedAt,
                            finishedAt   = item.finishedAt
                        )
                    )

                    val code = response.code()
                    when {
                        response.isSuccessful -> {
                            // Sofort persistieren: sonst würde nach einem Absturz
                            // schon Übertragenes beim nächsten Start doppelt gesendet
                            synchronized(lock) {
                                _pendingItems.value = _pendingItems.value.filter { it.id != item.id }
                                saveToDiskLocked()
                            }
                            Log.d("QueueManager", "✓ Übertragen: ${item.licensePlate}")
                            appLog.log(TAG, "übertragen: ${item.licensePlate} · HTTP $code · id=${item.id}")
                        }
                        // Vorübergehend: Anmeldung, Endpunkt nicht erreichbar, Zeitüberschreitung, Server-Störung.
                        // Bleibt ausstehend und geht automatisch raus, sobald es wieder
                        // klappt – der Nutzer muss nichts tun.
                        code == 401 || code == 403 || code == 404 || code == 408 || code == 429 || code >= 500 -> {
                            Log.w("QueueManager", "… Später erneut: ${item.licensePlate} – HTTP $code")
                            appLog.log(TAG, "später erneut: ${item.licensePlate} · HTTP $code · " +
                                "bleibt ausstehend · id=${item.id}")
                            return
                        }
                        else -> {
                            val errorMsg = "HTTP $code"
                            Log.w("QueueManager", "✗ Fehlgeschlagen: ${item.licensePlate} – $errorMsg")
                            val serverSaid = runCatching { response.errorBody()?.string() }.getOrNull()
                            appLog.log(TAG, "fehlgeschlagen: ${item.licensePlate} · $errorMsg · " +
                                "Versuch ${item.attemptCount + 1} · id=${item.id}" +
                                (serverSaid?.takeIf { it.isNotBlank() }?.let { "\n    Serverantwort: ${it.take(1000)}" } ?: ""))
                            synchronized(lock) {
                                markFailedLocked(item, errorMsg)
                                saveToDiskLocked()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("QueueManager", "✗ Exception: ${item.licensePlate} – ${e.message}")
                    appLog.log(TAG, "Netzwerkfehler: ${item.licensePlate} · " +
                        "${e.javaClass.simpleName}: ${e.message} · bleibt ausstehend · id=${item.id}")
                    // Netzwerkfehler → nicht als FAILED markieren, nächsten Durchlauf abwarten
                    return
                }
            }
        } finally {
            isProcessing.set(false)
        }
    }

    /** Nur unter [lock] aufrufen. */
    private fun markFailedLocked(item: QueueItem, error: String) {
        _pendingItems.value = _pendingItems.value.filter { it.id != item.id }
        _failedItems.value = _failedItems.value + item.copy(
            status       = QueueItemStatus.FAILED,
            errorMessage = error,
            attemptCount = item.attemptCount + 1
        )
    }

    /** Nur unter [lock] aufrufen. Schreibt synchron und atomar. */
    private fun saveToDiskLocked() {
        val json = gson.toJson(_pendingItems.value + _failedItems.value)
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e("QueueManager", "Queue-Datei nicht beschreibbar", e)
            return
        }
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e("QueueManager", "Queue konnte nicht gespeichert werden", e)
        }
    }

    /** Nur unter [lock] aufrufen. */
    private fun loadFromDisk() {
        val json = try {
            if (file.baseFile.exists()) String(file.readFully(), Charsets.UTF_8) else migrateFromPrefs()
        } catch (e: Exception) {
            Log.e("QueueManager", "Queue-Datei nicht lesbar", e)
            null
        } ?: return

        val all: List<QueueItem?> = try {
            gson.fromJson(json, object : TypeToken<List<QueueItem?>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            // Datei zur Analyse sichern statt beim nächsten Speichern zu überschreiben
            Log.e("QueueManager", "Queue-Datei beschädigt – Kopie wird aufbewahrt", e)
            runCatching {
                File(context.filesDir, "radwechsel_queue_corrupt_${System.currentTimeMillis()}.json").writeText(json)
            }
            return
        }

        // Gson umgeht die Kotlin-Defaults: fehlt ein Feld in alten Daten, ist es null.
        // Nicht eindeutig fehlgeschlagene Einträge gelten daher als ausstehend –
        // lieber einmal zu oft senden als einen Eintrag stillschweigend verlieren.
        val items = all.filterNotNull()
        _pendingItems.value = items.filter { it.status != QueueItemStatus.FAILED }
            .map { if (it.status == null) it.copy(status = QueueItemStatus.PENDING) else it }
        _failedItems.value = items.filter { it.status == QueueItemStatus.FAILED }
    }

    private companion object {
        /** Kennzeichnet die Queue-Zeilen im Dateiprotokoll (siehe [AppLog]). */
        const val TAG = "QUEUE"

        /** Sicherheitsnetz, falls kein Anlass kommt: alle 10 Minuten ein Versuch. */
        const val RETRY_INTERVAL_MS = 10L * 60 * 1000
    }

    /** Frühere Versionen hielten die Queue in SharedPreferences. */
    private fun migrateFromPrefs(): String? {
        val prefs = context.getSharedPreferences("radwechsel_queue", Context.MODE_PRIVATE)
        val json = prefs.getString("queue", null) ?: return null
        Log.i("QueueManager", "Queue aus SharedPreferences übernommen")
        val out = file.startWrite()
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        // Erst nach erfolgreichem Schreiben der Datei entfernen
        prefs.edit().remove("queue").commit()
        return json
    }
}
