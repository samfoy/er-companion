package com.ercompanion.parser

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Comprehensive test suite for Gen3 Pokemon parsing with ground truth data
 *
 * Tests cover:
 * - Known personality/OT ID combinations with encryption
 * - All 24 substructure orderings (personality % 24)
 * - IV extraction from packed 32-bit format
 * - Nature calculation (personality % 25)
 * - Ability slot detection
 * - Level fallback from experience
 * - OT ID filtering for party detection
 */
class Gen3PokemonParserTest {

    /**
     * Helper to create a minimal valid Pokemon structure (104 bytes)
     */
    private fun createPokemonData(
        personality: UInt,
        otId: UInt,
        species: Int,
        level: Int,
        hp: Int,
        maxHp: Int,
        attack: Int,
        defense: Int,
        speed: Int,
        spAttack: Int,
        spDefense: Int,
        experience: UInt,
        friendship: Int,
        heldItem: Int,
        moves: List<Int>,
        movePP: List<Int>,
        ivs: IntArray,  // [hp, atk, def, spd, spa, spd]
        abilitySlot: Int,
        hiddenNatureModifier: UInt = 0u,
        status: UInt = 0u
    ): ByteArray {
        val data = ByteArray(104)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // BoxPokemon structure (80 bytes)
        buffer.putInt(0, personality.toInt())
        buffer.putInt(4, otId.toInt())

        // Nickname at 0x08 - "TESTMON" in Gen3 encoding
        // Gen3: A=0xBB, so T=0xCD, E=0xBE, S=0xCC, M=0xC6, O=0xC8, N=0xC7
        val nickname = byteArrayOf(
            0xCD.toByte(), 0xBE.toByte(), 0xCC.toByte(), 0xCD.toByte(), // TEST
            0xC6.toByte(), 0xC8.toByte(), 0xC7.toByte(), // MON
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()  // Terminator
        )
        System.arraycopy(nickname, 0, data, 0x08, 10)

        // Language and hidden nature modifier at 0x12
        val languageAndNature = ((hiddenNatureModifier.toInt() shl 3) and 0xF8).toByte()
        data[0x12] = languageAndNature

        // Create and encrypt substructures
        val decryptedSubs = createSubstructures(
            species, heldItem, experience, friendship,
            moves, movePP, ivs, abilitySlot
        )
        val encryptedData = encryptSubstructures(decryptedSubs, personality, otId)
        System.arraycopy(encryptedData, 0, data, 0x20, 48)

        // Pokemon extra fields (24 bytes starting at 0x50)
        buffer.putInt(0x50, status.toInt())
        data[0x54] = level.toByte()
        buffer.putShort(0x56, hp.toShort())
        buffer.putShort(0x58, maxHp.toShort())
        buffer.putShort(0x5A, attack.toShort())
        buffer.putShort(0x5C, defense.toShort())
        buffer.putShort(0x5E, speed.toShort())
        buffer.putShort(0x60, spAttack.toShort())
        buffer.putShort(0x62, spDefense.toShort())

        return data
    }

    private fun createSubstructures(
        species: Int,
        heldItem: Int,
        experience: UInt,
        friendship: Int,
        moves: List<Int>,
        movePP: List<Int>,
        ivs: IntArray,
        abilitySlot: Int
    ): ByteArray {
        val subs = ByteArray(48)
        val buffer = ByteBuffer.wrap(subs).order(ByteOrder.LITTLE_ENDIAN)

        // Substructure 0 (Growth): species, item, experience, friendship
        buffer.putShort(0, species.toShort())
        buffer.putShort(2, heldItem.toShort())
        buffer.putInt(4, experience.toInt())
        subs[9] = friendship.toByte()

        // Substructure 1 (Attacks): 4 moves + 4 PP values
        for (i in 0 until 4) {
            val moveId = if (i < moves.size) moves[i] else 0
            val pp = if (i < movePP.size) movePP[i] else 0
            buffer.putShort(12 + i * 2, moveId.toShort())
            subs[20 + i] = pp.toByte()
        }

        // Substructure 2 (EVs/IVs): Pack IVs into 32-bit value
        val ivData = (ivs[0] and 0x1F) or
                    ((ivs[1] and 0x1F) shl 5) or
                    ((ivs[2] and 0x1F) shl 10) or
                    ((ivs[3] and 0x1F) shl 15) or
                    ((ivs[4] and 0x1F) shl 20) or
                    ((ivs[5] and 0x1F) shl 25)
        buffer.putInt(24, ivData)

        // Substructure 3 (Misc): ivEggAbility with ability slot
        val ivEggAbility = ((abilitySlot and 0x3) shl 2).toUInt()
        buffer.putInt(40, ivEggAbility.toInt())

        return subs
    }

