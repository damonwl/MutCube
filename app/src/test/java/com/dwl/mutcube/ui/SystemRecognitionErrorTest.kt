package com.dwl.mutcube.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRecognitionErrorTest {
    @Test
    fun distinguishesNetworkFailureFromTimeout() {
        assertTrue(systemRecognitionErrorDescription(1).contains("ERROR_NETWORK_TIMEOUT，代码 1"))
        assertTrue(systemRecognitionErrorDescription(2).contains("ERROR_NETWORK，代码 2"))
        assertTrue(systemRecognitionErrorDescription(99).contains("代码 99"))
    }
}
