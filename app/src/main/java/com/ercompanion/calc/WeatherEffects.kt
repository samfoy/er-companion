package com.ercompanion.calc

import com.ercompanion.data.PokemonData
import com.ercompanion.data.Types

/**
 * Handles weather effects in battle.
 */
object WeatherEffects {

    /**
     * Check if weather can be changed (primal weather blocks changes).
     */
    fun canChangeWeather(currentWeather: Weather): Boolean {
        return when (currentWeather) {
            Weather.HARSH_SUN,
            Weather.HEAVY_RAIN,
            Weather.STRONG_WINDS -> false  // Primal weather cannot be changed
            else -> true
        }
    }

    /**
     * Set weather, respecting primal weather rules.
     * In Gen 6+ (ORAS), primal weathers can override each other (last one wins).
     */
    fun setWeather(
        state: BattleState,
        newWeather: Weather
    ): BattleState {
        // Primal weathers can override each other
        if (isPrimalWeather(state.weather) && isPrimalWeather(newWeather)) {
            return state.copy(weather = newWeather)  // New primal overrides old primal
        }

        // Normal weather cannot override primal weather
        if (!canChangeWeather(state.weather)) {
            return state
        }

        return state.copy(weather = newWeather)
    }

    /**
     * Check if weather is primal (cannot be changed by normal weather moves).
     */
    private fun isPrimalWeather(weather: Weather): Boolean {
        return weather in listOf(Weather.HARSH_SUN, Weather.HEAVY_RAIN, Weather.STRONG_WINDS)
    }

    /**
     * Get damage multiplier from weather.
     */
    fun getWeatherMultiplier(
        weather: Weather,
        moveType: Int,
        attacker: BattlerState
    ): Float {
        return when (weather) {
            Weather.SUN, Weather.HARSH_SUN -> {
                when (moveType) {
                    Types.FIRE -> 1.5f   // Fire moves boosted
                    Types.WATER -> 0.5f  // Water moves weakened
                    else -> 1.0f
                }
            }

            Weather.RAIN, Weather.HEAVY_RAIN -> {
                when (moveType) {
                    Types.WATER -> 1.5f  // Water moves boosted
                    Types.FIRE -> 0.5f   // Fire moves weakened
                    else -> 1.0f
                }
            }

            Weather.SANDSTORM -> {
                // No direct damage multiplier, but Rock types get SpDef boost
                1.0f
            }

            Weather.HAIL -> {
                // No direct damage multiplier, but Blizzard never misses
                1.0f
            }

            Weather.STRONG_WINDS -> {
                // Reduces super-effective damage against Flying types to 1.0x
                // Note: This would need to check if move is super-effective
                // Simplified for now
                1.0f
            }

            else -> 1.0f
        }
    }

    /**
     * Apply weather damage at end of turn (Sandstorm, Hail).
     */
    fun applyWeatherDamage(
        state: BattleState,
        isPlayer: Boolean
    ): BattleState {
        val battler = if (isPlayer) state.player else state.enemy
        val types = PokemonData.getSpeciesTypes(battler.mon.species)
        var damage = 0

        when (state.weather) {
            Weather.SANDSTORM -> {
                // 1/16 max HP damage unless Rock/Ground/Steel type
                if (!types.contains(Types.ROCK) && !types.contains(Types.GROUND) && !types.contains(Types.STEEL)) {
                    damage = battler.mon.maxHp / 16
                }
            }

            Weather.HAIL -> {
                // 1/16 max HP damage unless Ice type
                if (!types.contains(Types.ICE)) {
                    damage = battler.mon.maxHp / 16
                }
            }

            else -> {}
        }

        if (damage > 0) {
            val newHp = maxOf(0, battler.currentHp - damage)
            return if (isPlayer) {
                state.copy(player = battler.copy(currentHp = newHp))
            } else {
                state.copy(enemy = battler.copy(currentHp = newHp))
            }
        }

        return state
    }

    /**
     * Get stat multiplier from weather (Sandstorm boosts Rock SpDef).
     */
    fun getWeatherStatMultiplier(
        weather: Weather,
        battler: BattlerState,
        stat: String
    ): Float {
        val types = PokemonData.getSpeciesTypes(battler.mon.species)

        return when {
            weather == Weather.SANDSTORM && stat == "spDefense" && types.contains(Types.ROCK) -> {
                1.5f  // Rock types get 1.5x SpDef in Sandstorm
            }
            else -> 1.0f
        }
    }

