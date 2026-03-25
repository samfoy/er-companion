package com.ercompanion.savefile

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

class SaveStateReader(private val context: Context) {

    companion object {
        const val MGBA_STATE_SIZE = 0x61000L
        const val EWRAM_OFFSET = 0x21000L
        const val PARTY_COUNT_ADDR = 0x020244ECL  // GBA address
        const val PARTY_DATA_ADDR  = 0x020244F0L  // GBA address
        val PARTY_COUNT_FILE_OFFSET = EWRAM_OFFSET + (PARTY_COUNT_ADDR - 0x02000000L)
        val PARTY_DATA_FILE_OFFSET  = EWRAM_OFFSET + (PARTY_DATA_ADDR - 0x02000000L)

        val SEARCH_PATHS = listOf(
            "/storage/emulated/0/RetroArch/states",
            "/sdcard/RetroArch/states",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states",
            "/storage/emulated/0/Android/data/com.retroarch/files/states"
        )
    }

    private var stateFile: File? = null
    private var lastModified: Long = 0L

    // Find the most recently modified .state0 file
    fun findStateFile(): File? {
        for (path in SEARCH_PATHS) {
            val dir = File(path)
            if (!dir.exists()) continue
            val stateFiles = dir.listFiles { f ->
                f.name.endsWith(".state0") || f.name.endsWith(".state")
            } ?: continue
            if (stateFiles.isNotEmpty()) {
                return stateFiles.maxByOrNull { it.lastModified() }
            }
        }
        return null
    }

    // Returns true if file has changed since last read
    fun hasNewData(): Boolean {
        val file = stateFile ?: findStateFile()?.also { stateFile = it } ?: return false
        val modified = file.lastModified()
        if (modified > lastModified) {
            lastModified = modified
            return true
        }
        return false
    }

    // Read raw party bytes from state file
    fun readPartyData(): Pair<Int, ByteArray>? {
        val file = stateFile ?: findStateFile()?.also { stateFile = it } ?: return null
        if (!file.exists() || file.length() < MGBA_STATE_SIZE) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Read party count (1 byte)
                raf.seek(PARTY_COUNT_FILE_OFFSET)
                val count = raf.read() and 0xFF
                if (count !in 1..6) return null

                // Read party data (6 * 104 bytes)
                raf.seek(PARTY_DATA_FILE_OFFSET)
                val buf = ByteArray(624)
                raf.readFully(buf)
                Pair(count, buf)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Get human-readable status for debug panel
    fun getStatus(): String {
        val file = stateFile ?: findStateFile()?.also { stateFile = it }
        return when {
            file == null -> "No state file found — check paths below"
            !file.exists() -> "File missing: ${file.absolutePath}"
            file.length() < MGBA_STATE_SIZE -> "File too small (${file.length()} bytes, expected ${MGBA_STATE_SIZE})"
            else -> "Reading: ${file.name} (${file.length()/1024}KB, modified ${(System.currentTimeMillis() - file.lastModified())/1000}s ago)"
        }
    }

    fun getSearchedPaths(): List<String> = SEARCH_PATHS

    fun setManualPath(path: String) {
        stateFile = File(path)
        lastModified = 0L
    }

    fun clearCache() {
        stateFile = null
        lastModified = 0L
    }
}
