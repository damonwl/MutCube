package com.dwl.mutcube.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.core.database.*
import com.dwl.mutcube.core.model.*
import com.dwl.mutcube.feature.chat.ChatGenerationService
import com.dwl.mutcube.template.builtin.FitnessTemplate
import com.dwl.mutcube.template.builtin.NotesTemplate
import com.dwl.mutcube.template.core.*
import com.dwl.mutcube.template.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConversationRegressionTest {
    @Test fun pastedTxtBodyReachesModelRequestOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        val store = AttachmentStore(context)
        val body = "训练记录起始\n" + "平板哑铃卧推 22.5kg，4组8次，RIR 1。\n".repeat(180) + "结束校验7391"
        val attachment = store.saveText(body)
        try {
            val conversations = RoomConversationRepository(db)
            val chat = conversations.createWithFirstUserMessage(listOf(attachment), null)
            var captured: GenerationRequest? = null
            val gateway = object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> {
                    captured = request
                    return flowOf("已读取")
                }
            }
            ChatGenerationService(conversations, gateway, FitnessFixture.credentials,
                providerProfileStore = FitnessFixture.profiles).generateReply(chat)
            val content = requireNotNull(captured).messages.last { it.role == ModelMessageRole.USER }.content
            assertTrue(content.contains(body))
            assertFalse(content.contains("未提取正文"))
            assertEquals("已读取", conversations.observeMessages(chat).first().last().text)
        } finally { db.close(); store.deleteDraft(attachment) }
    }

    @Test fun replacingProjectTemplateRevokesPreviousButPreservesItsRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MutCubeDatabase::class.java).build()
        try {
            val conversations = RoomConversationRepository(db)
            val project = conversations.createSpace("隔离单模板测试")
            val repository = TemplateRepository(db)
            val gateway = object : ModelGateway {
                override fun stream(request: GenerationRequest, credential: ApiCredential) = flowOf("{}")
            }
            val runtime = TemplateRuntime(repository, conversations, FitnessFixture.profiles,
                DefaultModelSettingsStore, FitnessFixture.credentials, gateway)
            val notes = NotesTemplate.manifest()
            val fitness = FitnessTemplate.manifest()
            runtime.enable(notes, project)
            TemplateActions(repository, runtime, conversations).engine.execute(
                TemplateInstance(project.value, notes.id), notes, "notes.save", ActionChannel.GUI,
                "fixture-note", """{"text":"保留原数据"}""",
            )
            val access = TemplateAccessContext(project.value, notes.id)
            runtime.enable(fitness, project)
            assertEquals(listOf(fitness.id), repository.userBindings.first().filter { it.enabled }.map { it.templateId })
            assertTrue(runCatching { repository.list(access, "notes") }.isFailure)
            assertEquals(1, repository.userRecords.first().size)
            val tools = TemplateDataToolService(repository, runtime, conversations, listOf(notes, fitness))
            assertTrue(tools.definitions(project).none { it.description.startsWith(notes.name + " /") })
            runtime.enable(notes, project)
            assertEquals(1, repository.list(access, "notes").size)
            assertEquals(listOf(notes.id), repository.userBindings.first().filter { it.enabled }.map { it.templateId })
        } finally { db.close() }
    }
}
