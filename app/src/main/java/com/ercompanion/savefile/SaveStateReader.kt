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
    private var cachedPartyOffset: Int = -1
    var lastStatus: String = "Not started"

    fun findStateFile(): File? {
        for (path in SEARCH_PATHS) {
            val dir = File(path)
            if (!dir.exists()) continue
            val stateFiles = dir.listFiles { f ->
                f.name.endsWith(".state0") || f.name.endsWith(".state") ||
                f.name.contains(".state")
            } ?: continue
            if (stateFiles.isEmpty()) continue
            // Just grab most recently modified — user can override in debug panel
            return stateFiles.maxByOrNull { it.lastModified() }
        }
        return null
    }

    fun listAllStateFiles(): List<String> {
        val results = mutableListOf<String>()
        for (path in SEARCH_PATHS) {
            val dir = File(path)
            if (!dir.exists()) {
                results.add("$path — not found")
                continue
            }
            val files = dir.listFiles() ?: continue
            if (files.isEmpty()) {
                results.add("$path — empty")
            } else {
                files.sortedByDescending { it.lastModified() }.take(5).forEach {
                    results.add("${it.name} (${it.length()/1024}KB)")
                }
            }
        }
        return results
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

        // Extract EWRAM (256KB starting at 0x21000)
        val ewram = stateBytes.copyOfRange(EWRAM_OFFSET.toInt(), EWRAM_OFFSET.toInt() + 0x40000)

        // Try cached offset first
        if (cachedPartyOffset >= 0) {
            val result = tryReadPartyAt(ewram, cachedPartyOffset)
            if (result != null) {
                lastStatus = "OK: ${file.name}, party=${result.first}, cached offset=0x${cachedPartyOffset.toString(16)}, ${(System.currentTimeMillis() - file.lastModified()) / 1000}s ago"
                return result
            }
            cachedPartyOffset = -1 // cache invalid, rescan
        }

        // Scan EWRAM for valid party data
        lastStatus = "Scanning EWRAM for party data..."
        val offset = scanForParty(ewram)
        if (offset < 0) {
            lastStatus = "Could not find valid party in EWRAM — is ER loaded and in-game?"
            return null
        }
        cachedPartyOffset = offset
        val result = tryReadPartyAt(ewram, offset)!!
        lastStatus = "OK: ${file.name}, party=${result.first}, found at EWRAM+0x${offset.toString(16)}, ${(System.currentTimeMillis() - file.lastModified()) / 1000}s ago"
        return result
    }

    private fun tryReadPartyAt(ewram: ByteArray, offset: Int): Pair<Int, ByteArray>? {
        if (offset < 0 || offset + 4 + 624 > ewram.size) return null
        val count = ewram[offset].toInt() and 0xFF
        if (count !in 1..6) return null
        val partyBytes = ewram.copyOfRange(offset + 4, offset + 4 + 624)
        // Quick sanity: first mon should have non-zero personality
        val personality = (partyBytes[0].toInt() and 0xFF) or
                          ((partyBytes[1].toInt() and 0xFF) shl 8) or
                          ((partyBytes[2].toInt() and 0xFF) shl 16) or
                          ((partyBytes[3].toInt() and 0xFF) shl 24)
        if (personality == 0) return null
        return Pair(count, partyBytes)
    }

    private fun scanForParty(ewram: ByteArray): Int {
        // Scan EWRAM for a byte in range 1-6 (party count) followed by valid Pokemon struct
        // Search in likely range: 0x20000 - 0x3C000 (where trainer data typically lives in Emerald variants)
        // Align to 4 bytes
        val searchStart = 0x20000
        val searchEnd   = minOf(ewram.size - 628, 0x3C000)
        var i = searchStart
        while (i < searchEnd) {
            val count = ewram[i].toInt() and 0xFF
            if (count in 1..6) {
                val result = tryReadPartyAt(ewram, i)
                if (result != null) return i
            }
            i += 4
        }
        // Broader scan if not found
        i = 0
        while (i < ewram.size - 628) {
            val count = ewram[i].toInt() and 0xFF
            if (count in 1..6) {
                val result = tryReadPartyAt(ewram, i)
                if (result != null) return i
            }
            i += 4
        }
        return -1
    }

    private fun decompressIfNeeded(data: ByteArray): ByteArray? {
        if (data.size < 4) return null

        // Already raw mGBA state?
        if (data.size >= MGBA_STATE_SIZE && isRawMgbaState(data)) {
            return data
        }

        // RetroArch RZIP format: magic "#RZIPv1#" (bytes: 35 82 90 73 80 118 1 35)
        if (data.size >= 20 &&
            data[0] == 35.toByte() && data[1] == 82.toByte() &&
            data[2] == 90.toByte() && data[3] == 73.toByte() &&
            data[4] == 80.toByte() && data[5] == 118.toByte() &&
            data[7] == 35.toByte()) {
            return tryRzipDecompress(data)
        }

        // Standard zlib
        if (data[0] == 0x78.toByte()) {
            tryZlibDecompress(data)?.let { return it }
        }

        // Gzip
        if (data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
            tryGzipDecompress(data)?.let { return it }
        }

        return null
    }

    private fun tryRzipDecompress(data: ByteArray): ByteArray? {
        return try {
            // Header layout (20 bytes):
            // [0-7]  magic "#RZIPv1#"
            // [8-11] chunk size (uint32 LE)
            // [12-19] total uncompressed size (uint64 LE)
            val chunkSize = ((data[8].toInt() and 0xFF)) or
                            ((data[9].toInt() and 0xFF) shl 8) or
                            ((data[10].toInt() and 0xFF) shl 16) or
                            ((data[11].toInt() and 0xFF) shl 24)

            val uncompressedSize = (data[12].toLong() and 0xFF) or
                                   ((data[13].toLong() and 0xFF) shl 8) or
                                   ((data[14].toLong() and 0xFF) shl 16) or
                                   ((data[15].toLong() and 0xFF) shl 24) or
                                   ((data[16].toLong() and 0xFF) shl 32) or
                                   ((data[17].toLong() and 0xFF) shl 40) or
                                   ((data[18].toLong() and 0xFF) shl 48) or
                                   ((data[19].toLong() and 0xFF) shl 56)

            lastStatus = "RZIP: chunkSize=$chunkSize uncompressed=${uncompressedSize}B"

            val baos = ByteArrayOutputStream(uncompressedSize.toInt())
            var pos = 20 // skip header

            while (pos < data.size) {
                if (pos + 4 > data.size) break
                // Each chunk: 4-byte compressed size (uint32 LE) + zlib data
                val compChunkSize = ((data[pos].toInt() and 0xFF)) or
                                    ((data[pos+1].toInt() and 0xFF) shl 8) or
                                    ((data[pos+2].toInt() and 0xFF) shl 16) or
                                    ((data[pos+3].toInt() and 0xFF) shl 24)
                pos += 4

                if (compChunkSize <= 0 || pos + compChunkSize > data.size) break

                val chunkData = data.copyOfRange(pos, pos + compChunkSize)
                pos += compChunkSize

                // Decompress this chunk with zlib
                val inflater = Inflater()
                inflater.setInput(chunkData)
                val outBuf = ByteArray(chunkSize * 2)
                val n = inflater.inflate(outBuf)
                inflater.end()
                baos.write(outBuf, 0, n)
            }

            val result = baos.toByteArray()
            if (result.size >= MGBA_STATE_SIZE) result else {
                lastStatus = "RZIP decoded ${result.size} bytes, expected >= $MGBA_STATE_SIZE"
                null
            }
        } catch (e: Exception) {
            lastStatus = "RZIP error: ${e.message}"
            null
        }
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
        cachedPartyOffset = -1
    }
}
