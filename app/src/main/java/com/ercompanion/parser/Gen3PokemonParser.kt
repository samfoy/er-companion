package com.ercompanion.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    val friendship: Int
)

object Gen3PokemonParser {
    private const val POKEMON_SIZE = 104

    // Substructure order lookup table (personality % 24)
    private val SUBSTRUCTURE_ORDER = arrayOf(
        intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3),
        intArrayOf(0, 3, 1, 2), intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 2, 1),
        intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(2, 0, 1, 3),
        intArrayOf(3, 0, 1, 2), intArrayOf(2, 0, 3, 1), intArrayOf(3, 0, 2, 1),
        intArrayOf(1, 2, 0, 3), intArrayOf(1, 3, 0, 2), intArrayOf(2, 1, 0, 3),
        intArrayOf(3, 1, 0, 2), intArrayOf(2, 3, 0, 1), intArrayOf(3, 2, 0, 1),
        intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 2, 0), intArrayOf(2, 1, 3, 0),
        intArrayOf(3, 1, 2, 0), intArrayOf(2, 3, 1, 0), intArrayOf(3, 2, 1, 0)
    )

    fun parseParty(data: ByteArray, partyCount: Int): List<PartyMon?> {
        val party = mutableListOf<PartyMon?>()

        for (i in 0 until minOf(partyCount, 6)) {
            val offset = i * POKEMON_SIZE
            if (offset + POKEMON_SIZE > data.size) break

            val monData = data.sliceArray(offset until offset + POKEMON_SIZE)
            party.add(parsePokemon(monData))
        }

        return party
    }

    fun parsePokemon(data: ByteArray): PartyMon? {
        if (data.size < POKEMON_SIZE) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Read BoxPokemon structure (80 bytes)
        val personality = buffer.getInt(0).toUInt()
        val otId = buffer.getInt(4).toUInt()

        if (personality == 0u) return null

        // Read nickname (10 bytes at offset 0x08)
        val nicknameBytes = ByteArray(10)
        buffer.position(0x08)
        buffer.get(nicknameBytes)
        val nickname = decodeGen3String(nicknameBytes)

        // Decrypt substructures (48 bytes starting at offset 0x20)
        val encryptedData = ByteArray(48)
        buffer.position(0x20)
        buffer.get(encryptedData)

        val decryptedData = decryptSubstructures(encryptedData, personality, otId)
        val substructures = reorderSubstructures(decryptedData, personality)

        // Parse substructure A: species, item, experience, friendship
        val species = readU16(substructures[0], 0)
        val experience = readU32(substructures[0], 4)
        val friendship = readU8(substructures[0], 9)

        // Parse substructure B: moves (4 moves, 2 bytes each)
        val moves = (0 until 4).map { readU16(substructures[1], it * 2) }

        // Read Pokemon extra fields (after BoxPokemon, offset 0x50)
        val level = readU8(data, 0x54)
        val hp = readU16(data, 0x56)
        val maxHp = readU16(data, 0x58)
        val attack = readU16(data, 0x5A)
        val defense = readU16(data, 0x5C)
        val speed = readU16(data, 0x5E)
        val spAttack = readU16(data, 0x60)
        val spDefense = readU16(data, 0x62)

        // Validate
        if (species == 0 || species > 412 || level == 0 || level > 100) {
            return null
        }

        return PartyMon(
            species = species,
            level = level,
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
            friendship = friendship
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

        // Split into 4 substructures of 12 bytes each
        val substructures = Array(4) { ByteArray(12) }
        for (i in 0 until 4) {
            System.arraycopy(decrypted, i * 12, substructures[i], 0, 12)
        }

        // Reorder according to personality
        return Array(4) { substructures[order[it]] }
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
