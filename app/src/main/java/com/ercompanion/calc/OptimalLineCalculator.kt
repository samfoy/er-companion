package com.ercompanion.calc

import com.ercompanion.data.PokemonData
import com.ercompanion.parser.PartyMon
import kotlin.math.abs
import java.util.concurrent.ConcurrentHashMap

/**
 * Configuration for tiered search depth strategy.
 */
data class SearchConfig(
    val tacticalDepth: Int = 3,      // Full search (all moves)
    val strategicDepth: Int = 5,     // Beam search (top N moves)
    val maxDepth: Int = 7,           // Fast rollout only
    val beamWidth: Int = 2           // Keep top N moves in strategic phase
)

/**
 * Main entry point for optimal battle line calculation.
 * Computes the best move sequences for singles battles.
 */
object OptimalLineCalculator {

    // Constants for validation
    private const val MAX_MOVE_ID = 847  // Maximum valid move ID in PokemonData
    private const val MAX_RECURSION_DEPTH = 100  // Stack depth limit for minimax

    // Transposition table for caching evaluated positions (thread-safe)
    private val transpositionTable = ConcurrentHashMap<Int, Float>()

    // Recursion depth tracking for minimax
    private var recursionDepth = 0

    // Cache for damage calculations (Issue 3.5, thread-safe)
    private val damageCache = ConcurrentHashMap<Triple<Int, Int, Int>, Int>()

    /**
     * Calculate optimal move sequences for the current battle state.
     * Returns top N best lines (default 3).
     *
     * @param player Player's active Pokemon
     * @param enemy Enemy's active Pokemon
     * @param maxDepth How many turns to look ahead (1-7, default 2)
     * @param searchConfig Configuration for tiered search (default: tactical depth only)
     * @param topN Return top N lines
     * @param isTrainer Whether enemy is a trainer (affects AI prediction)
     * @param useMinimaxSearch Use minimax algorithm (true) or simple search (false)
     * @param onProgress Optional progress callback (0.0 to 1.0) for long calculations
     * @return List of top battle lines, sorted by score (best first)
     */
    fun calculateOptimalLines(
        player: PartyMon,
        enemy: PartyMon,
        maxDepth: Int = 2,
        searchConfig: SearchConfig = SearchConfig(
            tacticalDepth = maxDepth,
            strategicDepth = maxDepth,
            maxDepth = maxDepth,
            beamWidth = 2
        ),
        topN: Int = 3,
        isTrainer: Boolean = true,
        useMinimaxSearch: Boolean = true,
        curses: CurseState = CurseState.NONE,
        onProgress: ((Float) -> Unit)? = null
    ): List<BattleLine> {
        return try {
            // Clear caches for fresh search
            transpositionTable.clear()
            damageCache.clear()  // Issue 3.5: Clear damage cache
            // Create initial battle state
        val initialState = BattleState(
            player = BattlerState(
                mon = player,
                currentHp = player.hp,
                statStages = StatStages()
            ),
            enemy = BattlerState(
                mon = enemy,
                currentHp = enemy.hp,
                statStages = StatStages()
            ),
            curses = curses
        )

        // OHKO Curse: Special handling - enemy always OHKOs
        if (CurseEffects.isOhkoCurseActive(curses)) {
            // With OHKO curse, only strategy is to outspeed and OHKO first
            // or use priority moves
            return calculateOhkoCurseLines(initialState, player, enemy)
        }

        // Get player's valid moves (issue 2.1: validate move ID range)
        val playerMoves = player.moves.filter { it in 1..MAX_MOVE_ID }
        if (playerMoves.isEmpty()) return emptyList()

        // Choose search algorithm
        val allLines = if (useMinimaxSearch && maxDepth >= 2) {
            // Use minimax search for better opponent modeling
            generateLinesWithMinimax(initialState, searchConfig, isTrainer, onProgress)
        } else {
            // Use simple search (backward compatible)
            val enemyMove = predictEnemyMove(enemy, player, isTrainer)
            val lines = mutableListOf<BattleLine>()
            for (depth in 1..maxDepth.coerceIn(1, 3)) {
                lines.addAll(generateLines(initialState, playerMoves, enemyMove, depth))
            }
            lines
        }

        // Evaluate and score all lines
        val scoredLines = allLines.map { line ->
            val score = LineEvaluator.evaluateLine(line)
            line.copy(score = score)
        }

            // Sort by score (descending) and return top N
            scoredLines
                .sortedByDescending { it.score }
                .take(topN)
        } finally {
            // Always clear caches on exit (even if exception occurs)
            transpositionTable.clear()
            damageCache.clear()
        }
    }

