package com.dwl.mutcube.template.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TemplateActionEngineTest {
    private val manifest = TemplateManifest.parse(serviceDocument())
    private val instance = TemplateInstance("project", manifest.id)
    private class Host : TemplateActionHost {
        var calls = 0; var denied = false; var cancelled = false; var received = ""; var resolved: String? = null
        override suspend fun authorize(instance: TemplateInstance, manifest: TemplateManifest, action: TemplateAction) { check(!denied) }
        override suspend fun resolve(instance: TemplateInstance, manifest: TemplateManifest, binding: ActionBinding) = resolved
        override suspend fun perform(instance: TemplateInstance, manifest: TemplateManifest, action: TemplateAction, requestId: String, input: String): String {
            if (cancelled) throw CancellationException("fixture")
            calls++; received = input; return """{"text":"processed"}"""
        }
    }
    @Test fun bothInterfacesUseExactlyTheSameEngine() = runBlocking {
        val host = Host(); val engine = TemplateActionEngine(host)
        val gui = engine.execute(instance, manifest, "text.generate", ActionChannel.GUI, "gui", """{"text":"hello"}""")
        val chat = engine.execute(instance, manifest, "text.generate", ActionChannel.CHAT, "chat", """{"text":"hello"}""")
        assertEquals(gui.data, chat.data); assertEquals(2, host.calls)
    }
    @Test fun invalidIdentityUndeclaredActionsInvalidInputAndRevocationDeny() = runBlocking {
        val host = Host(); val engine = TemplateActionEngine(host)
        assertTrue(runCatching { engine.execute(instance.copy(templateId = "other"), manifest, "text.generate", ActionChannel.GUI, "id", """{"text":"x"}""") }.isFailure)
        assertTrue(runCatching { engine.execute(instance, manifest, "main", ActionChannel.GUI, "id", "{}") }.isFailure)
        assertTrue(runCatching { engine.execute(instance, manifest, "text.generate", ActionChannel.GUI, "../id", "{}") }.isFailure)
        assertTrue(runCatching { engine.execute(instance, manifest, "text.generate", ActionChannel.GUI, "id", """{"text":1}""") }.isFailure)
        host.denied = true
        assertTrue(runCatching { engine.execute(instance, manifest, "text.generate", ActionChannel.GUI, "id", """{"text":"x"}""") }.isFailure)
        assertEquals(0, host.calls)
    }
    @Test fun guiCannotBypassHostBindingsEither() = runBlocking {
        val host = Host(); val engine = TemplateActionEngine(host)
        val empty = """{"type":"object","properties":{},"required":[],"additionalProperties":false}"""
        val action = manifest.actions[0].copy(inputSchema = empty, bindings = listOf(ActionBinding("note", "inputs", BindingSource.LATEST, true)))
        val service = manifest.copy(actions = listOf(action))
        assertTrue(runCatching { engine.execute(instance, service, action.id, ActionChannel.GUI, "id", "{}") }.isFailure)
        host.resolved = """{"text":"host fact"}"""
        engine.execute(instance, service, action.id, ActionChannel.GUI, "id", "{}")
        assertEquals("""{"note":{"text":"host fact"}}""", host.received)
        assertTrue(runCatching { engine.execute(instance, service, action.id, ActionChannel.GUI, "id", """{"note":{"text":"fake"}}""") }.isFailure)
    }
    @Test fun chatMutationsAlwaysDenyAndGuiDeletionRequiresNativeConfirmation() = runBlocking {
        val host = Host(); val action = manifest.actions[0].copy(mode = ActionMode.DELETE, inputSchema = """{"type":"object","properties":{},"required":[],"additionalProperties":false}""")
        val service = manifest.copy(actions = listOf(action)); val engine = TemplateActionEngine(host)
        assertTrue(runCatching { engine.execute(instance, service, action.id, ActionChannel.CHAT, "id", "{}") }.isFailure)
        assertTrue(runCatching { engine.execute(instance, service, action.id, ActionChannel.GUI, "id", "{}") }.isFailure)
        assertEquals("{\"confirmed\":false}", engine.withConfirmation { _, _, _ -> false }.execute(instance, service, action.id, ActionChannel.GUI, "id", "{}").data)
        assertEquals(0, host.calls)
    }
    @Test fun guiVersionSelectionStillRequiresHostGateAndCannotBeInvokedFromChat() = runBlocking {
        val host = Host()
        val action = manifest.actions[0].copy(mode = ActionMode.SELECT_VERSION,
            inputSchema = """{"type":"object","properties":{},"required":[],"additionalProperties":false}""")
        val service = manifest.copy(actions = listOf(action))
        val engine = TemplateActionEngine(host)
        assertTrue(runCatching { engine.execute(instance, service, action.id, ActionChannel.GUI, "id", "{}") }.isFailure)
        val trustedGui = engine.withConfirmation { _, _, _ -> true }
        assertTrue(runCatching { trustedGui.execute(instance, service, action.id, ActionChannel.CHAT, "id", "{}") }.isFailure)
        trustedGui.execute(instance, service, action.id, ActionChannel.GUI, "id", "{}")
        assertEquals(1, host.calls)
        host.denied = true
        assertTrue(runCatching { trustedGui.execute(instance, service, action.id, ActionChannel.GUI, "id", "{}") }.isFailure)
        assertEquals(1, host.calls)
    }
    @Test(expected = CancellationException::class) fun cancellationPropagates() = runBlocking {
        val host = Host().apply { cancelled = true }
        TemplateActionEngine(host).execute(instance, manifest, "text.generate", ActionChannel.GUI, "id", """{"text":"x"}""")
        Unit
    }
}
