package com.ercompanion.calc

import com.ercompanion.data.MoveData
import com.ercompanion.parser.PartyMon
import org.junit.Assert.*
import org.junit.Test

class ItemEffectsTest {

    // Item ID constants (matching ItemEffects.kt)
    private val CHOICE_BAND = 222
    private val CHOICE_SPECS = 223
    private val CHOICE_SCARF = 224
    private val LIFE_ORB = 225
    private val EXPERT_BELT = 226
    private val MUSCLE_BAND = 227
    private val WISE_GLASSES = 228
    private val LEFTOVERS = 234
    private val FOCUS_SASH = 235
    private val FOCUS_BAND = 275
    private val BLACK_SLUDGE = 282

    // Helper function to create test Pokemon
    private fun createTestMon(
        species: Int = 6,  // Charizard
        level: Int = 50,
        maxHp: Int = 150,
        hp: Int = 150,
        attack: Int = 100,
        defense: Int = 80,
        spAttack: Int = 120,
        spDefense: Int = 85,
        speed: Int = 100,
        heldItem: Int = 0,
        ability: Int = 0
    ): PartyMon {
        return PartyMon(
            species = species,
            level = level,
            hp = hp,
            maxHp = maxHp,
            nickname = "Test",
            moves = listOf(33, 0, 0, 0),  // Tackle as default
            attack = attack,
            defense = defense,
            speed = speed,
            spAttack = spAttack,
            spDefense = spDefense,
            experience = 0,
            friendship = 0,
            heldItem = heldItem,
            ability = ability
        )
    }

    private fun createBattlerState(
        mon: PartyMon,
        currentHp: Int = mon.maxHp,
        status: Int = StatusConditions.NONE
    ): BattlerState {
        return BattlerState(
            mon = mon,
            currentHp = currentHp,
            statStages = StatStages(),
            status = status,
            tempBoosts = TempBoosts()
        )
    }

    @Test
    fun testIsChoiceItem() {
        assertTrue(ItemEffects.isChoiceItem(CHOICE_BAND))
        assertTrue(ItemEffects.isChoiceItem(CHOICE_SPECS))
        assertTrue(ItemEffects.isChoiceItem(CHOICE_SCARF))
        assertFalse(ItemEffects.isChoiceItem(LIFE_ORB))
        assertFalse(ItemEffects.isChoiceItem(LEFTOVERS))
        assertFalse(ItemEffects.isChoiceItem(0))
    }

    @Test
    fun testLifeOrbBoostsDamage() {
        val physicalMove = MoveData("Tackle", 40, 0, 0)  // Physical move
        val effectiveness = 1.0f

        val multiplier = ItemEffects.getDamageMultiplier(LIFE_ORB, physicalMove, effectiveness)
        assertEquals(1.3f, multiplier, 0.01f)
    }

    @Test
    fun testExpertBeltBoostsOnlySuperEffective() {
        val move = MoveData("Thunder", 110, 12, 1)  // Electric special move

        // Super effective (2x)
        val superEffective = ItemEffects.getDamageMultiplier(EXPERT_BELT, move, 2.0f)
        assertEquals(1.2f, superEffective, 0.01f)

        // Neutral
        val neutral = ItemEffects.getDamageMultiplier(EXPERT_BELT, move, 1.0f)
        assertEquals(1.0f, neutral, 0.01f)

        // Not very effective
        val notVeryEffective = ItemEffects.getDamageMultiplier(EXPERT_BELT, move, 0.5f)
        assertEquals(1.0f, notVeryEffective, 0.01f)
    }

    @Test
    fun testMuscleBandBoostsPhysicalOnly() {
        val physicalMove = MoveData("Tackle", 40, 0, 0)
        val specialMove = MoveData("Ember", 40, 9, 1)

        val physicalMultiplier = ItemEffects.getDamageMultiplier(MUSCLE_BAND, physicalMove, 1.0f)
        assertEquals(1.1f, physicalMultiplier, 0.01f)

        val specialMultiplier = ItemEffects.getDamageMultiplier(MUSCLE_BAND, specialMove, 1.0f)
        assertEquals(1.0f, specialMultiplier, 0.01f)
    }

