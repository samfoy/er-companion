package com.ercompanion.calc

import com.ercompanion.data.PokemonData
import com.ercompanion.data.Types
import kotlin.math.min
import kotlin.random.Random

/**
 * Simulates battle state changes from move execution.
 * Handles stat stages, damage calculation, and state transitions.
 *
 * MEMORY OPTIMIZATION NOTE (Issue 3.2):
 * Functions like executePlayerMove() and executeEnemyMove() create multiple BattleState copies
 * in sequence (lines ~200-260), which can create GC pressure during deep analysis.
 *
 * Optimization available: Use BattleStateBuilder to batch multiple updates into a single copy.
 * Current approach prioritizes readability. Apply builder pattern if profiling shows this is
 * a bottleneck (100 samples × 20 turns = 2000+ state objects per analysis).
 */
object MoveSimulator {

    /**
     * Simulate what happens when player uses a move against enemy.
     * Assumes player moves first (speed checking should be done externally).
     *
     * @param state Current battle state
     * @param playerMoveId Move ID the player is using
     * @param enemyMoveId Move ID the enemy is using (or -1 if unknown/predicted)
     * @return New battle state after both moves execute
     */
    fun simulateMove(
        state: BattleState,
        playerMoveId: Int,
        enemyMoveId: Int = -1
    ): BattleState {
        var currentState = state

        // Torment curse: Player can't use same move twice in a row
        if (CurseEffects.isTormentCurseActive(currentState.curses)) {
            if (playerMoveId == currentState.player.tempBoosts.lastUsedMove && playerMoveId != 0) {
                // Move is blocked by Torment
                val newPlayer = currentState.player.copy(
                    tempBoosts = currentState.player.tempBoosts.copy(lastUsedMove = 0)
                )
                return currentState.copy(
                    player = newPlayer,
                    turn = currentState.turn + 1
                )
            }
        }

        // Check Choice item lock for player
        var actualPlayerMove = playerMoveId
        if (currentState.player.tempBoosts.lockedMove != 0 &&
            currentState.player.tempBoosts.lockedMove != playerMoveId) {
            // Locked into a move, force the locked move instead
            actualPlayerMove = currentState.player.tempBoosts.lockedMove
        }

        // Check if player is incapacitated by status before moving
        val playerCanMove = checkCanMove(currentState.player)
        if (!playerCanMove) {
            // Player can't move due to status, update status and skip turn
            currentState = updateStatusCondition(currentState, isPlayer = true)
            // Execute enemy move
            if (currentState.enemy.currentHp > 0 && enemyMoveId > 0) {
                val enemyCanMove = checkCanMove(currentState.enemy)
                if (enemyCanMove) {
                    currentState = executeEnemyMove(currentState, enemyMoveId)
                } else {
                    currentState = updateStatusCondition(currentState, isPlayer = false)
                }
            }
            // Apply end-of-turn effects in correct order (Gen 3 mechanics)
            currentState = applyAllEndOfTurnEffects(currentState)
            return currentState.copy(turn = currentState.turn + 1)
        }

        // Determine who moves first (considering priority and speed)
        val playerMovesFirst = calculateMoveOrder(
            player = state.player,
            enemy = state.enemy,
            playerMoveId = actualPlayerMove,
            enemyMoveId = enemyMoveId,
            weather = currentState.weather,
            curses = currentState.curses
        )

        // Execute moves in order
        if (playerMovesFirst) {
            currentState = executePlayerMove(currentState, actualPlayerMove)

            // Lock into move if Choice item (only for damaging moves)
            val moveData = PokemonData.getMoveData(actualPlayerMove)
            if (moveData != null && moveData.power > 0 &&
                ItemEffects.isChoiceItem(currentState.player.mon.heldItem)) {
                currentState = currentState.copy(
                    player = currentState.player.copy(
                        tempBoosts = currentState.player.tempBoosts.copy(
                            lockedMove = actualPlayerMove,
                            lockedTurns = currentState.player.tempBoosts.lockedTurns + 1
                        )
                    )
                )
            }

            // Apply Life Orb recoil for player
            if (moveData != null && moveData.power > 0) {
                currentState = ItemEffects.applyLifeOrbRecoil(currentState, isPlayer = true, moveUsed = true)
            }

            // Only execute enemy move if they're still alive
            if (currentState.enemy.currentHp > 0 && enemyMoveId > 0) {
                val enemyCanMove = checkCanMove(currentState.enemy)
                if (enemyCanMove) {
                    currentState = executeEnemyMove(currentState, enemyMoveId)

                    // Apply Life Orb recoil for enemy
                    val enemyMoveData = PokemonData.getMoveData(enemyMoveId)
                    if (enemyMoveData != null && enemyMoveData.power > 0) {
                        currentState = ItemEffects.applyLifeOrbRecoil(currentState, isPlayer = false, moveUsed = true)
                    }
                } else {
                    currentState = updateStatusCondition(currentState, isPlayer = false)
                }
            }
        } else {
            // Enemy moves first (if they can)
            if (enemyMoveId > 0) {
                val enemyCanMove = checkCanMove(currentState.enemy)
                if (enemyCanMove) {
                    currentState = executeEnemyMove(currentState, enemyMoveId)

                    // Apply Life Orb recoil for enemy
                    val enemyMoveData = PokemonData.getMoveData(enemyMoveId)
                    if (enemyMoveData != null && enemyMoveData.power > 0) {
                        currentState = ItemEffects.applyLifeOrbRecoil(currentState, isPlayer = false, moveUsed = true)
                    }
                } else {
                    currentState = updateStatusCondition(currentState, isPlayer = false)
                }
            }
            // Only execute player move if they're still alive
            if (currentState.player.currentHp > 0) {
                currentState = executePlayerMove(currentState, actualPlayerMove)

                // Lock into move if Choice item
                val moveData = PokemonData.getMoveData(actualPlayerMove)
                if (moveData != null && moveData.power > 0 &&
                    ItemEffects.isChoiceItem(currentState.player.mon.heldItem)) {
                    currentState = currentState.copy(
                        player = currentState.player.copy(
                            tempBoosts = currentState.player.tempBoosts.copy(
                                lockedMove = actualPlayerMove,
                                lockedTurns = currentState.player.tempBoosts.lockedTurns + 1
                            )
                        )
                    )
                }

                // Apply Life Orb recoil for player
                if (moveData != null && moveData.power > 0) {
                    currentState = ItemEffects.applyLifeOrbRecoil(currentState, isPlayer = true, moveUsed = true)
                }
            }
        }

        // Issue 3.10: Apply end-of-turn effects in batches to reduce state copies
        // Gen 3 mechanics: weather → status → items → abilities → terrain
        currentState = applyAllEndOfTurnEffects(currentState)

        return currentState.copy(turn = currentState.turn + 1)
    }

