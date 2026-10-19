package com.couchbase.inspectionsapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.couchbase.inspectionsapp.AppConfig
import com.couchbase.inspectionsapp.auth.AuthState
import com.couchbase.inspectionsapp.data.DatabaseManager
import com.couchbase.inspectionsapp.data.ReplicationManager

/** Settings & Diagnostics - essential for debugging sync. Scrollable, Sign Out pinned at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authState: AuthState,
    replication: ReplicationManager,
    onClose: () -> Unit,
) {
    val status by replication.status.collectAsStateWithLifecycle()
    val lastError by replication.lastError.collectAsStateWithLifecycle()
    val paused by replication.isManuallyPaused.collectAsStateWithLifecycle()
    val dbError by DatabaseManager.openError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Diagnostics") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back") } },
            )
        },
        // Sign Out is pinned in the bottom bar so it is ALWAYS visible - never clipped below the fold.
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = { authState.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign Out") }
            }
        },
    ) { padding ->
        // MUST be scrollable - the content is taller than the screen.
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // User
            ListItem(headlineContent = { Text("Username") }, trailingContent = { Text(authState.username) })
            ListItem(
                headlineContent = { Text("Role") },
                trailingContent = { Text(if (authState.isAdmin) "inspection manager (admin)" else "inspector") },
            )

            // Sync status - color by state (green=IDLE, orange=CONNECTING, blue=BUSY, grey=OFFLINE, red=error)
            ListItem(
                headlineContent = { Text("Sync") },
                leadingContent = { Icon(status.icon, null, tint = status.color) },
                trailingContent = { Text(status.label) },
            )

            // Error detail with hints
            lastError?.let { err ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(err, color = MaterialTheme.colorScheme.error)
                    if ("401" in err || "unauthorized" in err.lowercase())
                        Text("-> Check username/password match App Services App User credentials")
                    if ("404" in err || "connect" in err.lowercase())
                        Text("-> Check the endpoint URL and that the App Endpoint is Online")
                    if ("APP_SERVICES_ENDPOINT_URL" in err)
                        Text("-> Run setup-capella.sh, then File > Sync Project with Gradle Files and re-run")
                }
            }
            dbError?.let { Text("Database: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            // Connection details (endpoint URL selectable for copy-paste debugging).
            // Read defensively: AppConfig throws while local.properties still holds the PLACEHOLDER.
            ListItem(headlineContent = { Text("Endpoint URL") })
            SelectionContainer(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    runCatching { AppConfig.appServicesEndpointURL }
                        .getOrElse { "Not set - ${it.message}" }
                )
            }
            ListItem(headlineContent = { Text("Scope") }, trailingContent = { Text(AppConfig.scopeName) })
            ListItem(
                headlineContent = { Text("Collections") },
                trailingContent = { Text("${AppConfig.inspectionsCollection}, ${AppConfig.checklistsCollection}") },
            )
            ListItem(headlineContent = { Text("Local documents") }, trailingContent = { Text("${replication.localDocCount}") })

            // Always-On Demo toggle - binds to the SINGLETON's isManuallyPaused (NOT local state)
            ListItem(
                headlineContent = { Text(if (paused) "Offline (simulated)" else "Online") },
                supportingContent = { Text("Always-On Demo: pause sync; edits keep saving locally") },
                leadingContent = { Icon(if (paused) Icons.Default.CloudOff else Icons.Default.Cloud, null) },
                trailingContent = {
                    Switch(checked = !paused, onCheckedChange = { online ->
                        if (online) replication.reconnect() else replication.pause()
                    })
                },
            )

            // Actions - Reconnect here; Sign Out is pinned in the bottomBar above.
            Button(
                onClick = { replication.reconnect() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Reconnect") }
        }
    }
}
