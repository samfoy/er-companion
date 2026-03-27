package com.ercompanion.calc

import com.ercompanion.data.PokemonData
import com.ercompanion.parser.PartyMon
import kotlin.random.Random

/**
 * Deep Analysis Configuration.
 * Allows fine-tuned control over analysis depth and performance.
 */
data class DeepAnalysisConfig(
    val maxDepth: Int = 10,              // Look up to 10 turns ahead
    val beamWidth: Int = 3,              // Keep top 3 moves at each level
    val monteCarloSamples: Int = 100,    // Run 100 random rollouts per position
    val considerSwitching: Boolean = true, // Evaluate switching strategies
    val exhaustiveSetup: Boolean = true,  // Try all setup combinations
    val maxTimeMs: Long = 5000           // 5 second timeout
)

/**
 * Analysis depth presets for different use cases.
 */
enum class AnalysisDepth {
    QUICK,      // <200ms, depth 3, no Monte Carlo
    STANDARD,   // <500ms, depth 7, beam search
    DEEP,       // 1-2s, depth 10, small Monte Carlo (50 samples)
    EXHAUSTIVE  // 3-5s, depth 10, full Monte Carlo (100 samples) + switching
}

/**
 * Result from Monte Carlo simulation.
 */
data class MonteCarloResult(
    val winRate: Float,        // 0.0-1.0
    val sampleCount: Int,
    val avgTurnsToWin: Int,    // -1 if no wins
    val avgTurnsToLose: Int    // -1 if no losses
)

/**
 * Recommendation to switch to a different party member.
 */
data class SwitchRecommendation(
    val targetIndex: Int,
    val targetMon: PartyMon,
    val score: Float,
    val survivalProbability: Float,
    val turnsToKO: Int,
    val reasoning: String
)

/**
 * A setup strategy (e.g., Dragon Dance x2 -> Outrage).
 */
data class SetupStrategy(
    val setupMove: Int,
    val setupTurns: Int,
    val attackMove: Int,
    val totalTurnsToKO: Int,
    val survivalHp: Int,
    val moves: List<Int>,
    val score: Float
)

/**
 * Complete deep analysis report.
 */
data class DeepAnalysisReport(
    val optimalLines: List<BattleLine>,
    val monteCarloResult: MonteCarloResult?,
    val switchRecommendations: List<SwitchRecommendation>?,
    val setupStrategies: List<SetupStrategy>?,
    val analysisTimeMs: Long,
    val depth: AnalysisDepth
)

/**
 * Result of a random rollout.
 */
private enum class RolloutResult {
    PLAYER_WIN,
    PLAYER_LOSS,
    TIMEOUT
}

/**
 * Deep Analysis Mode provides optional thorough battle analysis.
 * This mode can take 1-5 seconds to produce extremely comprehensive battle strategies.
 */
object DeepAnalysisMode {

