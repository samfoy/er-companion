package com.ercompanion.parser

/**
 * Validates known memory addresses for ER Companion save states.
 *
 * Emerald Rogue addresses are hardcoded for the mocha build but may shift in different builds.
 * This validator checks for known patterns and magic values to ensure addresses are correct:
 * - Party slots should have valid personality values (non-zero, reasonable range)
 * - gBattlersCount should be 0-4 (not random garbage)
 * - Species IDs should be in 1-1526 range
 */
object AddressValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val confidence: Double, // 0.0 to 1.0
        val reason: String
    )

    // Known addresses for ER mocha build
    private const val PARTY_COUNT_OFFSET = 0x3777c
    private const val PARTY_OFFSET = 0x37780
    private const val GBATTLE_MONS_OFFSET = 0x1c358
    private const val GBATTLERS_COUNT_OFFSET = 0x1839c

    private const val POKEMON_SIZE = 104
    private const val BATTLE_MON_SIZE = 0x60

    /**
     * Validate party address by checking for valid Pokemon data patterns.
     * Checks all 12 slots for valid personality, species, level, and HP values.
     */
    fun validatePartyAddress(ewram: ByteArray, offset: Int = PARTY_OFFSET): ValidationResult {
        if (offset + 12 * POKEMON_SIZE > ewram.size) {
            return ValidationResult(false, 0.0, "EWRAM too small for party buffer at offset $offset")
        }

        var validSlots = 0
        var suspiciousSlots = 0
        var firstOtId: Long = -1L
        var nonMatchingOtId = 0

        for (i in 0 until 12) {
            val monOffset = offset + i * POKEMON_SIZE

            // Read personality (must be non-zero for valid Pokemon)
            val personality = readU32LE(ewram, monOffset)
            if (personality == 0L) {
                // Empty slot - this is expected after player's party ends
                break
            }

            // Read OT ID (all player Pokemon share same OT ID)
            val otId = readU32LE(ewram, monOffset + 4)
            if (firstOtId < 0) {
                firstOtId = otId
            } else if (otId != firstOtId) {
                nonMatchingOtId++
                break // Stop at first non-matching OT ID (enemy Pokemon)
            }

            // Read level (unencrypted, at offset +84)
            val level = ewram[monOffset + 84].toInt() and 0xFF
            if (level !in 1..100) {
                suspiciousSlots++
                continue
            }

            // Read maxHP (unencrypted u16 at offset +88)
            val maxHP = readU16LE(ewram, monOffset + 88)
            if (maxHP == 0L || maxHP > 9999L) {
                suspiciousSlots++
                continue
            }

            // Sanity check: maxHP should be reasonable for level
            if (maxHP > level * 10 + 250) {
                suspiciousSlots++
                continue
            }

            // Read currentHP (unencrypted u16 at offset +86)
            val currentHP = readU16LE(ewram, monOffset + 86)
            if (currentHP > maxHP) {
                suspiciousSlots++
                continue
            }

            // Decrypt and validate species ID
            val species = decryptSpecies(ewram, monOffset)
            if (species !in 1..1526) {
                suspiciousSlots++
                continue
            }

            validSlots++
        }

        // Calculate confidence based on valid slots and suspicious patterns
        return when {
            validSlots == 0 -> {
                ValidationResult(false, 0.0, "No valid Pokemon found at offset $offset")
            }
            suspiciousSlots > validSlots -> {
                ValidationResult(false, 0.3, "More suspicious slots ($suspiciousSlots) than valid ($validSlots)")
            }
            validSlots >= 1 && suspiciousSlots == 0 -> {
                val confidence = (validSlots / 6.0).coerceAtMost(1.0)
                ValidationResult(true, confidence, "Found $validSlots valid Pokemon with matching OT ID")
            }
            else -> {
                val confidence = (validSlots.toDouble() / (validSlots + suspiciousSlots)).coerceAtMost(0.8)
                ValidationResult(true, confidence, "Found $validSlots valid, $suspiciousSlots suspicious")
            }
        }
    }

    /**
     * Validate gBattleMons address by checking for reasonable battle data.
     */
    fun validateBattleAddress(ewram: ByteArray, offset: Int = GBATTLE_MONS_OFFSET): ValidationResult {
        if (offset + 4 * BATTLE_MON_SIZE > ewram.size) {
            return ValidationResult(false, 0.0, "EWRAM too small for battle buffer at offset $offset")
        }

        var validSlots = 0
        var suspiciousSlots = 0

        for (i in 0 until 4) {
            val base = offset + i * BATTLE_MON_SIZE

            // Read species (u16 at +0x00)
            val species = readU16LE(ewram, base).toInt()
            if (species == 0) {
                // Empty battle slot - valid
                continue
            }

            if (species > 2000) {
                suspiciousSlots++
                continue
            }

            // Read level (u8 at +0x2c)
            val level = ewram[base + 0x2c].toInt() and 0xFF
            if (level !in 1..100) {
                suspiciousSlots++
                continue
            }

            // Read maxHP (u16 at +0x2e)
            val maxHp = readU16LE(ewram, base + 0x2e).toInt()
            if (maxHp == 0 || maxHp > 9999) {
                suspiciousSlots++
                continue
            }

            // Read HP (u16 at +0x2a)
            val hp = readU16LE(ewram, base + 0x2a).toInt()
            if (hp > maxHp) {
                suspiciousSlots++
                continue
            }

            // Read stats - should be in reasonable range (1-999)
            val attack = readU16LE(ewram, base + 0x02).toInt()
            val defense = readU16LE(ewram, base + 0x04).toInt()
            val speed = readU16LE(ewram, base + 0x06).toInt()
            val spAttack = readU16LE(ewram, base + 0x08).toInt()
            val spDefense = readU16LE(ewram, base + 0x0a).toInt()

            if (attack !in 1..999 || defense !in 1..999 || speed !in 1..999 ||
                spAttack !in 1..999 || spDefense !in 1..999) {
                suspiciousSlots++
                continue
            }

            validSlots++
        }

        // Battle data validation is less strict since battles might not always be active
        return when {
            validSlots == 0 && suspiciousSlots == 0 -> {
                ValidationResult(true, 0.5, "No battle active (all empty slots)")
            }
            validSlots > 0 && suspiciousSlots == 0 -> {
                ValidationResult(true, 1.0, "Found $validSlots valid battle slots")
            }
            suspiciousSlots > validSlots -> {
                ValidationResult(false, 0.2, "More suspicious ($suspiciousSlots) than valid ($validSlots)")
            }
            else -> {
                ValidationResult(true, 0.6, "Found $validSlots valid, $suspiciousSlots suspicious")
            }
        }
    }

    /**
     * Validate gBattlersCount value - should be 0, 2, or 4 for valid battle states.
     */
    fun validateBattlersCount(ewram: ByteArray, offset: Int = GBATTLERS_COUNT_OFFSET): ValidationResult {
        if (offset >= ewram.size) {
            return ValidationResult(false, 0.0, "Offset $offset out of bounds")
        }

        val battlersCount = ewram[offset].toInt() and 0xFF

        return when (battlersCount) {
            0 -> ValidationResult(true, 1.0, "No battle active (count=0)")
            2 -> ValidationResult(true, 1.0, "Singles battle (count=2)")
            4 -> ValidationResult(true, 1.0, "Doubles battle (count=4)")
            else -> ValidationResult(false, 0.0, "Invalid battlers count: $battlersCount (expected 0, 2, or 4)")
        }
    }

    /**
     * Comprehensive validation of all known addresses.
     * Returns overall validation result with aggregate confidence.
     */
    fun validateAllAddresses(ewram: ByteArray): ValidationResult {
        val partyResult = validatePartyAddress(ewram)
        val battleResult = validateBattleAddress(ewram)
        val battlersResult = validateBattlersCount(ewram)

        // Party address is most critical
        if (!partyResult.isValid) {
            return partyResult
        }

        // Battle addresses are less critical (might be invalid when not in battle)
        val avgConfidence = (partyResult.confidence * 0.7 +
                           battleResult.confidence * 0.2 +
                           battlersResult.confidence * 0.1)

        val reasons = buildList {
            add("Party: ${partyResult.reason}")
            if (!battleResult.isValid || battleResult.confidence < 0.5) {
                add("Battle: ${battleResult.reason}")
            }
            if (!battlersResult.isValid) {
                add("BattlersCount: ${battlersResult.reason}")
            }
        }

        return ValidationResult(
            isValid = true,
            confidence = avgConfidence,
            reason = reasons.joinToString("; ")
        )
    }

    /**
     * Decrypt species ID from encrypted Pokemon data.
     * Species is in substructure 0 (Growth), bits 0-10 of first u32.
     */
    private fun decryptSpecies(data: ByteArray, offset: Int): Int {
        val personality = readU32LE(data, offset)
        val otId = readU32LE(data, offset + 4)
        val key = personality xor otId

        // Get substructure order
        val orderIdx = (personality % 24).toInt()
        val order = SUBSTRUCTURE_ORDER[orderIdx]

        // Find position of substructure 0 (Growth)
        val pos = order.indexOf(0)
        val substructOffset = offset + 32 + pos * 12

        if (substructOffset + 4 > data.size) return 0

        // Decrypt first u32 of substructure 0
        val encrypted = readU32LE(data, substructOffset)
        val decrypted = encrypted xor key

        // Species is bits 0-10
        return (decrypted and 0x7FF).toInt()
    }

    private fun readU16LE(buf: ByteArray, offset: Int): Long {
        if (offset + 1 >= buf.size) return 0L
        return ((buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8)).toLong()
    }

    private fun readU32LE(buf: ByteArray, offset: Int): Long {
        if (offset + 3 >= buf.size) return 0L
        return ((buf[offset].toLong() and 0xFF)) or
               ((buf[offset + 1].toLong() and 0xFF) shl 8) or
               ((buf[offset + 2].toLong() and 0xFF) shl 16) or
               ((buf[offset + 3].toLong() and 0xFF) shl 24)
    }

    // Substructure order table (personality % 24)
    private val SUBSTRUCTURE_ORDER = arrayOf(
        intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3),
        intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 1, 2), intArrayOf(0, 3, 2, 1),
        intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(1, 2, 0, 3),
        intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 0, 2), intArrayOf(1, 3, 2, 0),
        intArrayOf(2, 0, 1, 3), intArrayOf(2, 0, 3, 1), intArrayOf(2, 1, 0, 3),
        intArrayOf(2, 1, 3, 0), intArrayOf(2, 3, 0, 1), intArrayOf(2, 3, 1, 0),
        intArrayOf(3, 0, 1, 2), intArrayOf(3, 0, 2, 1), intArrayOf(3, 1, 0, 2),
        intArrayOf(3, 1, 2, 0), intArrayOf(3, 2, 0, 1), intArrayOf(3, 2, 1, 0)
    )
}
