package com.dwl.mutcube.storage

import com.dwl.mutcube.core.ai.ApiCredential
import com.dwl.mutcube.core.ai.GenerationRequest
import com.dwl.mutcube.core.ai.ModelGateway
import com.dwl.mutcube.core.ai.ModelStreamEvent
import com.dwl.mutcube.core.ai.ProviderModelCatalog
import com.dwl.mutcube.core.ai.ProviderProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

class LoggingModelGateway(
    private val delegate: ModelGateway,
    private val catalog: ProviderModelCatalog,
    private val logs: RequestLogSink,
) : ModelGateway, ProviderModelCatalog {
    override fun stream(request: GenerationRequest, credential: ApiCredential): Flow<String> =
        streamEvents(request, credential).transform { event ->
            if (event is ModelStreamEvent.TextDelta) emit(event.text)
        }

    override fun streamEvents(request: GenerationRequest, credential: ApiCredential): Flow<ModelStreamEvent> = flow {
        val startedAt = System.currentTimeMillis()
        val startedNanos = System.nanoTime()
        var responseCharacters = 0
        var inputTokens: Long? = null
        var outputTokens: Long? = null
        var failure: Throwable? = null
        try {
            delegate.streamEvents(request, credential).collect { event ->
                if (event is ModelStreamEvent.TextDelta) responseCharacters += event.text.length
                if (event is ModelStreamEvent.Usage) {
                    inputTokens = event.inputTokens ?: inputTokens
                    outputTokens = event.outputTokens ?: outputTokens
                }
                emit(event)
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            // Optional diagnostics must never replace a provider error or fail a successful reply.
            withContext(NonCancellable + Dispatchers.IO) { runCatching { logs.append(
                RequestLogEntry(
                    id = UUID.randomUUID().toString(), startedAt = startedAt,
                    durationMs = (System.nanoTime() - startedNanos) / 1_000_000,
                    modelId = request.modelId, providerUrl = SensitiveTextRedactor.redact(request.baseUrl.orEmpty()),
                    protocol = request.protocol.name, messageCount = request.messages.size,
                    inputCharacters = request.messages.sumOf { it.content.length }, toolCount = request.tools.size,
                    responseCharacters = responseCharacters, inputTokens = inputTokens, outputTokens = outputTokens,
                    error = failure?.message?.let(SensitiveTextRedactor::redact)?.take(2_000),
                ),
            ) } }
        }
    }

    override suspend fun listModels(
        baseUrl: String,
        credential: ApiCredential,
        protocol: ProviderProtocol,
    ): List<String> = catalog.listModels(baseUrl, credential, protocol)
}
