package com.ercompanion.savefile

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

class SaveStateReader(private val context: Context) {

    companion object {
        const val MGBA_STATE_SIZE = 0x50000L   // conservative min
        const val EWRAM_OFFSET    = 0x21000L
        // ER-specific: gPlayerParty found at EWRAM+0x37780 (GBA: 0x02037780)
        // gPlayerPartyCount at EWRAM+0x3777c (GBA: 0x0203777c)
        // These differ from vanilla Emerald (0x020244F0) due to ER's extra EWRAM data

        // zlib magic bytes
        private val ZLIB_MAGIC = byteArrayOf(0x78.toByte(), 0x9C.toByte())
        private val ZLIB_MAGIC2 = byteArrayOf(0x78.toByte(), 0x01.toByte())
        private val ZLIB_MAGIC3 = byteArrayOf(0x78.toByte(), 0xDA.toByte())
        private val GZIP_MAGIC = byteArrayOf(0x1F.toByte(), 0x8B.toByte())
        // mGBA state magic
        private val MGBA_MAGIC = byteArrayOf(0x00, 0x00, 0x00, 0x01) // version magic LE

        val SEARCH_PATHS = listOf(
            "/storage/emulated/0/RetroArch/states/mGBA",
            "/sdcard/RetroArch/states/mGBA",
            "/storage/emulated/0/RetroArch/states",
            "/sdcard/RetroArch/states",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states/mGBA",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states",
            "/storage/emulated/0/Android/data/com.retroarch/files/states/mGBA",
            "/storage/emulated/0/Android/data/com.retroarch/files/states"
        )
    }

    private var stateFile: File? = null
    private var manualOverride: Boolean = false
    private var lastModified: Long = 0L
    var cachedPartyOffset: Int = -1
    var lastStatus: String = "Not started"
    // Cached EWRAM from last successful decompress — reused by all readers in same poll cycle
    private var cachedEwram: ByteArray? = null