    /**
     * Execute player's move.
     */
    private fun executePlayerMove(state: BattleState, moveId: Int): BattleState {
        val moveData = PokemonData.getMoveData(moveId)
        val moveEffect = MoveEffects.getStatChanges(moveId)
        val statusEffect = StatusMoves.getStatusEffect(moveId)

        var newState = state

        // Check if move sets weather/terrain
        val playerWeather = MoveEffects.setsWeather(moveId)
        if (playerWeather != null) {
            newState = WeatherEffects.setWeather(newState, playerWeather)
        }

        val playerTerrain = MoveEffects.setsTerrain(moveId)
        if (playerTerrain != null) {
            newState = newState.copy(terrain = playerTerrain)
        }

        // If it's a damaging move, calculate damage
        if (moveData != null && moveData.power > 0) {
            val damageResult = calculateDamageWithStages(
                attacker = newState.player,
                defender = newState.enemy,
                moveId = moveId,
                weather = newState.weather,
                terrain = newState.terrain
            )

            val newEnemyHp = (state.enemy.currentHp - damageResult.maxDamage).coerceAtLeast(0)
            newState = newState.copy(
                enemy = newState.enemy.copy(currentHp = newEnemyHp)
            )

            // Apply KO abilities (Moxie, Beast Boost) if enemy was KO'd
            if (newEnemyHp <= 0) {
                newState = AbilityEffects.applyKOAbility(newState, isPlayerKO = true)
            }
        }

        // If it's a stat-changing move, apply the stat changes
        if (moveEffect != null) {
            when (moveEffect.target) {
                MoveEffects.EffectTarget.USER -> {
                    // Boost player stats
                    val newStages = state.player.statStages.applyChanges(moveEffect.changes)
                    newState = newState.copy(
                        player = newState.player.copy(statStages = newStages)
                    )
                }
                MoveEffects.EffectTarget.TARGET -> {
                    // Lower enemy stats
                    val newStages = state.enemy.statStages.applyChanges(moveEffect.changes)
                    newState = newState.copy(
                        enemy = newState.enemy.copy(statStages = newStages)
                    )
                }
            }
        }

        // Check for status effect infliction
        if (statusEffect != null && newState.enemy.currentHp > 0) {
            // Can only inflict status if target doesn't already have one
            if (newState.enemy.status == StatusConditions.NONE) {
                // Use deterministic random based on state hash and move ID for reproducible simulations
                val random = Random(newState.hashCode() + newState.turn + moveId)

                // Apply Serene Grace curse to enemy status moves (note: player is inflicting, so no curse boost)
                val baseChance = statusEffect.chance
                val effectiveChance = baseChance

                // Roll for status chance
                if (random.nextFloat() < effectiveChance) {
                    // Gen 5+: Sleep lasts 1-3 turns (randomly determined on infliction)
                    val finalStatus = if (StatusConditions.isSleep(statusEffect.condition)) {
                        // Random 1-3 turns for sleep
                        when (random.nextInt(3)) {
                            0 -> StatusConditions.SLEEP_1
                            1 -> StatusConditions.SLEEP_2
                            else -> StatusConditions.SLEEP_3
                        }
                    } else {
                        statusEffect.condition
                    }

                    newState = newState.copy(
                        enemy = newState.enemy.copy(status = finalStatus)
                    )
                }
            }
        }

        // Track last used move for Torment curse
        newState = newState.copy(
            player = newState.player.copy(
                tempBoosts = newState.player.tempBoosts.copy(lastUsedMove = moveId)
            )
        )

        return newState
    }

