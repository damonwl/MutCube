package com.dwl.mutcube.storage

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolApprovalCoordinatorTest {
    @Test
    fun requestIsPresentedAndCanBeAllowedOnce() = runTest {
        val coordinator = ToolApprovalCoordinator()
        val result = async {
            coordinator.request("文件服务", "read_file", "读取文件", "{\"path\":\"notes.md\"}")
        }

        yield()
        val request = coordinator.pending.value
        assertEquals("文件服务", request?.serverName)
        assertEquals("read_file", request?.toolName)

        coordinator.resolve(requireNotNull(request).id, ToolApprovalDecision.ALLOW_ONCE)
        assertEquals(ToolApprovalDecision.ALLOW_ONCE, result.await())
        assertNull(coordinator.pending.value)
    }

    @Test
    fun unrelatedResolutionDoesNotCompleteRequest() = runTest {
        val coordinator = ToolApprovalCoordinator()
        val result = async { coordinator.request("服务", "工具", null, "{}") }

        yield()
        coordinator.resolve("other", ToolApprovalDecision.DENY)
        yield()
        assertTrue(result.isActive)

        coordinator.resolve(requireNotNull(coordinator.pending.value).id, ToolApprovalDecision.DENY)
        assertEquals(ToolApprovalDecision.DENY, result.await())
    }

    @Test
    fun timedRequestMeasuresOnlyTimeWaitingForDecision() = runTest {
        var timeNanos = 1_000_000_000L
        val coordinator = ToolApprovalCoordinator { timeNanos }
        val result = async { coordinator.requestTimed("服务", "工具", null, "{}") }

        yield()
        timeNanos += 12_000_000_000L
        coordinator.resolve(requireNotNull(coordinator.pending.value).id, ToolApprovalDecision.ALLOW_ONCE)

        assertEquals(ToolApprovalOutcome(ToolApprovalDecision.ALLOW_ONCE, 12_000_000_000L), result.await())
        assertNull(coordinator.pending.value)
    }
}
