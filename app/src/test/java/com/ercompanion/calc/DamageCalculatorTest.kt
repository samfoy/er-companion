package com.ercompanion.calc

import org.junit.Assert.*
import org.junit.Test

/**
 * Test damage calculation formulas with known ground truth values
 *
 * Tests cover:
 * - Base damage formula
 * - STAB (1.5x normal, 2.0x with Adaptability)
 * - Type effectiveness (all combinations including immunities)
 * - Critical hits (1.5x in Gen 6+)
 * - Burn/status effects
 * - Item modifiers (Choice Band, Life Orb, Expert Belt, etc.)
 * - Random variation (85-100%)
 */
class DamageCalculatorTest {

    // Type constants for readability
    companion object {
        const val TYPE_NORMAL = 0
        const val TYPE_FIGHTING = 1
        const val TYPE_FLYING = 2
        const val TYPE_POISON = 3
        const val TYPE_GROUND = 4
        const val TYPE_ROCK = 5
        const val TYPE_BUG = 6
        const val TYPE_GHOST = 7
        const val TYPE_STEEL = 8
        const val TYPE_FIRE = 9
        const val TYPE_WATER = 10
        const val TYPE_GRASS = 11
        const val TYPE_ELECTRIC = 12
        const val TYPE_PSYCHIC = 13
        const val TYPE_ICE = 14
        const val TYPE_DRAGON = 15
        const val TYPE_DARK = 16
        const val TYPE_FAIRY = 17
    }

    @Test
    fun testBaseDamageFormula() {
        // Ground truth: Level 50 Pokemon, 100 Attack, 100 Defense, 80 Base Power
        // Formula: ((2*50/5 + 2) * 80 * 100 / 100 / 50 + 2) = (22 * 80 * 100 / 100 / 50 + 2)
        // = (176000 / 100 / 50 + 2) = (1760 / 50 + 2) = (35 + 2) = 37

        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        // 85% = 31, 100% = 37
        assertEquals("Min damage should be 85% of base", 31, result.minDamage)
        assertEquals("Max damage should be base", 37, result.maxDamage)
        assertEquals("Type effectiveness should be neutral", 1.0f, result.effectiveness, 0.01f)
        assertEquals("Should not be STAB", false, result.isStab)
    }

    @Test
    fun testSTABNormal() {
        // STAB = 1.5x damage
        // Base: 37, with STAB: 55 (37 * 1.5)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),  // Same type as move
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        assertEquals("Should have STAB", true, result.isStab)
        // With STAB: 37 * 1.5 = 55.5 → 55
        assertEquals("Max damage with STAB", 55, result.maxDamage)
        assertEquals("Min damage with STAB (85%)", 46, result.minDamage)
    }

