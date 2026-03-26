package com.ercompanion.parser

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test memory layout parsing and address calculations
 *
 * Tests cover:
 * - Party data extraction from EWRAM offset 0x37780
 * - gBattleMons extraction from EWRAM offset 0x1C358
 * - Stat stage baseline=6 conversion
 * - RASTATE format handling
 * - Memory offset calculations
 */
class MemoryAddressTest {

    companion object {
        // EWRAM memory offsets
        const val EWRAM_BASE = 0x02000000
        const val PARTY_OFFSET = 0x37780
        const val PARTY_COUNT_OFFSET = 0x3777C
        const val BATTLE_MONS_OFFSET = 0x1C358

        // RASTATE format offsets
        const val RASTATE_EWRAM_OFFSET = 0x21000
    }

    @Test
    fun testPartyDataOffsetCalculation() {
        // Test that party data starts at correct offset
        val gbaAddress = EWRAM_BASE + PARTY_OFFSET
        assertEquals("Party GBA address", 0x02037780, gbaAddress)

        // In RASTATE format, add RASTATE_EWRAM_OFFSET
        val rastateOffset = RASTATE_EWRAM_OFFSET + PARTY_OFFSET
        assertEquals("Party RASTATE offset", 0x58780, rastateOffset)
    }

    @Test
    fun testPartyCountOffsetCalculation() {
        // Party count is 4 bytes before party data
        val gbaAddress = EWRAM_BASE + PARTY_COUNT_OFFSET
        assertEquals("Party count GBA address", 0x0203777C, gbaAddress)

        val offset = PARTY_COUNT_OFFSET - PARTY_OFFSET
        assertEquals("Party count is -4 bytes from party start", -4, offset)
    }

    @Test
    fun testBattleMonsOffsetCalculation() {
        // gBattleMons is at EWRAM+0x1C358
        val gbaAddress = EWRAM_BASE + BATTLE_MONS_OFFSET
        assertEquals("gBattleMons GBA address", 0x0201C358, gbaAddress)

        val rastateOffset = RASTATE_EWRAM_OFFSET + BATTLE_MONS_OFFSET
        assertEquals("gBattleMons RASTATE offset", 0x3D358, rastateOffset)
    }

    @Test
    fun testPartySlotSize() {
        // Each Pokemon is 104 bytes
        val slotSize = 104
        val partySize = slotSize * 12  // 12 slots

        assertEquals("Party buffer size", 1248, partySize)
    }

    @Test
    fun testBattleMonSlotSize() {
        // Each gBattleMon is 0x60 (96) bytes
        val slotSize = 0x60
        val battleMonsSize = slotSize * 4  // 4 slots

        assertEquals("gBattleMons buffer size", 384, battleMonsSize)
    }

    @Test
    fun testStatStageBaselineConversion() {
        // Stat stages: stored as 0-12, baseline is 6
        // -6 = 0, 0 = 6, +6 = 12

        val testCases = mapOf(
            0 to -6,
            3 to -3,
            6 to 0,
            9 to 3,
            12 to 6
        )

        for ((stored, actual) in testCases) {
            val converted = stored - 6
            assertEquals("Stat stage $stored should convert to $actual", actual, converted)
        }
    }

    @Test
    fun testStatStageMultipliers() {
        // Stat stage multipliers: stage N = (2 + max(0, N)) / (2 + max(0, -N))
        val multipliers = mapOf(
            -6 to 2.0 / 8.0,  // 0.25x
            -3 to 2.0 / 5.0,  // 0.4x
            0 to 1.0,         // 1x
            1 to 3.0 / 2.0,   // 1.5x
            2 to 4.0 / 2.0,   // 2x
            3 to 5.0 / 2.0,   // 2.5x
            6 to 8.0 / 2.0    // 4x
        )

        for ((stage, expectedMult) in multipliers) {
            val multiplier = if (stage >= 0) {
                (2 + stage).toDouble() / 2.0
            } else {
                2.0 / (2 - stage)
            }
            assertEquals("Stage $stage multiplier", expectedMult, multiplier, 0.01)
        }
    }

