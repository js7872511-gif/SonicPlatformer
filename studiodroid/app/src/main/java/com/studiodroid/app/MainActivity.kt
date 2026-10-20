package com.studiodroid.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var workspace: WorkspaceManager
    private lateinit var status: TextView
    private lateinit var installButton: Button
    private lateinit var launchButton: Button
    private lateinit var repairButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        workspace = WorkspaceManager(File(filesDir, "StudioDroid"))
        status = findViewById(R.id.statusText)
        installButton = findViewById(R.id.installButton)
        launchButton = findViewById(R.id.launchButton)
        repairButton = findViewById(R.id.repairButton)

        findViewById<Button>(R.id.importButton).setOnClickListener { pickStudioExe() }
        installButton.setOnClickListener { handoff("Install_Studio.bat") }
        launchButton.setOnClickListener { handoff("Launch_Studio.bat") }
        repairButton.setOnClickListener { handoff("Repair_Studio.bat") }
        findViewById<Button>(R.id.filesButton).setOnClickListener { openFiles() }
        findViewById<Button>(R.id.windowsButton).setOnClickListener { openWindowsLayer() }

        refreshState()
    }

    private fun pickStudioExe() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
            REQUEST_EXE
        )
    }

    @Deprecated("Platform result API retained to avoid AndroidX dependency in v0.1")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }

        val result = runCatching {
            val input = contentResolver.openInputStream(uri) ?: error("Android could not open that file")
            workspace.importStudioExe(input).getOrThrow()
            prepareHelpers()
            mirrorWorkspace()
        }

        if (result.isSuccess) {
            toast("Studio launcher imported. Files copied to Downloads/StudioDroid.")
        } else {
            toast(result.exceptionOrNull()?.message ?: "Import failed")
        }
        refreshState()
    }

    private fun prepareHelpers() {
        HELPER_FILES.forEach { name ->
            workspace.prepareHelper(name, assets.open(name))
        }
    }

    private fun mirrorWorkspace() {
        workspace.mirrorFiles().forEach(::publishToDownloads)
    }

    private fun publishToDownloads(file: File) {
        val relativePath = "Download/StudioDroid/"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existingUri = contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(file.name, relativePath),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) Uri.withAppendedPath(collection, cursor.getLong(0).toString()) else null
        }

        val uri = existingUri ?: contentResolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            }
        ) ?: error("Could not create Downloads/StudioDroid/${file.name}")

        contentResolver.openOutputStream(uri, "wt")?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not write Downloads/StudioDroid/${file.name}")
    }

    private fun handoff(scriptName: String) {
        if (!workspace.hasStudioExe()) {
            toast("Import RobloxStudioLauncherBeta.exe first.")
            return
        }

        val prepared = runCatching {
            prepareHelpers()
            mirrorWorkspace()
        }
        if (prepared.isFailure) {
            toast(prepared.exceptionOrNull()?.message ?: "Could not prepare StudioDroid files")
            return
        }

        val detected = WindowsLayerDetector.detect(packageManager)
        if (detected == null) {
            toast("No supported Winlator installation detected. Your StudioDroid files are still safe.")
            return
        }

        toast("Opening Windows layer. Run D:\\StudioDroid\\$scriptName inside it.")
        startActivity(detected.second)
    }

    private fun openWindowsLayer() {
        val detected = WindowsLayerDetector.detect(packageManager)
        if (detected == null) toast("Winlator was not detected.") else startActivity(detected.second)
    }

    private fun openFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FStudioDroid")
            )
        }
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
    }

    private fun refreshState() {
        val hasExe = workspace.hasStudioExe()
        val layer = WindowsLayerDetector.detect(packageManager)?.first
        status.text = buildString {
            append(if (hasExe) "Studio EXE: Ready" else "Studio EXE: Not imported")
            append("\n")
            append(if (layer != null) "Windows layer: $layer" else "Windows layer: Not detected")
            append("\nWorkspace: Downloads/StudioDroid")
        }
        installButton.isEnabled = hasExe
        launchButton.isEnabled = hasExe
        repairButton.isEnabled = hasExe
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQUEST_EXE = 1001
        private val HELPER_FILES = listOf("Install_Studio.bat", "Launch_Studio.bat", "Repair_Studio.bat")
    }
}