    @Test
    fun testWiseGlassesBoostsSpecialOnly() {
        val physicalMove = MoveData("Tackle", 40, 0, 0)
        val specialMove = MoveData("Ember", 40, 9, 1)

        val physicalMultiplier = ItemEffects.getDamageMultiplier(WISE_GLASSES, physicalMove, 1.0f)
        assertEquals(1.0f, physicalMultiplier, 0.01f)

        val specialMultiplier = ItemEffects.getDamageMultiplier(WISE_GLASSES, specialMove, 1.0f)
        assertEquals(1.1f, specialMultiplier, 0.01f)
    }

    @Test
    fun testSpeedMultipliers() {
        assertEquals(1.5f, ItemEffects.getSpeedMultiplier(CHOICE_SCARF), 0.01f)
        assertEquals(0.5f, ItemEffects.getSpeedMultiplier(285), 0.01f)  // Iron Ball
        assertEquals(1.0f, ItemEffects.getSpeedMultiplier(LIFE_ORB), 0.01f)
        assertEquals(1.0f, ItemEffects.getSpeedMultiplier(0), 0.01f)
    }

    @Test
    fun testLifeOrbRecoil() {
        val testMon = createTestMon(maxHp = 200, hp = 200, heldItem = LIFE_ORB)
        val player = createBattlerState(testMon)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        // Apply Life Orb recoil
        val newState = ItemEffects.applyLifeOrbRecoil(state, isPlayer = true, moveUsed = true)

        // Should take 10% recoil (20 HP)
        val expectedRecoil = 20
        val actualRecoil = state.player.currentHp - newState.player.currentHp
        assertEquals(expectedRecoil, actualRecoil)
    }

    @Test
    fun testLifeOrbRecoilOnlyWhenMoveUsed() {
        val testMon = createTestMon(maxHp = 200, hp = 200, heldItem = LIFE_ORB)
        val player = createBattlerState(testMon)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        // Apply Life Orb recoil when no move was used
        val newState = ItemEffects.applyLifeOrbRecoil(state, isPlayer = true, moveUsed = false)

        // Should take no recoil
        assertEquals(state.player.currentHp, newState.player.currentHp)
    }

    @Test
    fun testLifeOrbRecoilWithoutLifeOrb() {
        val testMon = createTestMon(maxHp = 200, hp = 200, heldItem = LEFTOVERS)
        val player = createBattlerState(testMon)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        // Apply Life Orb recoil (but player doesn't have Life Orb)
        val newState = ItemEffects.applyLifeOrbRecoil(state, isPlayer = true, moveUsed = true)

        // Should take no recoil
        assertEquals(state.player.currentHp, newState.player.currentHp)
    }

    @Test
    fun testLeftoversHealing() {
        val testMon = createTestMon(maxHp = 160, hp = 100, heldItem = LEFTOVERS)
        val player = createBattlerState(testMon, currentHp = 100)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        // Apply Leftovers healing
        val newState = ItemEffects.applyEndOfTurnHealing(state, isPlayer = true)

        // Should heal 1/16 max HP (10 HP)
        val expectedHealing = 10
        val actualHealing = newState.player.currentHp - state.player.currentHp
        assertEquals(expectedHealing, actualHealing)
    }

    @Test
    fun testLeftoversDoesNotOverheal() {
        val testMon = createTestMon(maxHp = 160, hp = 155, heldItem = LEFTOVERS)
        val player = createBattlerState(testMon, currentHp = 155)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        // Apply Leftovers healing
        val newState = ItemEffects.applyEndOfTurnHealing(state, isPlayer = true)

        // Should heal to max HP, not beyond
        assertEquals(160, newState.player.currentHp)
    }

