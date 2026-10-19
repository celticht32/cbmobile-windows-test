package com.couchbase.inspectionsapp.model

import com.couchbase.inspectionsapp.AppConfig

/** Shared read-only reference checklist (manager authors, every inspector reads). */
data class Checklist(
    val id: String,
    val title: String,
    val description: String,
    val items: List<String>,
) {
    companion object {
        const val TYPE = AppConfig.checklistType

        fun from(id: String, map: Map<String, Any?>): Checklist? {
            if (map["type"] != TYPE) return null
            return Checklist(
                id = id,
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                items = (map["items"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            )
        }
    }
}