    /**
     * Special line calculation for OHKO curse.
     * With OHKO curse, enemy can OHKO on any hit, so only viable strategies are:
     * 1. Outspeed and OHKO first
     * 2. Use priority moves
     */
    private fun calculateOhkoCurseLines(
        state: BattleState,
        player: PartyMon,
        enemy: PartyMon
    ): List<BattleLine> {
        // With OHKO curse, only moves that can OHKO or have priority matter
        val moves = player.moves.filter { it > 0 }
        val lines = moves.map { moveId ->
            val moveName = PokemonData.getMoveName(moveId)
            val result = MoveSimulator.calculateDamageWithStages(
                state.player, state.enemy, moveId
            )

            BattleLine(
                moves = listOf(moveId),
                finalState = state,
                turnsToKO = if (result.maxDamage >= enemy.hp) 1 else 99,
                damageDealt = result.maxDamage,
                damageTaken = 0,
                survivalProbability = if (result.maxDamage >= enemy.hp) 1.0f else 0.0f,
                score = 0f,
                description = if (result.maxDamage >= enemy.hp) {
                    "OHKO enemy before they OHKO you (${result.maxDamage}/${enemy.hp} HP)"
                } else {
                    "Cannot outspeed - will be OHKO'd"
                }
            )
        }

        return lines.sortedByDescending { it.survivalProbability }
    }

    /**
     * Generate all possible battle lines of a given depth.
     */
    private fun generateLines(
        initialState: BattleState,
        playerMoves: List<Int>,
        enemyMoveId: Int,
        depth: Int
    ): List<BattleLine> {
        if (depth <= 0) return emptyList()

        val lines = mutableListOf<BattleLine>()

        // For depth 1, generate all single-move lines
        if (depth == 1) {
            for (moveId in playerMoves) {
                val line = simulateLine(initialState, listOf(moveId), enemyMoveId)
                lines.add(line)
            }
            return lines
        }

        // For depth > 1, generate multi-turn sequences
        // We'll use a simple approach: try all combinations of moves
        if (depth == 2) {
            for (move1 in playerMoves) {
                for (move2 in playerMoves) {
                    val line = simulateLine(initialState, listOf(move1, move2), enemyMoveId)
                    lines.add(line)
                }
            }
        } else if (depth == 3) {
            // For depth 3, use beam search pruning to avoid O(n³) explosion
            // Issue 3.1: Reduce from O(n³) to O(n log n) with quick evaluation + pruning
            // Issue 2.1: Filter moves by valid ID range
            // If Unaware curse active, enemy ignores our stat stages
            // Setup moves become worthless
            val setupMoves = if (CurseEffects.isUnawareCurseActive(initialState.curses)) {
                emptyList()  // Don't consider setup moves
            } else {
                playerMoves.filter { it in 1..MAX_MOVE_ID && MoveEffects.isSetupMove(it) }
            }
            val attackMoves = playerMoves.filter { it in 1..MAX_MOVE_ID }.filter {
                val moveData = PokemonData.getMoveData(it)
                moveData != null && moveData.power > 0
            }

            // Quick evaluation: estimate line value without full simulation
            fun quickEvaluateLine(moves: List<Int>): Float {
                var state = initialState
                var totalDamage = 0
                for (moveId in moves) {
                    val damageResult = MoveSimulator.calculateDamageWithStages(
                        attacker = state.player,
                        defender = state.enemy,
                        moveId = moveId,
                        weather = state.weather,
                        terrain = state.terrain
                    )
                    totalDamage += damageResult.maxDamage
                    // Quick state update: just apply stat changes for setup moves
                    if (MoveEffects.isSetupMove(moveId)) {
                        state = state.copy(
                            player = state.player.copy(
                                statStages = MoveSimulator.applyStatChanges(state.player.statStages, moveId)
                            )
                        )
                    }
                }
                return totalDamage.toFloat()
            }

            // Generate all combinations and prune to top N
            val beamWidth = 10  // Only keep top 10 combinations after quick evaluation
            val allCombinations = mutableListOf<List<Int>>()

            // Setup -> Attack -> Attack
            for (setup in setupMoves) {
                for (attack1 in attackMoves) {
                    for (attack2 in attackMoves) {
                        allCombinations.add(listOf(setup, attack1, attack2))
                    }
                }
            }

            // Attack -> Attack -> Attack (for comparison)
            for (attack1 in attackMoves) {
                for (attack2 in attackMoves) {
                    for (attack3 in attackMoves) {
                        allCombinations.add(listOf(attack1, attack2, attack3))
                    }
                }
            }

            // Prune: only simulate the top combinations
            val topCombinations = if (allCombinations.size > beamWidth) {
                allCombinations
                    .map { combination -> Pair(combination, quickEvaluateLine(combination)) }
                    .sortedByDescending { it.second }
                    .take(beamWidth)
                    .map { it.first }
            } else {
                allCombinations
            }

            // Simulate only the top combinations
            for (moves in topCombinations) {
                val line = simulateLine(initialState, moves, enemyMoveId)
                lines.add(line)
            }
        }

        return lines
    }