    /**
     * Execute enemy's move.
     */
    private fun executeEnemyMove(state: BattleState, moveId: Int): BattleState {
        if (moveId <= 0) return state

        val moveData = PokemonData.getMoveData(moveId)
        val moveEffect = MoveEffects.getStatChanges(moveId)
        val statusEffect = StatusMoves.getStatusEffect(moveId)

        var newState = state

        // Check if move sets weather/terrain
        val enemyWeather = MoveEffects.setsWeather(moveId)
        if (enemyWeather != null) {
            newState = WeatherEffects.setWeather(newState, enemyWeather)
        }

        val enemyTerrain = MoveEffects.setsTerrain(moveId)
        if (enemyTerrain != null) {
            newState = newState.copy(terrain = enemyTerrain)
        }

        // If it's a damaging move, calculate damage
        if (moveData != null && moveData.power > 0) {
            val damageResult = calculateDamageWithStages(
                attacker = newState.enemy,
                defender = newState.player,
                moveId = moveId,
                weather = newState.weather,
                terrain = newState.terrain
            )

            val newPlayerHp = (state.player.currentHp - damageResult.maxDamage).coerceAtLeast(0)
            newState = newState.copy(
                player = newState.player.copy(currentHp = newPlayerHp)
            )

            // Apply KO abilities (Moxie, Beast Boost) if player was KO'd
            if (newPlayerHp <= 0) {
                newState = AbilityEffects.applyKOAbility(newState, isPlayerKO = false)
            }
        }

        // Apply enemy stat changes (if any)
        if (moveEffect != null) {
            when (moveEffect.target) {
                MoveEffects.EffectTarget.USER -> {
                    // Enemy boosts their own stats
                    val newStages = state.enemy.statStages.applyChanges(moveEffect.changes)
                    newState = newState.copy(
                        enemy = newState.enemy.copy(statStages = newStages)
                    )
                }
                MoveEffects.EffectTarget.TARGET -> {
                    // Enemy lowers player stats
                    val newStages = state.player.statStages.applyChanges(moveEffect.changes)
                    newState = newState.copy(
                        player = newState.player.copy(statStages = newStages)
                    )
                }
            }
        }

        // Check for status effect infliction
        if (statusEffect != null && newState.player.currentHp > 0) {
            // Can only inflict status if target doesn't already have one
            if (newState.player.status == StatusConditions.NONE) {
                // Use deterministic random based on state hash and move ID for reproducible simulations
                val random = Random(newState.hashCode() + newState.turn + moveId)

                // Apply Serene Grace curse to enemy status moves
                val baseChance = statusEffect.chance
                val multiplier = CurseEffects.getEnemySecondaryEffectMultiplier(newState.curses)
                val effectiveChance = (baseChance * multiplier).coerceAtMost(1.0f)

                // Roll for status chance
                if (random.nextFloat() < effectiveChance) {
                    // Gen 5+: Sleep lasts 1-3 turns (randomly determined on infliction)
                    val finalStatus = if (StatusConditions.isSleep(statusEffect.condition)) {
                        // Random 1-3 turns for sleep
                        when (random.nextInt(3)) {
                            0 -> StatusConditions.SLEEP_1
                            1 -> StatusConditions.SLEEP_2
                            else -> StatusConditions.SLEEP_3
                        }
                    } else {
                        statusEffect.condition
                    }

                    newState = newState.copy(
                        player = newState.player.copy(status = finalStatus)
                    )
                }
            }
        }

        return newState
    }