    /**
     * Check if weather prevents move (e.g., Solar Beam in rain).
     */
    fun doesWeatherPreventMove(
        weather: Weather,
        moveId: Int
    ): Boolean {
        // Solar Beam requires charging in non-sun weather
        if (moveId == 76) {  // Solar Beam
            return weather != Weather.SUN && weather != Weather.HARSH_SUN
        }

        return false
    }
}

/**
 * Handles terrain effects in battle.
 */
object TerrainEffects {

    /**
     * Get damage multiplier from terrain.
     */
    fun getTerrainMultiplier(
        terrain: Terrain,
        moveType: Int,
        attacker: BattlerState,
        isGrounded: Boolean
    ): Float {
        if (!isGrounded) return 1.0f  // Flying types & Levitate ignore terrain

        return when (terrain) {
            Terrain.ELECTRIC -> {
                if (moveType == Types.ELECTRIC) 1.3f else 1.0f  // Gen 8+: 1.3x boost (was 1.5x in Gen 7)
            }

            Terrain.GRASSY -> {
                if (moveType == Types.GRASS) 1.3f else 1.0f  // Gen 8+: 1.3x boost (was 1.5x in Gen 7)
            }

            Terrain.PSYCHIC -> {
                if (moveType == Types.PSYCHIC) 1.3f else 1.0f  // Gen 8+: 1.3x boost (was 1.5x in Gen 7)
            }

            Terrain.MISTY -> {
                if (moveType == Types.DRAGON) 0.5f else 1.0f  // Dragon moves weakened
            }

            else -> 1.0f
        }
    }

    /**
     * Apply terrain healing (Grassy Terrain heals 1/16 HP per turn).
     */
    fun applyTerrainHealing(
        state: BattleState,
        isPlayer: Boolean
    ): BattleState {
        if (state.terrain != Terrain.GRASSY) return state

        val battler = if (isPlayer) state.player else state.enemy

        // Check if grounded using proper grounded check
        if (!isGrounded(battler)) return state

        // Heal 1/16 max HP
        val healing = battler.mon.maxHp / 16
        val newHp = minOf(battler.mon.maxHp, battler.currentHp + healing)

        return if (isPlayer) {
            state.copy(player = battler.copy(currentHp = newHp))
        } else {
            state.copy(enemy = battler.copy(currentHp = newHp))
        }
    }

    /**
     * Check if a battler is grounded (affected by terrain).
     * Pokemon are not grounded if they:
     * - Are Flying type
     * - Have Levitate ability
     * - Are holding Air Balloon
     * - Are under effect of Magnet Rise or Telekinesis
     */
    fun isGrounded(battler: BattlerState): Boolean {
        val types = PokemonData.getSpeciesTypes(battler.mon.species)

        // Flying type is not grounded
        if (types.contains(Types.FLYING)) return false

        // Levitate ability makes Pokemon not grounded
        if (battler.mon.ability == 26) return false

        // Air Balloon makes Pokemon not grounded (item ID 541 in Gen 5+)
        // Note: This needs to be popped when hit, but that's tracked elsewhere
        if (battler.mon.heldItem == 541) return false

        // Check tempBoosts for Magnet Rise/Telekinesis effects
        if (battler.tempBoosts.isUngrounded) return false

        return true
    }

    /**
     * Check if terrain prevents status (Misty prevents status conditions).
     */
    fun doesTerrainPreventStatus(
        terrain: Terrain,
        isGrounded: Boolean
    ): Boolean {
        return terrain == Terrain.MISTY && isGrounded
    }

    /**
     * Check if terrain prevents priority moves (Psychic Terrain).
     * Psychic Terrain prevents priority moves from hitting grounded Pokemon.
     */
    fun doesTerrainBlockPriority(
        terrain: Terrain,
        moveId: Int,
        defender: BattlerState
    ): Boolean {
        if (terrain != Terrain.PSYCHIC) return false

        // Check if defender is grounded using proper grounded check
        if (!isGrounded(defender)) return false

        // Check if move has positive priority
        val moveData = com.ercompanion.data.PokemonData.getMoveData(moveId)
        if (moveData != null && moveData.priority > 0) {
            return true  // Psychic Terrain blocks priority moves against grounded targets
        }

        return false
    }
}