    @Test
    fun testBlackSludgeHealsPoisonTypes() {
        // Create Poison-type Pokemon (species 89 = Muk, Poison type)
        val testMon = createTestMon(species = 89, maxHp = 160, hp = 100, heldItem = BLACK_SLUDGE)
        val player = createBattlerState(testMon, currentHp = 100)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        // Apply Black Sludge healing
        val newState = ItemEffects.applyEndOfTurnHealing(state, isPlayer = true)

        // Should heal 1/16 max HP (10 HP)
        val expectedHealing = 10
        val actualHealing = newState.player.currentHp - state.player.currentHp
        assertEquals(expectedHealing, actualHealing)
    }

    @Test
    fun testBlackSludgeDamagesNonPoisonTypes() {
        // Create non-Poison Pokemon
        val testMon = createTestMon(species = 6, maxHp = 160, hp = 160, heldItem = BLACK_SLUDGE)
        val player = createBattlerState(testMon, currentHp = 160)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        // Apply Black Sludge "healing" (actually damage)
        val newState = ItemEffects.applyEndOfTurnHealing(state, isPlayer = true)

        // Should take 1/8 max HP damage (20 HP)
        val expectedDamage = 20
        val actualDamage = state.player.currentHp - newState.player.currentHp
        assertEquals(expectedDamage, actualDamage)
    }

    @Test
    fun testFocusSashPreventsKO() {
        val testMon = createTestMon(maxHp = 100, hp = 100, heldItem = FOCUS_SASH)
        val defender = createBattlerState(testMon, currentHp = 100)

        // Take massive damage that would KO
        val incomingDamage = 150
        val adjustedDamage = ItemEffects.checkFocusItem(defender, incomingDamage, wasFullHP = true)

        // Should survive at 1 HP (damage = 99)
        assertEquals(99, adjustedDamage)
    }

    @Test
    fun testFocusSashDoesNotActivateWhenNotFullHP() {
        val testMon = createTestMon(maxHp = 100, hp = 80, heldItem = FOCUS_SASH)
        val defender = createBattlerState(testMon, currentHp = 80)

        // Take fatal damage
        val incomingDamage = 100
        val adjustedDamage = ItemEffects.checkFocusItem(defender, incomingDamage, wasFullHP = false)

        // Should not activate (was not at full HP)
        assertEquals(100, adjustedDamage)
    }

    @Test
    fun testFocusSashDoesNotActivateOnNonLethalDamage() {
        val testMon = createTestMon(maxHp = 100, hp = 100, heldItem = FOCUS_SASH)
        val defender = createBattlerState(testMon, currentHp = 100)

        // Take non-lethal damage
        val incomingDamage = 50
        val adjustedDamage = ItemEffects.checkFocusItem(defender, incomingDamage, wasFullHP = true)

        // Should not modify damage
        assertEquals(50, adjustedDamage)
    }

    @Test
    fun testStatusOrbsActivateAfterFirstTurn() {
        val testMon = createTestMon(heldItem = 280)  // Flame Orb
        val player = createBattlerState(testMon)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy, turn = 1)

        // Apply status orb
        val newState = ItemEffects.applyStatusOrb(state, isPlayer = true, turnCount = 1)