    /**
     * Calculate damage with current stat stages applied.
     * Returns a DamageResult based on the attacker's boosted stats.
     */
    fun calculateDamageWithStages(
        attacker: BattlerState,
        defender: BattlerState,
        moveId: Int,
        weather: Weather = Weather.NONE,
        terrain: Terrain = Terrain.NONE
    ): DamageResult {
        val moveData = PokemonData.getMoveData(moveId) ?: return DamageResult(
            moveName = "Unknown",
            minDamage = 0,
            maxDamage = 0,
            effectiveness = 0f,
            effectLabel = "Unknown move",
            percentMin = 0,
            percentMax = 0,
            isStab = false,
            wouldKO = false,
            isValid = false
        )

        // Get base stats
        val baseAttack = if (moveData.category == 0) attacker.mon.attack else attacker.mon.spAttack
        val baseDefense = if (moveData.category == 0) defender.mon.defense else defender.mon.spDefense

        // Apply stat stage multipliers
        val attackStage = if (moveData.category == 0) attacker.statStages.attack else attacker.statStages.spAttack
        val defenseStage = if (moveData.category == 0) defender.statStages.defense else defender.statStages.spDefense

        val modifiedAttack = (baseAttack * getStatMultiplier(attackStage)).toInt()
        val modifiedDefense = (baseDefense * getStatMultiplier(defenseStage)).toInt()

        // Get types
        val attackerTypes = PokemonData.getSpeciesTypes(attacker.mon.species)
        val defenderTypes = PokemonData.getSpeciesTypes(defender.mon.species)

        // Check if attacker is burned (for physical moves only)
        val isBurned = StatusConditions.isBurned(attacker.status) && moveData.category == 0

        // Calculate base damage using existing DamageCalculator
        val baseDamageResult = DamageCalculator.calc(
            attackerLevel = attacker.mon.level,
            attackStat = modifiedAttack,
            defenseStat = modifiedDefense,
            movePower = moveData.power,
            moveType = moveData.type,
            attackerTypes = attackerTypes,
            defenderTypes = defenderTypes,
            targetMaxHP = defender.mon.maxHp,
            moveName = moveData.name,
            attackerItem = attacker.mon.heldItem,
            defenderItem = defender.mon.heldItem,
            attackerHp = attacker.currentHp,
            attackerMaxHp = attacker.mon.maxHp,
            attackerAbility = attacker.mon.ability,
            defenderAbility = defender.mon.ability,
            isBurned = isBurned,
            moveCategory = moveData.category
        )

        // Apply weather multiplier
        val weatherMult = WeatherEffects.getWeatherMultiplier(weather, moveData.type, attacker)

        // Apply terrain multiplier
        val isGrounded = !defenderTypes.contains(Types.FLYING) && defender.mon.ability != 26  // Flying type or Levitate
        val terrainMult = TerrainEffects.getTerrainMultiplier(terrain, moveData.type, attacker, isGrounded)

        // Combine multipliers
        val totalMultiplier = weatherMult * terrainMult

        // Apply multipliers to damage if they're not 1.0
        if (totalMultiplier != 1.0f) {
            val finalMinDamage = (baseDamageResult.minDamage * totalMultiplier).toInt()
            val finalMaxDamage = (baseDamageResult.maxDamage * totalMultiplier).toInt()

            // Recalculate percentages
            val percentMin = if (defender.mon.maxHp > 0) (finalMinDamage * 100 / defender.mon.maxHp) else 0
            val percentMax = if (defender.mon.maxHp > 0) (finalMaxDamage * 100 / defender.mon.maxHp) else 0

            return baseDamageResult.copy(
                minDamage = finalMinDamage,
                maxDamage = finalMaxDamage,
                percentMin = percentMin,
                percentMax = percentMax,
                wouldKO = percentMax >= 100
            )
        }

        return baseDamageResult
    }

