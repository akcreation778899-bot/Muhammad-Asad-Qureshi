package com.example.filemanager

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int = 0
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "$childCount items"
            if (size < 1024) return "$size B"
            val kb = size / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }

    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastModified))

    val fileExtension: String
        get() = if (isDirectory) "folder" else name.substringAfterLast('.', "").lowercase()
}

data class StorageSpaceInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedPercent: Float
) {
    val formattedTotal: String get() = formatBytes(totalBytes)
    val formattedFree: String get() = formatBytes(freeBytes)
    val formattedUsed: String get() = formatBytes(usedBytes)

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB", gb)
    }
}

class FileManager(private val context: Context) {

    // Primary base directory for guaranteed read/write in modern Android
    val appStorageDir: File = context.getExternalFilesDir(null) ?: context.filesDir

    init {
        setupInitialWorkspace()
    }

    private fun setupInitialWorkspace() {
        try {
            // Create rich default folder structure so user can test folder copy immediately
            val downloadsDir = File(appStorageDir, "Downloads").apply { mkdirs() }
            val documentsDir = File(appStorageDir, "Documents").apply { mkdirs() }
            val picturesDir = File(appStorageDir, "Pictures").apply { mkdirs() }
            val backupDir = File(appStorageDir, "Backups").apply { mkdirs() }

            // Add sample files in Downloads if empty
            val sampleReport = File(downloadsDir, "Annual_Report_2026.pdf")
            if (!sampleReport.exists()) {
                sampleReport.writeText("Annual Performance Report 2026 - Zoya AI Controller Project Overview.")
            }
            val sampleCode = File(downloadsDir, "Automation_Script.py")
            if (!sampleCode.exists()) {
                sampleCode.writeText("print('AI Phone Control System Initialized')")
            }
            val sampleData = File(downloadsDir, "Financial_Data.csv")
            if (!sampleData.exists()) {
                sampleData.writeText("Month,Revenue,Expenses\nJan,12000,8000\nFeb,15000,9000\nMar,18000,9500")
            }
            val sampleNotes = File(documentsDir, "My_Notes.txt")
            if (!sampleNotes.exists()) {
                sampleNotes.writeText("Zoya Voice Assistant: Voice commands, phone control, real-time web browsing.")
            }
        } catch (e: Exception) {}
    }

    fun getStorageStats(): StorageSpaceInfo {
        return try {
            val stat = StatFs(appStorageDir.path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free
            val percent = if (total > 0) (used.toFloat() / total.toFloat()) else 0.45f
            StorageSpaceInfo(total, free, used, percent)
        } catch (e: Exception) {
            StorageSpaceInfo(64L * 1024 * 1024 * 1024, 38L * 1024 * 1024 * 1024, 26L * 1024 * 1024 * 1024, 0.4f)
        }
    }

    suspend fun listFilesInDirectory(directory: File): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val files = directory.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                lastModified = file.lastModified(),
                childCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun copyFileOrDirectory(source: File, targetDir: File): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!source.exists()) return@withContext Pair(false, "Source '${source.name}' does not exist.")
        if (!targetDir.exists()) targetDir.mkdirs()

        try {
            val destination = File(targetDir, source.name)
            if (source.isDirectory) {
                copyDirectoryRecursive(source, destination)
                Pair(true, "Successfully copied folder '${source.name}' to '${targetDir.name}'")
            } else {
                copySingleFile(source, destination)
                Pair(true, "Successfully copied file '${source.name}' to '${targetDir.name}'")
            }
        } catch (e: Exception) {
            Pair(false, "Copy failed: ${e.message}")
        }
    }

    private fun copySingleFile(src: File, dest: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun copyDirectoryRecursive(srcDir: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        val children = srcDir.listFiles() ?: return
        for (child in children) {
            val target = File(destDir, child.name)
            if (child.isDirectory) {
                copyDirectoryRecursive(child, target)
            } else {
                copySingleFile(child, target)
            }
        }
    }

    suspend fun moveFileOrDirectory(source: File, targetDir: File): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val copyResult = copyFileOrDirectory(source, targetDir)
        if (copyResult.first) {
            source.deleteRecursively()
            Pair(true, "Moved '${source.name}' to '${targetDir.name}'")
        } else {
            copyResult
        }
    }

    suspend fun deleteFileOrDirectory(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createNewFolder(parent: File, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val newDir = File(parent, name)
            newDir.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createNewFile(parent: File, name: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val newFile = File(parent, name)
            newFile.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeVoiceFolderCopy(sourceName: String, targetName: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val srcDir = findFolderByName(sourceName)
        val tgtDir = findFolderByName(targetName) ?: File(appStorageDir, targetName).apply { mkdirs() }

        if (srcDir == null || !srcDir.exists()) {
            return@withContext Pair(false, "Source folder '$sourceName' nahi mila.")
        }

        val files = srcDir.listFiles() ?: emptyArray()
        var count = 0
        for (f in files) {
            val dest = File(tgtDir, f.name)
            if (f.isDirectory) {
                copyDirectoryRecursive(f, dest)
            } else {
                copySingleFile(f, dest)
            }
            count++
        }

        Pair(true, "Zoya ne '$sourceName' folder se tamam ($count items) data successfully '$targetName' folder mein copy kar diya hai.")
    }

    private fun findFolderByName(name: String): File? {
        val lower = name.lowercase().trim()
        val direct = File(appStorageDir, name)
        if (direct.exists()) return direct

        // Check well-known system dirs
        if (lower.contains("download")) {
            val appDl = File(appStorageDir, "Downloads")
            if (appDl.exists()) return appDl
            val extDl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (extDl.exists()) return extDl
        }
        if (lower.contains("document")) {
            val appDoc = File(appStorageDir, "Documents")
            if (appDoc.exists()) return appDoc
            val extDoc = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (extDoc.exists()) return extDoc
        }
        if (lower.contains("picture") || lower.contains("photo") || lower.contains("dcim")) {
            val appPic = File(appStorageDir, "Pictures")
            if (appPic.exists()) return appPic
        }

        // Search subdirectories
        return appStorageDir.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }
    }
}
