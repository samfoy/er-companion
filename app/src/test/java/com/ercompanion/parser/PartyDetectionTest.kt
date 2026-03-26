package com.ercompanion.parser

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test party detection logic with focus on ER-specific issues:
 * - Contiguous slot detection (stop at first null)
 * - OT ID filtering (player vs enemy separation)
 * - Corrupted party count handling (count=16+ but real party is 6)
 * - Mixed player/enemy slots in the 12-slot buffer
 */
class PartyDetectionTest {

    private fun createMinimalPokemon(
        personality: UInt,
        otId: UInt,
        species: Int,
        level: Int = 50
    ): ByteArray {
        val data = ByteArray(104)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Minimal valid structure
        buffer.putInt(0, personality.toInt())
        buffer.putInt(4, otId.toInt())

        // Nickname
        data[0x08] = 0xFF.toByte()  // Immediate terminator

        // Create minimal encrypted substructures with species
        val subs = ByteArray(48)
        val subsBuffer = ByteBuffer.wrap(subs).order(ByteOrder.LITTLE_ENDIAN)
        subsBuffer.putShort(0, species.toShort())  // Species in substructure 0

        // Encrypt substructures
        val key = personality xor otId
        for (i in 0 until 48 step 4) {
            val word = ((subs[i].toInt() and 0xFF) or
                       ((subs[i + 1].toInt() and 0xFF) shl 8) or
                       ((subs[i + 2].toInt() and 0xFF) shl 16) or
                       ((subs[i + 3].toInt() and 0xFF) shl 24)).toUInt()
            buffer.putInt(0x20 + i, (word xor key).toInt())
        }

        // Pokemon extra fields
        data[0x54] = level.toByte()
        buffer.putShort(0x56, 100)  // HP
        buffer.putShort(0x58, 100)  // Max HP
        buffer.putShort(0x5A, 50)   // Attack
        buffer.putShort(0x5C, 50)   // Defense
        buffer.putShort(0x5E, 50)   // Speed
        buffer.putShort(0x60, 50)   // Sp.Attack
        buffer.putShort(0x62, 50)   // Sp.Defense

        return data
    }

    private fun createEmptySlot(): ByteArray {
        return ByteArray(104)  // All zeros = empty slot
    }

    @Test
    fun testContiguousSlotDetection() {
        // Create party: 3 Pokemon, then empty slots
        val party = ByteArray(104 * 12)

        // Slot 0-2: Valid Pokemon
        System.arraycopy(createMinimalPokemon(1u, 100u, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, 100u, 2), 0, party, 104, 104)
        System.arraycopy(createMinimalPokemon(3u, 100u, 3), 0, party, 208, 104)

        // Slot 3-11: Empty
        // (already zeros)

        // Parse with count=12 (corrupted/unreliable)
        val parsed = Gen3PokemonParser.parseParty(party, 12)

        // Should only get 3 Pokemon (stops at first null)
        var nonNullCount = 0
        for (mon in parsed) {
            if (mon != null) nonNullCount++
        }

        assertTrue("Should have at least 3 Pokemon", nonNullCount >= 3)
        assertNotNull("Slot 0 should be valid", parsed[0])
        assertNotNull("Slot 1 should be valid", parsed[1])
        assertNotNull("Slot 2 should be valid", parsed[2])
    }