    /**
     * Perform deep analysis with all advanced features.
     * This is the main entry point for deep analysis.
     *
     * @param player Player's active Pokemon
     * @param enemy Enemy's active Pokemon
     * @param playerParty Full player party (for switch analysis)
     * @param depth Analysis depth preset
     * @param curses Active curse state
     * @param onProgress Optional callback for progress updates
     * @return Complete deep analysis report
     */
    fun performDeepAnalysis(
        player: PartyMon,
        enemy: PartyMon,
        playerParty: List<PartyMon> = listOf(player),
        depth: AnalysisDepth = AnalysisDepth.DEEP,
        curses: CurseState = CurseState.NONE,
        onProgress: ((String) -> Unit)? = null
    ): DeepAnalysisReport {
        val config = when (depth) {
            AnalysisDepth.QUICK -> DeepAnalysisConfig(
                maxDepth = 3,
                beamWidth = 2,
                monteCarloSamples = 0,
                considerSwitching = false,
                exhaustiveSetup = false
            )
            AnalysisDepth.STANDARD -> DeepAnalysisConfig(
                maxDepth = 7,
                beamWidth = 2,
                monteCarloSamples = 0,
                considerSwitching = false,
                exhaustiveSetup = false
            )
            AnalysisDepth.DEEP -> DeepAnalysisConfig(
                maxDepth = 10,
                beamWidth = 3,
                monteCarloSamples = 50,
                considerSwitching = false,
                exhaustiveSetup = true
            )
            AnalysisDepth.EXHAUSTIVE -> DeepAnalysisConfig(
                maxDepth = 10,
                beamWidth = 3,
                monteCarloSamples = 100,
                considerSwitching = true,
                exhaustiveSetup = true
            )
        }

        val startTime = System.currentTimeMillis()

        // 1. Standard optimal lines
        onProgress?.invoke("Calculating optimal lines...")
        val optimalLines = OptimalLineCalculator.calculateOptimalLines(
            player = player,
            enemy = enemy,
            maxDepth = config.maxDepth,
            searchConfig = SearchConfig(
                tacticalDepth = 3,
                strategicDepth = 5,
                maxDepth = config.maxDepth,
                beamWidth = config.beamWidth
            ),
            topN = 5,  // Get top 5 lines for deep analysis
            curses = curses,
            onProgress = { progress ->
                // Report progress as percentage during long calculations
                val percent = (progress * 100).toInt()
                onProgress?.invoke("Calculating optimal lines... $percent%")
            }
        )

        // 2. Monte Carlo evaluation (if enabled)
        var monteCarloResult: MonteCarloResult? = null
        if (config.monteCarloSamples > 0) {
            onProgress?.invoke("Running Monte Carlo simulations (${config.monteCarloSamples} samples)...")
            val initialState = BattleState(
                player = BattlerState(player, player.hp, StatStages(), player.status, TempBoosts()),
                enemy = BattlerState(enemy, enemy.hp, StatStages(), enemy.status, TempBoosts()),
                curses = curses
            )
            monteCarloResult = monteCarloEvaluation(initialState, config.monteCarloSamples)
        }

        // 3. Switching analysis (if enabled)
        var switchRecommendations: List<SwitchRecommendation>? = null
        if (config.considerSwitching && playerParty.size > 1) {
            onProgress?.invoke("Analyzing switching strategies...")
            val initialState = BattleState(
                player = BattlerState(player, player.hp, StatStages(), player.status, TempBoosts()),
                enemy = BattlerState(enemy, enemy.hp, StatStages(), enemy.status, TempBoosts()),
                curses = curses
            )
            switchRecommendations = analyzeSwitchingStrategies(initialState, playerParty, config, curses)
        }

        // 4. Exhaustive setup analysis (if enabled)
        var setupStrategies: List<SetupStrategy>? = null
        if (config.exhaustiveSetup) {
            onProgress?.invoke("Analyzing exhaustive setup combinations...")
            val initialState = BattleState(
                player = BattlerState(player, player.hp, StatStages(), player.status, TempBoosts()),
                enemy = BattlerState(enemy, enemy.hp, StatStages(), enemy.status, TempBoosts()),
                curses = curses
            )
            setupStrategies = analyzeExhaustiveSetups(initialState)
        }

        val elapsed = System.currentTimeMillis() - startTime
        onProgress?.invoke("Analysis complete!")

        return DeepAnalysisReport(
            optimalLines = optimalLines,
            monteCarloResult = monteCarloResult,
            switchRecommendations = switchRecommendations,
            setupStrategies = setupStrategies,
            analysisTimeMs = elapsed,
            depth = depth
        )
    }

    /**
     * Run Monte Carlo tree search to estimate position value.
     * Simulates many random games from the current position.
     *
     * @param state Current battle state
     * @param samples Number of random rollouts to perform
     * @param maxRolloutDepth Maximum turns per rollout
     * @return Monte Carlo evaluation result
     */
    fun monteCarloEvaluation(
        state: BattleState,
        samples: Int = 100,
        maxRolloutDepth: Int = 20
    ): MonteCarloResult {
        var winCount = 0
        var lossCount = 0
        var totalTurnsToWin = 0
        var totalTurnsToLose = 0

        repeat(samples) {
            val (result, turns) = randomRollout(state, maxRolloutDepth)
            when (result) {
                RolloutResult.PLAYER_WIN -> {
                    winCount++
                    totalTurnsToWin += turns
                }
                RolloutResult.PLAYER_LOSS -> {
                    lossCount++
                    totalTurnsToLose += turns
                }
                RolloutResult.TIMEOUT -> {
                    // Count as draw, estimate from HP ratio
                    if (state.player.currentHp > state.enemy.currentHp) {
                        winCount++
                    } else {
                        lossCount++
                    }
                }
            }
        }

        val winRate = winCount.toFloat() / samples
        val avgWinTurns = if (winCount > 0) totalTurnsToWin / winCount else -1
        val avgLossTurns = if (lossCount > 0) totalTurnsToLose / lossCount else -1

        return MonteCarloResult(
            winRate = winRate,
            sampleCount = samples,
            avgTurnsToWin = avgWinTurns,
            avgTurnsToLose = avgLossTurns
        )
    }