        // Should be burned
        assertTrue(StatusConditions.isBurned(newState.player.status))
    }

    @Test
    fun testStatusOrbsDoNotActivateOnTurnZero() {
        val testMon = createTestMon(heldItem = 280)  // Flame Orb
        val player = createBattlerState(testMon)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy, turn = 0)

        // Apply status orb
        val newState = ItemEffects.applyStatusOrb(state, isPlayer = true, turnCount = 0)

        // Should not have status
        assertEquals(StatusConditions.NONE, newState.player.status)
    }

    @Test
    fun testToxicOrbInflictsToxic() {
        val testMon = createTestMon(heldItem = 281)  // Toxic Orb
        val player = createBattlerState(testMon)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy, turn = 1)

        // Apply status orb
        val newState = ItemEffects.applyStatusOrb(state, isPlayer = true, turnCount = 1)

        // Should be badly poisoned
        assertTrue(StatusConditions.isToxic(newState.player.status))
    }

    @Test
    fun testStatusOrbsDoNotOverwriteExistingStatus() {
        val testMon = createTestMon(heldItem = 280)  // Flame Orb
        val player = createBattlerState(testMon, status = StatusConditions.PARALYSIS)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy, turn = 1)

        // Apply status orb
        val newState = ItemEffects.applyStatusOrb(state, isPlayer = true, turnCount = 1)

        // Should still be paralyzed (not burned)
        assertTrue(StatusConditions.isParalyzed(newState.player.status))
        assertFalse(StatusConditions.isBurned(newState.player.status))
    }

    @Test
    fun testStrategicValueOfChoiceScarf() {
        val value = ItemEffects.getStrategicValue(CHOICE_SCARF, hpPercent = 0.8f, turnsElapsed = 2)
        assertEquals(2.0f, value, 0.01f)  // High value for speed control
    }

    @Test
    fun testStrategicValueOfLifeOrbDecreasesWithUse() {
        val initialValue = ItemEffects.getStrategicValue(LIFE_ORB, hpPercent = 1.0f, turnsElapsed = 0)
        val afterUse = ItemEffects.getStrategicValue(LIFE_ORB, hpPercent = 0.7f, turnsElapsed = 3)

        assertTrue(initialValue > afterUse)
    }

    @Test
    fun testStrategicValueOfLifeOrbNegativeWhenLowHP() {
        val value = ItemEffects.getStrategicValue(LIFE_ORB, hpPercent = 0.15f, turnsElapsed = 5)
        assertTrue(value < 0)  // Dangerous when low HP
    }

    @Test
    fun testStrategicValueOfLeftoversIncreasesOverTime() {
        val earlyValue = ItemEffects.getStrategicValue(LEFTOVERS, hpPercent = 0.8f, turnsElapsed = 1)
        val lateValue = ItemEffects.getStrategicValue(LEFTOVERS, hpPercent = 0.8f, turnsElapsed = 10)

        assertTrue(lateValue > earlyValue)
    }

    @Test
    fun testStrategicValueOfFocusSashOnlyAtFullHP() {
        val fullHP = ItemEffects.getStrategicValue(FOCUS_SASH, hpPercent = 1.0f, turnsElapsed = 0)
        val damaged = ItemEffects.getStrategicValue(FOCUS_SASH, hpPercent = 0.99f, turnsElapsed = 0)

        assertEquals(2.5f, fullHP, 0.01f)
        assertEquals(0.0f, damaged, 0.01f)
    }

    @Test
    fun testItemWarningForLifeOrbKO() {
        val testMon = createTestMon(maxHp = 100, hp = 8, heldItem = LIFE_ORB)
        val player = createBattlerState(testMon, currentHp = 8)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        val warning = ItemEffects.getItemWarning(LIFE_ORB, state, isPlayer = true)
        assertNotNull(warning)
        assertTrue(warning!!.contains("KO"))
    }

    @Test
    fun testItemWarningForFocusSashInactive() {
        val testMon = createTestMon(maxHp = 100, hp = 80, heldItem = FOCUS_SASH)
        val player = createBattlerState(testMon, currentHp = 80)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        val warning = ItemEffects.getItemWarning(FOCUS_SASH, state, isPlayer = true)
        assertNotNull(warning)
        assertTrue(warning!!.contains("inactive"))
    }

    @Test
    fun testItemWarningForBlackSludgeOnNonPoison() {
        val testMon = createTestMon(species = 6, heldItem = BLACK_SLUDGE)  // Charizard (Fire/Flying)
        val player = createBattlerState(testMon)
        val enemy = createBattlerState(createTestMon())

        val state = BattleState(player, enemy)

        val warning = ItemEffects.getItemWarning(BLACK_SLUDGE, state, isPlayer = true)
        assertNotNull(warning)
        assertTrue(warning!!.contains("damages"))
    }
}
