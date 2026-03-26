package com.ercompanion.calc

import com.ercompanion.parser.PartyMon
import org.junit.Assert.*
import org.junit.Test

class OptimalLineCalculatorTest {

    /**
     * Create a test PartyMon with given stats and moves.
     */
    private fun createTestMon(
        species: Int,
        level: Int,
        hp: Int,
        maxHp: Int,
        attack: Int,
        defense: Int,
        speed: Int,
        spAttack: Int,
        spDefense: Int,
        moves: List<Int>
    ): PartyMon {
        return PartyMon(
            species = species,
            level = level,
            hp = hp,
            maxHp = maxHp,
            nickname = "Test",
            moves = moves,
            attack = attack,
            defense = defense,
            speed = speed,
            spAttack = spAttack,
            spDefense = spDefense,
            experience = 0,
            friendship = 255
        )
    }

    @Test
    fun testSpeedComparison() {
        // Player speed 100, enemy speed 80
        val result = OptimalLineCalculator.calculateSpeedTier(
            playerSpeed = 100,
            enemySpeed = 80,
            playerStages = 0,
            enemyStages = 0
        )

        assertTrue(result.playerOutspeeds)
        assertEquals(20, result.speedDifference)
        assertFalse(result.canOutspeedWithBoost)  // Already outspeeding
    }

    @Test
    fun testSpeedComparisonWithBoost() {
        // Player speed 80, enemy speed 100 (we're slower)
        val result = OptimalLineCalculator.calculateSpeedTier(
            playerSpeed = 80,
            enemySpeed = 100,
            playerStages = 0,
            enemyStages = 0
        )

        assertFalse(result.playerOutspeeds)
        assertEquals(-20, result.speedDifference)

        // Check if +1 speed would help
        // 80 * 1.5 = 120, which is > 100
        assertTrue(result.canOutspeedWithBoost)
    }

    @Test
    fun testSpeedComparisonWithStages() {
        // Player speed 80 at +1, enemy speed 100 at +0
        val result = OptimalLineCalculator.calculateSpeedTier(
            playerSpeed = 80,
            enemySpeed = 100,
            playerStages = 1,
            enemyStages = 0
        )

        // 80 * 1.5 = 120 > 100
        assertTrue(result.playerOutspeeds)
        assertTrue(result.speedDifference > 0)
    }

    @Test
    fun testCalculateOptimalLines() {
        // Create a strong attacker vs weak defender
        val player = createTestMon(
            species = 6,  // Charizard
            level = 50,
            hp = 150,
            maxHp = 150,
            attack = 120,
            defense = 80,
            speed = 100,
            spAttack = 140,
            spDefense = 85,
            moves = listOf(14, 83, 0, 0)  // Swords Dance, Fire Blast
        )

        val enemy = createTestMon(
            species = 12,  // Butterfree
            level = 50,
            hp = 100,
            maxHp = 100,
            attack = 45,
            defense = 50,
            speed = 70,
            spAttack = 90,
            spDefense = 80,
            moves = listOf(33, 0, 0, 0)  // Tackle
        )

        val lines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 2,
            topN = 3,
            isTrainer = false
        )

        // Should return some lines
        assertTrue(lines.isNotEmpty())

        // Best line should have a decent score
        val bestLine = lines.first()
        assertTrue(bestLine.score > 0)

