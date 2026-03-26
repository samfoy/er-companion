package com.ercompanion.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

data class PartyMon(
    val species: Int,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val nickname: String,
    val moves: List<Int>,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val spAttack: Int,
    val spDefense: Int,
    val experience: Int,
    val friendship: Int,
    val heldItem: Int = 0,
    val ability: Int = 0,
    val personality: UInt = 0u,
    val ivHp: Int = 0,
    val ivAttack: Int = 0,
    val ivDefense: Int = 0,
    val ivSpeed: Int = 0,
    val ivSpAttack: Int = 0,
    val ivSpDefense: Int = 0,
    val status: Int = 0,        // Status condition flags
    val movePP: List<Int> = listOf(0, 0, 0, 0),  // PP for each move
    val otId: Long = 0L,
    val nature: UInt = 0u,      // Effective nature (accounts for mints in ER)
    val abilitySlot: Int = 0    // 0=ability1, 1=ability2, 2=hidden ability
)

object Gen3PokemonParser {
    private const val POKEMON_SIZE = 104

    // Canonical Gen3 substructure order table (personality % 24)
    // Each entry: order[i] = the substruct TYPE at raw encrypted position i
    // Types: 0=Growth(species/item/exp), 1=Attacks(moves), 2=EVs/Condition, 3=Misc
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

    /**
     * Parse party with contiguous slot detection and OT ID filtering.
     * - Always reads up to maxSlots (default 12)
     * - Automatically detects player's OT ID from first valid Pokemon
     * - Returns only contiguous valid Pokemon starting from slot 0
     * - Stops at first null/invalid slot or OT ID mismatch
     */
    fun parseParty(data: ByteArray, maxSlots: Int = 12): List<PartyMon> {
        val party = mutableListOf<PartyMon>()
        var playerOtId: Long = -1L

        for (i in 0 until minOf(maxSlots, 12)) {
            val offset = i * POKEMON_SIZE
            if (offset + POKEMON_SIZE > data.size) break

            val monData = data.sliceArray(offset until offset + POKEMON_SIZE)
            val mon = parsePokemon(monData) ?: break // Stop at first null slot

            // First valid Pokemon establishes the player's OT ID
            if (playerOtId < 0L) {
                playerOtId = mon.otId
            }

            // Stop if OT ID doesn't match player (enemy Pokemon in buffer)
            if (mon.otId != playerOtId) break

            party.add(mon)
        }

        return party
    }

    /** Parse all 12 slots raw (no count limit, no OT filter) — for enemy detection */
    fun parseAllSlots(data: ByteArray): List<PartyMon?> {
        val result = mutableListOf<PartyMon?>()
        for (i in 0 until 12) {
            val offset = i * POKEMON_SIZE
            if (offset + POKEMON_SIZE > data.size) break
            result.add(parsePokemon(data.sliceArray(offset until offset + POKEMON_SIZE)))
        }
        return result
    }

