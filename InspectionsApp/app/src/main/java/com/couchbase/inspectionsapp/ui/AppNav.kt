package com.couchbase.inspectionsapp.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.couchbase.inspectionsapp.auth.AuthState
import com.couchbase.inspectionsapp.data.ReplicationManager

private const val NEW_ID = "new"

/** Login is always the first screen on cold start (no auto-login). */
@Composable
fun AppNav() {
    val session by AuthState.session.collectAsStateWithLifecycle()
    if (session == null) {
        LoginScreen(onLogin = { u, p -> AuthState.login(u, p) })
        return
    }

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "inspections") {
        composable("inspections") {
            InspectionListScreen(
                onOpen = { id -> nav.navigate("inspection/${Uri.encode(id)}") },
                onCreate = { nav.navigate("inspection/$NEW_ID") },
                onOpenChecklists = { nav.navigate("checklists") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("inspection/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it != NEW_ID }
            InspectionDetailScreen(inspectionId = id, onDone = { nav.popBackStack() })
        }
        composable("checklists") {
            ChecklistListScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                authState = AuthState,
                replication = ReplicationManager,
                onClose = { nav.popBackStack() },
            )
        }
    }
}