    /**
     * Calculate stat stage multiplier.
     * Gen 3+ formula: stages -6 to +6
     * Multiplier = (2 + max(0, stage)) / (2 + max(0, -stage))
     *
     * Stage | Multiplier
     * ------|------------
     *  -6   | 2/8  = 0.25x
     *  -5   | 2/7  = 0.286x
     *  -4   | 2/6  = 0.333x
     *  -3   | 2/5  = 0.4x
     *  -2   | 2/4  = 0.5x
     *  -1   | 2/3  = 0.667x
     *   0   | 2/2  = 1.0x
     *  +1   | 3/2  = 1.5x
     *  +2   | 4/2  = 2.0x
     *  +3   | 5/2  = 2.5x
     *  +4   | 6/2  = 3.0x
     *  +5   | 7/2  = 3.5x
     *  +6   | 8/2  = 4.0x
     */
    fun getStatMultiplier(stage: Int): Float {
        val clampedStage = stage.coerceIn(-6, 6)
        return if (clampedStage >= 0) {
            (2 + clampedStage).toFloat() / 2f
        } else {
            2f / (2 - clampedStage).toFloat()
        }
    }

    /**
     * Apply stat changes from a move (for convenience).
     */
    fun applyStatChanges(stages: StatStages, moveId: Int): StatStages {
        val moveEffect = MoveEffects.getStatChanges(moveId) ?: return stages
        return stages.applyChanges(moveEffect.changes)
    }

    /**
     * Simulate switch-in (for future switch tracking).
     *
     * @param state Current battle state
     * @param isPlayer True if the player's Pokemon is switching in
     * @return Updated battle state with switch-in ability effects applied
     */
    fun simulateSwitchIn(
        state: BattleState,
        isPlayer: Boolean
    ): BattleState {
        val battler = if (isPlayer) state.player else state.enemy

        // Reset temp boosts including toxic counter on switch
        val resetBattler = battler.copy(
            tempBoosts = TempBoosts(),  // Reset all temp boosts including toxic counter
            statStages = StatStages()   // Reset stat stages on switch
        )

        val newState = if (isPlayer) {
            state.copy(player = resetBattler)
        } else {
            state.copy(enemy = resetBattler)
        }

        // Apply switch-in abilities (Intimidate, Drought, etc.)
        return AbilityEffects.applySwitchInAbility(newState, isPlayer)
    }

