package com.dwl.mutcube.template.builtin

import com.dwl.mutcube.template.core.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FitnessTemplateTest {
    @Test fun declarationSeparatesFactsDraftsAndCandidates() {
        val manifest = FitnessTemplate.manifest()
        assertEquals(8, manifest.collections.size)
        assertEquals(15, manifest.actions.size)
        assertEquals(CollectionPolicy.IMMUTABLE_HISTORY, manifest.collection("training").policy)
        assertEquals(CollectionPolicy.MUTABLE, manifest.collection("drafts").policy)
        assertEquals(CollectionPolicy.VERSIONED, manifest.collection("plans").policy)
        assertTrue(manifest.actions.filter { ActionChannel.CHAT in it.channels }.all {
            it.mode in setOf(ActionMode.LIST, ActionMode.CURRENT, ActionMode.GENERATE)
        })
        assertFalse(ActionChannel.CHAT in manifest.action("draft.list").channels)
        assertFalse(ActionChannel.CHAT in manifest.action("profile.assist").channels)
        assertEquals(CollectionPolicy.IMMUTABLE_HISTORY, manifest.collection("profile_candidates").policy)
    }
    @Test fun generationPublicInputCannotOverrideHostBindings() {
        val manifest = FitnessTemplate.manifest()
        val action = manifest.action("plan.generate")
        val publicInput = """{"request":"首份计划","baseRevision":0,"targetDate":"2026-09-17"}"""
        TemplateJsonContract.validate(action.inputSchema, publicInput)
        assertTrue(runCatching { TemplateJsonContract.validate(action.inputSchema,
            JsonObject(Json.parseToJsonElement(publicInput).jsonObject + ("profile" to buildJsonObject {})).toString())
        }.isFailure)
        assertEquals(listOf("profile", "current_plan", "last_training"), action.bindings.map { it.field })
        assertEquals(manifest.collection("requests").schema, manifest.preparedSchema(action))
    }
}
