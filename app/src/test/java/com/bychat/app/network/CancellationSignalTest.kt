package com.bychat.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationSignalTest {
    @Test fun cancelRunsRegisteredActionOnce() {
        var calls = 0
        val signal = CancellationSignal()
        signal.onCancel { calls++ }
        signal.cancel(); signal.cancel()
        assertEquals(1, calls)
        assertTrue(signal.isCancelled)
    }

    @Test fun registrationAfterCancelRunsImmediately() {
        var calls = 0
        val signal = CancellationSignal().also { it.cancel() }
        signal.onCancel { calls++ }
        assertEquals(1, calls)
    }
}