    /**
     * Calculate move order based on priority and speed.
     * Returns true if player moves first.
     *
     * Priority brackets: -6 to +5 (Quick Attack = +1, Extremespeed = +2, etc.)
     * Higher priority always goes first, regardless of speed.
     * Within same priority, faster Pokemon goes first.
     * Speed ties are decided randomly (50/50).
     */
    fun calculateMoveOrder(
        player: BattlerState,
        enemy: BattlerState,
        playerMoveId: Int = 0,
        enemyMoveId: Int = 0,
        weather: Weather = Weather.NONE,
        curses: CurseState = CurseState.NONE
    ): Boolean {
        // Get move priorities
        val playerPriority = getMovePriority(playerMoveId, isEnemy = false, curses = curses)
        val enemyPriority = getMovePriority(enemyMoveId, isEnemy = true, curses = curses)

        // Higher priority moves always go first
        if (playerPriority != enemyPriority) {
            return playerPriority > enemyPriority
        }

        // Same priority: compare speed
        val playerSpeed = getEffectiveSpeed(player, weather)
        val enemySpeed = getEffectiveSpeed(enemy, weather)

        // If tied, random 50/50 (Gen 3 mechanics)
        return if (playerSpeed == enemySpeed) {
            Random.nextBoolean()
        } else {
            playerSpeed > enemySpeed
        }
    }

    /**
     * Get effective priority of a move, including curse modifiers.
     *
     * @param moveId The move ID
     * @param isEnemy Whether this is the enemy's move (for priority curse)
     * @param curses Current curse state
     * @return Effective priority (-6 to +5, or higher with curses)
     */
    private fun getMovePriority(moveId: Int, isEnemy: Boolean, curses: CurseState): Int {
        val moveData = PokemonData.getMoveData(moveId) ?: return 0
        var priority = moveData.priority

        // Priority curse: Enemy moves have 10% chance per curse to gain +1 priority
        if (isEnemy && CurseEffects.shouldPriorityBoostOccur(curses)) {
            priority += 1
        }

        return priority
    }

    /**
     * Get effective speed of a battler, accounting for stat stages and status conditions.
     */
    fun getEffectiveSpeed(battler: BattlerState, weather: Weather = Weather.NONE): Int {
        // Apply speed stage modifiers
        var speed = (battler.mon.speed * getStatMultiplier(battler.statStages.speed)).toInt()

        // Paralysis reduces speed to 25% (Gen 7+) or 50% (Gen 3-6)
        // Using Gen 3 mechanics (50%) for Pokemon Emerald
        if (StatusConditions.isParalyzed(battler.status)) {
            speed = (speed * 0.5f).toInt()
        }

        // Apply weather speed abilities (Chlorophyll, Swift Swim, etc.)
        val weatherSpeedMult = AbilityEffects.getWeatherSpeedMultiplier(battler, weather)
        speed = (speed * weatherSpeedMult).toInt()

        // Apply item speed modifiers (Choice Scarf, Iron Ball)
        val itemSpeedMult = ItemEffects.getSpeedMultiplier(battler.mon.heldItem)
        speed = (speed * itemSpeedMult).toInt()

        return speed
    }

    /**
     * Check if a battler can move this turn based on their status condition.
     * Returns false if they're incapacitated (asleep, frozen, or fully paralyzed).
     * Note: Sleep counter should be decremented BEFORE calling this, on move attempt.
     */
    private fun checkCanMove(battler: BattlerState): Boolean {
        // Asleep: can't move (counter should be decremented before this check)
        if (StatusConditions.isAsleep(battler.status)) {
            return false
        }

        // Frozen: 20% chance to thaw
        if (StatusConditions.isFrozen(battler.status)) {
            return Random.nextFloat() < 0.2f  // 20% thaw chance
        }

        // Paralyzed: 25% chance of full paralysis
        if (StatusConditions.isParalyzed(battler.status)) {
            return Random.nextFloat() >= 0.25f  // 75% chance to move
        }

        return true
    }

    /**
     * Update status condition counters.
     * Decrements sleep counter when Pokemon attempts to move (Gen 3 mechanics).
     * Frozen status is checked in checkCanMove.
     */
    private fun updateStatusCondition(state: BattleState, isPlayer: Boolean): BattleState {
        val battler = if (isPlayer) state.player else state.enemy

        var newStatus = battler.status

        // Decrement sleep counter on move attempt (Gen 5+: 1-3 turns)
        if (StatusConditions.isAsleep(newStatus)) {
            newStatus = (newStatus - 1).coerceAtLeast(0)
        }

        // Shed Skin curse: Enemy has increased chance to cure status
        if (!isPlayer && battler.status != 0) {
            val shedSkinChance = CurseEffects.getEnemyShedSkinBoost(state.curses)
            if (shedSkinChance > 0) {
                val random = Random(state.hashCode() + state.turn + 1000)  // Deterministic
                if (random.nextFloat() < shedSkinChance) {
                    newStatus = StatusConditions.NONE
                }
            }
        }

        // Note: Frozen thaw is checked in checkCanMove() - don't duplicate here
        // Removing duplicate freeze check (was causing double-rolling of thaw chance)

        val newBattler = battler.copy(status = newStatus)
        return if (isPlayer) {
            state.copy(player = newBattler)
        } else {
            state.copy(enemy = newBattler)
        }
    }