    fun parsePokemon(data: ByteArray): PartyMon? {
        if (data.size < POKEMON_SIZE) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Read BoxPokemon structure (80 bytes)
        // Read personality as 4 bytes manually to ensure correct unsigned conversion
        val p0 = buffer.get(0).toInt() and 0xFF
        val p1 = buffer.get(1).toInt() and 0xFF
        val p2 = buffer.get(2).toInt() and 0xFF
        val p3 = buffer.get(3).toInt() and 0xFF
        val personality = (p0 or (p1 shl 8) or (p2 shl 16) or (p3 shl 24)).toUInt()

        val otId = buffer.getInt(4).toUInt()

        if (personality == 0u) return null

        // Read nickname (10 bytes at offset 0x08)
        val nicknameBytes = ByteArray(10)
        buffer.position(0x08)
        buffer.get(nicknameBytes)
        val nickname = decodeGen3String(nicknameBytes)

        // Read hiddenNatureModifier at offset 0x12, bits 3-7 (for mint detection)
        val languageAndNature = readU8(data, 0x12)
        val hiddenNatureModifier = ((languageAndNature shr 3) and 0x1F).toUInt()

        // Decrypt substructures (48 bytes starting at offset 0x20)
        val encryptedData = ByteArray(48)
        buffer.position(0x20)
        buffer.get(encryptedData)

        val decryptedData = decryptSubstructures(encryptedData, personality, otId)
        val substructures = reorderSubstructures(decryptedData, personality)

        // Parse substructure A: species, item, experience, friendship
        val species = readU16(substructures[0], 0)
        val heldItem = readU16(substructures[0], 2)
        val experience = readU32(substructures[0], 4)
        val friendship = readU8(substructures[0], 9)

        // Parse substructure B: moves (4 moves, 2 bytes each) and PP (4 bytes at offset +8)
        val moves = (0 until 4).map { readU16(substructures[1], it * 2) }
        val movePP = (0 until 4).map { readU8(substructures[1], 8 + it) }

        // Parse substructure C (EVs/IVs): IVs are packed in 32-bit value at offset 0
        val ivData = readU32(substructures[2], 0)
        val ivHp = ((ivData shr 0) and 0x1Fu).toInt()
        val ivAttack = ((ivData shr 5) and 0x1Fu).toInt()
        val ivDefense = ((ivData shr 10) and 0x1Fu).toInt()
        val ivSpeed = ((ivData shr 15) and 0x1Fu).toInt()
        val ivSpAttack = ((ivData shr 20) and 0x1Fu).toInt()
        val ivSpDefense = ((ivData shr 25) and 0x1Fu).toInt()

        // Parse substructure D (Misc): ability at offset +1, ivEggAbility at offset +4
        val ivEggAbility = readU32(substructures[3], 4)
        // Nature: calculate base nature from personality, then apply mint modifier (XOR)
        // hiddenNatureModifier is stored at offset 0x12, bits 3-7 in BoxPokemon structure
        // If hiddenNatureModifier is 0, no mint was used
        val baseNature = personality % 25u
        val effectiveNature = baseNature xor hiddenNatureModifier
        // ER stores ability slot - bits 2-3 of ivEggAbility
        val abilitySlot = ((ivEggAbility shr 2) and 0x3u).toInt()
        // Look up actual ability ID from species + slot
        val ability = com.ercompanion.data.SpeciesAbilities.getAbility(species, abilitySlot)

        // Read Pokemon extra fields (after BoxPokemon, offset 0x50)
        val status = readU32(data, 0x50).toInt()
        val level = readU8(data, 0x54)
        val hp = readU16(data, 0x56)
        val maxHp = readU16(data, 0x58)
        val attack = readU16(data, 0x5A)
        val defense = readU16(data, 0x5C)
        val speed = readU16(data, 0x5E)
        val spAttack = readU16(data, 0x60)
        val spDefense = readU16(data, 0x62)

        // Validate — ER has up to 1526 species
        if (species == 0 || species > 1526) {
            return null
        }

        // Level validation: if unencrypted level is 0 or invalid, calculate from experience
        // (can happen when Pokemon switches out during battle - level field not maintained)
        val validLevel = if (level in 1..100) {
            level
        } else {
            // Rough approximation: level from experience (for medium-fast growth rate)
            // This is a fallback - most Pokemon will have valid level
            val approxLevel = if (experience > 0u) {
                (experience.toDouble().pow(1.0/3.0)).toInt().coerceIn(1, 100)
            } else {
                1 // Default to level 1 if no experience data
            }
            approxLevel
        }


        return PartyMon(
            species = species,
            level = validLevel,
            hp = hp,
            maxHp = maxHp,
            nickname = nickname,
            moves = moves.filter { it > 0 },
            attack = attack,
            defense = defense,
            speed = speed,
            spAttack = spAttack,
            spDefense = spDefense,
            experience = experience.toInt(),
            friendship = friendship,
            heldItem = heldItem,
            ability = ability,
            personality = personality,
            ivHp = ivHp,
            ivAttack = ivAttack,
            ivDefense = ivDefense,
            ivSpeed = ivSpeed,
            ivSpAttack = ivSpAttack,
            ivSpDefense = ivSpDefense,
            status = status,
            movePP = movePP,
            otId = otId.toLong() and 0xFFFFFFFFL,
            nature = effectiveNature,
            abilitySlot = abilitySlot
        )
    }

    private fun decryptSubstructures(
        encrypted: ByteArray,
        personality: UInt,
        otId: UInt
    ): ByteArray {
        val key = personality xor otId
        val decrypted = ByteArray(48)

        for (i in 0 until 48 step 4) {
            val encryptedWord = readU32(encrypted, i)
            val decryptedWord = encryptedWord xor key
            writeU32(decrypted, i, decryptedWord)
        }

        return decrypted
    }

    private fun reorderSubstructures(decrypted: ByteArray, personality: UInt): Array<ByteArray> {
        val orderIndex = (personality % 24u).toInt()
        val order = SUBSTRUCTURE_ORDER[orderIndex]

        // Split into 4 raw substructures of 12 bytes each (by position in decrypted data)
        val rawSubs = Array(4) { i -> decrypted.sliceArray(i * 12 until (i + 1) * 12) }

        // order[i] = the substructure TYPE stored at raw position i
        // We want result indexed by type: result[type] = rawSubs[positionOfType]
        // i.e. result[order[i]] = rawSubs[i]
        val result = Array(4) { ByteArray(12) }
        for (i in 0 until 4) {
            result[order[i]] = rawSubs[i]
        }
        // result[0] = Growth (species/item/exp), result[1] = Attacks, result[2] = EVs, result[3] = Misc
        return result
    }

    private fun decodeGen3String(bytes: ByteArray): String {
        val result = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c == 0xFF) break // String terminator
            result.append(gen3CharToChar(c))
        }
        return result.toString()
    }

    private fun gen3CharToChar(c: Int): Char {
        // Simplified Gen3 character mapping (supports basic ASCII)
        return when (c) {
            in 0xBB..0xD4 -> ('A' + (c - 0xBB)) // A-Z
            in 0xD5..0xEE -> ('a' + (c - 0xD5)) // a-z
            0xA1 -> '0'
            0xA2 -> '1'
            0xA3 -> '2'
            0xA4 -> '3'
            0xA5 -> '4'
            0xA6 -> '5'
            0xA7 -> '6'
            0xA8 -> '7'
            0xA9 -> '8'
            0xAA -> '9'
            0x00 -> ' '
            else -> '?'
        }
    }

    private fun readU8(data: ByteArray, offset: Int): Int {
        return data[offset].toInt() and 0xFF
    }

    private fun readU16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8))
    }

    private fun readU32(data: ByteArray, offset: Int): UInt {
        return ((data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)).toUInt()
    }

    private fun writeU32(data: ByteArray, offset: Int, value: UInt) {
        data[offset] = (value and 0xFFu).toByte()
        data[offset + 1] = ((value shr 8) and 0xFFu).toByte()
        data[offset + 2] = ((value shr 16) and 0xFFu).toByte()
        data[offset + 3] = ((value shr 24) and 0xFFu).toByte()
    }
}