    fun findStateFile(): File? {
        for (path in SEARCH_PATHS) {
            val dir = File(path)
            if (!dir.exists()) continue
            val stateFiles = dir.listFiles { f ->
                f.name.contains(".state")
            } ?: continue
            if (stateFiles.isEmpty()) continue

            // Sort all by most recently modified
            val sorted = stateFiles.sortedByDescending { it.lastModified() }

            // Prefer any Emerald Rogue file (most recent first)
            val erFile = sorted.firstOrNull { it.name.contains("Emerald", ignoreCase = true) ||
                                              it.name.contains("Rogue", ignoreCase = true) ||
                                              it.name.contains("emerogue", ignoreCase = true) }
            if (erFile != null) return erFile

            // Fall back to most recently modified file in this dir
            return sorted.first()
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
                files.sortedByDescending { it.lastModified() }.take(8).forEach {
                    val age = (System.currentTimeMillis() - it.lastModified()) / 1000
                    results.add("${it.name} (${it.length()/1024}KB, ${age}s ago)")
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
        // Always re-scan for newest file unless manually overridden
        val file = if (stateFile != null && manualOverride) {
            stateFile!!
        } else {
            findStateFile()?.also { stateFile = it }
        } ?: run {
            lastStatus = "No state file found in searched paths"
            return null
        }
        if (!file.exists()) {
            lastStatus = "File not found: ${file.absolutePath}"
            return null
        }

        val rawBytes = file.readBytes()
        lastStatus = "Found: ${file.name} (${rawBytes.size/1024}KB)"

        // Decompress if needed
        val stateBytes = decompressIfNeeded(rawBytes) ?: run {
            lastStatus = "Decompression failed for ${file.name} (${rawBytes.size} bytes)"
            return null
        }

        if (stateBytes.size < MGBA_STATE_SIZE) {
            lastStatus = "State too small after decompress: ${stateBytes.size} bytes (need ${MGBA_STATE_SIZE})"
            return null
        }

        // Extract EWRAM (256KB starting at 0x21000) and cache it for this poll cycle
        val ewram = stateBytes.copyOfRange(EWRAM_OFFSET.toInt(), EWRAM_OFFSET.toInt() + 0x40000)
        cachedEwram = ewram

        // gPlayerPartyCount at 0x3777c is UNRELIABLE in ER mocha (can be 16+, used for run state).
        // Instead: read all 12 slots at the known fixed address, return the full 12-slot buffer.
        // The ViewModel splits player/enemy by OT ID.
        val partyStart = 0x3777c + 4
        if (partyStart + 12 * 104 > ewram.size) {
            lastStatus = "EWRAM too small for party buffer"
            return null
        }
        val partyBytes = ewram.copyOfRange(partyStart, partyStart + 12 * 104)
        cachedPartyOffset = 0x3777c

        // Count valid player slots (non-empty personality, valid level/HP)
        // Use OT ID from first non-empty slot as the player's ID
        var playerOtId = -1L
        var validCount = 0
        for (i in 0 until 12) {
            val monOff = i * 104
            val personality = readU32LE(partyBytes, monOff)
            if (personality == 0L) continue
            val otId = readU32LE(partyBytes, monOff + 4)
            if (playerOtId < 0) playerOtId = otId
            if (otId == playerOtId && isValidMon(partyBytes, monOff)) validCount++
        }
        if (validCount == 0) {
            lastStatus = "No valid player mons at 0x3777c. State may be outdated."
            return null
        }

        val fileAge = (System.currentTimeMillis() - file.lastModified()) / 1000
        lastStatus = "OK: ${file.name}, playerMons=$validCount otId=0x${playerOtId.toString(16)}, ${fileAge}s ago"
        return Pair(12, partyBytes)  // always return all 12 slots; ViewModel does OT filtering
    }

    fun readRawPartyBuffer(): ByteArray? {
        val ewram = cachedEwram ?: return null
        val partyStart = 0x3777c + 4
        if (partyStart + 12 * 104 > ewram.size) return null
        return ewram.copyOfRange(partyStart, partyStart + 12 * 104)
    }

    /**
     * Returns true only if gBattlersCount == 2 (singles battle in progress).
     * Uses cached EWRAM from the current poll cycle — must be called after readPartyData().
     */
    fun readInBattle(): Boolean {
        val ewram = cachedEwram ?: return false
        val BATTLERS_COUNT_OFFSET = 0x1839c
        if (BATTLERS_COUNT_OFFSET >= ewram.size) return false
        return ewram[BATTLERS_COUNT_OFFSET].toInt() and 0xFF == 2
    }

    /**
     * Read gBattlerPartyIndexes[0] (active player party slot) from EWRAM.
     * Confirmed address: EWRAM+0x1839c = gBattlersCount (u8), +0x1839d = pad,
     * +0x1839e = gBattlerPartyIndexes[0] (u16, player active slot 0-5),
     * +0x183a0 = gBattlerPartyIndexes[1] (u16, enemy active party slot).
     * Returns -1 if not in battle (gBattlersCount != 2).
     */
    fun readActivePlayerSlot(): Int {
        val ewram = cachedEwram ?: return -1
        val BATTLERS_COUNT_OFFSET = 0x1839c
        if (BATTLERS_COUNT_OFFSET + 6 > ewram.size) return -1
        val battlersCount = ewram[BATTLERS_COUNT_OFFSET].toInt() and 0xFF
        if (battlersCount != 2) return -1  // not in singles battle
        // gBattlerPartyIndexes[0] = player active slot (u16 at +2)
        val playerSlot = (ewram[BATTLERS_COUNT_OFFSET + 2].toInt() and 0xFF) or
                         ((ewram[BATTLERS_COUNT_OFFSET + 3].toInt() and 0xFF) shl 8)
        return if (playerSlot in 0..5) playerSlot else -1
    }

    // Read enemy party from save state — gEnemyPartyCount is 1 byte after gPlayerPartyCount,
    // gEnemyParty starts at gPlayerParty + 12*104 bytes (max party size buffer)
    fun readEnemyPartyData(playerCountOffset: Int): Pair<Int, ByteArray>? {
        val rawBytes = stateFile?.readBytes() ?: return null
        val stateBytes = decompressIfNeeded(rawBytes) ?: return null
        if (stateBytes.size < MGBA_STATE_SIZE) return null
        val ewram = stateBytes.copyOfRange(EWRAM_OFFSET.toInt(), EWRAM_OFFSET.toInt() + 0x40000)

        val enemyCountOffset = playerCountOffset + 1
        val enemyPartyOffset = playerCountOffset + 4 + 12 * 104

        if (enemyCountOffset >= ewram.size || enemyPartyOffset + 12 * 104 > ewram.size) return null

        val count = ewram[enemyCountOffset].toInt() and 0xFF
        if (count !in 1..12) return null

        val partyBytes = ewram.copyOfRange(enemyPartyOffset, enemyPartyOffset + 12 * 104)
        var validCount = 0
        for (i in 0 until count) {
            if (isValidMon(partyBytes, i * 104)) validCount++
            else break
        }
        if (validCount == 0) return null
        return Pair(validCount, partyBytes)
    }

    private fun computeChecksum(boxPokemon: ByteArray, offset: Int): UShort {
        // Checksum = sum of 48 bytes of substruct data (offset 32..79) treated as u16s
        var sum = 0
        for (i in 0 until 24) {
            val lo = boxPokemon[offset + 32 + i * 2].toInt() and 0xFF
            val hi = boxPokemon[offset + 32 + i * 2 + 1].toInt() and 0xFF
            sum += lo or (hi shl 8)
        }
        return (sum and 0xFFFF).toUShort()
    }

    private fun decryptSubstruct0Species(boxPokemon: ByteArray, offset: Int): Int {
        // XOR key = personality ^ otId
        val personality = readU32LE(boxPokemon, offset + 0)
        val otId = readU32LE(boxPokemon, offset + 4)
        val key = personality xor otId

        // Substruct order index = personality % 24
        // Substruct 0 (species/item/etc) position depends on substructOrder
        val substructOrder = arrayOf(
            intArrayOf(0,1,2,3), intArrayOf(0,1,3,2), intArrayOf(0,2,1,3), intArrayOf(0,2,3,1),
            intArrayOf(0,3,1,2), intArrayOf(0,3,2,1), intArrayOf(1,0,2,3), intArrayOf(1,0,3,2),
            intArrayOf(1,2,0,3), intArrayOf(1,2,3,0), intArrayOf(1,3,0,2), intArrayOf(1,3,2,0),
            intArrayOf(2,0,1,3), intArrayOf(2,0,3,1), intArrayOf(2,1,0,3), intArrayOf(2,1,3,0),
            intArrayOf(2,3,0,1), intArrayOf(2,3,1,0), intArrayOf(3,0,1,2), intArrayOf(3,0,2,1),
            intArrayOf(3,1,0,2), intArrayOf(3,1,2,0), intArrayOf(3,2,0,1), intArrayOf(3,2,1,0)
        )
        val orderIdx = (personality % 24).toInt()
        val order = substructOrder[orderIdx]

        // Find which position substruct 0 (species data) is at
        val pos = order.indexOf(0)
        val substructOffset = offset + 32 + pos * 12

        // Decrypt: XOR each u32 with key
        val decrypted = IntArray(3)
        for (i in 0..2) {
            val raw = readU32LE(boxPokemon, substructOffset + i * 4)
            decrypted[i] = (raw xor key).toInt()
        }

        // PokemonSubstruct0: species is bits 0-10 of first u32
        return decrypted[0] and 0x7FF
    }

    private fun readU32LE(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFF)) or
               ((buf[offset+1].toLong() and 0xFF) shl 8) or
               ((buf[offset+2].toLong() and 0xFF) shl 16) or
               ((buf[offset+3].toLong() and 0xFF) shl 24)
    }

