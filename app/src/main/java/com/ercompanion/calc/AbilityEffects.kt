package com.ercompanion.calc

import com.ercompanion.data.PokemonData
import com.ercompanion.data.Types
import com.ercompanion.parser.PartyMon
import kotlin.random.Random

/**
 * Handles ability effects that trigger during battle.
 * Abilities are categorized by when they trigger:
 * - Switch-in abilities (Intimidate, Download)
 * - On-KO abilities (Moxie, Beast Boost)
 * - End-of-turn abilities (Speed Boost, Shed Skin)
 * - Stat multiplier abilities (Huge Power, Guts, Marvel Scale)
 * - Move power modifiers (Technician, Iron Fist, Tough Claws, Sheer Force)
 */
object AbilityEffects {

    // Ability ID constants (from Gen 3+)
    private const val INTIMIDATE = 22
    private const val MOXIE = 153
    private const val SPEED_BOOST = 3
    private const val HUGE_POWER = 37
    private const val PURE_POWER = 74
    private const val GUTS = 62
    private const val TECHNICIAN = 101
    private const val IRON_FIST = 89
    private const val SHEER_FORCE = 125
    private const val BEAST_BOOST = 224
    private const val DOWNLOAD = 88
    private const val MARVEL_SCALE = 63
    private const val FUR_COAT = 169
    private const val SHED_SKIN = 61
    private const val TOUGH_CLAWS = 181
    private const val STRONG_JAW = 173
    private const val JUSTIFIED = 154
    private const val WEAK_ARMOR = 124
    private const val REGENERATOR = 144
    private const val ADAPTABILITY = 91
    private const val FLASH_FIRE = 18
    private const val DROUGHT = 70
    private const val DRIZZLE = 2
    private const val SAND_STREAM = 45
    private const val SNOW_WARNING = 117
    private const val DESOLATE_LAND = 190
    private const val PRIMORDIAL_SEA = 189
    private const val DELTA_STREAM = 191
    private const val CHLOROPHYLL = 34
    private const val SWIFT_SWIM = 33
    private const val SAND_RUSH = 146
    private const val SLUSH_RUSH = 202

    /**
     * Apply switch-in ability effects.
     * Called when a Pokemon enters the battle.
     *
     * @param state Current battle state
     * @param isPlayer True if the player's Pokemon is switching in
     * @return Updated battle state with switch-in ability effects applied
     */
    fun applySwitchInAbility(
        state: BattleState,
        isPlayer: Boolean
    ): BattleState {
        val battler = if (isPlayer) state.player else state.enemy
        val opponent = if (isPlayer) state.enemy else state.player
        var newState = state

        when (battler.mon.ability) {
            INTIMIDATE -> {
                // Lower opponent's Attack by 1 stage
                val newOpponent = opponent.copy(
                    statStages = opponent.statStages.copy(
                        attack = maxOf(-6, opponent.statStages.attack - 1)
                    )
                )
                newState = if (isPlayer) {
                    newState.copy(enemy = newOpponent)
                } else {
                    newState.copy(player = newOpponent)
                }
            }

            DOWNLOAD -> {
                // Raise Attack or SpAtk based on opponent's lower defense
                val oppDefense = opponent.mon.defense * MoveSimulator.getStatMultiplier(opponent.statStages.defense)
                val oppSpDefense = opponent.mon.spDefense * MoveSimulator.getStatMultiplier(opponent.statStages.spDefense)

                val newStages = if (oppDefense < oppSpDefense) {
                    battler.statStages.copy(attack = minOf(6, battler.statStages.attack + 1))
                } else {
                    battler.statStages.copy(spAttack = minOf(6, battler.statStages.spAttack + 1))
                }

                val newBattler = battler.copy(statStages = newStages)
                newState = if (isPlayer) {
                    newState.copy(player = newBattler)
                } else {
                    newState.copy(enemy = newBattler)
                }
            }

            // Weather-setting abilities
            DROUGHT -> {
                newState = newState.copy(weather = Weather.SUN)
            }

            DRIZZLE -> {
                newState = newState.copy(weather = Weather.RAIN)
            }

            SAND_STREAM -> {
                newState = newState.copy(weather = Weather.SANDSTORM)
            }

            SNOW_WARNING -> {
                newState = newState.copy(weather = Weather.HAIL)
            }

            DESOLATE_LAND -> {
                newState = newState.copy(weather = Weather.HARSH_SUN)
            }

            PRIMORDIAL_SEA -> {
                newState = newState.copy(weather = Weather.HEAVY_RAIN)
            }

            DELTA_STREAM -> {
                newState = newState.copy(weather = Weather.STRONG_WINDS)
            }
        }

        return newState
    }

