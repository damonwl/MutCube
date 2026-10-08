package com.dwl.mutcube.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatePolicyTest {
    @Test
    fun grantsAreExplicitScopedAndRevocable() {
        val context = TemplateAccessContext("project-a", "template")
        val permission = TemplatePermission("project-a", "template", "logs", TemplateOperation.READ)
        assertFalse(TemplatePolicy.allows(context, "logs", TemplateOperation.READ, emptyList()))
        assertTrue(TemplatePolicy.allows(context, "logs", TemplateOperation.READ, listOf(permission)))
        assertFalse(TemplatePolicy.allows(context.copy(projectId = "project-b"), "logs", TemplateOperation.READ, listOf(permission)))
        assertFalse(TemplatePolicy.allows(context, "logs", TemplateOperation.APPEND, listOf(permission)))
        assertFalse(TemplatePolicy.allows(context, "logs", TemplateOperation.READ, listOf(permission.copy(revoked = true))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun historyCannotBeProposedForUpdate() {
        TemplatePolicy.requireMutationAllowed(DataMutability.IMMUTABLE_HISTORY, true, TemplateOperation.PROPOSE_UPDATE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun appendCannotOverwriteAnExistingRecord() {
        TemplatePolicy.requireMutationAllowed(DataMutability.IMMUTABLE_HISTORY, true, TemplateOperation.APPEND)
    }
}