    /**
     * Simulate a battle line (sequence of moves) and return the result.
     */
    private fun simulateLine(
        initialState: BattleState,
        moves: List<Int>,
        enemyMoveId: Int
    ): BattleLine {
        var state = initialState
        var totalDamageDealt = 0
        var totalDamageTaken = 0
        var turnsToKO = -1

        for ((turnIndex, moveId) in moves.withIndex()) {
            val previousEnemyHp = state.enemy.currentHp
            val previousPlayerHp = state.player.currentHp

            // Simulate the turn
            state = MoveSimulator.simulateMove(state, moveId, enemyMoveId)

            // Track damage
            val damageDealt = previousEnemyHp - state.enemy.currentHp
            val damageTaken = previousPlayerHp - state.player.currentHp
            totalDamageDealt += damageDealt
            totalDamageTaken += damageTaken

            // Check if enemy KO'd
            if (state.enemy.currentHp <= 0 && turnsToKO < 0) {
                turnsToKO = turnIndex + 1
                break  // Enemy defeated, stop simulation
            }

            // Check if player KO'd
            if (state.player.currentHp <= 0) {
                break  // Player defeated, stop simulation
            }
        }

        // Calculate survival probability
        val survivalProb = LineEvaluator.calculateSurvivalProbability(
            state.player.currentHp,
            state.player.mon.maxHp
        )

        // Build description
        val description = LineEvaluator.buildDescription(moves, turnsToKO, survivalProb)

        return BattleLine(
            moves = moves,
            finalState = state,
            turnsToKO = turnsToKO,
            damageDealt = totalDamageDealt,
            damageTaken = totalDamageTaken,
            survivalProbability = survivalProb,
            score = 0f,  // Will be calculated later
            description = description
        )
    }

    /**
     * Check if a setup move is worth using.
     * Example: Is Swords Dance → Attack better than Attack → Attack?
     *
     * @return true if setup is worthwhile, false otherwise
     */
    fun isSetupWorthwhile(
        state: BattleState,
        setupMoveId: Int,
        followUpMoveId: Int
    ): Boolean {
        // Simulate setup line
        val setupLine = simulateLine(
            state,
            listOf(setupMoveId, followUpMoveId),
            predictEnemyMove(state.enemy.mon, state.player.mon, true)
        )

        // Simulate direct attack line
        val directLine = simulateLine(
            state,
            listOf(followUpMoveId, followUpMoveId),
            predictEnemyMove(state.enemy.mon, state.player.mon, true)
        )

        // Compare scores
        val setupScore = LineEvaluator.evaluateLine(setupLine)
        val directScore = LineEvaluator.evaluateLine(directLine)

        return setupScore > directScore
    }

