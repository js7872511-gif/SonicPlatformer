package com.studiodroid.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowsLayerDetectorTest {
    @Test fun prefersOfficialPackage() {
        assertEquals(
            "com.winlator",
            WindowsLayerDetector.firstSupported(setOf("com.winlator.cmod", "com.winlator"))
        )
    }

    @Test fun findsKnownFallback() {
        assertEquals(
            "com.winlator.glibc",
            WindowsLayerDetector.firstSupported(setOf("com.winlator.glibc"))
        )
    }

    @Test fun returnsNullWhenMissing() {
        assertNull(WindowsLayerDetector.firstSupported(setOf("example.other")))
    }
}
