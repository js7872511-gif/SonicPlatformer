package com.studiodroid.app

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WorkspaceManager(private val root: File) {
    val studioExe: File get() = File(root, "RobloxStudioLauncherBeta.exe")

    fun hasStudioExe(): Boolean = studioExe.isFile && studioExe.length() > 2

    fun importStudioExe(input: InputStream): Result<File> = runCatching {
        ensureRoot()
        val temp = File(root, "RobloxStudioLauncherBeta.exe.tmp")
        input.use { source -> temp.outputStream().use { output -> source.copyTo(output) } }

        val header = ByteArray(2)
        val read = FileInputStream(temp).use { it.read(header) }
        if (read < 2 || !PeValidator.isPeExecutable(header)) {
            temp.delete()
            throw IllegalArgumentException("That file is not a Windows executable (missing MZ header).")
        }

        moveReplacing(temp, studioExe)
        studioExe
    }

    fun prepareHelper(name: String, input: InputStream): File {
        ensureRoot()
        val target = File(root, name)
        val temp = File(root, "$name.tmp")
        input.use { source -> temp.outputStream().use { output -> source.copyTo(output) } }
        moveReplacing(temp, target)
        return target
    }

    fun mirrorFiles(): List<File> = listOf(
        studioExe,
        File(root, "Install_Studio.bat"),
        File(root, "Launch_Studio.bat"),
        File(root, "Repair_Studio.bat")
    ).filter { it.isFile }

    private fun ensureRoot() {
        if (!root.exists() && !root.mkdirs()) error("Could not create StudioDroid workspace")
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(), destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
