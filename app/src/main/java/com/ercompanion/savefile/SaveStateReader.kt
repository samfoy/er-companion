package com.ercompanion.savefile

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

class SaveStateReader(private val context: Context) {

    companion object {
        const val MGBA_STATE_SIZE = 0x61000L   // 397,312 bytes uncompressed
        const val EWRAM_OFFSET    = 0x21000L
        const val PARTY_COUNT_ADDR = 0x020244ECL
        const val PARTY_DATA_ADDR  = 0x020244F0L
        val PARTY_COUNT_FILE_OFFSET = EWRAM_OFFSET + (PARTY_COUNT_ADDR - 0x02000000L)
        val PARTY_DATA_FILE_OFFSET  = EWRAM_OFFSET + (PARTY_DATA_ADDR - 0x02000000L)

        // zlib magic bytes
        private val ZLIB_MAGIC = byteArrayOf(0x78.toByte(), 0x9C.toByte())
        private val ZLIB_MAGIC2 = byteArrayOf(0x78.toByte(), 0x01.toByte())
        private val ZLIB_MAGIC3 = byteArrayOf(0x78.toByte(), 0xDA.toByte())
        private val GZIP_MAGIC = byteArrayOf(0x1F.toByte(), 0x8B.toByte())
        // mGBA state magic
        private val MGBA_MAGIC = byteArrayOf(0x00, 0x00, 0x00, 0x01) // version magic LE

        val SEARCH_PATHS = listOf(
            "/storage/emulated/0/RetroArch/states",
            "/sdcard/RetroArch/states",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states",
            "/storage/emulated/0/Android/data/com.retroarch/files/states"
        )
    }

    private var stateFile: File? = null
    private var lastModified: Long = 0L
    var lastStatus: String = "Not started"

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

    fun hasNewData(): Boolean {
        val file = stateFile ?: findStateFile()?.also { stateFile = it } ?: return false
        val modified = file.lastModified()
        if (modified > lastModified) {
            lastModified = modified
            return true
        }
        return false
    }

    fun readPartyData(): Pair<Int, ByteArray>? {
        val file = stateFile ?: findStateFile()?.also { stateFile = it } ?: run {
            lastStatus = "No state file found in searched paths"
            return null
        }
        if (!file.exists()) {
            lastStatus = "File not found: ${file.absolutePath}"
            return null
        }

        val rawBytes = file.readBytes()
        lastStatus = "Found: ${file.name} (${rawBytes.size} bytes raw)"

        // Decompress if needed
        val stateBytes = decompressIfNeeded(rawBytes) ?: run {
            lastStatus = "Decompression failed for ${file.name} (${rawBytes.size} bytes)"
            return null
        }

        if (stateBytes.size < MGBA_STATE_SIZE) {
            lastStatus = "State too small after decompress: ${stateBytes.size} bytes (need ${MGBA_STATE_SIZE})"
            return null
        }

        // Parse party count
        val countOffset = PARTY_COUNT_FILE_OFFSET.toInt()
        val count = stateBytes[countOffset].toInt() and 0xFF
        if (count !in 1..6) {
            lastStatus = "Invalid party count: $count at offset 0x${countOffset.toString(16)}"
            return null
        }

        // Parse party data
        val dataOffset = PARTY_DATA_FILE_OFFSET.toInt()
        val partyBytes = stateBytes.copyOfRange(dataOffset, dataOffset + 624)
        lastStatus = "OK: ${file.name}, party count=$count, ${(System.currentTimeMillis() - file.lastModified()) / 1000}s ago"
        return Pair(count, partyBytes)
    }

    private fun decompressIfNeeded(data: ByteArray): ByteArray? {
        if (data.size < 4) return null

        // Check if already raw mGBA state (magic = 0x0100000A little-endian)
        // versionMagic = GBASavestateMagic(0x01000000) + GBASavestateVersion(0x0A) = 0x0100000A
        // stored as LE: 0x0A 0x00 0x00 0x01
        if (data.size >= MGBA_STATE_SIZE && isRawMgbaState(data)) {
            return data
        }

        // Try zlib (most common for RetroArch)
        if (data[0] == 0x78.toByte() && (data[1] == 0x9C.toByte() || data[1] == 0x01.toByte() || data[1] == 0xDA.toByte())) {
            return tryZlibDecompress(data)
        }

        // Try gzip
        if (data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
            return tryGzipDecompress(data)
        }

        // RetroArch sometimes prepends a small header before compressed data
        // Try scanning first 32 bytes for zlib/gzip magic
        for (i in 1..32) {
            if (i + 1 >= data.size) break
            if (data[i] == 0x78.toByte() && (data[i+1] == 0x9C.toByte() || data[i+1] == 0x01.toByte() || data[i+1] == 0xDA.toByte())) {
                val result = tryZlibDecompress(data.copyOfRange(i, data.size))
                if (result != null) return result
            }
            if (data[i] == 0x1F.toByte() && data[i+1] == 0x8B.toByte()) {
                val result = tryGzipDecompress(data.copyOfRange(i, data.size))
                if (result != null) return result
            }
        }

        return null
    }

    private fun isRawMgbaState(data: ByteArray): Boolean {
        // Check versionMagic: should be between 0x01000000 and 0x0100000F (LE)
        val magic = (data[3].toInt() and 0xFF shl 24) or
                    (data[2].toInt() and 0xFF shl 16) or
                    (data[1].toInt() and 0xFF shl 8)  or
                    (data[0].toInt() and 0xFF)
        return magic in 0x01000000..0x0100000F
    }

    private fun tryZlibDecompress(data: ByteArray): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream()
            val inflater = java.util.zip.Inflater()
            inflater.setInput(data)
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                baos.write(buf, 0, n)
            }
            inflater.end()
            val result = baos.toByteArray()
            if (result.size >= MGBA_STATE_SIZE) result else null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryGzipDecompress(data: ByteArray): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream()
            GZIPInputStream(data.inputStream()).use { gzip ->
                val buf = ByteArray(8192)
                var n: Int
                while (gzip.read(buf).also { n = it } != -1) {
                    baos.write(buf, 0, n)
                }
            }
            val result = baos.toByteArray()
            if (result.size >= MGBA_STATE_SIZE) result else null
        } catch (e: Exception) {
            null
        }
    }

    fun getStatus(): String = lastStatus

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
