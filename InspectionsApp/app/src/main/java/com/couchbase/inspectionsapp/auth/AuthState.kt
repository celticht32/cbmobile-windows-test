package com.couchbase.inspectionsapp.auth

import android.content.Context
import com.couchbase.inspectionsapp.AppConfig
import com.couchbase.inspectionsapp.data.CredentialStore
import com.couchbase.inspectionsapp.data.ReplicationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-singleton session state.
 *
 * The app ALWAYS shows the Login screen on cold start (no silent auto-login). Always-on comes
 * from the local Couchbase Lite database persisting on disk, not from skipping Login.
 * The password is stored only via the Keystore-backed CredentialStore; the running session
 * (Reconnect / Offline toggle) uses the in-memory credentials held by ReplicationManager.
 */
object AuthState {
    data class Session(val username: String)

    private lateinit var credentialStore: CredentialStore

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    val username: String get() = _session.value?.username.orEmpty()

    /** The manager App User holds the admin App Role. NOT the App Services Admin Credential. */
    val isAdmin: Boolean get() = username == AppConfig.managerUsername

    fun init(context: Context) {
        credentialStore = CredentialStore(context)
    }

    fun login(username: String, password: String) {
        val user = username.trim()
        require(user.isNotEmpty() && password.isNotEmpty()) { "Enter a username and password" }
        credentialStore.savePassword(user, password)
        _session.value = Session(user)
        ReplicationManager.setCredentials(user, password)
        ReplicationManager.start()
    }

    fun logout() {
        val user = username
        ReplicationManager.stop()
        ReplicationManager.clearCredentials()
        if (user.isNotEmpty()) credentialStore.clear(user)
        _session.value = null
    }
}
