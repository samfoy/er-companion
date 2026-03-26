package com.ercompanion.calc

/**
 * Builder pattern for BattleState to reduce object allocations.
 *
 * Issue 3.2 (CODE_REVIEW_ISSUES.md): Multiple sequential .copy() calls on BattleState
 * create intermediate objects that increase GC pressure during deep analysis.
 *
 * This builder allows batching multiple changes before creating the final BattleState.
 *
 * Example usage:
 * ```
 * val newState = BattleStateBuilder(state)
 *     .updateEnemyHp(newHp)
 *     .updateEnemyStatStages(newStages)
 *     .updateEnemyStatus(newStatus)
 *     .build()  // Single copy operation
 * ```
 *
 * vs the current approach:
 * ```
 * var newState = state.copy(enemy = state.enemy.copy(currentHp = newHp))
 * newState = newState.copy(enemy = newState.enemy.copy(statStages = newStages))
 * newState = newState.copy(enemy = newState.enemy.copy(status = newStatus))
 * // 3 BattleState objects created
 * ```
 *
 * Performance impact: In deep analysis with 100 samples × 20 turns, this could save
 * thousands of intermediate object allocations.
 *
 * Note: This is currently unused. To apply the optimization:
 * 1. Replace sequential .copy() calls in MoveSimulator.kt with builder
 * 2. Profile to verify the optimization provides meaningful benefit
 * 3. Ensure all tests still pass
 */
class BattleStateBuilder(private val initialState: BattleState) {
    private var player: BattlerState = initialState.player
    private var enemy: BattlerState = initialState.enemy
    private var turn: Int = initialState.turn
    private var weather: Weather = initialState.weather
    private var terrain: Terrain = initialState.terrain
    private var curses: CurseState = initialState.curses

    fun updatePlayerHp(hp: Int): BattleStateBuilder {
        player = player.copy(currentHp = hp)
        return this
    }

    fun updateEnemyHp(hp: Int): BattleStateBuilder {
        enemy = enemy.copy(currentHp = hp)
        return this
    }

    fun updatePlayerStatStages(stages: StatStages): BattleStateBuilder {
        player = player.copy(statStages = stages)
        return this
    }

    fun updateEnemyStatStages(stages: StatStages): BattleStateBuilder {
        enemy = enemy.copy(statStages = stages)
        return this
    }

    fun updatePlayerStatus(status: Int): BattleStateBuilder {
        player = player.copy(status = status)
        return this
    }

    fun updateEnemyStatus(status: Int): BattleStateBuilder {
        enemy = enemy.copy(status = status)
        return this
    }

    fun updateWeather(newWeather: Weather): BattleStateBuilder {
        weather = newWeather
        return this
    }

    fun updateTerrain(newTerrain: Terrain): BattleStateBuilder {
        terrain = newTerrain
        return this
    }

    fun updateTurn(newTurn: Int): BattleStateBuilder {
        turn = newTurn
        return this
    }

    fun updateCurses(newCurses: CurseState): BattleStateBuilder {
        curses = newCurses
        return this
    }

    /**
     * Build the final BattleState with all accumulated changes.
     * This is the only copy operation, regardless of how many updates were made.
     */
    fun build(): BattleState {
        return BattleState(
            player = player,
            enemy = enemy,
            turn = turn,
            weather = weather,
            terrain = terrain,
            curses = curses
        )
    }
}