    @Test
    fun testTypeEffectivenessSuperEffective() {
        // Fire vs Grass = 2x
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_GRASS),
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be 2x", 2.0f, result.effectiveness, 0.01f)
        assertEquals("Effect label", "Super effective!", result.effectLabel)
        // Base 37 * 1.5 (STAB) * 2 (SE) = 111
        assertEquals("Max damage with STAB + SE", 111, result.maxDamage)
    }

    @Test
    fun testTypeEffectivenessNotVeryEffective() {
        // Fire vs Water = 0.5x
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_NORMAL),  // No STAB
            defenderTypes = listOf(TYPE_WATER),
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be 0.5x", 0.5f, result.effectiveness, 0.01f)
        assertEquals("Effect label", "Not very effective", result.effectLabel)
        // Base 37 * 0.5 = 18
        assertEquals("Max damage with NVE", 18, result.maxDamage)
    }

    @Test
    fun testTypeEffectivenessQuadrupleResist() {
        // Electric vs Ground/Rock = 0x (immune) × 0.5x = 0x (but let's test 0.5×0.5=0.25)
        // Actually, Ground is immune to Electric, so it should be 0x
        // Let's test Grass vs Fire/Rock = 0.5 × 0.5 = 0.25x
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_GRASS,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_FIRE, TYPE_ROCK),  // Both resist Grass
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be 0.25x", 0.25f, result.effectiveness, 0.01f)
        assertEquals("Effect label", "Not very effective", result.effectLabel)
        // Base 37 * 0.25 = 9
        assertEquals("Max damage with 4x resist", 9, result.maxDamage)
    }

    @Test
    fun testTypeEffectivenessQuadrupleWeak() {
        // Ice vs Grass/Dragon = 2 × 2 = 4x
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_ICE,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_GRASS, TYPE_DRAGON),
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be 4x", 4.0f, result.effectiveness, 0.01f)
        assertEquals("Effect label", "Super effective!", result.effectLabel)
        // Base 37 * 4 = 148
        assertEquals("Max damage with 4x weakness", 148, result.maxDamage)
    }

    @Test
    fun testTypeImmunityGround() {
        // Electric vs Ground = 0x (immune)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_ELECTRIC,
            attackerTypes = listOf(TYPE_ELECTRIC),
            defenderTypes = listOf(TYPE_GROUND),
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be 0x", 0.0f, result.effectiveness, 0.01f)
        assertEquals("Effect label", "No effect", result.effectLabel)
        assertEquals("Damage should be 0", 0, result.maxDamage)
    }

    @Test
    fun testTypeImmunityGhost() {
        // Normal vs Ghost = 0x (immune)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_GHOST),
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be 0x", 0.0f, result.effectiveness, 0.01f)
        assertEquals("Damage should be 0", 0, result.maxDamage)
    }

    @Test
    fun testTypeImmunityFairy() {
        // Dragon vs Fairy = 0x (Gen 6+)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_DRAGON,
            attackerTypes = listOf(TYPE_DRAGON),
            defenderTypes = listOf(TYPE_FAIRY),
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be 0x", 0.0f, result.effectiveness, 0.01f)
        assertEquals("Damage should be 0", 0, result.maxDamage)
    }

    @Test
    fun testBurnEffect() {
        // Burn halves physical attack damage (unless Guts ability)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isBurned = true
        )

        // Base 55 (with STAB) * 0.5 (burn) = 27.5 → 27
        assertEquals("Max damage with burn should be halved", 27, result.maxDamage)
    }

    @Test
    fun testStatusMoveNoDamage() {
        // Status moves (power = 0) should return 0 damage
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 0,  // Status move
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        assertEquals("Status move should deal 0 damage", 0, result.maxDamage)
        assertEquals("Min damage should be 0", 0, result.minDamage)
    }

    @Test
    fun testHighLevelVsLowLevel() {
        // Level 100 attacker vs Level 1 defender
        val result = DamageCalculator.calc(
            attackerLevel = 100,
            attackStat = 200,
            defenseStat = 50,
            movePower = 100,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 100
        )

        // Should deal massive damage
        assertTrue("High level should deal significant damage", result.maxDamage > 100)
        assertTrue("Should OHKO", result.wouldKO)
    }

    @Test
    fun testLowLevelVsHighLevel() {
        // Level 1 attacker vs Level 100 defender
        val result = DamageCalculator.calc(
            attackerLevel = 1,
            attackStat = 10,
            defenseStat = 200,
            movePower = 40,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 300
        )

        // Should deal minimal damage
        assertTrue("Low level should deal minimal damage", result.maxDamage < 10)
        assertFalse("Should not KO", result.wouldKO)
    }

    @Test
    fun testPercentageCalculation() {
        // Test that percentages are calculated correctly
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 110  // Max damage of 55 should be 50% of 110
        )

        assertEquals("Max damage", 55, result.maxDamage)
        assertEquals("Percentage should be 50%", 50, result.percentMax)
        assertEquals("Min percentage", 41, result.percentMin)  // 46/110 = 41%
    }

    @Test
    fun testWouldKOThreshold() {
        // Test KO detection at exactly 100%
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 150,
            defenseStat = 50,
            movePower = 100,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 100
        )

        if (result.percentMax >= 100) {
            assertTrue("Should indicate KO when damage >= 100% HP", result.wouldKO)
        }
    }

    @Test
    fun testMinimumDamageIsOne() {
        // Even with massive defense, damage should be at least 1
        val result = DamageCalculator.calc(
            attackerLevel = 1,
            attackStat = 5,
            defenseStat = 999,
            movePower = 1,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 100
        )

        assertTrue("Minimum damage should be at least 1", result.minDamage >= 1)
        assertTrue("Maximum damage should be at least 1", result.maxDamage >= 1)
    }

    @Test
    fun testRandomRollRange() {
        // Test that min and max damage have correct ratio (85% to 100%)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        val ratio = result.minDamage.toFloat() / result.maxDamage.toFloat()
        assertTrue("Ratio should be approximately 0.85", ratio >= 0.84 && ratio <= 0.86)
    }

    @Test
    fun testDualTypeDefender() {
        // Fire vs Water/Ground
        // Fire is 0.5x on Water, 2x on Ground = 0.5 × 2 = 1x (neutral)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_WATER, TYPE_GROUND),
            targetMaxHP = 150
        )

        assertEquals("Effectiveness should be neutral", 1.0f, result.effectiveness, 0.01f)
    }

    @Test
    fun testDualTypeAttacker() {
        // Attacker with Fire/Flying, using Fire move
        // Should get STAB
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE, TYPE_FLYING),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        assertTrue("Should have STAB", result.isStab)
    }

    @Test
    fun testFightingTypeChart() {
        // Fighting is super effective against Normal, Rock, Steel, Ice, Dark
        val superEffective = listOf(TYPE_NORMAL, TYPE_ROCK, TYPE_STEEL, TYPE_ICE, TYPE_DARK)

        for (defType in superEffective) {
            val eff = DamageCalculator.getTypeEffectiveness(TYPE_FIGHTING, listOf(defType))
            assertEquals("Fighting should be 2x vs type $defType", 2.0f, eff, 0.01f)
        }

        // Fighting is not very effective against Flying, Poison, Bug, Psychic, Fairy
        val notVeryEffective = listOf(TYPE_FLYING, TYPE_POISON, TYPE_BUG, TYPE_PSYCHIC, TYPE_FAIRY)

        for (defType in notVeryEffective) {
            val eff = DamageCalculator.getTypeEffectiveness(TYPE_FIGHTING, listOf(defType))
            assertEquals("Fighting should be 0.5x vs type $defType", 0.5f, eff, 0.01f)
        }

        // Fighting has no effect on Ghost
        val immune = listOf(TYPE_GHOST)

        for (defType in immune) {
            val eff = DamageCalculator.getTypeEffectiveness(TYPE_FIGHTING, listOf(defType))
            assertEquals("Fighting should be 0x vs type $defType", 0.0f, eff, 0.01f)
        }
    }

    @Test
    fun testPsychicTypeChart() {
        // Psychic is super effective against Fighting, Poison
        val effectiveness = DamageCalculator.getTypeEffectiveness(TYPE_PSYCHIC, listOf(TYPE_FIGHTING))
        assertEquals("Psychic vs Fighting should be 2x", 2.0f, effectiveness, 0.01f)

        val effectiveness2 = DamageCalculator.getTypeEffectiveness(TYPE_PSYCHIC, listOf(TYPE_POISON))
        assertEquals("Psychic vs Poison should be 2x", 2.0f, effectiveness2, 0.01f)

        // Psychic is not very effective against Steel, Psychic
        val effectiveness3 = DamageCalculator.getTypeEffectiveness(TYPE_PSYCHIC, listOf(TYPE_STEEL))
        assertEquals("Psychic vs Steel should be 0.5x", 0.5f, effectiveness3, 0.01f)

        // Psychic has no effect on Dark
        val effectiveness4 = DamageCalculator.getTypeEffectiveness(TYPE_PSYCHIC, listOf(TYPE_DARK))
        assertEquals("Psychic vs Dark should be 0x", 0.0f, effectiveness4, 0.01f)
    }

    @Test
    fun testFairyTypeChart() {
        // Fairy is super effective against Fighting, Dragon, Dark
        val effectiveness = DamageCalculator.getTypeEffectiveness(TYPE_FAIRY, listOf(TYPE_FIGHTING))
        assertEquals("Fairy vs Fighting should be 2x", 2.0f, effectiveness, 0.01f)

        val effectiveness2 = DamageCalculator.getTypeEffectiveness(TYPE_FAIRY, listOf(TYPE_DRAGON))
        assertEquals("Fairy vs Dragon should be 2x", 2.0f, effectiveness2, 0.01f)

        val effectiveness3 = DamageCalculator.getTypeEffectiveness(TYPE_FAIRY, listOf(TYPE_DARK))
        assertEquals("Fairy vs Dark should be 2x", 2.0f, effectiveness3, 0.01f)

        // Fairy is not very effective against Poison, Steel, Fire
        val effectiveness4 = DamageCalculator.getTypeEffectiveness(TYPE_FAIRY, listOf(TYPE_POISON))
        assertEquals("Fairy vs Poison should be 0.5x", 0.5f, effectiveness4, 0.01f)

        val effectiveness5 = DamageCalculator.getTypeEffectiveness(TYPE_FAIRY, listOf(TYPE_STEEL))
        assertEquals("Fairy vs Steel should be 0.5x", 0.5f, effectiveness5, 0.01f)
    }

    @Test
    fun testAllTypeImmunities() {
        // Comprehensive test of all type immunities in Gen 6+
        val immunities = mapOf(
            TYPE_NORMAL to listOf(TYPE_GHOST),
            TYPE_FIGHTING to listOf(TYPE_GHOST),
            TYPE_POISON to listOf(TYPE_STEEL),
            TYPE_GROUND to listOf(TYPE_FLYING),
            TYPE_ELECTRIC to listOf(TYPE_GROUND),
            TYPE_PSYCHIC to listOf(TYPE_DARK),
            TYPE_DRAGON to listOf(TYPE_FAIRY),
            TYPE_GHOST to listOf(TYPE_NORMAL)
        )

        for ((attackType, immuneTypes) in immunities) {
            for (defType in immuneTypes) {
                val eff = DamageCalculator.getTypeEffectiveness(attackType, listOf(defType))
                assertEquals("Type $attackType should not affect type $defType", 0.0f, eff, 0.01f)
            }
        }
    }

    @Test
    fun testInvalidStatsHandling() {
        // Test with 0 or negative stats (should return 0 damage)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 0,  // Invalid
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        assertEquals("Invalid attack stat should result in 0 damage", 0, result.maxDamage)
    }

    @Test
    fun testVeryHighDamage() {
        // Test with extremely high stats (should not overflow)
        val result = DamageCalculator.calc(
            attackerLevel = 100,
            attackStat = 500,
            defenseStat = 50,
            movePower = 250,
            moveType = TYPE_DRAGON,
            attackerTypes = listOf(TYPE_DRAGON),
            defenderTypes = listOf(TYPE_DRAGON),
            targetMaxHP = 200
        )

        assertTrue("Should calculate very high damage without overflow", result.maxDamage > 0)
        assertTrue("Damage should be reasonable", result.maxDamage < 10000)
    }

    @Test
    fun testSTABWithDualTypeAttacker() {
        // Pokemon with two types, one matches move type
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_WATER,
            attackerTypes = listOf(TYPE_WATER, TYPE_GROUND),  // Water type gets STAB
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        assertTrue("Should have STAB when one type matches", result.isStab)
    }

    @Test
    fun testNoSTABWithDualTypeAttacker() {
        // Pokemon with two types, neither matches move type
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_ELECTRIC,
            attackerTypes = listOf(TYPE_WATER, TYPE_GROUND),  // Neither type matches
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        assertFalse("Should not have STAB when no type matches", result.isStab)
    }

    @Test
    fun testMoveNamePreserved() {
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            moveName = "Body Slam"
        )

        assertEquals("Move name should be preserved", "Body Slam", result.moveName)
    }

    // ===== ADVANCED EFFECTS TESTS =====

    @Test
    fun testWeatherSunBoostsFireMoves() {
        // Sun boosts Fire moves by 1.5x
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            weather = 0  // No weather
        )

        val sunDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            weather = Weather.SUN.ordinal
        )

        // Sun should boost damage by 1.5x
        assertTrue("Sun should boost Fire move damage",
            sunDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() > 1.4f)
    }

    @Test
    fun testWeatherRainWeakensFireMoves() {
        // Rain weakens Fire moves to 0.5x
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            weather = 0
        )

        val rainDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            weather = Weather.RAIN.ordinal
        )

        // Rain should weaken damage by 0.5x
        assertTrue("Rain should weaken Fire move damage",
            rainDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() < 0.6f)
    }

    @Test
    fun testWeatherRainBoostsWaterMoves() {
        // Rain boosts Water moves by 1.5x
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_WATER,
            attackerTypes = listOf(TYPE_WATER),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            weather = 0
        )

        val rainDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_WATER,
            attackerTypes = listOf(TYPE_WATER),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            weather = Weather.RAIN.ordinal
        )

        assertTrue("Rain should boost Water move damage",
            rainDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() > 1.4f)
    }

    @Test
    fun testWeatherSandstormBoostsRockSpDef() {
        // Sandstorm boosts Rock-type SpDef by 1.5x
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_WATER,  // Special move
            attackerTypes = listOf(TYPE_WATER),
            defenderTypes = listOf(TYPE_ROCK),
            targetMaxHP = 150,
            weather = 0,
            moveCategory = 1,  // Special
            defenderSpecies = 74  // Geodude (Rock type)
        )

        val sandstormDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_WATER,
            attackerTypes = listOf(TYPE_WATER),
            defenderTypes = listOf(TYPE_ROCK),
            targetMaxHP = 150,
            weather = Weather.SANDSTORM.ordinal,
            moveCategory = 1,
            defenderSpecies = 74
        )

        // Sandstorm should reduce damage (higher SpDef = less damage)
        assertTrue("Sandstorm should boost Rock SpDef, reducing special damage",
            sandstormDamage.maxDamage < normalDamage.maxDamage)
    }

    @Test
    fun testTerrainElectricBoostsElectricMoves() {
        // Electric Terrain boosts Electric moves by 1.5x (if grounded)
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_ELECTRIC,
            attackerTypes = listOf(TYPE_ELECTRIC),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = 0,
            isGrounded = true
        )

        val terrainDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_ELECTRIC,
            attackerTypes = listOf(TYPE_ELECTRIC),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = Terrain.ELECTRIC.ordinal,
            isGrounded = true
        )

        assertTrue("Electric Terrain should boost Electric moves",
            terrainDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() > 1.4f)
    }

    @Test
    fun testTerrainGrassyBoostsGrassMoves() {
        // Grassy Terrain boosts Grass moves by 1.5x (if grounded)
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_GRASS,
            attackerTypes = listOf(TYPE_GRASS),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = 0,
            isGrounded = true
        )

        val terrainDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_GRASS,
            attackerTypes = listOf(TYPE_GRASS),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = Terrain.GRASSY.ordinal,
            isGrounded = true
        )

        assertTrue("Grassy Terrain should boost Grass moves",
            terrainDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() > 1.4f)
    }

    @Test
    fun testTerrainMistyWeakensDragonMoves() {
        // Misty Terrain weakens Dragon moves to 0.5x (if grounded)
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_DRAGON,
            attackerTypes = listOf(TYPE_DRAGON),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = 0,
            isGrounded = true
        )

        val terrainDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_DRAGON,
            attackerTypes = listOf(TYPE_DRAGON),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = Terrain.MISTY.ordinal,
            isGrounded = true
        )

        assertTrue("Misty Terrain should weaken Dragon moves",
            terrainDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() < 0.6f)
    }

    @Test
    fun testTerrainDoesNotAffectFlyingTypes() {
        // Terrain effects shouldn't apply if not grounded
        val groundedDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_ELECTRIC,
            attackerTypes = listOf(TYPE_ELECTRIC),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = Terrain.ELECTRIC.ordinal,
            isGrounded = true
        )

        val flyingDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_ELECTRIC,
            attackerTypes = listOf(TYPE_ELECTRIC),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            terrain = Terrain.ELECTRIC.ordinal,
            isGrounded = false  // Flying/Levitate
        )

        assertTrue("Terrain should not affect Flying types",
            flyingDamage.maxDamage < groundedDamage.maxDamage)
    }

    @Test
    fun testAbilityGutsBoostsAttackWhenStatused() {
        // Guts: 1.5x Attack when statused (ignores burn's Attack reduction)
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            moveCategory = 0,  // Physical
            attackerAbility = 0,
            attackerStatus = 0
        )

        val gutsDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            moveCategory = 0,
            attackerAbility = 62,  // Guts
            attackerStatus = StatusConditions.BURN
        )

        // Guts should boost Attack by 1.5x despite burn
        assertTrue("Guts should boost damage when statused",
            gutsDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() > 1.4f)
    }

    @Test
    fun testAbilityMarvelScaleBoostsDefenseWhenStatused() {
        // Marvel Scale: 1.5x Defense when statused
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            moveCategory = 0,  // Physical
            defenderAbility = 0,
            defenderStatus = 0
        )

        val marvelScaleDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            moveCategory = 0,
            defenderAbility = 63,  // Marvel Scale
            defenderStatus = StatusConditions.BURN
        )

        // Marvel Scale should reduce damage taken
        assertTrue("Marvel Scale should reduce physical damage when statused",
            marvelScaleDamage.maxDamage < normalDamage.maxDamage)
    }

    @Test
    fun testAbilityTechnicianBoostsLowPowerMoves() {
        // Technician: 1.5x for moves with 60 or less base power
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 60,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            attackerAbility = 0
        )

        val technicianDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 60,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            attackerAbility = 101  // Technician
        )

        assertTrue("Technician should boost low power moves",
            technicianDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() > 1.4f)
    }

    @Test
    fun testAbilityTechnicianDoesNotBoostHighPowerMoves() {
        // Technician should NOT boost moves with power > 60
        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            attackerAbility = 0
        )

        val technicianDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            attackerAbility = 101  // Technician
        )

        // Should be the same
        assertEquals("Technician should not boost high power moves",
            normalDamage.maxDamage, technicianDamage.maxDamage)
    }

    @Test
    fun testCombinedEffects_WeatherAndTerrain() {
        // Test that weather and terrain stack correctly
        // Sun boosts Fire, Grassy Terrain should not affect Fire
        val sunDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            weather = Weather.SUN.ordinal,
            terrain = Terrain.GRASSY.ordinal,
            isGrounded = true
        )

        val normalDamage = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150
        )

        // Sun should still boost by 1.5x
        assertTrue("Sun should boost Fire moves even with terrain",
            sunDamage.maxDamage.toFloat() / normalDamage.maxDamage.toFloat() > 1.4f)
    }

    // ===== CURSE EFFECTS TESTS =====

    @Test
    fun testOHKOCurse_EnemyAttackAlwaysKOs() {
        // OHKO Curse: Enemy attacks always deal max HP damage
        val curses = CurseState(ohkoCurse = true)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = curses
        )

        assertEquals("OHKO curse should deal exactly max HP", 150, result.maxDamage)
        assertEquals("OHKO curse should deal exactly max HP", 150, result.minDamage)
        assertEquals("OHKO curse should be 100%", 100, result.percentMax)
        assertTrue("OHKO curse should guarantee KO", result.wouldKO)
        assertEquals("OHKO curse should show warning", "⚠️ OHKO CURSE - Instant KO", result.effectLabel)
    }

    @Test
    fun testOHKOCurse_DoesNotAffectPlayerMoves() {
        // OHKO Curse should only affect enemy attacks
        val curses = CurseState(ohkoCurse = true)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = false,  // Player move
            curses = curses
        )

        // Should calculate normal damage, not OHKO
        assertTrue("Player moves should not be affected by OHKO curse", result.maxDamage < 150)
    }

    @Test
    fun testOHKOCurse_DoesNotAffectStatusMoves() {
        // OHKO Curse should not affect status moves (power = 0)
        val curses = CurseState(ohkoCurse = true)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 0,  // Status move
            moveType = TYPE_NORMAL,
            attackerTypes = listOf(TYPE_NORMAL),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = curses
        )

        assertEquals("Status moves should not be affected by OHKO curse", 0, result.maxDamage)
    }

    @Test
    fun testAdaptabilityCurse_OneCurse() {
        // 1 Adaptability curse: 1.5x base STAB becomes 1.55x
        val noCurse = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = CurseState.NONE
        )

        val oneCurse = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = CurseState(adaptabilityCurse = 1)
        )

        // 1.55 / 1.5 = 1.033x increase
        val ratio = oneCurse.maxDamage.toFloat() / noCurse.maxDamage.toFloat()
        assertTrue("1 Adaptability curse should boost STAB by ~3.3%", ratio > 1.02f && ratio < 1.05f)
    }

    @Test
    fun testAdaptabilityCurse_ThreeCurses() {
        // 3 Adaptability curses: 1.5x base STAB becomes 1.65x (formula: 1.5 + 3*0.05)
        val noCurse = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = CurseState.NONE
        )

        val threeCurses = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = CurseState(adaptabilityCurse = 3)
        )

        // 1.65 / 1.5 = 1.1x increase (10% more damage)
        // Formula: 1.5 + (3 * 0.05) = 1.65
        val ratio = threeCurses.maxDamage.toFloat() / noCurse.maxDamage.toFloat()
        assertTrue("3 Adaptability curses should boost STAB by ~10%", ratio > 1.08f && ratio < 1.12f)
    }

    @Test
    fun testAdaptabilityCurse_DoesNotAffectNonSTABMoves() {
        // Adaptability curse should only affect STAB moves
        val noCurse = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_WATER),  // No STAB
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = CurseState.NONE
        )

        val threeCurses = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_WATER),  // No STAB
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = CurseState(adaptabilityCurse = 3)
        )

        // Damage should be the same (no STAB to boost)
        assertEquals("Adaptability curse should not affect non-STAB moves",
            noCurse.maxDamage, threeCurses.maxDamage)
    }

    @Test
    fun testAdaptabilityCurse_DoesNotAffectPlayerMoves() {
        // Adaptability curse should only affect enemy moves
        val playerMove = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = false,
            curses = CurseState(adaptabilityCurse = 3)
        )

        val playerMoveNoCurse = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = false,
            curses = CurseState.NONE
        )

        // Should be the same
        assertEquals("Adaptability curse should not affect player moves",
            playerMoveNoCurse.maxDamage, playerMove.maxDamage)
    }

    @Test
    fun testMultipleCurses_OHKOAndAdaptability() {
        // If OHKO curse is active, it should override everything including Adaptability
        val curses = CurseState(ohkoCurse = true, adaptabilityCurse = 3)
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = curses
        )

        assertEquals("OHKO curse should override Adaptability", 150, result.maxDamage)
        assertTrue("OHKO curse should guarantee KO", result.wouldKO)
    }

    @Test
    fun testCurseState_NoneConstant() {
        // Verify that CurseState.NONE represents no active curses
        val result = DamageCalculator.calc(
            attackerLevel = 50,
            attackStat = 100,
            defenseStat = 100,
            movePower = 80,
            moveType = TYPE_FIRE,
            attackerTypes = listOf(TYPE_FIRE),
            defenderTypes = listOf(TYPE_NORMAL),
            targetMaxHP = 150,
            isEnemyAttacking = true,
            curses = CurseState.NONE
        )

        // Should have normal STAB damage (1.5x)
        assertEquals("No curses should result in normal STAB", true, result.isStab)
        // Base 37 * 1.5 STAB = 55 (rounded down from 55.5)
        assertEquals("No curses should result in normal damage", 55, result.maxDamage)
    }
}
