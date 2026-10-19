package com.couchbase.inspectionsapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.couchbase.inspectionsapp.auth.AuthState
import com.couchbase.inspectionsapp.data.DatabaseManager
import com.couchbase.inspectionsapp.data.ReplicationManager
import com.couchbase.inspectionsapp.model.Inspection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/** Live, local-first list of inspections. Inspectors only ever hold their own; the manager holds all. */
class InspectionsViewModel : ViewModel() {
    val inspections: StateFlow<List<Inspection>> = DatabaseManager.inspectionsFlow()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionListScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenChecklists: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: InspectionsViewModel = viewModel(),
) {
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val status by ReplicationManager.status.collectAsStateWithLifecycle()
    val isAdmin = AuthState.isAdmin

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAdmin) "All inspections" else "My inspections") },
                navigationIcon = { SyncStatusIndicator(status, onClick = onOpenSettings) },
                actions = {
                    if (isAdmin) IconButton(onClick = onCreate) { Icon(Icons.Default.Add, "New inspection") }
                    IconButton(onClick = onOpenChecklists) {
                        Icon(Icons.AutoMirrored.Filled.List, "Checklists")
                    }
                    IconButton(onClick = onOpenSettings) {
                        val err = status.isError
                        Icon(
                            if (err) Icons.Default.CloudOff else Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (err) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (inspections.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isAdmin) "No inspections yet. Tap + to create one and assign it to an inspector."
                    else "No inspections assigned to you yet.",
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(inspections, key = { it.id }) { inspection ->
                    ListItem(
                        headlineContent = { Text(inspection.title) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    inspection.site.takeIf { it.isNotBlank() },
                                    if (isAdmin) "Assigned to ${inspection.assignee}" else null,
                                ).joinToString("  -  ")
                            )
                        },
                        trailingContent = {
                            if (inspection.status.isLocked) Icon(Icons.Default.Lock, "Closed")
                            else Text(inspection.status.label)
                        },
                        modifier = Modifier.clickable { onOpen(inspection.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