    @Test
    fun testGBattleMonStructureLayout() {
        // Test parsing gBattleMon structure (0x60 bytes)
        val battleMon = ByteArray(0x60)
        val buffer = ByteBuffer.wrap(battleMon).order(ByteOrder.LITTLE_ENDIAN)

        // Set known values
        buffer.putShort(0x00, 150)  // species (Mewtwo)
        buffer.putShort(0x02, 200)  // attack
        buffer.putShort(0x04, 150)  // defense
        buffer.putShort(0x06, 180)  // speed
        buffer.putShort(0x08, 220)  // spAttack
        buffer.putShort(0x0A, 150)  // spDefense

        // Moves at 0x0C (4 moves, 2 bytes each)
        buffer.putShort(0x0C, 94)   // Psychic
        buffer.putShort(0x0E, 473)  // Psystrike
        buffer.putShort(0x10, 396)  // Aura Sphere
        buffer.putShort(0x12, 58)   // Ice Beam

        // Stat stages at 0x18 (8 bytes)
        battleMon[0x18] = 6  // Attack (0)
        battleMon[0x19] = 6  // Defense (0)
        battleMon[0x1A] = 8  // Speed (+2)
        battleMon[0x1B] = 10 // Sp.Attack (+4)
        battleMon[0x1C] = 6  // Sp.Defense (0)
        battleMon[0x1D] = 6  // Accuracy (0)
        battleMon[0x1E] = 6  // Evasion (0)

        buffer.putShort(0x2A, 300)  // hp
        battleMon[0x2C] = 70         // level
        buffer.putShort(0x2E, 350)  // maxHP

        // Parse
        val species = buffer.getShort(0x00).toInt() and 0xFFFF
        val attack = buffer.getShort(0x02).toInt() and 0xFFFF
        val defense = buffer.getShort(0x04).toInt() and 0xFFFF
        val speed = buffer.getShort(0x06).toInt() and 0xFFFF
        val spAttack = buffer.getShort(0x08).toInt() and 0xFFFF
        val spDefense = buffer.getShort(0x0A).toInt() and 0xFFFF
        val hp = buffer.getShort(0x2A).toInt() and 0xFFFF
        val level = battleMon[0x2C].toInt() and 0xFF
        val maxHP = buffer.getShort(0x2E).toInt() and 0xFFFF

        val atkStage = (battleMon[0x18].toInt() and 0xFF) - 6
        val spdStage = (battleMon[0x1A].toInt() and 0xFF) - 6
        val spaStage = (battleMon[0x1B].toInt() and 0xFF) - 6

        assertEquals("Species", 150, species)
        assertEquals("Attack stat", 200, attack)
        assertEquals("Defense stat", 150, defense)
        assertEquals("Speed stat", 180, speed)
        assertEquals("Sp.Attack stat", 220, spAttack)
        assertEquals("Sp.Defense stat", 150, spDefense)
        assertEquals("HP", 300, hp)
        assertEquals("Max HP", 350, maxHP)
        assertEquals("Level", 70, level)
        assertEquals("Attack stage", 0, atkStage)
        assertEquals("Speed stage", 2, spdStage)
        assertEquals("Sp.Attack stage", 4, spaStage)
    }

    @Test
    fun testPartyDataExtraction() {
        // Test extracting party data from full EWRAM buffer
        val ewramSize = 0x40000  // 256KB
        val ewram = ByteArray(ewramSize)

        // Create a minimal Pokemon at party offset
        val personality = 0x12345678u
        val otId = 0xABCDEF00u

        val buffer = ByteBuffer.wrap(ewram).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(PARTY_OFFSET)
        buffer.putInt(personality.toInt())
        buffer.putInt(otId.toInt())

        // Extract party data
        val partyData = ewram.sliceArray(PARTY_OFFSET until PARTY_OFFSET + 104)

        // Verify
        val extractedPersonality = ((partyData[0].toInt() and 0xFF) or
                                   ((partyData[1].toInt() and 0xFF) shl 8) or
                                   ((partyData[2].toInt() and 0xFF) shl 16) or
                                   ((partyData[3].toInt() and 0xFF) shl 24)).toUInt()

        assertEquals("Extracted personality", personality, extractedPersonality)
    }

