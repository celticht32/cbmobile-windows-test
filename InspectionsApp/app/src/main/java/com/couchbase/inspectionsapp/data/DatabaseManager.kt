package com.couchbase.inspectionsapp.data

import com.couchbase.inspectionsapp.AppConfig
import com.couchbase.inspectionsapp.model.Checklist
import com.couchbase.inspectionsapp.model.Inspection
import com.couchbase.inspectionsapp.model.InspectionStatus
import com.couchbase.lite.Collection
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.Database
import com.couchbase.lite.DatabaseConfiguration
import com.couchbase.lite.MutableDocument
import com.couchbase.lite.ValueIndexConfiguration
import com.couchbase.lite.queryChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Process singleton that owns the local Couchbase Lite database and the two collections in the
 * named scope "fieldops". All reads are local (always fast, work offline); the replicator syncs
 * in the background.
 */
object DatabaseManager {
    private var database: Database? = null
    private var inspections: Collection? = null
    private var checklists: Collection? = null

    private val _openError = MutableStateFlow<String?>(null)
    val openError: StateFlow<String?> = _openError.asStateFlow()

    @Throws(CouchbaseLiteException::class)
    fun open() {
        if (database != null) return
        val db = Database(AppConfig.databaseName, DatabaseConfiguration())
        // Always a named scope; createCollection is idempotent (returns the existing one).
        val insp = db.createCollection(AppConfig.inspectionsCollection, AppConfig.scopeName)
        val chk = db.createCollection(AppConfig.checklistsCollection, AppConfig.scopeName)
        // Index every field used in WHERE / ORDER BY.
        insp.createIndex("inspectionTypeCreatedAt", ValueIndexConfiguration("type", "createdAt"))
        insp.createIndex("inspectionAssigneeStatus", ValueIndexConfiguration("assignee", "status"))
        chk.createIndex("checklistTypeTitle", ValueIndexConfiguration("type", "title"))
        database = db
        inspections = insp
        checklists = chk
        _openError.value = null
    }

    fun reportOpenError(e: Exception) {
        _openError.value = e.message ?: e.toString()
    }

    /** Collections to replicate (both, in the named scope). */
    fun syncedCollections(): List<Collection> = listOfNotNull(inspections, checklists)

    /** Total local documents across both collections (for the diagnostics screen). */
    fun localDocCount(): Long = try {
        (inspections?.count ?: 0L) + (checklists?.count ?: 0L)
    } catch (e: CouchbaseLiteException) {
        0L
    }

    // --- live queries -------------------------------------------------------------------------

    fun inspectionsFlow(): Flow<List<Inspection>> {
        val db = database ?: return flowOf(emptyList())
        val query = db.createQuery(
            "SELECT META().id AS id, * FROM ${AppConfig.scopeName}.${AppConfig.inspectionsCollection} " +
                "WHERE type = '${AppConfig.inspectionType}' ORDER BY createdAt DESC"
        )
        return query.queryChangeFlow()
            .map { change ->
                change.error?.let { throw it }
                change.results?.allResults().orEmpty().mapNotNull { result ->
                    val id = result.getString("id") ?: return@mapNotNull null
                    // SELECT * nests the document under the collection name.
                    val dict = result.getDictionary(AppConfig.inspectionsCollection)?.toMap()
                        ?: return@mapNotNull null
                    Inspection.from(id, dict)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    fun checklistsFlow(): Flow<List<Checklist>> {
        val db = database ?: return flowOf(emptyList())
        val query = db.createQuery(
            "SELECT META().id AS id, * FROM ${AppConfig.scopeName}.${AppConfig.checklistsCollection} " +
                "WHERE type = '${AppConfig.checklistType}' ORDER BY title"
        )
        return query.queryChangeFlow()
            .map { change ->
                change.error?.let { throw it }
                change.results?.allResults().orEmpty().mapNotNull { result ->
                    val id = result.getString("id") ?: return@mapNotNull null
                    val dict = result.getDictionary(AppConfig.checklistsCollection)?.toMap()
                        ?: return@mapNotNull null
                    Checklist.from(id, dict)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    // --- CRUD (call from Dispatchers.IO) --------------------------------------------------------

    @Throws(CouchbaseLiteException::class)
    fun getInspection(id: String): Inspection? {
        val doc = requireInspections().getDocument(id) ?: return null
        return Inspection.from(doc.id, doc.toMap())
    }

    /** Manager only (Admin-Assigns): create an inspection and assign it to an inspector. */
    @Throws(CouchbaseLiteException::class)
    fun createInspection(
        isAdmin: Boolean,
        title: String,
        site: String,
        assignee: String,
        checklistId: String?,
        notes: String,
    ): String {
        check(isAdmin) { "Only the manager can create inspections" }
        val now = timestamp()
        val doc = MutableDocument("inspection::${UUID.randomUUID()}").apply {
            setString("type", AppConfig.inspectionType)
            setString("title", title.trim())
            setString("site", site.trim())
            setString("notes", notes)
            setString("status", InspectionStatus.OPEN.wire)
            setString("assignee", assignee.trim())   // routes the doc to that inspector's channel
            setString("checklistId", checklistId)
            setString("createdAt", now)
            setString("updatedAt", now)
        }
        requireInspections().save(doc)
        return doc.id
    }

    /**
     * Update an inspection. Mirrors the Access Control Function rules for instant feedback
     * (the server stays authoritative): closed = admin-only; reassign = admin-only.
     */
    @Throws(CouchbaseLiteException::class)
    fun updateInspection(
        isAdmin: Boolean,
        id: String,
        title: String,
        site: String,
        notes: String,
        status: InspectionStatus,
        assignee: String,
        checklistId: String?,
    ) {
        val coll = requireInspections()
        val existing = coll.getDocument(id) ?: error("Inspection not found: $id")
        val oldStatus = InspectionStatus.from(existing.getString("status"))
        check(!oldStatus.isLocked || isAdmin) { "Closed inspections can only be edited by the manager" }
        check(isAdmin || assignee == existing.getString("assignee")) { "Only the manager can reassign" }
        check(isAdmin || status != InspectionStatus.CLOSED) { "Only the manager can close an inspection" }
        val doc = existing.toMutable().apply {
            if (isAdmin) {
                setString("title", title.trim())
                setString("site", site.trim())
                setString("assignee", assignee.trim())
                setString("checklistId", checklistId)
            }
            setString("notes", notes)
            setString("status", status.wire)
            setString("updatedAt", timestamp())
        }
        coll.save(doc)
    }

    @Throws(CouchbaseLiteException::class)
    fun deleteInspection(isAdmin: Boolean, id: String) {
        check(isAdmin) { "Only the manager can delete inspections" }
        val coll = requireInspections()
        coll.getDocument(id)?.let { coll.delete(it) }
    }

    /** Manager only (Shared Read-Only): author a reference checklist. */
    @Throws(CouchbaseLiteException::class)
    fun createChecklist(isAdmin: Boolean, title: String, description: String, items: List<String>): String {
        check(isAdmin) { "Only the manager can create checklists" }
        val coll = checklists ?: error("Database not open")
        val doc = MutableDocument("checklist::${UUID.randomUUID()}").apply {
            setString("type", AppConfig.checklistType)
            setString("title", title.trim())
            setString("description", description.trim())
            setValue("items", items.map { it.trim() }.filter { it.isNotEmpty() })
        }
        coll.save(doc)
        return doc.id
    }

    private fun requireInspections(): Collection = inspections ?: error("Database not open")

    // java.time needs API 26; minSdk is 24, so use SimpleDateFormat (ISO-8601, UTC).
    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
}