    /**
     * Calculate speed tiers - can we outspeed?
     */
    fun calculateSpeedTier(
        playerSpeed: Int,
        enemySpeed: Int,
        playerStages: Int = 0,
        enemyStages: Int = 0
    ): SpeedComparison {
        val playerEffectiveSpeed = (playerSpeed * MoveSimulator.getStatMultiplier(playerStages)).toInt()
        val enemyEffectiveSpeed = (enemySpeed * MoveSimulator.getStatMultiplier(enemyStages)).toInt()

        val playerOutspeeds = playerEffectiveSpeed >= enemyEffectiveSpeed
        val speedDifference = playerEffectiveSpeed - enemyEffectiveSpeed

        // Check if +1 speed stage would let us outspeed
        val playerSpeedPlus1 = (playerSpeed * MoveSimulator.getStatMultiplier(playerStages + 1)).toInt()
        val canOutspeedWithBoost = playerSpeedPlus1 >= enemyEffectiveSpeed && !playerOutspeeds

        return SpeedComparison(
            playerOutspeeds = playerOutspeeds,
            speedDifference = speedDifference,
            canOutspeedWithBoost = canOutspeedWithBoost
        )
    }

    /**
     * Predict enemy's move using BattleAISimulator.
     */
    private fun predictEnemyMove(enemy: PartyMon, player: PartyMon, isTrainer: Boolean): Int {
        val scoredMoves = BattleAISimulator.scoreMovesVsTarget(enemy, player, isTrainer)
        val predicted = BattleAISimulator.predictAiMove(scoredMoves)

        // Return the first (or random among tied) predicted move
        return predicted.firstOrNull()?.moveId ?: enemy.moves.firstOrNull { it > 0 } ?: 0
    }

    // ========== MINIMAX SEARCH IMPLEMENTATION ==========

    /**
     * Generate battle lines using minimax search with alpha-beta pruning.
     * This properly accounts for enemy responses to each player move.
     */
    private fun generateLinesWithMinimax(
        initialState: BattleState,
        searchConfig: SearchConfig,
        isTrainer: Boolean,
        onProgress: ((Float) -> Unit)? = null
    ): List<BattleLine> {
        val lines = mutableListOf<BattleLine>()
        val playerMoves = generateOrderedMoves(initialState, isPlayer = true)

        // Try each possible first move for the player
        for ((index, firstMove) in playerMoves.withIndex()) {
            // Report progress during move evaluation loop (0.0 to 1.0)
            onProgress?.invoke(index.toFloat() / playerMoves.size)

            val result = minimax(
                state = initialState,
                depth = 0,
                maxDepth = searchConfig.maxDepth,
                config = searchConfig,
                isPlayerTurn = true,
                isTrainer = isTrainer,
                alpha = Float.NEGATIVE_INFINITY,
                beta = Float.POSITIVE_INFINITY,
                forcedMove = firstMove  // Force this move as the first move
            )

            // Convert MinimaxResult to BattleLine
            if (result.line.isNotEmpty()) {
                val line = buildBattleLineFromMinimax(initialState, result, isTrainer)
                lines.add(line)
            }
        }

        // Report completion
        onProgress?.invoke(1.0f)

        return lines
    }

    /**
     * Minimax search with alpha-beta pruning and tiered depth strategy.
     * Returns the best move sequence considering enemy responses.
     *
     * @param state Current battle state
     * @param depth Current search depth (0 = root)
     * @param maxDepth Maximum search depth
     * @param config Search configuration for tiered strategy
     * @param isPlayerTurn True if player's turn, false if enemy's turn
     * @param isTrainer Whether enemy is a trainer (for AI prediction)
     * @param alpha Alpha value for pruning
     * @param beta Beta value for pruning
     * @param forcedMove If set, forces this move (used for first ply)
     * @return MinimaxResult with score, move, and resulting state
     */
    private fun minimax(
        state: BattleState,
        depth: Int,
        maxDepth: Int,
        config: SearchConfig,
        isPlayerTurn: Boolean,
        isTrainer: Boolean,
        alpha: Float,
        beta: Float,
        forcedMove: Int? = null
    ): MinimaxResult {
        // Issue 2.18: Stack depth limit check to prevent stack overflow
        if (++recursionDepth > MAX_RECURSION_DEPTH) {
            recursionDepth--
            throw IllegalStateException("Recursion depth exceeded $MAX_RECURSION_DEPTH - possible infinite loop")
        }

        try {
            // Terminal conditions
            if (state.player.currentHp <= 0 || state.enemy.currentHp <= 0) {
                return MinimaxResult(
                    score = evaluateState(state),
                    bestMove = -1,
                    line = emptyList(),
                    finalState = state
                )
            }

            // Depth-based strategy selection
            when {
                depth >= maxDepth -> {
                    // Max depth reached: Use fast rollout
                    val rolloutScore = fastRollout(state, isPlayerTurn, isTrainer)
                    return MinimaxResult(rolloutScore, -1, emptyList(), state)
                }

                depth >= config.strategicDepth -> {
                    // Strategic depth: Use beam search (top N moves only)
                    return beamSearchMinimax(
                        state, depth, maxDepth, config, isPlayerTurn, isTrainer, alpha, beta, forcedMove
                    )
                }

                else -> {
                    // Tactical depth: Full minimax (all moves)
                    return fullMinimax(
                        state, depth, maxDepth, config, isPlayerTurn, isTrainer, alpha, beta, forcedMove
                    )
                }
            }
        } finally {
            // Always decrement recursion depth counter
            recursionDepth--
        }
    }

