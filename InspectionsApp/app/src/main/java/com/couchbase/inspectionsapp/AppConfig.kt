package com.couchbase.inspectionsapp

/**
 * App-wide constants. The scope and collection names are the contract shared with
 * the COLLECTIONS value in provision.env (fieldops/inspections fieldops/checklists)
 * and with the Access Control Function file names in the sync-functions folder.
 */
object AppConfig {
    const val databaseName = "InspectionsDB"

    // Named scope - NEVER "_default" for app data
    const val scopeName = "fieldops"                   // matches COLLECTIONS env var scope

    // One constant per collection
    const val inspectionsCollection = "inspections"    // Admin-Assigns records
    const val checklistsCollection = "checklists"      // Shared Read-Only reference data

    // Document types (must match DOC_TYPE in each Access Control Function)
    const val inspectionType = "inspection"
    const val checklistType = "checklist"

    // Channels
    const val adminChannel = "admin"                   // channel for App Users with the admin App Role

    // The manager App User holds the admin App Role and sees all inspections.
    // Must match MANAGER_USER in provision.env (default: "manager").
    // Distinct from the App Services Admin Credential (infrastructure only - not a mobile user).
    const val managerUsername = "manager"

    // Default regular App User created by setup-capella.sh (pre-filled as the assignee).
    const val defaultInspector = "bob"

    // WebSocket endpoint - injected at build time from local.properties (cbl.endpointUrl)
    val appServicesEndpointURL: String
        get() = BuildConfig.APP_SERVICES_ENDPOINT_URL.also {
            require(it.isNotBlank() && !it.contains("PLACEHOLDER")) {
                "APP_SERVICES_ENDPOINT_URL not set. Run setup-capella.sh, then re-sync Gradle."
            }
        }
}