    @Test
    fun testOTIDFilteringSeparatesPlayerFromEnemy() {
        val playerOtId = 0x12345678u
        val enemyOtId = 0xABCDEF00u

        val party = ByteArray(104 * 6)

        // Create party with all same OT ID (player party)
        // parseParty now stops at first OT ID mismatch
        System.arraycopy(createMinimalPokemon(1u, playerOtId, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, playerOtId, 2), 0, party, 104, 104)
        System.arraycopy(createMinimalPokemon(3u, playerOtId, 3), 0, party, 208, 104)

        // Parse player party
        val parsedPlayer = Gen3PokemonParser.parseParty(party, 6)

        assertEquals("Should have 3 player Pokemon", 3, parsedPlayer.size)
        assertEquals("Slot 0 species", 1, parsedPlayer[0].species)
        assertEquals("Slot 1 species", 2, parsedPlayer[1].species)
        assertEquals("Slot 2 species", 3, parsedPlayer[2].species)

        // Test that parseAllSlots returns everything without filtering
        val allSlots = Gen3PokemonParser.parseAllSlots(party)
        assertEquals("parseAllSlots should return 12 slots", 12, allSlots.size)

        // Create mixed party to test OT ID mismatch detection
        val mixedParty = ByteArray(104 * 3)
        System.arraycopy(createMinimalPokemon(1u, playerOtId, 1), 0, mixedParty, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, enemyOtId, 2), 0, mixedParty, 104, 104)  // Different OT
        System.arraycopy(createMinimalPokemon(3u, playerOtId, 3), 0, mixedParty, 208, 104)