    /**
     * Apply abilities that trigger when landing a KO.
     *
     * @param state Current battle state (after KO has occurred)
     * @param isPlayerKO True if the player scored the KO
     * @return Updated battle state with KO ability effects applied
     */
    fun applyKOAbility(
        state: BattleState,
        isPlayerKO: Boolean
    ): BattleState {
        val battler = if (isPlayerKO) state.player else state.enemy

        return when (battler.mon.ability) {
            MOXIE -> {
                // +1 Attack on KO
                val newBattler = battler.copy(
                    statStages = battler.statStages.copy(
                        attack = minOf(6, battler.statStages.attack + 1)
                    )
                )
                if (isPlayerKO) {
                    state.copy(player = newBattler)
                } else {
                    state.copy(enemy = newBattler)
                }
            }

            BEAST_BOOST -> {
                // +1 to highest base stat
                val highestStat = getHighestStat(battler.mon)
                val newStages = when (highestStat) {
                    "attack" -> battler.statStages.copy(attack = minOf(6, battler.statStages.attack + 1))
                    "defense" -> battler.statStages.copy(defense = minOf(6, battler.statStages.defense + 1))
                    "spAttack" -> battler.statStages.copy(spAttack = minOf(6, battler.statStages.spAttack + 1))
                    "spDefense" -> battler.statStages.copy(spDefense = minOf(6, battler.statStages.spDefense + 1))
                    "speed" -> battler.statStages.copy(speed = minOf(6, battler.statStages.speed + 1))
                    else -> battler.statStages
                }
                val newBattler = battler.copy(statStages = newStages)
                if (isPlayerKO) {
                    state.copy(player = newBattler)
                } else {
                    state.copy(enemy = newBattler)
                }
            }

            else -> state
        }
    }

    /**
     * Apply abilities that trigger when hit by a move.
     *
     * @param state Current battle state
     * @param defender Who was hit
     * @param moveType Type of the move that hit
     * @param moveCategory 0=Physical, 1=Special, 2=Status
     * @return Updated battle state with on-hit ability effects applied
     */
    fun applyOnHitAbility(
        state: BattleState,
        defender: BattlerState,
        moveType: Int,
        moveCategory: Int
    ): BattleState {
        return when (defender.mon.ability) {
            JUSTIFIED -> {
                // +1 Attack when hit by Dark-type move
                if (moveType == Types.DARK) {
                    val newDefender = defender.copy(
                        statStages = defender.statStages.copy(
                            attack = minOf(6, defender.statStages.attack + 1)
                        )
                    )
                    if (state.player == defender) {
                        state.copy(player = newDefender)
                    } else {
                        state.copy(enemy = newDefender)
                    }
                } else {
                    state
                }
            }

            WEAK_ARMOR -> {
                // +2 Speed, -1 Defense when hit by physical move
                if (moveCategory == 0) {
                    val newDefender = defender.copy(
                        statStages = defender.statStages.copy(
                            defense = maxOf(-6, defender.statStages.defense - 1),
                            speed = minOf(6, defender.statStages.speed + 2)
                        )
                    )
                    if (state.player == defender) {
                        state.copy(player = newDefender)
                    } else {
                        state.copy(enemy = newDefender)
                    }
                } else {
                    state
                }
            }

            else -> state
        }
    }