    /**
     * Full minimax search with all moves considered.
     */
    private fun fullMinimax(
        state: BattleState,
        depth: Int,
        maxDepth: Int,
        config: SearchConfig,
        isPlayerTurn: Boolean,
        isTrainer: Boolean,
        alpha: Float,
        beta: Float,
        forcedMove: Int? = null
    ): MinimaxResult {
        // Check transposition table
        val stateHash = getStateHash(state)
        if (forcedMove == null && transpositionTable.containsKey(stateHash)) {
            return MinimaxResult(transpositionTable[stateHash]!!, -1, emptyList(), state)
        }

        if (isPlayerTurn) {
            // Maximizing player
            var maxScore = Float.NEGATIVE_INFINITY
            var bestMove = -1
            var bestLine = emptyList<Int>()
            var bestState = state
            var currentAlpha = alpha

            val moves = if (forcedMove != null) {
                listOf(forcedMove)
            } else {
                val allMoves = generateOrderedMoves(state, isPlayer = true)
                // Issue 2.16: Handle empty move list gracefully
                if (allMoves.isEmpty()) {
                    // No valid moves, return current state evaluation
                    return MinimaxResult(evaluateState(state), -1, emptyList(), state)
                }
                allMoves.take(4)  // Prune to top 4 moves
            }

            for (moveId in moves) {
                // Simulate player move followed by enemy response
                val enemyMove = predictEnemyMove(state.enemy.mon, state.player.mon, isTrainer)
                val newState = MoveSimulator.simulateMove(state, moveId, enemyMove)

                // Recurse for next turn (both moves executed, so next is player turn again)
                val result = minimax(
                    state = newState,
                    depth = depth + 1,
                    maxDepth = maxDepth,
                    config = config,
                    isPlayerTurn = true,  // After both moves, it's player turn again
                    isTrainer = isTrainer,
                    alpha = currentAlpha,
                    beta = beta
                )

                if (result.score > maxScore) {
                    maxScore = result.score
                    bestMove = moveId
                    bestLine = listOf(moveId) + result.line
                    bestState = result.finalState
                }

                // Issue 3.8: Early termination for instant wins
                if (maxScore == Float.POSITIVE_INFINITY) {
                    // Found a winning move, no need to search further
                    break
                }

                currentAlpha = maxOf(currentAlpha, maxScore)
                if (beta <= currentAlpha) {
                    break  // Beta cutoff
                }
            }

            // Cache result
            if (forcedMove == null) {
                transpositionTable[stateHash] = maxScore
            }

            return MinimaxResult(maxScore, bestMove, bestLine, bestState)
        } else {
            // This shouldn't happen in our model since we simulate both moves together
            // But if it does, just continue to next player turn
            return minimax(
                state = state,
                depth = depth,
                maxDepth = maxDepth,
                config = config,
                isPlayerTurn = true,
                isTrainer = isTrainer,
                alpha = alpha,
                beta = beta
            )
        }
    }