    /**
     * Random playout to terminal state.
     * Simulates a random game until someone wins or max depth is reached.
     *
     * PERFORMANCE NOTE: This function is called repeatedly in Monte Carlo simulation
     * (default 100 samples × 20 turns = 2000+ BattleState allocations per analysis).
     * State copying is necessary for immutability, but the overhead is acceptable
     * because:
     * 1. BattleState is a data class with efficient copy() operations
     * 2. MoveSimulator batches multiple updates to reduce intermediate copies
     * 3. Modern GCs handle short-lived allocations well
     *
     * Future optimization: Consider object pooling if GC pressure becomes an issue.
     *
     * @param initialState Starting battle state
     * @param maxDepth Maximum turns to simulate
     * @return Pair of (result, turns taken)
     */
    private fun randomRollout(
        initialState: BattleState,
        maxDepth: Int
    ): Pair<RolloutResult, Int> {
        var state = initialState
        var turns = 0
        var noProgressCounter = 0

        while (turns < maxDepth) {
            // Terminal check
            if (state.player.currentHp <= 0) return Pair(RolloutResult.PLAYER_LOSS, turns)
            if (state.enemy.currentHp <= 0) return Pair(RolloutResult.PLAYER_WIN, turns)

            // Choose random valid moves
            val playerMoves = state.player.mon.moves.filter { it > 0 }
            val enemyMoves = state.enemy.mon.moves.filter { it > 0 }

            if (playerMoves.isEmpty() || enemyMoves.isEmpty()) {
                return Pair(RolloutResult.TIMEOUT, turns)
            }

            val playerMove = playerMoves.random()
            val enemyMove = enemyMoves.random()

            // Store HP before move
            val prevPlayerHp = state.player.currentHp
            val prevEnemyHp = state.enemy.currentHp

            // Simulate turn (creates new BattleState - performance-critical)
            state = MoveSimulator.simulateMove(state, playerMove, enemyMove)
            turns++

            // Check for stalemate (no progress for 5 turns)
            if (prevPlayerHp == state.player.currentHp && prevEnemyHp == state.enemy.currentHp) {
                noProgressCounter++
                if (noProgressCounter >= 5) {
                    return Pair(RolloutResult.TIMEOUT, turns)  // Stalemate detected
                }
            } else {
                noProgressCounter = 0  // Reset counter when progress is made
            }
        }

        return Pair(RolloutResult.TIMEOUT, turns)
    }