    /**
     * Apply end-of-turn abilities (called after both moves execute).
     */
    fun applyEndOfTurnAbilities(state: BattleState): BattleState {
        var newState = state

        // Use deterministic random based on state hash for reproducible simulations
        val random = Random(state.hashCode() + state.turn)

        // Player abilities
        when (newState.player.mon.ability) {
            SPEED_BOOST -> {
                // +1 Speed at end of turn
                newState = newState.copy(
                    player = newState.player.copy(
                        statStages = newState.player.statStages.copy(
                            speed = minOf(6, newState.player.statStages.speed + 1)
                        )
                    )
                )
            }

            SHED_SKIN -> {
                // 33% chance to cure status (deterministic based on state)
                if (newState.player.status != 0 && random.nextFloat() < 0.33f) {
                    newState = newState.copy(
                        player = newState.player.copy(status = 0)
                    )
                }
            }

            REGENERATOR -> {
                // Note: Regenerator triggers on switch-out, not end of turn
                // This would require switch tracking to implement properly
            }
        }

        // Enemy abilities (use offset to get different random value)
        val enemyRandom = Random(state.hashCode() + state.turn + 1)
        when (newState.enemy.mon.ability) {
            SPEED_BOOST -> {
                newState = newState.copy(
                    enemy = newState.enemy.copy(
                        statStages = newState.enemy.statStages.copy(
                            speed = minOf(6, newState.enemy.statStages.speed + 1)
                        )
                    )
                )
            }

            SHED_SKIN -> {
                if (newState.enemy.status != 0 && enemyRandom.nextFloat() < 0.33f) {
                    newState = newState.copy(
                        enemy = newState.enemy.copy(status = 0)
                    )
                }
            }
        }

        return newState
    }

    /**
     * Get stat multiplier from attacker's ability.
     * Applied to attack/special attack stat BEFORE damage calculation.
     *
     * NOTE: Huge Power/Pure Power are already baked into stats from memory,
     * so we don't apply them again. This function handles STATUS-DEPENDENT abilities.
     *
     * @param battler The attacking Pokemon
     * @param moveCategory 0=Physical, 1=Special, 2=Status
     * @return Multiplier to apply to the attack stat
     */
    fun getAttackerStatMultiplier(
        battler: BattlerState,
        moveCategory: Int
    ): Float {
        return when (battler.mon.ability) {
            GUTS -> {
                // 1.5x physical Attack when statused (ignores burn's Attack reduction)
                if (battler.status != 0 && moveCategory == 0) 1.5f else 1.0f
            }

            // Note: Huge Power/Pure Power are NOT applied here because they're
            // already in the stats read from memory

            else -> 1.0f
        }
    }

    /**
     * Get defense multiplier from defender's ability.
     * Applied to defense/special defense stat BEFORE damage calculation.
     *
     * @param battler The defending Pokemon
     * @param moveCategory 0=Physical, 1=Special, 2=Status
     * @return Multiplier to apply to the defense stat
     */
    fun getDefenderStatMultiplier(
        battler: BattlerState,
        moveCategory: Int
    ): Float {
        return when (battler.mon.ability) {
            MARVEL_SCALE -> {
                // 1.5x physical Defense when statused
                if (battler.status != 0 && moveCategory == 0) 1.5f else 1.0f
            }

            FUR_COAT -> {
                // 2x physical Defense always
                if (moveCategory == 0) 2.0f else 1.0f
            }

            else -> 1.0f
        }
    }

    /**
     * Get move power multiplier from attacker's ability.
     * These abilities boost move power directly (not stats).
     *
     * @param battler The attacking Pokemon
     * @param moveId The move being used
     * @return Multiplier to apply to move power
     */
    fun getMovePowerMultiplier(
        battler: BattlerState,
        moveId: Int
    ): Float {
        val moveData = PokemonData.getMoveData(moveId) ?: return 1.0f

        return when (battler.mon.ability) {
            TECHNICIAN -> {
                // 1.5x power for moves with 60 or less base power
                if (moveData.power > 0 && moveData.power <= 60) 1.5f else 1.0f
            }

            IRON_FIST -> {
                // 1.2x power for punch moves
                if (isPunchMove(moveId)) 1.2f else 1.0f
            }

            STRONG_JAW -> {
                // 1.5x power for bite moves
                if (isBiteMove(moveId)) 1.5f else 1.0f
            }

            TOUGH_CLAWS -> {
                // 1.3x power for contact moves
                if (isContactMove(moveId)) 1.3f else 1.0f
            }

            SHEER_FORCE -> {
                // 1.3x power for moves with secondary effects (removes secondary effects)
                if (hasSecondaryEffect(moveId)) 1.3f else 1.0f
            }

            else -> 1.0f
        }
    }

