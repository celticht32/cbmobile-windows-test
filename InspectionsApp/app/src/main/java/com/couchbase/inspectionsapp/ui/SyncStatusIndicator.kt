package com.couchbase.inspectionsapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.couchbase.inspectionsapp.data.SyncStatus
import com.couchbase.inspectionsapp.ui.theme.SyncBlue
import com.couchbase.inspectionsapp.ui.theme.SyncGreen
import com.couchbase.inspectionsapp.ui.theme.SyncGrey
import com.couchbase.inspectionsapp.ui.theme.SyncOrange
import com.couchbase.inspectionsapp.ui.theme.SyncRed

val SyncStatus.color: Color
    get() = when (this) {
        SyncStatus.IDLE -> SyncGreen
        SyncStatus.CONNECTING -> SyncOrange
        SyncStatus.BUSY -> SyncBlue
        SyncStatus.OFFLINE, SyncStatus.STOPPED -> SyncGrey
        SyncStatus.ERROR -> SyncRed
    }

val SyncStatus.icon: ImageVector
    get() = when (this) {
        SyncStatus.IDLE -> Icons.Default.CloudDone
        SyncStatus.CONNECTING -> Icons.Default.CloudQueue
        SyncStatus.BUSY -> Icons.Default.Sync
        SyncStatus.OFFLINE -> Icons.Default.CloudOff
        SyncStatus.STOPPED -> Icons.Default.Cloud
        SyncStatus.ERROR -> Icons.Default.Error
    }

/** Small colored sync-state icon for the app bar. */
@Composable
fun SyncStatusIndicator(status: SyncStatus, onClick: () -> Unit = {}) {
    IconButton(onClick = onClick) {
        Icon(status.icon, contentDescription = "Sync: ${status.label}", tint = status.color)
    }
}