    /**
     * Beam search: Only consider top N moves based on quick evaluation.
     */
    private fun beamSearchMinimax(
        state: BattleState,
        depth: Int,
        maxDepth: Int,
        config: SearchConfig,
        isPlayerTurn: Boolean,
        isTrainer: Boolean,
        alpha: Float,
        beta: Float,
        forcedMove: Int? = null
    ): MinimaxResult {
        if (isPlayerTurn) {
            var maxScore = Float.NEGATIVE_INFINITY
            var bestMove = -1
            var bestLine = emptyList<Int>()
            var bestState = state
            var currentAlpha = alpha

            val moves = if (forcedMove != null) {
                listOf(forcedMove)
            } else {
                // Quick score each move (shallow evaluation)
                val allMoves = generateOrderedMoves(state, isPlayer = true)
                // Issue 2.16: Handle empty move list gracefully
                if (allMoves.isEmpty()) {
                    // No valid moves, return current state evaluation
                    return MinimaxResult(evaluateState(state), -1, emptyList(), state)
                }

                val scoredMoves = allMoves.map { moveId ->
                    val enemyMove = predictEnemyMove(state.enemy.mon, state.player.mon, isTrainer)
                    val newState = MoveSimulator.simulateMove(state, moveId, enemyMove)
                    moveId to evaluateState(newState)
                }.sortedByDescending { it.second }

                // Keep only top N moves (beam width)
                scoredMoves.take(config.beamWidth).map { it.first }
            }

            for (moveId in moves) {
                // Simulate player move followed by enemy response
                val enemyMove = predictEnemyMove(state.enemy.mon, state.player.mon, isTrainer)
                val newState = MoveSimulator.simulateMove(state, moveId, enemyMove)

                // Recurse for next turn (both moves executed, so next is player turn again)
                val result = minimax(
                    state = newState,
                    depth = depth + 1,
                    maxDepth = maxDepth,
                    config = config,
                    isPlayerTurn = true,  // After both moves, it's player turn again
                    isTrainer = isTrainer,
                    alpha = currentAlpha,
                    beta = beta
                )

                if (result.score > maxScore) {
                    maxScore = result.score
                    bestMove = moveId
                    bestLine = listOf(moveId) + result.line
                    bestState = result.finalState
                }

                // Issue 3.8: Early termination for instant wins
                if (maxScore == Float.POSITIVE_INFINITY) {
                    // Found a winning move, no need to search further
                    break
                }

                currentAlpha = maxOf(currentAlpha, maxScore)
                if (beta <= currentAlpha) {
                    break  // Beta cutoff
                }
            }

            return MinimaxResult(maxScore, bestMove, bestLine, bestState)
        } else {
            // This shouldn't happen in our model since we simulate both moves together
            // But if it does, just continue to next player turn
            return minimax(
                state = state,
                depth = depth,
                maxDepth = maxDepth,
                config = config,
                isPlayerTurn = true,
                isTrainer = isTrainer,
                alpha = alpha,
                beta = beta
            )
        }
    }

    /**
     * Fast rollout: Simulate to end using greedy strategy.
     * Returns estimated score without computing exact sequences.
     */
    private fun fastRollout(
        state: BattleState,
        isPlayerTurn: Boolean,
        isTrainer: Boolean,
        maxRolloutTurns: Int = 10
    ): Float {
        var currentState = state
        var turns = 0

        while (turns < maxRolloutTurns) {
            // Check terminal
            if (currentState.player.currentHp <= 0) return Float.NEGATIVE_INFINITY
            if (currentState.enemy.currentHp <= 0) return Float.POSITIVE_INFINITY

            // Player uses highest damage move
            val playerMove = findHighestDamageMove(currentState.player, currentState.enemy)

            // Enemy uses their best move (from AI)
            val enemyMove = predictEnemyMove(currentState.enemy.mon, currentState.player.mon, isTrainer)

            // Simulate one turn
            currentState = MoveSimulator.simulateMove(currentState, playerMove, enemyMove)
            turns++
        }

        // Return evaluation after rollout
        return evaluateState(currentState)
    }

    /**
     * Find highest damage move for a battler.
     * Issue 2.1: Validate move ID range to prevent crashes from invalid IDs
     * Issue 3.5: Cache damage results to avoid redundant calculations
     */
    private fun findHighestDamageMove(attacker: BattlerState, defender: BattlerState): Int {
        val validMove = attacker.mon.moves
            .filter { it in 1..MAX_MOVE_ID }
            .maxByOrNull { moveId ->
                // Use cache to avoid recalculating damage for same attacker/defender/move combo
                val cacheKey = Triple(attacker.currentHp, defender.currentHp, moveId)
                val damage = damageCache.getOrPut(cacheKey) {
                    val result = MoveSimulator.calculateDamageWithStages(attacker, defender, moveId)
                    result.maxDamage
                }
                damage
            }

        // If no valid damaging moves found, return -1 to signal error
        return validMove ?: -1
    }

