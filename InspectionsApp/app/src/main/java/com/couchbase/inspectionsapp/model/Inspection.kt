package com.couchbase.inspectionsapp.model

import com.couchbase.inspectionsapp.AppConfig

/** Inspection lifecycle. CLOSED is locked: only the manager (admin role) may edit it. */
enum class InspectionStatus(val wire: String, val label: String) {
    OPEN("open", "Open"),
    IN_PROGRESS("in_progress", "In progress"),
    DONE("done", "Done"),
    CLOSED("closed", "Closed");

    val isLocked: Boolean get() = this == CLOSED

    companion object {
        fun from(value: String?): InspectionStatus = entries.firstOrNull { it.wire == value } ?: OPEN
    }
}

/** An inspection assigned by the manager to one inspector (Admin-Assigns). */
data class Inspection(
    val id: String,
    val title: String,
    val site: String,
    val notes: String,
    val status: InspectionStatus,
    val assignee: String,
    val checklistId: String?,
    val createdAt: String,
    val updatedAt: String,
) {
    companion object {
        const val TYPE = AppConfig.inspectionType

        fun from(id: String, map: Map<String, Any?>): Inspection? {
            if (map["type"] != TYPE) return null
            return Inspection(
                id = id,
                title = map["title"] as? String ?: "",
                site = map["site"] as? String ?: "",
                notes = map["notes"] as? String ?: "",
                status = InspectionStatus.from(map["status"] as? String),
                assignee = map["assignee"] as? String ?: "",
                checklistId = (map["checklistId"] as? String)?.takeIf { it.isNotBlank() },
                createdAt = map["createdAt"] as? String ?: "",
                updatedAt = map["updatedAt"] as? String ?: "",
            )
        }
    }
}
