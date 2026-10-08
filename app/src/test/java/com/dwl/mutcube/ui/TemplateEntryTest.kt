package com.dwl.mutcube.ui

import com.dwl.mutcube.core.database.TemplateBindingEntity
import com.dwl.mutcube.core.database.TemplatePermissionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEntryTest {
    private val bindings = listOf(TemplateBindingEntity("a", "fitness", "2.1.0", true),
        TemplateBindingEntity("b", "fitness", "2.1.0", true),
        TemplateBindingEntity("c", "fitness", "2.1.0", false),
        TemplateBindingEntity("deleted", "fitness", "2.1.0", true),
        TemplateBindingEntity("a", "notes", "2.0.0", true))
    @Test fun boundTemplatePrefersCurrentProjectWithoutRebinding() {
        assertEquals(listOf("b"), templateEntryProjects(bindings, "fitness", setOf("a", "b", "c"), "b"))
    }
    @Test fun multipleBindingsRequireOnlyProjectSelection() {
        assertEquals(listOf("a", "b"), templateEntryProjects(bindings, "fitness", setOf("a", "b", "c"), null))
    }
    @Test fun unboundDisabledOrDeletedProjectRequiresBinding() {
        assertEquals(emptyList<String>(), templateEntryProjects(bindings, "fitness", setOf("c"), "c"))
        assertEquals(emptyList<String>(), templateEntryProjects(bindings, "unknown", setOf("a"), "a"))
    }
    @Test fun singleBindingOpensWithoutCurrentProject() {
        assertEquals(listOf("a"), templateEntryProjects(bindings, "notes", setOf("a", "b"), null))
    }
    @Test fun restoredBindingWithoutActiveGrantsRequiresExplicitAuthorization() {
        val binding = bindings.first()
        val revoked = TemplatePermissionEntity("a", "fitness", "plans", "LIST", true)
        assertTrue(templateAuthorizationRequired(binding, listOf(revoked)))
        assertTrue(templateAuthorizationRequired(binding, listOf(revoked.copy(projectId = "b", revoked = false))))
        assertFalse(templateAuthorizationRequired(binding, listOf(revoked.copy(revoked = false))))
        assertFalse(templateAuthorizationRequired(binding.copy(enabled = false), emptyList()))
        assertFalse(templateAuthorizationRequired(null, emptyList()))
    }
}