    /**
     * Get speed multiplier from weather-boosting abilities.
     *
     * @param battler The Pokemon whose speed is being calculated
     * @param weather The current weather
     * @return Multiplier to apply to speed stat
     */
    fun getWeatherSpeedMultiplier(
        battler: BattlerState,
        weather: Weather
    ): Float {
        return when {
            battler.mon.ability == CHLOROPHYLL && weather == Weather.SUN -> 2.0f
            battler.mon.ability == SWIFT_SWIM && weather == Weather.RAIN -> 2.0f
            battler.mon.ability == SAND_RUSH && weather == Weather.SANDSTORM -> 2.0f
            battler.mon.ability == SLUSH_RUSH && weather == Weather.HAIL -> 2.0f
            else -> 1.0f
        }
    }

    /**
     * Check if ability prevents stat drops (Clear Body, White Smoke, etc.)
     */
    fun preventsStatLoss(abilityId: Int): Boolean {
        return when (abilityId) {
            29 -> true  // Clear Body
            73 -> true  // White Smoke
            126 -> true // Hyper Cutter (prevents Attack drops only, but simplified)
            else -> false
        }
    }

    // Helper functions

    private fun getHighestStat(mon: PartyMon): String {
        val stats = mapOf(
            "attack" to mon.attack,
            "defense" to mon.defense,
            "spAttack" to mon.spAttack,
            "spDefense" to mon.spDefense,
            "speed" to mon.speed
        )
        return stats.maxByOrNull { it.value }?.key ?: "attack"
    }

    private fun isPunchMove(moveId: Int): Boolean {
        // Common punch moves
        // Fire Punch = 7, Ice Punch = 8, Thunder Punch = 9, Mega Punch = 5,
        // Dizzy Punch = 146, Hammer Arm = 359, Focus Punch = 264, etc.
        return moveId in listOf(5, 7, 8, 9, 146, 223, 264, 327, 359, 409, 612, 721)
    }

    private fun isBiteMove(moveId: Int): Boolean {
        // Bite moves: Bite, Crunch, Fire Fang, Ice Fang, Thunder Fang, Poison Fang, etc.
        // Bite = 44, Crunch = 242, Fire Fang = 424, Ice Fang = 423, Thunder Fang = 422
        return moveId in listOf(44, 242, 422, 423, 424, 305)
    }

    private fun isContactMove(moveId: Int): Boolean {
        // Most physical moves are contact moves
        // Non-contact physical moves are projectiles/special cases
        val moveData = PokemonData.getMoveData(moveId) ?: return false

        // If it's not physical, it's not contact
        if (moveData.category != 0) return false

        // Non-contact physical moves (simplified list)
        val nonContactMoves = setOf(
            89,  // Earthquake
            91,  // Dig
            157, // Rock Slide
            317, // Rock Tomb
            179, // Bullet Seed
            331, // Razor Leaf
            // Add more as needed
        )

        return moveId !in nonContactMoves
    }

    private fun hasSecondaryEffect(moveId: Int): Boolean {
        // Moves with secondary effects (flinch, stat changes, status, etc.)
        // This is a simplified implementation - ideally would read from move data
        // For now, return false (Sheer Force won't boost anything)
        // TODO: Add comprehensive secondary effect detection

        // Common moves with secondary effects:
        // Rock Slide (30% flinch), Iron Head (30% flinch), Waterfall (20% flinch)
        // Fire moves with burn chance, Ice moves with freeze chance, etc.
        val movesWithSecondary = setOf(
            157, // Rock Slide (flinch)
            442, // Iron Head (flinch)
            127, // Waterfall (flinch)
            85,  // Thunderbolt (paralysis)
            94,  // Psychic (Sp.Def drop)
            // Add more as needed
        )

        return moveId in movesWithSecondary
    }
}