    /**
     * Check if position is interesting enough to extend search.
     */
    private fun isInterestingPosition(state: BattleState): Boolean {
        // Extend if someone is in KO range
        val playerKORange = state.player.currentHp < state.player.mon.maxHp * 0.3
        val enemyKORange = state.enemy.currentHp < state.enemy.mon.maxHp * 0.3
        if (playerKORange || enemyKORange) return true

        // Extend if significant stat changes happened
        val totalPlayerStages = state.player.statStages.attack +
                                state.player.statStages.spAttack +
                                state.player.statStages.speed
        val totalEnemyStages = state.enemy.statStages.attack +
                               state.enemy.statStages.spAttack +
                               state.enemy.statStages.speed
        if (abs(totalPlayerStages) >= 3 || abs(totalEnemyStages) >= 3) return true

        // Extend if weather/terrain was just set
        if (state.weather != Weather.NONE) return true

        // Otherwise, not interesting
        return false
    }

    /**
     * Get hash for state (for transposition table).
     * Issue 3.7: Hash only battle-relevant fields to avoid collisions from irrelevant data
     * (personality, OT ID, experience, friendship don't affect battle outcomes).
     */
    private fun getStateHash(state: BattleState): Int {
        // Manual hash calculation for better control over which fields are included
        var result = 1

        // Player state - only battle-relevant fields
        result = 31 * result + state.player.currentHp
        result = 31 * result + state.player.statStages.hashCode()
        result = 31 * result + state.player.status
        result = 31 * result + state.player.tempBoosts.lockedMove
        result = 31 * result + state.player.tempBoosts.toxicCounter

        // Enemy state - only battle-relevant fields
        result = 31 * result + state.enemy.currentHp
        result = 31 * result + state.enemy.statStages.hashCode()
        result = 31 * result + state.enemy.status
        result = 31 * result + state.enemy.tempBoosts.lockedMove
        result = 31 * result + state.enemy.tempBoosts.toxicCounter

        // Battle environment
        result = 31 * result + state.weather.hashCode()
        result = 31 * result + state.terrain.hashCode()
        result = 31 * result + state.turn

        return result
    }

    /**
     * Generate moves sorted by priority (better moves first for alpha-beta pruning).
     */
    private fun generateOrderedMoves(state: BattleState, isPlayer: Boolean): List<Int> {
        val mon = if (isPlayer) state.player.mon else state.enemy.mon
        val attacker = if (isPlayer) state.player else state.enemy
        val defender = if (isPlayer) state.enemy else state.player
        // Issue 2.1: Validate move ID range
        val validMoves = mon.moves.filter { it in 1..MAX_MOVE_ID }

        // Quick score each move by damage potential
        return validMoves.sortedByDescending { moveId ->
            val moveData = PokemonData.getMoveData(moveId)
            if (moveData != null && moveData.power > 0) {
                // Damaging move - score by damage
                val result = MoveSimulator.calculateDamageWithStages(attacker, defender, moveId)
                result.maxDamage
            } else if (MoveEffects.isSetupMove(moveId)) {
                // Setup move - give moderate priority (unless Unaware curse is active)
                if (isPlayer && CurseEffects.isUnawareCurseActive(state.curses)) {
                    0  // Don't prioritize setup moves under Unaware curse
                } else {
                    50
                }
            } else {
                // Other moves (status, etc.)
                10
            }
        }
    }

