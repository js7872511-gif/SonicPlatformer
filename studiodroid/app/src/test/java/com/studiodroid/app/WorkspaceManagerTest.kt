package com.studiodroid.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceManagerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun importsValidExe() {
        val manager = WorkspaceManager(temp.newFolder("workspace"))
        val bytes = byteArrayOf(0x4D, 0x5A, 1, 2, 3)
        assertTrue(manager.importStudioExe(bytes.inputStream()).isSuccess)
        assertArrayEquals(bytes, manager.studioExe.readBytes())
    }

    @Test fun invalidImportPreservesExistingExe() {
        val manager = WorkspaceManager(temp.newFolder("workspace"))
        val valid = byteArrayOf(0x4D, 0x5A, 9)
        manager.importStudioExe(valid.inputStream()).getOrThrow()
        assertTrue(manager.importStudioExe(byteArrayOf(1, 2, 3).inputStream()).isFailure)
        assertArrayEquals(valid, manager.studioExe.readBytes())
    }

    @Test fun reimportReplacesExistingExe() {
        val manager = WorkspaceManager(temp.newFolder("workspace"))
        val first = byteArrayOf(0x4D, 0x5A, 1)
        val second = byteArrayOf(0x4D, 0x5A, 2)
        manager.importStudioExe(first.inputStream()).getOrThrow()
        manager.importStudioExe(second.inputStream()).getOrThrow()
        assertArrayEquals(second, manager.studioExe.readBytes())
    }

    @Test fun helperPreparationIsRepeatable() {
        val manager = WorkspaceManager(temp.newFolder("workspace"))
        manager.prepareHelper("Install_Studio.bat", "first".byteInputStream())
        manager.prepareHelper("Install_Studio.bat", "second".byteInputStream())
        val helper = manager.mirrorFiles().first { it.name == "Install_Studio.bat" }
        assertTrue(helper.readText() == "second")
        assertFalse(manager.hasStudioExe())
    }
}