    /**
     * Apply damage from status conditions at the end of the turn.
     */
    private fun applyStatusDamage(state: BattleState, isPlayer: Boolean): BattleState {
        val battler = if (isPlayer) state.player else state.enemy
        val maxHp = battler.mon.maxHp
        var damage = 0

        when {
            StatusConditions.isBurned(battler.status) -> {
                // Burn: 1/16 max HP per turn
                damage = maxHp / 16
            }
            StatusConditions.isToxic(battler.status) -> {
                // Toxic: N/16 max HP (increases each turn)
                val toxicCounter = battler.tempBoosts.toxicCounter + 1
                damage = (maxHp * toxicCounter) / 16

                // Update toxic counter
                val newBattler = battler.copy(
                    tempBoosts = battler.tempBoosts.copy(toxicCounter = toxicCounter)
                )
                val stateWithCounter = if (isPlayer) {
                    state.copy(player = newBattler)
                } else {
                    state.copy(enemy = newBattler)
                }

                // Apply damage
                val newHp = (newBattler.currentHp - damage).coerceAtLeast(0)
                return if (isPlayer) {
                    stateWithCounter.copy(player = stateWithCounter.player.copy(currentHp = newHp))
                } else {
                    stateWithCounter.copy(enemy = stateWithCounter.enemy.copy(currentHp = newHp))
                }
            }
            StatusConditions.isPoisoned(battler.status) && !StatusConditions.isToxic(battler.status) -> {
                // Regular poison: 1/8 max HP per turn
                damage = maxHp / 8
            }
        }

        // Apply damage
        val newHp = (battler.currentHp - damage).coerceAtLeast(0)
        return if (isPlayer) {
            state.copy(player = state.player.copy(currentHp = newHp))
        } else {
            state.copy(enemy = state.enemy.copy(currentHp = newHp))
        }
    }

    /**
     * Issue 3.10: Batch all end-of-turn effects to reduce intermediate state copies.
     * Applies effects in the correct order for Gen 3 mechanics.
     */
    private fun applyAllEndOfTurnEffects(state: BattleState): BattleState {
        var newState = state

        // 1. Weather damage (both players)
        newState = WeatherEffects.applyWeatherDamage(newState, isPlayer = true)
        newState = WeatherEffects.applyWeatherDamage(newState, isPlayer = false)

        // 2. Status damage (burn, poison, toxic)
        if (newState.player.currentHp > 0) {
            newState = applyStatusDamage(newState, isPlayer = true)
        }
        if (newState.enemy.currentHp > 0) {
            newState = applyStatusDamage(newState, isPlayer = false)
        }

        // 3. Items (Leftovers, Black Sludge, Status Orbs)
        newState = ItemEffects.applyEndOfTurnHealing(newState, isPlayer = true)
        newState = ItemEffects.applyEndOfTurnHealing(newState, isPlayer = false)
        newState = ItemEffects.applyStatusOrb(newState, isPlayer = true, newState.turn)
        newState = ItemEffects.applyStatusOrb(newState, isPlayer = false, newState.turn)

        // 4. Abilities (Speed Boost, Shed Skin)
        newState = AbilityEffects.applyEndOfTurnAbilities(newState)

        // 5. Terrain effects (not in Gen 3, but included for completeness)
        newState = TerrainEffects.applyTerrainHealing(newState, isPlayer = true)
        newState = TerrainEffects.applyTerrainHealing(newState, isPlayer = false)

        return newState
    }
}