        // Should include move descriptions
        assertTrue(bestLine.description.isNotEmpty())
    }

    @Test
    fun testLineEvaluationFactors() {
        // Create a line that achieves 1HKO with high survival
        val player = createTestMon(
            species = 6, level = 50, hp = 150, maxHp = 150,
            attack = 120, defense = 80, speed = 100, spAttack = 140, spDefense = 85,
            moves = listOf(83)  // Fire Blast
        )
        val enemy = createTestMon(
            species = 12, level = 50, hp = 1, maxHp = 100,  // Very low HP
            attack = 45, defense = 50, speed = 70, spAttack = 90, spDefense = 80,
            moves = listOf(33)
        )

        val initialState = BattleState(
            player = BattlerState(player, player.hp, StatStages()),
            enemy = BattlerState(enemy, enemy.hp, StatStages())
        )

        val line = BattleLine(
            moves = listOf(83),
            finalState = initialState,
            turnsToKO = 1,
            damageDealt = 100,
            damageTaken = 10,
            survivalProbability = 0.9f,
            score = 0f,
            description = "Fire Blast (1HKO)"
        )

        val score = LineEvaluator.evaluateLine(line)

        // Should have high score (1HKO + high survival)
        assertTrue(score > 7.0f)
    }

    @Test
    fun testSurvivalProbabilityCalculation() {
        // Full HP
        assertEquals(1.0f, LineEvaluator.calculateSurvivalProbability(100, 100), 0.01f)

        // 60% HP (safe)
        assertEquals(1.0f, LineEvaluator.calculateSurvivalProbability(60, 100), 0.01f)

        // 40% HP (moderate)
        assertEquals(0.7f, LineEvaluator.calculateSurvivalProbability(40, 100), 0.01f)

        // 20% HP (risky)
        assertEquals(0.5f, LineEvaluator.calculateSurvivalProbability(20, 100), 0.01f)

        // 5% HP (very risky)
        assertEquals(0.2f, LineEvaluator.calculateSurvivalProbability(5, 100), 0.01f)

        // 0 HP (fainted)
        assertEquals(0.0f, LineEvaluator.calculateSurvivalProbability(0, 100), 0.01f)
    }

    @Test
    fun testMinimaxRejectsDeadlySetup() {
        // Create a scenario where setup would be deadly
        // Player: Moderate HP, has Swords Dance (14) and Close Combat (370)
        // Enemy: High attack, can KO player during setup
        val player = createTestMon(
            species = 257,  // Blaziken
            level = 50,
            hp = 80,  // Low HP
            maxHp = 150,
            attack = 120,
            defense = 70,
            speed = 80,
            spAttack = 110,
            spDefense = 70,
            moves = listOf(14, 370, 0, 0)  // Swords Dance, Close Combat
        )

        val enemy = createTestMon(
            species = 248,  // Tyranitar
            level = 50,
            hp = 180,
            maxHp = 180,
            attack = 140,  // High attack
            defense = 110,
            speed = 61,
            spAttack = 95,
            spDefense = 100,
            moves = listOf(89, 0, 0, 0)  // Earthquake (high damage)
        )

        val lines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 3,
            topN = 3,
            isTrainer = true,
            useMinimaxSearch = true
        )

        // Should return some lines
        assertTrue(lines.isNotEmpty())

        // Best line should prioritize survival
        val bestLine = lines.first()

        // If setup is chosen, player should survive
        if (bestLine.moves.firstOrNull() == 14) {  // Swords Dance
            assertTrue(
                "Setup line should have reasonable survival probability",
                bestLine.survivalProbability > 0.3f
            )
        }
    }

    @Test
    fun testMinimaxPerformance() {
        // Test that 3-turn minimax completes in reasonable time
        val player = createTestMon(
            species = 6,  // Charizard
            level = 50,
            hp = 150,
            maxHp = 150,
            attack = 120,
            defense = 80,
            speed = 100,
            spAttack = 140,
            spDefense = 85,
            moves = listOf(14, 83, 17, 0)  // Swords Dance, Fire Blast, Wing Attack
        )

        val enemy = createTestMon(
            species = 12,  // Butterfree
            level = 50,
            hp = 100,
            maxHp = 100,
            attack = 45,
            defense = 50,
            speed = 70,
            spAttack = 90,
            spDefense = 80,
            moves = listOf(33, 93, 0, 0)  // Tackle, Confusion
        )

        val startTime = System.currentTimeMillis()
        val lines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 3,
            topN = 3,
            isTrainer = false,
            useMinimaxSearch = true
        )
        val elapsed = System.currentTimeMillis() - startTime

        // Should complete in under 2 seconds (200ms target, but being generous for tests)
        assertTrue(
            "Minimax search took ${elapsed}ms (should be < 2000ms)",
            elapsed < 2000
        )

        // Should return valid lines
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.first().score > 0)
    }

    @Test
    fun testMinimaxVsSimpleSearch() {
        // Compare minimax vs simple search
        val player = createTestMon(
            species = 6,
            level = 50,
            hp = 150,
            maxHp = 150,
            attack = 120,
            defense = 80,
            speed = 100,
            spAttack = 140,
            spDefense = 85,
            moves = listOf(14, 83, 0, 0)  // Swords Dance, Fire Blast
        )

        val enemy = createTestMon(
            species = 12,
            level = 50,
            hp = 100,
            maxHp = 100,
            attack = 45,
            defense = 50,
            speed = 70,
            spAttack = 90,
            spDefense = 80,
            moves = listOf(33, 0, 0, 0)  // Tackle
        )

        val minimaxLines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 2,
            topN = 3,
            useMinimaxSearch = true
        )

        val simpleLines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 2,
            topN = 3,
            useMinimaxSearch = false
        )

        // Both should return results
        assertTrue(minimaxLines.isNotEmpty())
        assertTrue(simpleLines.isNotEmpty())

        // Both should have scored lines
        assertTrue(minimaxLines.first().score > 0)
        assertTrue(simpleLines.first().score > 0)
    }

    @Test
    fun testMinimax3TurnLookahead() {
        // Test 3-turn lookahead specifically
        val player = createTestMon(
            species = 6,
            level = 50,
            hp = 150,
            maxHp = 150,
            attack = 120,
            defense = 80,
            speed = 100,
            spAttack = 140,
            spDefense = 85,
            moves = listOf(14, 83, 17, 0)  // Swords Dance, Fire Blast, Wing Attack
        )

        val enemy = createTestMon(
            species = 12,
            level = 50,
            hp = 150,  // More HP so 3 turns might be needed
            maxHp = 150,
            attack = 45,
            defense = 50,
            speed = 70,
            spAttack = 90,
            spDefense = 80,
            moves = listOf(33, 93, 0, 0)  // Tackle, Confusion
        )

        val lines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 3,
            topN = 5,
            isTrainer = false,
            useMinimaxSearch = true
        )

        assertTrue(lines.isNotEmpty())

        // Should consider multi-turn lines
        val hasMultiTurnLine = lines.any { it.moves.size >= 2 }
        assertTrue("Should generate multi-turn lines", hasMultiTurnLine)
    }

    @Test
    fun testExtendedDepthSearch() {
        // Test that extended depth search (beyond 3 turns) works
        // Using 4 turns to test beam search without being too slow
        val player = createTestMon(
            species = 6,  // Charizard
            level = 50,
            hp = 150,
            maxHp = 150,
            attack = 120,
            defense = 80,
            speed = 100,
            spAttack = 140,
            spDefense = 85,
            moves = listOf(14, 83, 17, 52)  // Swords Dance, Fire Blast, Wing Attack, Ember
        )

        val enemy = createTestMon(
            species = 12,  // Butterfree (easier opponent for testing)
            level = 50,
            hp = 120,
            maxHp = 120,
            attack = 45,
            defense = 50,
            speed = 70,
            spAttack = 90,
            spDefense = 80,
            moves = listOf(33, 93, 1, 36)  // Tackle, Confusion, Pound, Double-Edge
        )

        val start = System.currentTimeMillis()

        val lines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 4,
            searchConfig = SearchConfig(
                tacticalDepth = 3,
                strategicDepth = 4,
                maxDepth = 4,
                beamWidth = 2
            ),
            topN = 3,
            isTrainer = false,
            useMinimaxSearch = true
        )

        val elapsed = System.currentTimeMillis() - start

        // Should return valid lines
        assertTrue(
            "Should return non-empty lines (got ${lines.size} lines)",
            lines.isNotEmpty()
        )

        if (lines.isNotEmpty()) {
            assertTrue(
                "Best line should have positive score (got ${lines.first().score})",
                lines.first().score > 0
            )
        }

        // Should complete in reasonable time
        assertTrue(
            "Extended search took ${elapsed}ms (target: <5000ms)",
            elapsed < 5000
        )
    }

    @Test
    fun testBeamSearchFindsGoodMoves() {
        // Beam search should still find good lines, just faster
        val player = createTestMon(
            species = 149,  // Dragonite
            level = 50,
            hp = 180,
            maxHp = 180,
            attack = 134,
            defense = 95,
            speed = 80,
            spAttack = 100,
            spDefense = 100,
            moves = listOf(14, 36, 89, 17)  // Swords Dance, Double-Edge, Earthquake, Wing Attack
        )

        val enemy = createTestMon(
            species = 242,  // Blissey
            level = 50,
            hp = 300,
            maxHp = 300,
            attack = 10,
            defense = 10,
            speed = 55,
            spAttack = 75,
            spDefense = 135,
            moves = listOf(1, 33, 34, 36)  // Pound, Tackle, Body Slam, Double-Edge
        )

        val fullSearch = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 3,
            searchConfig = SearchConfig(
                tacticalDepth = 3,
                strategicDepth = 3,
                maxDepth = 3
            ),
            topN = 3,
            isTrainer = false,
            useMinimaxSearch = true
        )

        val beamSearch = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 4,
            searchConfig = SearchConfig(
                tacticalDepth = 3,
                strategicDepth = 4,
                maxDepth = 4,
                beamWidth = 2
            ),
            topN = 3,
            isTrainer = false,
            useMinimaxSearch = true
        )

        // Both should find valid lines
        assertTrue("Full search should return lines", fullSearch.isNotEmpty())
        assertTrue("Beam search should return lines", beamSearch.isNotEmpty())

        // Beam search should find reasonable lines (may differ slightly due to different depth)
        if (fullSearch.isNotEmpty() && beamSearch.isNotEmpty()) {
            assertTrue(
                "Beam search score should be reasonable (got ${beamSearch.first().score})",
                beamSearch.first().score > 0
            )
        }
    }

    @Test
    fun testSearchConfigDefaultBehavior() {
        // Test that default config maintains backward compatibility
        val player = createTestMon(
            species = 6,
            level = 50,
            hp = 150,
            maxHp = 150,
            attack = 120,
            defense = 80,
            speed = 100,
            spAttack = 140,
            spDefense = 85,
            moves = listOf(14, 83, 0, 0)
        )

        val enemy = createTestMon(
            species = 12,
            level = 50,
            hp = 100,
            maxHp = 100,
            attack = 45,
            defense = 50,
            speed = 70,
            spAttack = 90,
            spDefense = 80,
            moves = listOf(33, 0, 0, 0)
        )

        // Old API (without searchConfig)
        val lines1 = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 3,
            topN = 3,
            isTrainer = false
        )

        // New API with explicit config
        val lines2 = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = 3,
            searchConfig = SearchConfig(
                tacticalDepth = 3,
                strategicDepth = 3,
                maxDepth = 3
            ),
            topN = 3,
            isTrainer = false
        )

        // Both should return similar results
        assertTrue(lines1.isNotEmpty())
        assertTrue(lines2.isNotEmpty())
        // Scores should be close (within 10%)
        assertTrue(
            kotlin.math.abs(lines1.first().score - lines2.first().score) < 1.0f
        )
    }
}
