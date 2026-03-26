package com.ercompanion.calc

import com.ercompanion.parser.PartyMon
import org.junit.Assert.*
import org.junit.Test

class DeepAnalysisModeTest {

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
    fun testQuickAnalysis() {
        // Create a simple battle scenario
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
            moves = listOf(52, 36, 14, 0)  // Flamethrower, Take Down, Swords Dance
        )

        val enemy = createTestMon(
            species = 3,  // Venusaur
            level = 50,
            hp = 160,
            maxHp = 160,
            attack = 100,
            defense = 100,
            speed = 80,
            spAttack = 120,
            spDefense = 120,
            moves = listOf(75, 22, 0, 0)  // Razor Leaf, Vine Whip
        )

        // Perform quick analysis
        val report = DeepAnalysisMode.performDeepAnalysis(
            player = player,
            enemy = enemy,
            depth = AnalysisDepth.QUICK
        )

        // Verify results
        assertNotNull(report)
        assertTrue(report.optimalLines.isNotEmpty())
        assertNull(report.monteCarloResult)  // Quick mode doesn't use Monte Carlo
        assertNull(report.setupStrategies)  // Quick mode doesn't do exhaustive setup
        assertTrue(report.analysisTimeMs < 500)  // Should be fast
    }

    @Test
    fun testMonteCarloEvaluation() {
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
            moves = listOf(52, 36, 14, 0)
        )

        val enemy = createTestMon(
            species = 3,  // Venusaur
            level = 50,
            hp = 160,
            maxHp = 160,
            attack = 100,
            defense = 100,
            speed = 80,
            spAttack = 120,
            spDefense = 120,
            moves = listOf(75, 22, 0, 0)
        )

        val state = BattleState(
            player = BattlerState(player, player.hp, StatStages(), 0, TempBoosts()),
            enemy = BattlerState(enemy, enemy.hp, StatStages(), 0, TempBoosts())
        )

        // Run Monte Carlo with small sample size for testing
        val result = DeepAnalysisMode.monteCarloEvaluation(state, samples = 20)

        assertNotNull(result)
        assertEquals(20, result.sampleCount)
        assertTrue(result.winRate >= 0f && result.winRate <= 1f)
    }

    @Test
    fun testExhaustiveSetupAnalysis() {
        // Create a mon with setup moves
        val player = createTestMon(
            species = 130,  // Gyarados
            level = 50,
            hp = 170,
            maxHp = 170,
            attack = 125,
            defense = 79,
            speed = 81,
            spAttack = 60,
            spDefense = 100,
            moves = listOf(349, 127, 0, 0)  // Dragon Dance (349), Waterfall (127)
        )

        val enemy = createTestMon(
            species = 3,  // Venusaur
            level = 50,
            hp = 160,
            maxHp = 160,
            attack = 100,
            defense = 100,
            speed = 80,
            spAttack = 120,
            spDefense = 120,
            moves = listOf(75, 22, 0, 0)
        )

        val state = BattleState(
            player = BattlerState(player, player.hp, StatStages(), 0, TempBoosts()),
            enemy = BattlerState(enemy, enemy.hp, StatStages(), 0, TempBoosts())
        )

        // Analyze setup strategies
        val strategies = DeepAnalysisMode.analyzeExhaustiveSetups(state)

        assertNotNull(strategies)
        assertTrue(strategies.isNotEmpty())

        // Verify that strategies include Dragon Dance
        val ddStrategy = strategies.firstOrNull { it.setupMove == 349 }
        assertNotNull(ddStrategy)
        assertTrue(ddStrategy!!.setupTurns >= 1)
    }

    @Test
    fun testSwitchingAnalysis() {
        val player = createTestMon(
            species = 6,  // Charizard (weak to Rock)
            level = 50,
            hp = 150,
            maxHp = 150,
            attack = 120,
            defense = 80,
            speed = 100,
            spAttack = 140,
            spDefense = 85,
            moves = listOf(52, 36, 0, 0)
        )

        val switchTarget = createTestMon(
            species = 9,  // Blastoise (resists Rock)
            level = 50,
            hp = 160,
            maxHp = 160,
            attack = 100,
            defense = 120,
            speed = 78,
            spAttack = 105,
            spDefense = 105,
            moves = listOf(55, 57, 0, 0)  // Water Gun, Surf
        )

        val enemy = createTestMon(
            species = 76,  // Golem (Rock type)
            level = 50,
            hp = 140,
            maxHp = 140,
            attack = 130,
            defense = 130,
            speed = 45,
            spAttack = 55,
            spDefense = 65,
            moves = listOf(88, 157, 0, 0)  // Rock Throw, Rock Slide
        )

        val state = BattleState(
            player = BattlerState(player, player.hp, StatStages(), 0, TempBoosts()),
            enemy = BattlerState(enemy, enemy.hp, StatStages(), 0, TempBoosts())
        )

        val party = listOf(player, switchTarget)

        // Analyze switching
        val config = DeepAnalysisConfig(maxDepth = 3, beamWidth = 2, monteCarloSamples = 0)
        val recommendations = DeepAnalysisMode.analyzeSwitchingStrategies(state, party, config)

        assertNotNull(recommendations)
        assertTrue(recommendations.isNotEmpty())

        // Should recommend switching to Blastoise
        val blastoiseRec = recommendations.firstOrNull { it.targetMon.species == 9 }
        assertNotNull(blastoiseRec)
    }

    @Test
    fun testDeepAnalysisReport() {
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
            moves = listOf(52, 36, 14, 0)
        )

        val enemy = createTestMon(
            species = 3,
            level = 50,
            hp = 160,
            maxHp = 160,
            attack = 100,
            defense = 100,
            speed = 80,
            spAttack = 120,
            spDefense = 120,
            moves = listOf(75, 22, 0, 0)
        )

        // Perform DEEP analysis
        val report = DeepAnalysisMode.performDeepAnalysis(
            player = player,
            enemy = enemy,
            depth = AnalysisDepth.DEEP
        )

        // Verify report structure
        assertNotNull(report)
        assertTrue(report.optimalLines.isNotEmpty())
        assertNotNull(report.monteCarloResult)  // DEEP includes Monte Carlo
        assertNotNull(report.setupStrategies)  // DEEP includes setup analysis
        assertEquals(AnalysisDepth.DEEP, report.depth)

        // Format and verify output
        val formatted = DeepAnalysisMode.formatReport(report)
        assertNotNull(formatted)
        assertTrue(formatted.contains("DEEP ANALYSIS REPORT"))
        assertTrue(formatted.contains("OPTIMAL LINES"))
    }

    @Test
    fun testAnalysisDepthProgression() {
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
            moves = listOf(52, 36, 14, 0)
        )

        val enemy = createTestMon(
            species = 3,
            level = 50,
            hp = 160,
            maxHp = 160,
            attack = 100,
            defense = 100,
            speed = 80,
            spAttack = 120,
            spDefense = 120,
            moves = listOf(75, 22, 0, 0)
        )

        // Test all depth levels
        val quickReport = DeepAnalysisMode.performDeepAnalysis(player, enemy, depth = AnalysisDepth.QUICK)
        val standardReport = DeepAnalysisMode.performDeepAnalysis(player, enemy, depth = AnalysisDepth.STANDARD)
        val deepReport = DeepAnalysisMode.performDeepAnalysis(player, enemy, depth = AnalysisDepth.DEEP)

        // Verify time progression (generally deeper = slower)
        assertTrue(quickReport.analysisTimeMs <= standardReport.analysisTimeMs + 100)  // Allow some variance

        // Verify feature availability
        assertNull(quickReport.monteCarloResult)
        assertNull(standardReport.monteCarloResult)
        assertNotNull(deepReport.monteCarloResult)
    }

    @Test
    fun testProgressCallback() {
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
            moves = listOf(52, 36, 14, 0)
        )

        val enemy = createTestMon(
            species = 3,
            level = 50,
            hp = 160,
            maxHp = 160,
            attack = 100,
            defense = 100,
            speed = 80,
            spAttack = 120,
            spDefense = 120,
            moves = listOf(75, 22, 0, 0)
        )

        val progressMessages = mutableListOf<String>()

        DeepAnalysisMode.performDeepAnalysis(
            player = player,
            enemy = enemy,
            depth = AnalysisDepth.DEEP,
            onProgress = { message ->
                progressMessages.add(message)
            }
        )

        // Verify progress callbacks were invoked
        assertTrue(progressMessages.isNotEmpty())
        assertTrue(progressMessages.any { it.contains("Calculating optimal lines") })
        assertTrue(progressMessages.any { it.contains("Analysis complete") })
    }
}
