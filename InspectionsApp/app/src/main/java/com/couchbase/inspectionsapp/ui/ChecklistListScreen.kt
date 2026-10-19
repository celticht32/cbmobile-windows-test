package com.couchbase.inspectionsapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.couchbase.inspectionsapp.auth.AuthState
import com.couchbase.inspectionsapp.data.DatabaseManager
import com.couchbase.inspectionsapp.model.Checklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared read-only reference checklists (public "!" channel): everyone reads, manager authors. */
class ChecklistsViewModel : ViewModel() {
    val checklists: StateFlow<List<Checklist>> = DatabaseManager.checklistsFlow()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistListScreen(onBack: () -> Unit, vm: ChecklistsViewModel = viewModel()) {
    val checklists by vm.checklists.collectAsStateWithLifecycle()
    val isAdmin = AuthState.isAdmin
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklists") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    if (isAdmin) IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "New checklist") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    if (isAdmin) "Reference checklists - every inspector receives these (read-only for them)."
                    else "Reference checklists from your manager (read-only).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (checklists.isEmpty()) {
                item { Text("No checklists yet.", modifier = Modifier.padding(16.dp)) }
            }
            items(checklists, key = { it.id }) { checklist ->
                ListItem(
                    headlineContent = { Text(checklist.title) },
                    supportingContent = {
                        Column {
                            if (checklist.description.isNotBlank()) Text(checklist.description)
                            checklist.items.forEach { Text("- $it") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showCreate && isAdmin) {
        CreateChecklistDialog(onDismiss = { showCreate = false })
    }
}

@Composable
private fun CreateChecklistDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var itemsText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New checklist") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    itemsText, { itemsText = it },
                    label = { Text("Items (one per line)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { error = "Title is required"; return@TextButton }
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            DatabaseManager.createChecklist(AuthState.isAdmin, title, description, itemsText.lines())
                        }
                        onDismiss()
                    } catch (e: Exception) {
                        error = e.message ?: e.toString()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