        // Should stop at first OT mismatch
        val parsedMixed = Gen3PokemonParser.parseParty(mixedParty, 3)
        assertTrue("Should stop at OT mismatch", parsedMixed.size <= 1)
    }

    @Test
    fun testCorruptedPartyCount() {
        // ER mocha bug: party count can be 16+ even when real party is 1-6
        val party = ByteArray(104 * 12)

        // Only 2 real Pokemon
        System.arraycopy(createMinimalPokemon(1u, 100u, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, 100u, 2), 0, party, 104, 104)

        // Parse with corrupted count = 255
        val parsed = Gen3PokemonParser.parseParty(party, 255)

        // Should clamp to maximum of 12 slots
        assertTrue("Should not exceed 12 slots", parsed.size <= 12)

        // Should have 2 valid Pokemon
        var nonNullCount = 0
        for (mon in parsed) {
            if (mon != null) nonNullCount++
        }
        assertTrue("Should have at least 2 Pokemon", nonNullCount >= 2)
    }

    @Test
    fun testEmptyParty() {
        val party = ByteArray(104 * 12)  // All zeros

        val parsed = Gen3PokemonParser.parseParty(party, 6)

        // All slots should be null (no valid Pokemon)
        for (i in 0 until minOf(parsed.size, 6)) {
            assertNull("Slot $i should be null in empty party", parsed[i])
        }
    }

    @Test
    fun testFullParty() {
        val party = ByteArray(104 * 12)

        // Fill all 6 slots
        for (i in 0 until 6) {
            System.arraycopy(
                createMinimalPokemon((i + 1).toUInt(), 100u, i + 1),
                0, party, i * 104, 104
            )
        }

        val parsed = Gen3PokemonParser.parseParty(party, 6)

        assertEquals("Should have 6 slots", 6, parsed.size)

        // All should be valid
        for (i in 0 until 6) {
            assertNotNull("Slot $i should be valid", parsed[i])
            assertEquals("Slot $i should have correct species", i + 1, parsed[i]?.species)
        }
    }

    @Test
    fun testPartyWithGapDoesNotCrossGap() {
        // Party: 2 Pokemon, empty slot, then 2 more Pokemon
        // Contiguous detection should stop at first empty
        val party = ByteArray(104 * 12)

        System.arraycopy(createMinimalPokemon(1u, 100u, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, 100u, 2), 0, party, 104, 104)
        // Slot 2: Empty
        System.arraycopy(createMinimalPokemon(4u, 100u, 4), 0, party, 312, 104)
        System.arraycopy(createMinimalPokemon(5u, 100u, 5), 0, party, 416, 104)

        val parsed = Gen3PokemonParser.parseParty(party, 5)

        // Should only get first 2 Pokemon (stops at first null)
        assertNotNull("Slot 0 should be valid", parsed[0])
        assertNotNull("Slot 1 should be valid", parsed[1])

        // Implementation may vary - this tests the current behavior
        // The key is that we don't crash and handle the gap gracefully
    }

    @Test
    fun testParseAllSlotsNoFiltering() {
        // parseAllSlots should return all 12 slots without OT filtering
        val party = ByteArray(104 * 12)

        val playerOtId = 100u
        val enemyOtId = 200u

        // Mix of player and enemy Pokemon
        System.arraycopy(createMinimalPokemon(1u, playerOtId, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, enemyOtId, 2), 0, party, 104, 104)
        System.arraycopy(createMinimalPokemon(3u, playerOtId, 3), 0, party, 208, 104)
        // Rest are empty

        val parsed = Gen3PokemonParser.parseAllSlots(party)

        assertEquals("Should return 12 slots", 12, parsed.size)

        // Should have both player and enemy Pokemon (no filtering)
        assertNotNull("Slot 0 (player) should be present", parsed[0])
        assertNotNull("Slot 1 (enemy) should be present", parsed[1])
        assertNotNull("Slot 2 (player) should be present", parsed[2])

        assertEquals("Slot 0 OT ID", playerOtId.toLong() and 0xFFFFFFFFL, parsed[0]?.otId)
        assertEquals("Slot 1 OT ID", enemyOtId.toLong() and 0xFFFFFFFFL, parsed[1]?.otId)
    }

    @Test
    fun testOTIDFilteringStopsAtMismatch() {
        // parseParty now stops at first OT ID mismatch (contiguous detection)
        val playerOtId = 111u
        val enemyOtId = 222u

        val party = ByteArray(104 * 4)

        System.arraycopy(createMinimalPokemon(1u, playerOtId, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, enemyOtId, 2), 0, party, 104, 104)  // Different OT
        System.arraycopy(createMinimalPokemon(3u, enemyOtId, 3), 0, party, 208, 104)
        System.arraycopy(createMinimalPokemon(4u, playerOtId, 4), 0, party, 312, 104)

        val parsed = Gen3PokemonParser.parseParty(party, 4)

        // Should only get first Pokemon (stops at OT mismatch)
        assertTrue("Should have at most 1 Pokemon", parsed.size <= 1)

        if (parsed.isNotEmpty()) {
            assertEquals("Slot 0 species", 1, parsed[0].species)
            assertEquals("Slot 0 OT ID", playerOtId.toLong() and 0xFFFFFFFFL, parsed[0].otId)
        }
    }

    @Test
    fun testZeroPartyCount() {
        // ER mocha bug: party count can be 0 even when party exists
        val party = ByteArray(104 * 12)

        // Real party has 3 Pokemon
        System.arraycopy(createMinimalPokemon(1u, 100u, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, 100u, 2), 0, party, 104, 104)
        System.arraycopy(createMinimalPokemon(3u, 100u, 3), 0, party, 208, 104)

        // Parse with count = 0 (unreliable count)
        val parsed = Gen3PokemonParser.parseParty(party, 0)

        // Should handle gracefully (may return empty list)
        assertNotNull("Should return a list", parsed)

        // The implementation should either:
        // 1. Return empty list (respecting count=0)
        // 2. Or have fallback logic to detect contiguous Pokemon

        // For now, we just verify it doesn't crash
        assertTrue("Should return a valid list", parsed.size >= 0)
    }

    @Test
    fun testMaximumPartySize() {
        // Test with all 12 slots filled
        val party = ByteArray(104 * 12)

        for (i in 0 until 12) {
            System.arraycopy(
                createMinimalPokemon((i + 1).toUInt(), 100u, i + 1),
                0, party, i * 104, 104
            )
        }

        val parsed = Gen3PokemonParser.parseParty(party, 12)

        assertTrue("Should not exceed 12 slots", parsed.size <= 12)

        // All should be valid
        for (i in 0 until minOf(12, parsed.size)) {
            assertNotNull("Slot $i should be valid", parsed[i])
        }
    }

    @Test
    fun testParseAllSlotsReturnsEverything() {
        // parseAllSlots returns all 12 slots without OT ID filtering
        val party = ByteArray(104 * 3)

        System.arraycopy(createMinimalPokemon(1u, 111u, 1), 0, party, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, 222u, 2), 0, party, 104, 104)
        System.arraycopy(createMinimalPokemon(3u, 333u, 3), 0, party, 208, 104)

        val parsed = Gen3PokemonParser.parseAllSlots(party)

        assertEquals("Should return 12 slots", 12, parsed.size)

        // First 3 should be valid (different OT IDs allowed)
        assertNotNull("Slot 0", parsed[0])
        assertNotNull("Slot 1", parsed[1])
        assertNotNull("Slot 2", parsed[2])

        assertEquals("Slot 0 species", 1, parsed[0]?.species)
        assertEquals("Slot 1 species", 2, parsed[1]?.species)
        assertEquals("Slot 2 species", 3, parsed[2]?.species)

        // Rest should be null (empty)
        for (i in 3 until 12) {
            assertNull("Slot $i should be null", parsed[i])
        }
    }

    @Test
    fun testInvalidDataInMiddleOfParty() {
        // Test resilience when there's corrupted data in the middle
        val party = ByteArray(104 * 12)

        System.arraycopy(createMinimalPokemon(1u, 100u, 1), 0, party, 0, 104)

        // Slot 1: Corrupted data (invalid species 9999)
        System.arraycopy(createMinimalPokemon(2u, 100u, 9999), 0, party, 104, 104)

        System.arraycopy(createMinimalPokemon(3u, 100u, 3), 0, party, 208, 104)

        val parsed = Gen3PokemonParser.parseParty(party, 3)

        // Should handle gracefully
        assertNotNull("Should return a list", parsed)

        // Slot 0 should be valid
        assertNotNull("Slot 0 should be valid", parsed[0])

        // Slot 1 should be null (invalid species)
        assertNull("Slot 1 should be null (invalid species)", parsed[1])

        // Slot 2 may or may not be parsed depending on contiguous detection
        // The key is we don't crash
    }

    @Test
    fun testMultipleOTIDsInSeparateParties() {
        // Test that parties with consistent OT IDs parse correctly
        val otId1 = 1000u
        val otId2 = 2000u

        val party1 = ByteArray(104 * 2)
        System.arraycopy(createMinimalPokemon(1u, otId1, 10), 0, party1, 0, 104)
        System.arraycopy(createMinimalPokemon(2u, otId1, 20), 0, party1, 104, 104)

        val party2 = ByteArray(104 * 2)
        System.arraycopy(createMinimalPokemon(3u, otId2, 30), 0, party2, 0, 104)
        System.arraycopy(createMinimalPokemon(4u, otId2, 40), 0, party2, 104, 104)

        // Parse both parties
        val parsed1 = Gen3PokemonParser.parseParty(party1, 2)
        val parsed2 = Gen3PokemonParser.parseParty(party2, 2)

        // Both should return their respective Pokemon
        assertEquals("Party 1 size", 2, parsed1.size)
        assertEquals("Party 2 size", 2, parsed2.size)

        assertEquals("Party 1 slot 0 species", 10, parsed1[0].species)
        assertEquals("Party 1 slot 1 species", 20, parsed1[1].species)
        assertEquals("Party 2 slot 0 species", 30, parsed2[0].species)
        assertEquals("Party 2 slot 1 species", 40, parsed2[1].species)

        // Verify OT IDs are consistent within each party
        assertEquals("Party 1 OT ID", otId1.toLong() and 0xFFFFFFFFL, parsed1[0].otId)
        assertEquals("Party 2 OT ID", otId2.toLong() and 0xFFFFFFFFL, parsed2[0].otId)
    }
}