    /**
     * Evaluate switching to different party members.
     * Analyzes each party member and recommends the best switches.
     *
     * @param currentState Current battle state
     * @param playerParty Full player party
     * @param config Deep analysis configuration
     * @param curses Active curse state
     * @return List of switch recommendations, sorted by score
     */
    fun analyzeSwitchingStrategies(
        currentState: BattleState,
        playerParty: List<PartyMon>,
        config: DeepAnalysisConfig,
        curses: CurseState = CurseState.NONE
    ): List<SwitchRecommendation> {
        val recommendations = mutableListOf<SwitchRecommendation>()

        // For each party member (except current)
        for ((index, mon) in playerParty.withIndex()) {
            if (mon.species == currentState.player.mon.species) continue  // Skip current
            if (mon.hp <= 0) continue  // Skip fainted

            // Simulate switching
            val switchedState = currentState.copy(
                player = BattlerState(
                    mon = mon,
                    currentHp = mon.hp,
                    statStages = StatStages(),  // Reset stages on switch
                    status = mon.status,
                    tempBoosts = TempBoosts()
                )
            )

            // Apply switch-in abilities (Intimidate, Download, etc.)
            val finalState = AbilityEffects.applySwitchInAbility(switchedState, isPlayer = true)

            // Evaluate position after switch
            val evaluation = OptimalLineCalculator.calculateOptimalLines(
                player = mon,
                enemy = currentState.enemy.mon,
                maxDepth = config.maxDepth,
                searchConfig = SearchConfig(
                    tacticalDepth = 3,
                    strategicDepth = 5,
                    maxDepth = config.maxDepth,
                    beamWidth = config.beamWidth
                ),
                curses = curses
            ).firstOrNull()

            if (evaluation != null) {
                recommendations.add(
                    SwitchRecommendation(
                        targetIndex = index,
                        targetMon = mon,
                        score = evaluation.score,
                        survivalProbability = evaluation.survivalProbability,
                        turnsToKO = evaluation.turnsToKO,
                        reasoning = "Switch to ${PokemonData.getSpeciesName(mon.species)}: ${evaluation.description}"
                    )
                )
            }
        }

        return recommendations.sortedByDescending { it.score }
    }

    /**
     * Try all combinations of setup moves (e.g., Dragon Dance x2 -> Attack).
     * Finds the optimal number of setup turns before attacking.
     *
     * @param state Current battle state
     * @param maxSetupTurns Maximum setup turns to consider
     * @return List of setup strategies, sorted by score
     */
    fun analyzeExhaustiveSetups(
        state: BattleState,
        maxSetupTurns: Int = 3
    ): List<SetupStrategy> {
        val setupMoves = state.player.mon.moves.filter { moveId ->
            MoveEffects.isSetupMove(moveId)
        }

        if (setupMoves.isEmpty()) return emptyList()

        val strategies = mutableListOf<SetupStrategy>()

        // Try each setup move 1-3 times
        for (setupMove in setupMoves) {
            for (setupCount in 1..maxSetupTurns) {
                // Simulate setup turns
                var currentState = state
                val moves = mutableListOf<Int>()

                repeat(setupCount) {
                    // Re-predict enemy move each turn to account for changing battle state
                    // (Intimidate, stat changes, etc.)
                    val enemyMove = predictEnemyMove(currentState.enemy.mon, currentState.player.mon)
                    currentState = MoveSimulator.simulateMove(currentState, setupMove, enemyMove)
                    moves.add(setupMove)
                }

                // Check if we survived setup
                if (currentState.player.currentHp <= 0) continue

                // Find best attacking move after setup
                val attackMove = currentState.player.mon.moves
                    .filter { it > 0 && !MoveEffects.isSetupMove(it) }
                    .maxByOrNull { moveId ->
                        val result = MoveSimulator.calculateDamageWithStages(
                            currentState.player,
                            currentState.enemy,
                            moveId
                        )
                        result.maxDamage
                    } ?: continue

                // Add attack turns
                var turnsToKO = setupCount
                var attackState = currentState
                while (attackState.enemy.currentHp > 0 && turnsToKO < 10) {
                    val enemyAttackMove = predictEnemyMove(attackState.enemy.mon, attackState.player.mon)
                    attackState = MoveSimulator.simulateMove(attackState, attackMove, enemyAttackMove)
                    moves.add(attackMove)
                    turnsToKO++

                    // Check if player fainted
                    if (attackState.player.currentHp <= 0) break
                }

                strategies.add(
                    SetupStrategy(
                        setupMove = setupMove,
                        setupTurns = setupCount,
                        attackMove = attackMove,
                        totalTurnsToKO = turnsToKO,
                        survivalHp = attackState.player.currentHp,
                        moves = moves,
                        score = evaluateSetupStrategy(
                            setupCount,
                            turnsToKO,
                            attackState.player.currentHp,
                            state.player.mon.maxHp,
                            attackState.enemy.currentHp <= 0
                        )
                    )
                )
            }
        }

        return strategies.sortedByDescending { it.score }
    }