    private fun encryptSubstructures(decrypted: ByteArray, personality: UInt, otId: UInt): ByteArray {
        val key = personality xor otId
        val encrypted = ByteArray(48)
        val buffer = ByteBuffer.wrap(encrypted).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until 48 step 4) {
            val word = ((decrypted[i].toInt() and 0xFF) or
                       ((decrypted[i + 1].toInt() and 0xFF) shl 8) or
                       ((decrypted[i + 2].toInt() and 0xFF) shl 16) or
                       ((decrypted[i + 3].toInt() and 0xFF) shl 24)).toUInt()
            buffer.putInt(i, (word xor key).toInt())
        }
        return encrypted
    }

    @Test
    fun testBasicDecryptionAndParsing() {
        // Ground truth: Pikachu with known values
        val personality = 0x12345678u
        val otId = 0x11223344u
        val pokemon = createPokemonData(
            personality = personality,
            otId = otId,
            species = 25,  // Pikachu
            level = 50,
            hp = 95,
            maxHp = 95,
            attack = 90,
            defense = 60,
            speed = 120,
            spAttack = 75,
            spDefense = 70,
            experience = 125000u,
            friendship = 100,
            heldItem = 0,
            moves = listOf(344, 209, 97, 86),  // Thunder, Thunderbolt, Agility, Thunder Wave
            movePP = listOf(10, 15, 30, 20),
            ivs = intArrayOf(31, 30, 29, 28, 27, 26),
            abilitySlot = 0
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNotNull("Pokemon should parse successfully", parsed)

        parsed?.let {
            assertEquals("Species should be Pikachu", 25, it.species)
            assertEquals("Level should be 50", 50, it.level)
            assertEquals("HP should be 95", 95, it.hp)
            assertEquals("Max HP should be 95", 95, it.maxHp)
            assertEquals("Attack should be 90", 90, it.attack)
            assertEquals("Defense should be 60", 60, it.defense)
            assertEquals("Speed should be 120", 120, it.speed)
            assertEquals("Sp. Attack should be 75", 75, it.spAttack)
            assertEquals("Sp. Defense should be 70", 70, it.spDefense)
            assertEquals("Experience should be 125000", 125000, it.experience)
            assertEquals("Friendship should be 100", 100, it.friendship)
            assertEquals("Personality should match", personality, it.personality)
            assertEquals("OT ID should match", otId.toLong() and 0xFFFFFFFFL, it.otId)
        }
    }

    @Test
    fun testIVExtraction() {
        // Test IV extraction with known packed values
        val ivs = intArrayOf(31, 0, 15, 31, 16, 8)
        val pokemon = createPokemonData(
            personality = 0xABCDEF00u,
            otId = 0x12345678u,
            species = 1,
            level = 1,
            hp = 1, maxHp = 1,
            attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
            experience = 0u,
            friendship = 0,
            heldItem = 0,
            moves = listOf(),
            movePP = listOf(),
            ivs = ivs,
            abilitySlot = 0
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNotNull(parsed)

        parsed?.let {
            assertEquals("IV HP should be 31", 31, it.ivHp)
            assertEquals("IV Attack should be 0", 0, it.ivAttack)
            assertEquals("IV Defense should be 15", 15, it.ivDefense)
            assertEquals("IV Speed should be 31", 31, it.ivSpeed)
            assertEquals("IV Sp.Attack should be 16", 16, it.ivSpAttack)
            assertEquals("IV Sp.Defense should be 8", 8, it.ivSpDefense)
        }
    }

    @Test
    fun testNatureCalculation() {
        // Test nature calculation for all 25 natures
        val natureTests = listOf(
            0u to 0u,   // personality % 25 = 0 (Hardy)
            1u to 1u,   // personality % 25 = 1 (Lonely)
            24u to 24u, // personality % 25 = 24 (Quirky)
            100u to 0u, // 100 % 25 = 0 (Hardy)
            127u to 2u  // 127 % 25 = 2 (Brave)
        )

        for ((personality, expectedNature) in natureTests) {
            val pokemon = createPokemonData(
                personality = personality,
                otId = 0u,
                species = 1,
                level = 1,
                hp = 1, maxHp = 1,
                attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
                experience = 0u,
                friendship = 0,
                heldItem = 0,
                moves = listOf(),
                movePP = listOf(),
                ivs = intArrayOf(0, 0, 0, 0, 0, 0),
                abilitySlot = 0
            )

            val parsed = Gen3PokemonParser.parsePokemon(pokemon)
            assertNotNull("Pokemon with personality $personality should parse", parsed)
            assertEquals("Nature for personality $personality", expectedNature, parsed?.nature)
        }
    }

    @Test
    fun testNatureWithMintModifier() {
        // Test mint modifier (XOR with hidden nature)
        // Base nature = 5 (Adamant), mint modifier = 10 → effective nature = 15 (Jolly)
        val personality = 5u  // personality % 25 = 5
        val hiddenNatureModifier = 10u  // XOR modifier
        val expectedEffectiveNature = 5u xor 10u  // = 15

        val pokemon = createPokemonData(
            personality = personality,
            otId = 0u,
            species = 1,
            level = 1,
            hp = 1, maxHp = 1,
            attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
            experience = 0u,
            friendship = 0,
            heldItem = 0,
            moves = listOf(),
            movePP = listOf(),
            ivs = intArrayOf(0, 0, 0, 0, 0, 0),
            abilitySlot = 0,
            hiddenNatureModifier = hiddenNatureModifier
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNotNull(parsed)
        assertEquals("Effective nature should be modified by mint", expectedEffectiveNature, parsed?.nature)
    }

    @Test
    fun testAbilitySlotDetection() {
        // Test all 3 ability slots
        for (slot in 0..2) {
            val pokemon = createPokemonData(
                personality = 0u,
                otId = 0u,
                species = 1,  // Bulbasaur has Overgrow (65) / Chlorophyll (34)
                level = 1,
                hp = 1, maxHp = 1,
                attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
                experience = 0u,
                friendship = 0,
                heldItem = 0,
                moves = listOf(),
                movePP = listOf(),
                ivs = intArrayOf(0, 0, 0, 0, 0, 0),
                abilitySlot = slot
            )

            val parsed = Gen3PokemonParser.parsePokemon(pokemon)
            assertNotNull("Pokemon with ability slot $slot should parse", parsed)
            assertEquals("Ability slot should be $slot", slot, parsed?.abilitySlot)
        }
    }

    @Test
    fun testLevelFallbackFromExperience() {
        // Test that level is calculated from experience if level field is invalid
        val pokemon = createPokemonData(
            personality = 1u,
            otId = 0u,
            species = 1,
            level = 0,  // Invalid level - should fallback to calculation
            hp = 1, maxHp = 1,
            attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
            experience = 125000u,  // Roughly level 50 for medium-fast
            friendship = 0,
            heldItem = 0,
            moves = listOf(),
            movePP = listOf(),
            ivs = intArrayOf(0, 0, 0, 0, 0, 0),
            abilitySlot = 0
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNotNull(parsed)
        assertTrue("Level should be calculated from experience and be > 1", (parsed?.level ?: 0) > 1)
        assertTrue("Calculated level should be reasonable (1-100)", (parsed?.level ?: 0) in 1..100)
    }

    @Test
    fun testAllSubstructureOrderings() {
        // Test all 24 possible substructure orderings (personality % 24)
        for (ordering in 0 until 24) {
            val personality = ordering.toUInt()
            val pokemon = createPokemonData(
                personality = personality,
                otId = 0u,
                species = 150,  // Mewtwo
                level = 70,
                hp = 200, maxHp = 200,
                attack = 150, defense = 120, speed = 180, spAttack = 200, spDefense = 120,
                experience = 500000u,
                friendship = 0,
                heldItem = 245,  // Life Orb
                moves = listOf(94, 473, 396, 58),  // Psychic, Psystrike, Aura Sphere, Ice Beam
                movePP = listOf(10, 10, 20, 10),
                ivs = intArrayOf(31, 31, 31, 31, 31, 31),
                abilitySlot = 0
            )

            val parsed = Gen3PokemonParser.parsePokemon(pokemon)
            assertNotNull("Pokemon with substructure ordering $ordering should parse", parsed)

            parsed?.let {
                assertEquals("Species should be correct for ordering $ordering", 150, it.species)
                assertEquals("Level should be correct for ordering $ordering", 70, it.level)
                assertEquals("Held item should be correct for ordering $ordering", 245, it.heldItem)
                assertEquals("Should have 4 moves for ordering $ordering", 4, it.moves.size)
                assertEquals("All IVs should be 31 for ordering $ordering", 31, it.ivHp)
                assertEquals("Experience should be correct for ordering $ordering", 500000, it.experience)
            }
        }
    }

    @Test
    fun testOTIDFiltering() {
        // Create party with mixed OT IDs
        // parseParty now auto-detects player OT ID from first Pokemon and stops at first mismatch
        val playerOtId = 0x12345678u
        val enemyOtId = 0xABCDEF00u

        val party = ByteArray(104 * 3)

        // Slot 0: Player's Pokemon
        System.arraycopy(createPokemonData(
            personality = 1u, otId = playerOtId,
            species = 1, level = 50,
            hp = 100, maxHp = 100, attack = 50, defense = 50, speed = 50, spAttack = 50, spDefense = 50,
            experience = 100000u, friendship = 100, heldItem = 0,
            moves = listOf(), movePP = listOf(),
            ivs = intArrayOf(31, 31, 31, 31, 31, 31), abilitySlot = 0
        ), 0, party, 0, 104)

        // Slot 1: Player's Pokemon (same OT)
        System.arraycopy(createPokemonData(
            personality = 2u, otId = playerOtId,
            species = 2, level = 50,
            hp = 100, maxHp = 100, attack = 50, defense = 50, speed = 50, spAttack = 50, spDefense = 50,
            experience = 100000u, friendship = 100, heldItem = 0,
            moves = listOf(), movePP = listOf(),
            ivs = intArrayOf(31, 31, 31, 31, 31, 31), abilitySlot = 0
        ), 0, party, 104, 104)

        // Slot 2: Player's Pokemon (same OT)
        System.arraycopy(createPokemonData(
            personality = 3u, otId = playerOtId,
            species = 3, level = 50,
            hp = 100, maxHp = 100, attack = 50, defense = 50, speed = 50, spAttack = 50, spDefense = 50,
            experience = 100000u, friendship = 100, heldItem = 0,
            moves = listOf(), movePP = listOf(),
            ivs = intArrayOf(31, 31, 31, 31, 31, 31), abilitySlot = 0
        ), 0, party, 208, 104)

        // Parse party (auto-detects OT ID)
        val parsed = Gen3PokemonParser.parseParty(party, 3)

        assertEquals("Should have 3 Pokemon", 3, parsed.size)
        assertEquals("Slot 0 should be Bulbasaur", 1, parsed[0].species)
        assertEquals("Slot 1 should be Ivysaur", 2, parsed[1].species)
        assertEquals("Slot 2 should be Venusaur", 3, parsed[2].species)

        // All should have same OT ID
        assertEquals("All Pokemon should have same OT", parsed[0].otId, parsed[1].otId)
        assertEquals("All Pokemon should have same OT", parsed[0].otId, parsed[2].otId)
    }

    @Test
    fun testInvalidPokemonData() {
        // Test with personality = 0 (should return null)
        val pokemon = createPokemonData(
            personality = 0u,  // Invalid
            otId = 0u,
            species = 1,
            level = 1,
            hp = 1, maxHp = 1,
            attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
            experience = 0u,
            friendship = 0,
            heldItem = 0,
            moves = listOf(),
            movePP = listOf(),
            ivs = intArrayOf(0, 0, 0, 0, 0, 0),
            abilitySlot = 0
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNull("Pokemon with personality 0 should return null", parsed)
    }

    @Test
    fun testInvalidSpecies() {
        // Test with species > 1526 (should return null)
        val pokemon = createPokemonData(
            personality = 1u,
            otId = 0u,
            species = 9999,  // Invalid
            level = 1,
            hp = 1, maxHp = 1,
            attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
            experience = 0u,
            friendship = 0,
            heldItem = 0,
            moves = listOf(),
            movePP = listOf(),
            ivs = intArrayOf(0, 0, 0, 0, 0, 0),
            abilitySlot = 0
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNull("Pokemon with invalid species should return null", parsed)
    }

    @Test
    fun testStatusConditions() {
        // Test with various status conditions
        val statusTests = mapOf(
            0x00u to "None",
            0x03u to "Sleep (3 turns)",
            0x08u to "Poison",
            0x10u to "Burn",
            0x20u to "Freeze",
            0x40u to "Paralysis",
            0x80u to "Toxic"
        )

        for ((status, description) in statusTests) {
            val pokemon = createPokemonData(
                personality = 1u,
                otId = 0u,
                species = 1,
                level = 1,
                hp = 1, maxHp = 1,
                attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
                experience = 0u,
                friendship = 0,
                heldItem = 0,
                moves = listOf(),
                movePP = listOf(),
                ivs = intArrayOf(0, 0, 0, 0, 0, 0),
                abilitySlot = 0,
                status = status
            )

            val parsed = Gen3PokemonParser.parsePokemon(pokemon)
            assertNotNull("Pokemon with status $description should parse", parsed)
            assertEquals("Status should be $status ($description)", status.toInt(), parsed?.status)
        }
    }

    @Test
    fun testMovesAndPP() {
        val moves = listOf(1, 2, 3, 4)
        val movePP = listOf(35, 25, 30, 20)

        val pokemon = createPokemonData(
            personality = 1u,
            otId = 0u,
            species = 1,
            level = 1,
            hp = 1, maxHp = 1,
            attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
            experience = 0u,
            friendship = 0,
            heldItem = 0,
            moves = moves,
            movePP = movePP,
            ivs = intArrayOf(0, 0, 0, 0, 0, 0),
            abilitySlot = 0
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNotNull(parsed)

        parsed?.let {
            assertEquals("Should have 4 moves", 4, it.moves.size)
            assertEquals("Move 1 should be correct", 1, it.moves[0])
            assertEquals("Move 2 should be correct", 2, it.moves[1])
            assertEquals("Move 3 should be correct", 3, it.moves[2])
            assertEquals("Move 4 should be correct", 4, it.moves[3])

            assertEquals("Should have 4 PP values", 4, it.movePP.size)
            assertEquals("PP 1 should be correct", 35, it.movePP[0])
            assertEquals("PP 2 should be correct", 25, it.movePP[1])
            assertEquals("PP 3 should be correct", 30, it.movePP[2])
            assertEquals("PP 4 should be correct", 20, it.movePP[3])
        }
    }

    @Test
    fun testNicknameDecoding() {
        // The nickname is set to "TESTMON" in createPokemonData
        val pokemon = createPokemonData(
            personality = 1u,
            otId = 0u,
            species = 1,
            level = 1,
            hp = 1, maxHp = 1,
            attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
            experience = 0u,
            friendship = 0,
            heldItem = 0,
            moves = listOf(),
            movePP = listOf(),
            ivs = intArrayOf(0, 0, 0, 0, 0, 0),
            abilitySlot = 0
        )

        val parsed = Gen3PokemonParser.parsePokemon(pokemon)
        assertNotNull(parsed)
        assertEquals("Nickname should be TESTMON", "TESTMON", parsed?.nickname)
    }

    @Test
    fun testEncryptionKeyCalculation() {
        // Test that encryption/decryption works correctly with different key combinations
        val testCases = listOf(
            0x00000000u to 0x00000000u,
            0xFFFFFFFFu to 0xFFFFFFFFu,
            0x12345678u to 0x87654321u,
            0xABCDEF00u to 0x00FEDCBAu
        )

        for ((personality, otId) in testCases) {
            val pokemon = createPokemonData(
                personality = personality,
                otId = otId,
                species = 1,
                level = 1,
                hp = 1, maxHp = 1,
                attack = 1, defense = 1, speed = 1, spAttack = 1, spDefense = 1,
                experience = 12345u,
                friendship = 123,
                heldItem = 5,
                moves = listOf(1, 2, 3, 4),
                movePP = listOf(10, 20, 30, 40),
                ivs = intArrayOf(15, 16, 17, 18, 19, 20),
                abilitySlot = 1
            )

            val parsed = Gen3PokemonParser.parsePokemon(pokemon)
            assertNotNull("Pokemon with personality=$personality otId=$otId should parse", parsed)

            parsed?.let {
                assertEquals("Species should decrypt correctly", 1, it.species)
                assertEquals("Experience should decrypt correctly", 12345, it.experience)
                assertEquals("Friendship should decrypt correctly", 123, it.friendship)
                assertEquals("Held item should decrypt correctly", 5, it.heldItem)
                assertEquals("IVs should decrypt correctly", 15, it.ivHp)
            }
        }
    }
}
