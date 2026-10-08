package com.dwl.mutcube.core.database

import androidx.room.Entity

// User data survives template unbinding and project deletion; no cascading foreign keys.
@Entity(tableName = "template_current_versions", primaryKeys = ["projectId", "templateId", "collection"])
data class TemplateCurrentVersionEntity(
    val projectId: String,
    val templateId: String,
    val collection: String,
    val recordKey: String,
    val revision: Long,
    val updatedAt: Long,
)
