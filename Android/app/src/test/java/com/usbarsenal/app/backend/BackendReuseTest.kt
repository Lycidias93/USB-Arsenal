package com.usbarsenal.app.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A fresh second app must not force a rewrite of a functional shared Root backend.
class BackendReuseTest {
    @Test
    fun runnableBackendCanBeKeptWithoutWriting() {
        assertTrue(Backend(BackendState.READY, "1.6.2").canKeepInstalledBackend())
        assertTrue(Backend(BackendState.UPDATE_AVAILABLE, "1.6.2").canKeepInstalledBackend())
    }

    @Test
    fun missingOrCorruptedBackendCannotBeSilentlyReused() {
        for (state in listOf(
            BackendState.NO_ROOT,
            BackendState.NOT_INSTALLED,
            BackendState.MODULE_MISSING,
            BackendState.CORRUPTED,
        )) {
            assertFalse(Backend(state, null).canKeepInstalledBackend())
        }
    }
}