    private fun isValidMon(partyBytes: ByteArray, monOffset: Int): Boolean {
        if (monOffset + 104 > partyBytes.size) return false

        // personality must be non-zero
        val personality = readU32LE(partyBytes, monOffset + 0)
        if (personality == 0L) return false

        // level (offset 84, unencrypted) must be 1-100
        val level = partyBytes[monOffset + 84].toInt() and 0xFF
        if (level !in 1..100) return false

        // maxHP (offset 88, unencrypted u16) — sane range 1-9999
        val maxHP = (partyBytes[monOffset + 88].toInt() and 0xFF) or
                    ((partyBytes[monOffset + 89].toInt() and 0xFF) shl 8)
        if (maxHP == 0 || maxHP > 9999) return false

        // maxHP sanity: even a 252 HP EV'd mon at level 100 won't exceed ~500 HP for most species.
        // A loose cap: maxHP should be roughly <= level * 10 + 200 (generous for tanky mons).
        // This catches false positives like Lv8 with 771 HP.
        if (maxHP > level * 10 + 250) return false

        // currentHP (offset 86, unencrypted u16) must be <= maxHP
        val currentHP = (partyBytes[monOffset + 86].toInt() and 0xFF) or
                        ((partyBytes[monOffset + 87].toInt() and 0xFF) shl 8)
        if (currentHP > maxHP) return false

        return true
    }

    private fun tryReadPartyAt(ewram: ByteArray, countOffset: Int): Pair<Int, ByteArray>? {
        // countOffset = position of gPlayerPartyCount byte
        // gPlayerParty starts at countOffset+4 (confirmed from ER state analysis)
        if (countOffset < 0 || countOffset + 4 + 12 * 104 > ewram.size) return null
        val rawCount = ewram[countOffset].toInt() and 0xFF
        if (rawCount !in 1..12) return null

        val partyStart = countOffset + 4
        val partyBytes = ewram.copyOfRange(partyStart, partyStart + 12 * 104)

        // Count valid mons (ER may report count=7 but only 6 real mons — clamp to valid)
        var validCount = 0
        for (i in 0 until minOf(rawCount, 12)) {
            if (isValidMon(partyBytes, i * 104)) validCount++
            else break
        }
        if (validCount < 1) return null
        return Pair(validCount, partyBytes)
    }