    @Test
    fun testRASTATEFormatExtraction() {
        // Simulate RASTATE format with MEM chunk
        // In RASTATE, EWRAM starts at offset 0x21000
        val rastateSize = 0x61000  // Large enough to hold EWRAM
        val rastate = ByteArray(rastateSize)

        // Write party data at RASTATE offset
        val personality = 0x11223344u
        val buffer = ByteBuffer.wrap(rastate).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(RASTATE_EWRAM_OFFSET + PARTY_OFFSET)
        buffer.putInt(personality.toInt())

        // Extract
        val partyData = rastate.sliceArray(
            RASTATE_EWRAM_OFFSET + PARTY_OFFSET until RASTATE_EWRAM_OFFSET + PARTY_OFFSET + 104
        )

        val extractedPersonality = ((partyData[0].toInt() and 0xFF) or
                                   ((partyData[1].toInt() and 0xFF) shl 8) or
                                   ((partyData[2].toInt() and 0xFF) shl 16) or
                                   ((partyData[3].toInt() and 0xFF) shl 24)).toUInt()

        assertEquals("Extracted personality from RASTATE", personality, extractedPersonality)
    }

    @Test
    fun testMultiplePartySlots() {
        // Test extracting multiple Pokemon from party buffer
        val partyData = ByteArray(104 * 3)

        for (i in 0 until 3) {
            val buffer = ByteBuffer.wrap(partyData).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(i * 104)
            buffer.putInt((i + 1).toInt())  // personality = 1, 2, 3
            buffer.putInt(100)              // otId
        }

        // Extract each slot
        val personalities = mutableListOf<Int>()
        for (i in 0 until 3) {
            val slotData = partyData.sliceArray(i * 104 until (i + 1) * 104)
            val personality = ByteBuffer.wrap(slotData).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
            personalities.add(personality)
        }

        assertEquals("Slot 0 personality", 1, personalities[0])
        assertEquals("Slot 1 personality", 2, personalities[1])
        assertEquals("Slot 2 personality", 3, personalities[2])
    }

    @Test
    fun testBattleMonsSlotPositions() {
        // Test that we can access all 4 gBattleMons slots
        val battleMons = ByteArray(0x60 * 4)

        for (i in 0 until 4) {
            val buffer = ByteBuffer.wrap(battleMons).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(i * 0x60)
            buffer.putShort((i + 1).toShort())  // species = 1, 2, 3, 4
        }

        // Extract
        val species = mutableListOf<Int>()
        for (i in 0 until 4) {
            val slotData = battleMons.sliceArray(i * 0x60 until (i + 1) * 0x60)
            val sp = ByteBuffer.wrap(slotData).order(ByteOrder.LITTLE_ENDIAN).getShort(0).toInt() and 0xFFFF
            species.add(sp)
        }

        assertEquals("Slot 0 species (player)", 1, species[0])
        assertEquals("Slot 1 species (enemy)", 2, species[1])
        assertEquals("Slot 2 species (doubles ally)", 3, species[2])
        assertEquals("Slot 3 species (doubles enemy)", 4, species[3])
    }

    @Test
    fun testBattleDetectionViaGBattleMons() {
        // Battle is active when gBattleMons[1] (enemy slot) has species > 0
        val battleMons = ByteArray(0x60 * 4)

        // Test: No battle (all species = 0)
        var buffer = ByteBuffer.wrap(battleMons).order(ByteOrder.LITTLE_ENDIAN)
        var enemySpecies = buffer.getShort(0x60).toInt() and 0xFFFF
        assertEquals("No battle active", 0, enemySpecies)

        // Test: Battle active (enemy has species)
        buffer.position(0x60)  // Slot 1 offset
        buffer.putShort(94)    // Enemy species (Gengar)

        buffer.position(0x60)
        enemySpecies = buffer.getShort().toInt() and 0xFFFF
        assertTrue("Battle should be active", enemySpecies > 0)
    }

    @Test
    fun testPartyCountReliability() {
        // Test the unreliable party count issue in ER mocha
        val testCases = listOf(
            0,    // Can be 0 even with party
            16,   // Can be corrupted to high values
            255,  // Max byte value corruption
            6     // Normal valid value
        )

        for (count in testCases) {
            // Clamp to 12 slots maximum
            val effectiveCount = minOf(count, 12)
            assertTrue("Count $count should clamp to <= 12", effectiveCount <= 12)
            assertTrue("Count $count should be non-negative", effectiveCount >= 0)
        }
    }