    /**
     * Evaluate a setup strategy's quality.
     *
     * @param setupTurns Number of setup turns used
     * @param totalTurns Total turns including attack
     * @param finalHp Player's HP after strategy
     * @param maxHp Player's max HP
     * @param achievedKO Whether enemy was KO'd
     * @return Score (higher is better)
     */
    private fun evaluateSetupStrategy(
        setupTurns: Int,
        totalTurns: Int,
        finalHp: Int,
        maxHp: Int,
        achievedKO: Boolean
    ): Float {
        var score = 0f

        // Heavily reward achieving KO
        if (achievedKO) {
            score += 10.0f
            // Fewer total turns is better
            score += (20 - totalTurns) * 0.5f
        } else {
            // Partial credit for not achieving KO
            score += 2.0f
        }

        // Survival is critical
        val hpPercent = finalHp.toFloat() / maxHp
        score += hpPercent * 5.0f

        // Penalty for excessive setup
        score -= setupTurns * 0.5f

        return score.coerceAtLeast(0f)
    }

    /**
     * Predict enemy's most likely move using AI simulator.
     */
    private fun predictEnemyMove(enemy: PartyMon, player: PartyMon): Int {
        val scoredMoves = BattleAISimulator.scoreMovesVsTarget(enemy, player, isTrainer = true)
        val predicted = BattleAISimulator.predictAiMove(scoredMoves)
        return predicted.firstOrNull()?.moveId ?: enemy.moves.firstOrNull { it > 0 } ?: 0
    }

    /**
     * Format a deep analysis report as human-readable text.
     * Useful for debugging or console output.
     */
    fun formatReport(report: DeepAnalysisReport): String {
        val sb = StringBuilder()

        sb.appendLine("=== DEEP ANALYSIS REPORT (${report.analysisTimeMs}ms) ===")
        sb.appendLine("Depth: ${report.depth}")
        sb.appendLine()

        // Optimal lines
        sb.appendLine("=== OPTIMAL LINES ===")
        report.optimalLines.take(3).forEachIndexed { index, line ->
            sb.appendLine("${index + 1}. ${line.description}")
            sb.appendLine("   Score: ${String.format("%.1f", line.score)}/10, Survival: ${(line.survivalProbability * 100).toInt()}%")
        }
        sb.appendLine()

        // Monte Carlo results
        report.monteCarloResult?.let { mc ->
            sb.appendLine("=== MONTE CARLO SIMULATION (${mc.sampleCount} samples) ===")
            sb.appendLine("Win Rate: ${(mc.winRate * 100).toInt()}%")
            if (mc.avgTurnsToWin > 0) {
                sb.appendLine("Avg Turns to Win: ${mc.avgTurnsToWin}")
            }
            if (mc.avgTurnsToLose > 0) {
                sb.appendLine("Avg Turns to Loss: ${mc.avgTurnsToLose}")
            }
            sb.appendLine()
        }

        // Switch recommendations
        report.switchRecommendations?.let { switches ->
            if (switches.isNotEmpty()) {
                sb.appendLine("=== SWITCHING RECOMMENDATIONS ===")
                switches.take(3).forEachIndexed { index, rec ->
                    sb.appendLine("${index + 1}. ${rec.reasoning}")
                    sb.appendLine("   Score: ${String.format("%.1f", rec.score)}/10")
                }
                sb.appendLine()
            }
        }

        // Setup strategies
        report.setupStrategies?.let { setups ->
            if (setups.isNotEmpty()) {
                sb.appendLine("=== EXHAUSTIVE SETUP ANALYSIS ===")
                val best = setups.first()
                val setupMoveName = PokemonData.getMoveData(best.setupMove)?.name ?: "Unknown"
                val attackMoveName = PokemonData.getMoveData(best.attackMove)?.name ?: "Unknown"
                sb.appendLine("Best: $setupMoveName x${best.setupTurns} -> $attackMoveName")
                sb.appendLine("  - ${best.setupTurns} setup turns")
                sb.appendLine("  - KO in ${best.totalTurnsToKO} total turns")
                sb.appendLine("  - ${(best.survivalHp.toFloat() / 100).toInt()}% HP remaining")
                sb.appendLine("  - Score: ${String.format("%.1f", best.score)}/10")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
