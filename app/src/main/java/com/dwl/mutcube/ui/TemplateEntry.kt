package com.dwl.mutcube.ui

import com.dwl.mutcube.core.database.TemplateBindingEntity
import com.dwl.mutcube.core.database.TemplatePermissionEntity

/** Project selection is not authorization. Disabled and deleted-project bindings never count. */
internal fun templateEntryProjects(bindings: List<TemplateBindingEntity>, templateId: String,
    existingProjectIds: Set<String>, preferredProjectId: String?): List<String> {
    val projects = bindings.filter { it.enabled && it.templateId == templateId && it.projectId in existingProjectIds }
        .map { it.projectId }.distinct()
    return if (preferredProjectId in projects) listOf(requireNotNull(preferredProjectId)) else projects
}

/** Restored bindings remain attached, while backup restore intentionally revokes their grants. */
internal fun templateAuthorizationRequired(binding: TemplateBindingEntity?, permissions: List<TemplatePermissionEntity>): Boolean =
    binding?.enabled == true && permissions.none {
        it.projectId == binding.projectId && it.templateId == binding.templateId && !it.revoked
    }