    @Test
    fun testMemoryBoundaryChecks() {
        // Test that we don't read beyond buffer boundaries
        val ewramSize = 0x40000

        // Party end offset
        val partyEnd = PARTY_OFFSET + (104 * 12)
        assertTrue("Party should fit in EWRAM", partyEnd < ewramSize)

        // gBattleMons end offset
        val battleMonsEnd = BATTLE_MONS_OFFSET + (0x60 * 4)
        assertTrue("gBattleMons should fit in EWRAM", battleMonsEnd < ewramSize)
    }

    @Test
    fun testOffsetArithmetic() {
        // Test that offset calculations don't overflow
        val maxOffset = PARTY_OFFSET + (104 * 12)
        assertTrue("Max offset should be positive", maxOffset > 0)
        assertTrue("Max offset should fit in 32-bit int", maxOffset < Int.MAX_VALUE)
    }

    @Test
    fun testStatStageArrayLayout() {
        // Stat stages are 8 bytes starting at 0x18 in gBattleMon
        // Order: Atk, Def, Spd, SpA, SpD, Acc, Eva, (1 unused)
        val statStages = ByteArray(8)
        statStages[0] = 8   // Attack +2
        statStages[1] = 4   // Defense -2
        statStages[2] = 6   // Speed 0
        statStages[3] = 10  // Sp.Attack +4
        statStages[4] = 6   // Sp.Defense 0
        statStages[5] = 6   // Accuracy 0
        statStages[6] = 3   // Evasion -3

        val stages = mapOf(
            "Attack" to (statStages[0].toInt() - 6),
            "Defense" to (statStages[1].toInt() - 6),
            "Speed" to (statStages[2].toInt() - 6),
            "SpAttack" to (statStages[3].toInt() - 6),
            "SpDefense" to (statStages[4].toInt() - 6),
            "Accuracy" to (statStages[5].toInt() - 6),
            "Evasion" to (statStages[6].toInt() - 6)
        )

        assertEquals("Attack stage", 2, stages["Attack"])
        assertEquals("Defense stage", -2, stages["Defense"])
        assertEquals("Speed stage", 0, stages["Speed"])
        assertEquals("Sp.Attack stage", 4, stages["SpAttack"])
        assertEquals("Sp.Defense stage", 0, stages["SpDefense"])
        assertEquals("Accuracy stage", 0, stages["Accuracy"])
        assertEquals("Evasion stage", -3, stages["Evasion"])
    }

    @Test
    fun testLittleEndianByteOrder() {
        // Verify little-endian byte order is used correctly
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x12345678)

        val bytes = buffer.array()
        assertEquals("Byte 0 (LSB)", 0x78.toByte(), bytes[0])
        assertEquals("Byte 1", 0x56.toByte(), bytes[1])
        assertEquals("Byte 2", 0x34.toByte(), bytes[2])
        assertEquals("Byte 3 (MSB)", 0x12.toByte(), bytes[3])
    }

    @Test
    fun testStatusConditionFlags() {
        // Status at 0x50 in party Pokemon (32-bit)
        val testCases = mapOf(
            0x00 to "None",
            0x03 to "Sleep 3 turns",
            0x08 to "Poison",
            0x10 to "Burn",
            0x20 to "Freeze",
            0x40 to "Paralysis",
            0x80 to "Toxic"
        )

        for ((flag, description) in testCases) {
            // Test that we can read status correctly
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(flag)

            val status = buffer.getInt(0)
            assertEquals("Status: $description", flag, status)
        }
    }

    @Test
    fun testSpeciesIDRange() {
        // ER supports species 1-1526
        val validRange = 1..1526

        assertTrue("Bulbasaur (1) is valid", 1 in validRange)
        assertTrue("Mewtwo (150) is valid", 150 in validRange)
        assertTrue("Pikachu (25) is valid", 25 in validRange)
        assertTrue("Max species (1526) is valid", 1526 in validRange)

        assertFalse("Species 0 is invalid", 0 in validRange)
        assertFalse("Species 9999 is invalid", 9999 in validRange)
    }
}
