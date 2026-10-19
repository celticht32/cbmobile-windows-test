package com.couchbase.inspectionsapp.data

import com.couchbase.inspectionsapp.AppConfig
import com.couchbase.lite.BasicAuthenticator
import com.couchbase.lite.CollectionConfiguration
import com.couchbase.lite.ListenerToken
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorActivityLevel
import com.couchbase.lite.ReplicatorConfiguration
import com.couchbase.lite.ReplicatorStatus
import com.couchbase.lite.ReplicatorType
import com.couchbase.lite.URLEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/** Replicator state as shown in the app bar and on the Settings & Diagnostics screen. */
enum class SyncStatus(val label: String) {
    STOPPED("Stopped"),
    OFFLINE("Offline"),
    CONNECTING("Connecting"),
    IDLE("Synced"),
    BUSY("Syncing"),
    ERROR("Error");

    val isError: Boolean get() = this == ERROR
}

/**
 * Process singleton that owns the cloud-edge replicator (Capella App Services over wss://).
 *
 * pause()  - user-initiated offline simulation (Always-On Demo toggle). Sets isManuallyPaused.
 * stop()   - logout/cleanup only. Clears isManuallyPaused.
 * start()  - clears isManuallyPaused, stops any existing replicator, then creates and starts one.
 */
object ReplicationManager {
    private var replicator: Replicator? = null
    private var token: ListenerToken? = null

    // In-memory credentials for the current session (Reconnect / Offline toggle use these).
    private var username: String? = null
    private var password: String? = null

    private val _status = MutableStateFlow(SyncStatus.STOPPED)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _isManuallyPaused = MutableStateFlow(false)
    val isManuallyPaused: StateFlow<Boolean> = _isManuallyPaused.asStateFlow()

    /** Local document count (both collections) for the diagnostics screen. */
    val localDocCount: Long get() = DatabaseManager.localDocCount()

    fun setCredentials(username: String, password: String) {
        this.username = username
        this.password = password
    }

    fun clearCredentials() {
        username = null
        password = null
    }

    @Synchronized
    fun start() {
        _isManuallyPaused.value = false
        stopReplicator()   // never run two replicators
        val user = username
        val pass = password
        if (user == null || pass == null) {
            _status.value = SyncStatus.STOPPED
            return
        }
        try {
            val endpoint = URLEndpoint(URI(AppConfig.appServicesEndpointURL))
            val collections = DatabaseManager.syncedCollections()
            check(collections.isNotEmpty()) { "Local database is not open" }
            // 4.x form: a set of CollectionConfiguration(collection) passed to the constructor.
            val collConfigs = collections.map { CollectionConfiguration(it) }.toSet()
            val config = ReplicatorConfiguration(collConfigs, endpoint).apply {
                type = ReplicatorType.PUSH_AND_PULL
                isContinuous = true
                authenticator = BasicAuthenticator(user, pass.toCharArray())   // CharArray, not String
            }
            val r = Replicator(config)
            token = r.addChangeListener { change -> onStatus(change.status) }
            replicator = r
            _lastError.value = null
            _status.value = SyncStatus.CONNECTING
            r.start()
        } catch (e: Exception) {
            // Surface config errors (e.g. endpoint URL not set) instead of crashing.
            _lastError.value = e.message ?: e.toString()
            _status.value = SyncStatus.ERROR
        }
    }

    /** Reconnect (Settings button / toggling Online) restarts sync with the session credentials. */
    fun reconnect() = start()

    /** User-initiated offline simulation. Does NOT clear isManuallyPaused (start() does). */
    @Synchronized
    fun pause() {
        _isManuallyPaused.value = true
        stopReplicator()
        _status.value = SyncStatus.OFFLINE
    }

    /** Logout / cleanup only. */
    @Synchronized
    fun stop() {
        _isManuallyPaused.value = false
        stopReplicator()
        _lastError.value = null
        _status.value = SyncStatus.STOPPED
    }

    private fun stopReplicator() {
        token?.remove()
        token = null
        replicator?.stop()
        replicator = null
    }

    private fun onStatus(status: ReplicatorStatus) {
        val error = status.error
        if (error != null) {
            _lastError.value = "Error ${error.code}: ${error.message}"
        }
        _status.value = when (status.activityLevel) {
            ReplicatorActivityLevel.STOPPED ->
                if (error != null) SyncStatus.ERROR
                else if (_isManuallyPaused.value) SyncStatus.OFFLINE
                else SyncStatus.STOPPED
            ReplicatorActivityLevel.OFFLINE -> if (error != null) SyncStatus.ERROR else SyncStatus.OFFLINE
            ReplicatorActivityLevel.CONNECTING -> SyncStatus.CONNECTING
            ReplicatorActivityLevel.IDLE -> { if (error == null) _lastError.value = null; SyncStatus.IDLE }
            ReplicatorActivityLevel.BUSY -> SyncStatus.BUSY
            else -> _status.value
        }
    }
}
