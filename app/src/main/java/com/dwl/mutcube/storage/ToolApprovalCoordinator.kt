package com.dwl.mutcube.storage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class ToolApprovalDecision { ALLOW_ONCE, ALWAYS_ALLOW, DENY }

data class PendingToolApproval(
    val id: String,
    val serverName: String,
    val toolName: String,
    val description: String?,
    val argumentsJson: String,
)

data class ToolApprovalOutcome(val decision: ToolApprovalDecision, val waitDurationNanos: Long)

class ToolApprovalCoordinator(private val nanoTime: () -> Long = System::nanoTime) {
    private data class ActiveRequest(
        val presentation: PendingToolApproval,
        val result: CompletableDeferred<ToolApprovalDecision>,
    )

    private val requestMutex = Mutex()
    @Volatile private var active: ActiveRequest? = null
    private val mutablePending = MutableStateFlow<PendingToolApproval?>(null)
    val pending = mutablePending.asStateFlow()

    suspend fun request(
        serverName: String,
        toolName: String,
        description: String?,
        argumentsJson: String,
    ): ToolApprovalDecision = requestTimed(serverName, toolName, description, argumentsJson).decision

    suspend fun requestTimed(
        serverName: String,
        toolName: String,
        description: String?,
        argumentsJson: String,
    ): ToolApprovalOutcome = requestMutex.withLock {
        val request = ActiveRequest(
            presentation = PendingToolApproval(
                id = UUID.randomUUID().toString(),
                serverName = serverName,
                toolName = toolName,
                description = description,
                argumentsJson = argumentsJson,
            ),
            result = CompletableDeferred(),
        )
        active = request
        mutablePending.value = request.presentation
        try {
            val waitingStartedAt = nanoTime()
            val decision = request.result.await()
            ToolApprovalOutcome(decision, (nanoTime() - waitingStartedAt).coerceAtLeast(0))
        } finally {
            if (active === request) {
                active = null
                mutablePending.value = null
            }
        }
    }

    fun resolve(requestId: String, decision: ToolApprovalDecision) {
        active?.takeIf { it.presentation.id == requestId }?.result?.complete(decision)
    }
}