    /**
     * Evaluate terminal state or current position.
     * Returns a score from the player's perspective (higher is better).
     */
    private fun evaluateState(state: BattleState): Float {
        // Battle over?
        if (state.player.currentHp <= 0) return Float.NEGATIVE_INFINITY
        if (state.enemy.currentHp <= 0) return Float.POSITIVE_INFINITY

        var score = 0f

        // Factor 1: HP advantage (40% weight)
        val playerHpPct = state.player.currentHp.toFloat() / state.player.mon.maxHp
        val enemyHpPct = state.enemy.currentHp.toFloat() / state.enemy.mon.maxHp
        score += (playerHpPct - enemyHpPct) * 40f

        // Factor 2: Stat stage advantage (30% weight)
        val playerStatAdvantage = state.player.statStages.attack +
                                 state.player.statStages.spAttack +
                                 state.player.statStages.speed
        val enemyStatAdvantage = state.enemy.statStages.attack +
                                state.enemy.statStages.spAttack +
                                state.enemy.statStages.speed
        score += (playerStatAdvantage - enemyStatAdvantage) * 10f

        // Factor 3: Speed advantage (10% weight)
        val playerSpeed = state.player.mon.speed * MoveSimulator.getStatMultiplier(state.player.statStages.speed)
        val enemySpeed = state.enemy.mon.speed * MoveSimulator.getStatMultiplier(state.enemy.statStages.speed)
        if (playerSpeed > enemySpeed) score += 10f

        // Factor 4: Status condition disadvantage (20% weight)
        if (state.player.status != 0) score -= 20f
        if (state.enemy.status != 0) score += 20f

        return score
    }

    /**
     * Generate all valid moves for the current battler.
     * Returns moves sorted by priority (attacks first, then setup moves).
     */
    private fun generateMoves(state: BattleState, isPlayer: Boolean): List<Int> {
        val mon = if (isPlayer) state.player.mon else state.enemy.mon
        // Issue 2.1: Validate move ID range
        val validMoves = mon.moves.filter { it in 1..MAX_MOVE_ID }

        // Separate attacks and setup moves
        val attackMoves = validMoves.filter {
            val moveData = PokemonData.getMoveData(it)
            moveData != null && moveData.power > 0
        }
        // If Unaware curse active, enemy ignores our stat stages - don't use setup moves
        val setupMoves = if (isPlayer && CurseEffects.isUnawareCurseActive(state.curses)) {
            emptyList()
        } else {
            validMoves.filter { MoveEffects.isSetupMove(it) }
        }

        // Prioritize attacks, then setup moves
        return attackMoves + setupMoves
    }

    /**
     * Build a BattleLine from a MinimaxResult.
     * Simulates the entire move sequence to get accurate damage/survival stats.
     */
    private fun buildBattleLineFromMinimax(
        initialState: BattleState,
        result: MinimaxResult,
        isTrainer: Boolean
    ): BattleLine {
        var state = initialState
        var totalDamageDealt = 0
        var totalDamageTaken = 0
        var turnsToKO = -1

        // Simulate each move in the line
        for ((turnIndex, moveId) in result.line.withIndex()) {
            val previousEnemyHp = state.enemy.currentHp
            val previousPlayerHp = state.player.currentHp

            // Predict enemy move
            val enemyMove = predictEnemyMove(state.enemy.mon, state.player.mon, isTrainer)

            // Simulate the turn
            state = MoveSimulator.simulateMove(state, moveId, enemyMove)

            // Track damage
            val damageDealt = previousEnemyHp - state.enemy.currentHp
            val damageTaken = previousPlayerHp - state.player.currentHp
            totalDamageDealt += damageDealt
            totalDamageTaken += damageTaken

            // Check if enemy KO'd
            if (state.enemy.currentHp <= 0 && turnsToKO < 0) {
                turnsToKO = turnIndex + 1
                break
            }

            // Check if player KO'd
            if (state.player.currentHp <= 0) {
                break
            }
        }

        // Calculate survival probability
        val survivalProb = LineEvaluator.calculateSurvivalProbability(
            state.player.currentHp,
            state.player.mon.maxHp
        )

        // Build description
        val description = LineEvaluator.buildDescription(result.line, turnsToKO, survivalProb)

        return BattleLine(
            moves = result.line,
            finalState = state,
            turnsToKO = turnsToKO,
            damageDealt = totalDamageDealt,
            damageTaken = totalDamageTaken,
            survivalProbability = survivalProb,
            score = 0f,  // Will be calculated later
            description = description
        )
    }
}

/**
 * Result from minimax search.
 */
data class MinimaxResult(
    val score: Float,
    val bestMove: Int,
    val line: List<Int>,
    val finalState: BattleState
)

/**
 * Speed comparison result.
 */
data class SpeedComparison(
    val playerOutspeeds: Boolean,
    val speedDifference: Int,
    val canOutspeedWithBoost: Boolean
)
