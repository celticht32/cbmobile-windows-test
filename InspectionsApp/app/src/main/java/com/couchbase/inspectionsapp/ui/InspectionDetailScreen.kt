package com.couchbase.inspectionsapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.couchbase.inspectionsapp.AppConfig
import com.couchbase.inspectionsapp.auth.AuthState
import com.couchbase.inspectionsapp.data.DatabaseManager
import com.couchbase.inspectionsapp.model.InspectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Create (manager only) or edit an inspection.
 *  - Manager: every field, reassign, close, delete.
 *  - Inspector (assignee): status (open / in progress / done) and notes; closed = read-only.
 * The Access Control Function enforces the same rules server-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailScreen(
    inspectionId: String?,
    onDone: () -> Unit,
    checklistsVm: ChecklistsViewModel = viewModel(),
) {
    val isAdmin = AuthState.isAdmin
    val isNew = inspectionId == null
    val checklists by checklistsVm.checklists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(isNew) }
    var title by remember { mutableStateOf("") }
    var site by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf(AppConfig.defaultInspector) }
    var status by remember { mutableStateOf(InspectionStatus.OPEN) }
    var originalStatus by remember { mutableStateOf(InspectionStatus.OPEN) }
    var checklistId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(inspectionId) {
        if (inspectionId != null) {
            try {
                val insp = withContext(Dispatchers.IO) { DatabaseManager.getInspection(inspectionId) }
                if (insp == null) {
                    error = "Inspection not found"
                } else {
                    title = insp.title; site = insp.site; notes = insp.notes
                    assignee = insp.assignee; status = insp.status; originalStatus = insp.status
                    checklistId = insp.checklistId
                }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
            loaded = true
        }
    }

    val readOnly = !isAdmin && originalStatus.isLocked
    val adminFieldsEnabled = isAdmin
    val selectableStatuses = if (isAdmin) InspectionStatus.entries
        else InspectionStatus.entries.filter { it != InspectionStatus.CLOSED }

    fun save() {
        error = null
        if (title.isBlank()) { error = "Title is required"; return }
        if (assignee.isBlank()) { error = "Assignee is required"; return }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (isNew) {
                        DatabaseManager.createInspection(isAdmin, title, site, assignee, checklistId, notes)
                    } else {
                        DatabaseManager.updateInspection(
                            isAdmin, inspectionId!!, title, site, notes, status, assignee, checklistId,
                        )
                    }
                }
                onDone()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New inspection" else "Inspection") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    if (isAdmin && !isNew) {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { DatabaseManager.deleteInspection(isAdmin, inspectionId!!) }
                                    onDone()
                                } catch (e: Exception) {
                                    error = e.message ?: e.toString()
                                }
                            }
                        }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!loaded) {
                Text("Loading...")
                return@Column
            }
            if (readOnly) {
                Text(
                    "This inspection is closed. Only the manager can edit it.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("Title") },
                enabled = adminFieldsEnabled, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = site, onValueChange = { site = it }, label = { Text("Site / location") },
                enabled = adminFieldsEnabled, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = assignee, onValueChange = { assignee = it },
                label = { Text("Assigned inspector (App User name)") },
                enabled = adminFieldsEnabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("Checklist", style = MaterialTheme.typography.titleSmall)
            if (adminFieldsEnabled) {
                OptionRow(label = "None", selected = checklistId == null, enabled = true) { checklistId = null }
                checklists.forEach { c ->
                    OptionRow(label = c.title, selected = checklistId == c.id, enabled = true) { checklistId = c.id }
                }
            }
            val selected = checklists.firstOrNull { it.id == checklistId }
            if (selected != null) {
                Text(selected.title, style = MaterialTheme.typography.bodyLarge)
                selected.items.forEach { Text("- $it") }
            } else if (!adminFieldsEnabled) {
                Text(if (checklistId == null) "None" else "Checklist not synced yet")
            }

            if (!isNew) {
                Text("Status", style = MaterialTheme.typography.titleSmall)
                selectableStatuses.forEach { s ->
                    OptionRow(label = s.label, selected = status == s, enabled = !readOnly) { status = s }
                }
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes / findings") },
                enabled = !readOnly, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { save() }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) {
                Text(if (isNew) "Create & assign" else "Save")
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
