package com.fourwheels.radwechsel.ui.radwechsel

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.fourwheels.radwechsel.api.FourWheelsApi
import com.fourwheels.radwechsel.model.WheelChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class QueueItemStatus { PENDING, FAILED }

data class QueueItem(
    val id: String = UUID.randomUUID().toString(),
    val wheelhotelId: String,
    val wheelhotelName: String,
    val username: String,
    val licensePlate: String,
    val torque: Int,
    val startedAt: String,
    val finishedAt: String,
    val status: QueueItemStatus = QueueItemStatus.PENDING,
    val errorMessage: String? = null,
    val attemptCount: Int = 0
)

@Singleton
class QueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fourWheelsApi: FourWheelsApi,
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("radwechsel_queue", Context.MODE_PRIVATE)

    private val _pendingItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val pendingItems: StateFlow<List<QueueItem>> = _pendingItems.asStateFlow()

    private val _failedItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val failedItems: StateFlow<List<QueueItem>> = _failedItems.asStateFlow()

    val hasPendingOrFailed: Boolean
        get() = _pendingItems.value.isNotEmpty() || _failedItems.value.isNotEmpty()

    init {
        loadFromDisk()
        startWorker()
    }

    fun enqueue(item: QueueItem) {
        _pendingItems.value = _pendingItems.value + item
        saveToDisk()
        Log.d("QueueManager", "Enqueued: ${item.licensePlate}")
    }

    fun retryFailed() {
        val toRetry = _failedItems.value.map {
            it.copy(status = QueueItemStatus.PENDING, errorMessage = null)
        }
        _failedItems.value = emptyList()
        _pendingItems.value = _pendingItems.value + toRetry
        saveToDisk()
    }

    fun retryItem(id: String) {
        val item = _failedItems.value.find { it.id == id } ?: return
        _failedItems.value = _failedItems.value.filter { it.id != id }
        _pendingItems.value = _pendingItems.value + item.copy(
            status = QueueItemStatus.PENDING,
            errorMessage = null
        )
        saveToDisk()
    }

    private fun startWorker() {
        scope.launch {
            while (true) {
                delay(5_000)
                processQueue()
            }
        }
    }

    private suspend fun processQueue() {
        val pending = _pendingItems.value.toList()
        if (pending.isEmpty()) return

        val token = dataStore.data
            .map { it[stringPreferencesKey("access_token")] }
            .first()

        if (token == null) {
            Log.w("QueueManager", "Kein Token – Queue pausiert")
            return
        }

        for (item in pending) {
            try {
                val response = fourWheelsApi.postWheelChange(
                    bearer = "Bearer $token",
                    body = WheelChangeRequest(
                        wheelhotel   = item.wheelhotelId,
                        username     = item.username,
                        licensePlate = item.licensePlate,
                        torque       = item.torque.toString(),
                        startedAt    = item.startedAt,
                        finishedAt   = item.finishedAt
                    )
                )

                if (response.isSuccessful) {
                    _pendingItems.value = _pendingItems.value.filter { it.id != item.id }
                    Log.d("QueueManager", "✓ Übertragen: ${item.licensePlate}")
                } else {
                    val errorMsg = "HTTP ${response.code()}"
                    Log.w("QueueManager", "✗ Fehlgeschlagen: ${item.licensePlate} – $errorMsg")
                    markFailed(item, errorMsg)
                }
            } catch (e: Exception) {
                Log.w("QueueManager", "✗ Exception: ${item.licensePlate} – ${e.message}")
                // Netzwerkfehler → nicht als FAILED markieren, einfach nächsten Versuch abwarten
            }
            saveToDisk()
        }
    }

    private fun markFailed(item: QueueItem, error: String) {
        _pendingItems.value = _pendingItems.value.filter { it.id != item.id }
        _failedItems.value = _failedItems.value + item.copy(
            status       = QueueItemStatus.FAILED,
            errorMessage = error,
            attemptCount = item.attemptCount + 1
        )
    }

    private fun saveToDisk() {
        val all = _pendingItems.value + _failedItems.value
        prefs.edit().putString("queue", gson.toJson(all)).apply()
    }

    private fun loadFromDisk() {
        val json = prefs.getString("queue", null) ?: return
        val type = object : TypeToken<List<QueueItem>>() {}.type
        val all: List<QueueItem> = gson.fromJson(json, type)
        _pendingItems.value = all.filter { it.status == QueueItemStatus.PENDING }
        _failedItems.value  = all.filter { it.status == QueueItemStatus.FAILED }
    }
}