    private fun scanForParty(ewram: ByteArray): Int {
        // ER mocha has a fixed address — only try this, never do a full EWRAM scan.
        // A broad scan produces false positives (random EWRAM data that passes isValidMon).
        val knownHint = 0x3777c
        tryReadPartyAt(ewram, knownHint)?.let { return knownHint }
        return -1
    }

    private fun decompressIfNeeded(data: ByteArray): ByteArray? {
        if (data.size < 8) return null

        // RASTATE\x01 — RetroArch's chunk wrapper around the raw core state
        if (data[0] == 'R'.code.toByte() && data[1] == 'A'.code.toByte() &&
            data[2] == 'S'.code.toByte() && data[3] == 'T'.code.toByte() &&
            data[4] == 'A'.code.toByte() && data[5] == 'T'.code.toByte() &&
            data[6] == 'E'.code.toByte()) {
            lastStatus = "RASTATE format detected"
            return parseRAState(data)
        }

        // Already raw mGBA state?
        if (data.size >= MGBA_STATE_SIZE && isRawMgbaState(data)) {
            lastStatus = "Raw mGBA state"
            return data
        }

        // RetroArch RZIP format: magic "#RZIPv1#"
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

        // Large unrecognized — try as raw
        if (data.size >= MGBA_STATE_SIZE) {
            lastStatus = "Unknown format (${data.size}B) — trying as raw"
            return data
        }

        lastStatus = "Unknown format size=${data.size} magic=${data[0].toInt() and 0xFF} ${data[1].toInt() and 0xFF} ${data[2].toInt() and 0xFF} ${data[3].toInt() and 0xFF}"
        return null
    }

    private fun parseRAState(data: ByteArray): ByteArray? {
        // RASTATE\x01 + chunks: each chunk = 4-byte tag + 4-byte LE size + data
        var pos = 8
        while (pos + 8 <= data.size) {
            val tag = String(data, pos, 4, Charsets.US_ASCII).trim()
            val size = ((data[pos+4].toLong() and 0xFF)) or
                       ((data[pos+5].toLong() and 0xFF) shl 8) or
                       ((data[pos+6].toLong() and 0xFF) shl 16) or
                       ((data[pos+7].toLong() and 0xFF) shl 24)
            pos += 8
            if (size < 0 || pos + size > data.size) break
            if (tag == "MEM") {
                lastStatus = "RASTATE MEM: ${size}B"
                return data.copyOfRange(pos, pos + size.toInt())
            }
            if (tag == "END") break
            pos += size.toInt()
        }
        lastStatus = "RASTATE: no MEM chunk found"
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

            lastStatus = "RZIP: chunk=$chunkSize uncompressed=${uncompressedSize}B file=${data.size}B"

            // If uncompressed size == (file size - 20), it's stored uncompressed after header
            if (uncompressedSize == (data.size - 20).toLong()) {
                lastStatus = "RZIP uncompressed: ${uncompressedSize}B"
                return data.copyOfRange(20, data.size)
            }

            val baos = ByteArrayOutputStream(uncompressedSize.toInt())
            var pos = 20

            while (pos < data.size) {
                if (pos + 4 > data.size) break
                val compChunkSize = ((data[pos].toInt() and 0xFF)) or
                                    ((data[pos+1].toInt() and 0xFF) shl 8) or
                                    ((data[pos+2].toInt() and 0xFF) shl 16) or
                                    ((data[pos+3].toInt() and 0xFF) shl 24)
                pos += 4

                if (compChunkSize <= 0 || pos + compChunkSize > data.size) break

                val chunkData = data.copyOfRange(pos, pos + compChunkSize)
                pos += compChunkSize

                val inflater = Inflater()
                inflater.setInput(chunkData)
                val outBuf = ByteArray(chunkSize * 2 + 11)
                val n = inflater.inflate(outBuf)
                inflater.end()
                baos.write(outBuf, 0, n)
            }

            val result = baos.toByteArray()
            if (result.size >= MGBA_STATE_SIZE) result else {
                lastStatus = "RZIP decoded ${result.size}B, expected >=${MGBA_STATE_SIZE}B"
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
        manualOverride = true
        lastModified = 0L
        cachedPartyOffset = -1
    }

    fun clearCache() {
        stateFile = null
        manualOverride = false
        lastModified = 0L
        cachedPartyOffset = -1
        cachedEwram = null
    }
}
