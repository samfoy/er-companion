package com.ercompanion.parser

import org.junit.Test
import org.junit.Assert.*

/**
 * Test address validation and scanning for save state memory addresses.
 */
class AddressValidationTest {

    @Test
    fun testValidateEmptyEwram() {
        // Empty EWRAM should fail validation
        val ewram = ByteArray(256 * 1024)
        val result = AddressValidator.validatePartyAddress(ewram)

        assertFalse("Empty EWRAM should fail validation", result.isValid)
        assertTrue("Confidence should be 0.0", result.confidence == 0.0)
    }

    @Test
    fun testValidateValidPartyData() {
        // Create mock EWRAM with valid Pokemon at default party offset (0x37780)
        val ewram = ByteArray(256 * 1024)
        val partyOffset = 0x37780

        // Create a valid Pokemon at slot 0 (offset +4 from count)
        val personality = 0x12345678u
        val otId = 0x87654321u

        // Write personality (u32 LE at +0)
        writeU32LE(ewram, partyOffset + 4, personality)
        // Write OT ID (u32 LE at +4)
        writeU32LE(ewram, partyOffset + 8, otId)

        // Write level (u8 at +84) - unencrypted
        ewram[partyOffset + 4 + 84] = 50

        // Write maxHP (u16 LE at +88) - unencrypted
        writeU16LE(ewram, partyOffset + 4 + 88, 150u)

        // Write currentHP (u16 LE at +86) - unencrypted
        writeU16LE(ewram, partyOffset + 4 + 86, 100u)

        // Write encrypted species (in substructure 0)
        // For simplicity, we'll create a minimal encrypted structure
        // Personality % 24 determines substructure order
        val orderIdx = (personality % 24u).toInt()
        val key = personality xor otId

        // Create substructure 0 with species=25 (Pikachu)
        val species = 25u
        val encryptedSpecies = species xor key
        val substructOrder = getSubstructOrder(orderIdx)
        val pos = substructOrder.indexOf(0)

        // Write encrypted species at correct position
        writeU32LE(ewram, partyOffset + 4 + 32 + pos * 12, encryptedSpecies)

        // Write checksum at offset +28
        // For this test, we'll write a dummy checksum (validation will be lenient)
        writeU16LE(ewram, partyOffset + 4 + 28, 0u)

        val result = AddressValidator.validatePartyAddress(ewram, partyOffset + 4)

        assertTrue("Valid party data should pass validation", result.isValid)
        assertTrue("Confidence should be > 0.0", result.confidence > 0.0)
    }

    @Test
    fun testValidateBattlersCount() {
        val ewram = ByteArray(256 * 1024)
        val offset = 0x1839c

        // Test valid values (0, 2, 4)
        ewram[offset] = 0
        var result = AddressValidator.validateBattlersCount(ewram, offset)
        assertTrue("gBattlersCount=0 should be valid", result.isValid)

        ewram[offset] = 2
        result = AddressValidator.validateBattlersCount(ewram, offset)
        assertTrue("gBattlersCount=2 should be valid", result.isValid)

        ewram[offset] = 4
        result = AddressValidator.validateBattlersCount(ewram, offset)
        assertTrue("gBattlersCount=4 should be valid", result.isValid)

        // Test invalid value
        ewram[offset] = 7
        result = AddressValidator.validateBattlersCount(ewram, offset)
        assertFalse("gBattlersCount=7 should be invalid", result.isValid)
    }

    @Test
    fun testScanForPartyInEmptyEwram() {
        val ewram = ByteArray(256 * 1024)
        val result = SaveStateAddressScanner.scanForParty(ewram)

        assertTrue("Scan should report no party found", result.partyOffset < 0)
        assertTrue("Confidence should be 0.0", result.confidence == 0.0)
    }

    @Test
    fun testScanForBattleMonsInEmptyEwram() {
        val ewram = ByteArray(256 * 1024)
        val result = SaveStateAddressScanner.scanForBattleMons(ewram)

        // Empty EWRAM with all zeros = valid empty battle structure
        // Should still find a candidate (all zeros = all empty slots)
        assertTrue("Scan result should have low/zero confidence for empty EWRAM",
            result.confidence <= 0.5)
    }

    // Helper functions for test data creation
    private fun writeU16LE(buf: ByteArray, offset: Int, value: UInt) {
        buf[offset] = (value and 0xFFu).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFFu).toByte()
    }

    private fun writeU32LE(buf: ByteArray, offset: Int, value: UInt) {
        buf[offset] = (value and 0xFFu).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFFu).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFFu).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFFu).toByte()
    }

    private fun getSubstructOrder(orderIdx: Int): IntArray {
        val orders = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3),
            intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 1, 2), intArrayOf(0, 3, 2, 1),
            intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(1, 2, 0, 3),
            intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 0, 2), intArrayOf(1, 3, 2, 0),
            intArrayOf(2, 0, 1, 3), intArrayOf(2, 0, 3, 1), intArrayOf(2, 1, 0, 3),
            intArrayOf(2, 1, 3, 0), intArrayOf(2, 3, 0, 1), intArrayOf(2, 3, 1, 0),
            intArrayOf(3, 0, 1, 2), intArrayOf(3, 0, 2, 1), intArrayOf(3, 1, 0, 2),
            intArrayOf(3, 1, 2, 0), intArrayOf(3, 2, 0, 1), intArrayOf(3, 2, 1, 0)
        )
        return orders[orderIdx]
    }
}
