package com.ercompanion.calc

import com.ercompanion.data.MoveData
import com.ercompanion.data.PokemonData
import kotlin.random.Random

/**
 * Advanced held item effects for battle calculation.
 * Handles complex items like Choice items, Life Orb, Focus Sash, etc.
 */
object ItemEffects {

    // Item ID constants (from ItemData.kt)
    private const val CHOICE_BAND = 222
    private const val CHOICE_SPECS = 223
    private const val CHOICE_SCARF = 224
    private const val LIFE_ORB = 225
    private const val EXPERT_BELT = 226
    private const val MUSCLE_BAND = 227
    private const val WISE_GLASSES = 228
    private const val LEFTOVERS = 234
    private const val FOCUS_SASH = 235
    private const val FOCUS_BAND = 275
    private const val FLAME_ORB = 280
    private const val TOXIC_ORB = 281
    private const val BLACK_SLUDGE = 282
    private const val IRON_BALL = 285

    // Type-boosting items (Gen 3 format)
    private const val CHARCOAL = 250
    private const val MYSTIC_WATER = 251
    private const val MIRACLE_SEED = 252
    private const val MAGNET = 253
    private const val NEVERMELTICE = 254
    private const val BLACK_BELT = 255
    private const val POISON_BARB = 256
    private const val SOFT_SAND = 257
    private const val SHARP_BEAK = 258
    private const val TWISTED_SPOON = 259
    private const val SILVER_POWDER = 260
    private const val HARD_STONE = 261
    private const val SPELL_TAG = 262
    private const val DRAGON_FANG = 263
    private const val BLACK_GLASSES = 264
    private const val METAL_COAT = 265

    /**
     * Check if item locks the user into a move (Choice items).
     */
    fun isChoiceItem(itemId: Int): Boolean {
        return itemId in listOf(CHOICE_BAND, CHOICE_SPECS, CHOICE_SCARF)
    }

    /**
     * Get damage multiplier from held item.
     * This is applied AFTER base damage calculation.
     *
     * @param itemId Held item ID
     * @param moveData Move being used
     * @param effectiveness Type effectiveness (for Expert Belt)
     * @return Damage multiplier (1.0 = no change)
     */
    fun getDamageMultiplier(
        itemId: Int,
        moveData: MoveData,
        effectiveness: Float
    ): Float {
        return when (itemId) {
            // Choice items don't add damage directly (their stat boost is already applied)
            // But we return 1.0 here for clarity
            CHOICE_BAND, CHOICE_SPECS, CHOICE_SCARF -> 1.0f

            LIFE_ORB -> 1.3f  // 30% boost to all moves

            EXPERT_BELT -> {
                if (effectiveness > 1.0f) 1.2f else 1.0f  // 20% boost on super-effective
            }

            MUSCLE_BAND -> {
                if (moveData.category == 0) 1.1f else 1.0f  // 10% boost to physical
            }

            WISE_GLASSES -> {
                if (moveData.category == 1) 1.1f else 1.0f  // 10% boost to special
            }

            else -> getTypeBoostMultiplier(itemId, moveData.type)
        }
    }

    /**
     * Type-boosting items (Charcoal, Mystic Water, etc.)
     * Each gives 1.2x boost to moves of their type.
     */
    private fun getTypeBoostMultiplier(itemId: Int, moveType: Int): Float {
        val typeBoostItems = mapOf(
            CHARCOAL to 9,        // Fire
            MYSTIC_WATER to 10,   // Water
            MIRACLE_SEED to 11,   // Grass
            MAGNET to 12,         // Electric
            NEVERMELTICE to 14,   // Ice
            BLACK_BELT to 1,      // Fighting
            POISON_BARB to 3,     // Poison
            SOFT_SAND to 4,       // Ground
            SHARP_BEAK to 2,      // Flying
            TWISTED_SPOON to 13,  // Psychic
            SILVER_POWDER to 6,   // Bug
            HARD_STONE to 5,      // Rock
            SPELL_TAG to 7,       // Ghost
            DRAGON_FANG to 15,    // Dragon
            BLACK_GLASSES to 16,  // Dark
            METAL_COAT to 8       // Steel
        )

        return if (typeBoostItems[itemId] == moveType) 1.2f else 1.0f
    }

    /**
     * Get speed multiplier from held item.
     */
    fun getSpeedMultiplier(itemId: Int): Float {
        return when (itemId) {
            CHOICE_SCARF -> 1.5f  // 50% speed boost
            IRON_BALL -> 0.5f     // Halves speed
            else -> 1.0f
        }
    }

    /**
     * Apply Life Orb recoil damage after a move.
     * Life Orb deals 10% max HP recoil per damaging move.
     *
     * @param state Current battle state
     * @param isPlayer True if player used the move
     * @param moveUsed True if a damaging move was used
     * @return Updated battle state with recoil applied
     */
    fun applyLifeOrbRecoil(
        state: BattleState,
        isPlayer: Boolean,
        moveUsed: Boolean
    ): BattleState {
        if (!moveUsed) return state

        val battler = if (isPlayer) state.player else state.enemy
        if (battler.mon.heldItem != LIFE_ORB) return state

        // 10% max HP recoil
        val recoil = battler.mon.maxHp / 10
        val newHp = maxOf(0, battler.currentHp - recoil)

        return if (isPlayer) {
            state.copy(player = battler.copy(currentHp = newHp))
        } else {
            state.copy(enemy = battler.copy(currentHp = newHp))
        }
    }

    /**
     * Apply Leftovers/Black Sludge healing at end of turn.
     * Leftovers heals 1/16 max HP for all Pokemon.
     * Black Sludge heals 1/16 max HP for Poison types, damages others.
     *
     * @param state Current battle state
     * @param isPlayer True to apply to player
     * @return Updated battle state
     */
    fun applyEndOfTurnHealing(
        state: BattleState,
        isPlayer: Boolean
    ): BattleState {
        val battler = if (isPlayer) state.player else state.enemy
        val types = PokemonData.getSpeciesTypes(battler.mon.species)

        val healing = when (battler.mon.heldItem) {
            LEFTOVERS -> battler.mon.maxHp / 16

            BLACK_SLUDGE -> {
                if (types.contains(3)) {  // Poison type (type ID 3)
                    battler.mon.maxHp / 16
                } else {
                    // Damages non-Poison types (1/8 max HP)
                    return if (isPlayer) {
                        state.copy(player = battler.copy(currentHp = maxOf(0, battler.currentHp - battler.mon.maxHp / 8)))
                    } else {
                        state.copy(enemy = battler.copy(currentHp = maxOf(0, battler.currentHp - battler.mon.maxHp / 8)))
                    }
                }
            }

            else -> 0
        }

        if (healing > 0) {
            val newHp = minOf(battler.mon.maxHp, battler.currentHp + healing)
            return if (isPlayer) {
                state.copy(player = battler.copy(currentHp = newHp))
            } else {
                state.copy(enemy = battler.copy(currentHp = newHp))
            }
        }

        return state
    }

    /**
     * Check if Focus Sash/Band prevents KO.
     * Focus Sash: Guarantees survival at 1 HP if at full HP when hit.
     * Focus Band: 10% chance to survive at 1 HP from any hit.
     *
     * @param battler Battler being hit
     * @param incomingDamage Damage from the attack
     * @param wasFullHP True if battler was at full HP before hit
     * @return Adjusted damage (reduced to keep battler at 1 HP if item activates)
     */
    fun checkFocusItem(
        battler: BattlerState,
        incomingDamage: Int,
        wasFullHP: Boolean
    ): Int {
        // Issue 1.11: Validate consistency between wasFullHP and currentHp
        if (wasFullHP && battler.currentHp < battler.mon.maxHp) {
            throw IllegalArgumentException("wasFullHP=true but currentHp (${battler.currentHp}) < maxHp (${battler.mon.maxHp})")
        }

        // Focus Sash: Survive at 1 HP if at full HP and would be KO'd
        if (battler.mon.heldItem == FOCUS_SASH && wasFullHP && incomingDamage >= battler.currentHp) {
            return battler.currentHp - 1  // Survive at 1 HP
        }

        // Focus Band: 10% chance to survive at 1 HP
        if (battler.mon.heldItem == FOCUS_BAND && incomingDamage >= battler.currentHp) {
            if (Random.nextFloat() < 0.1f) {
                return battler.currentHp - 1  // Survive at 1 HP
            }
        }

        return incomingDamage
    }

    /**
     * Apply status orb (Flame Orb, Toxic Orb) at end of turn.
     * These items inflict status conditions on the holder after the first turn.
     *
     * @param state Current battle state
     * @param isPlayer True to apply to player
     * @param turnCount Current turn number
     * @return Updated battle state with status applied
     */
    fun applyStatusOrb(
        state: BattleState,
        isPlayer: Boolean,
        turnCount: Int
    ): BattleState {
        // Issue 2.15: Validate turn count to catch corruption
        if (turnCount < 0) {
            throw IllegalArgumentException("turnCount cannot be negative: $turnCount")
        }
        if (turnCount < 1) return state  // Orbs activate after first turn

        val battler = if (isPlayer) state.player else state.enemy
        if (battler.status != 0) return state  // Already have status

        val newStatus = when (battler.mon.heldItem) {
            FLAME_ORB -> StatusConditions.BURN   // Burn holder
            TOXIC_ORB -> StatusConditions.TOXIC  // Badly poison holder
            else -> return state
        }

        val newBattler = battler.copy(status = newStatus)
        return if (isPlayer) {
            state.copy(player = newBattler)
        } else {
            state.copy(enemy = newBattler)
        }
    }

    /**
     * Get the strategic value of an item for line evaluation.
     * Positive values are good, negative values indicate drawbacks.
     *
     * @param itemId Item ID
     * @param hpPercent Current HP as percentage of max (0.0 to 1.0)
     * @param turnsElapsed Number of turns the item has been in use
     * @return Strategic value modifier
     */
    fun getStrategicValue(
        itemId: Int,
        hpPercent: Float,
        turnsElapsed: Int
    ): Float {
        return when (itemId) {
            // Choice items: Great power, but locking is risky
            CHOICE_BAND, CHOICE_SPECS -> 1.5f
            CHOICE_SCARF -> 2.0f  // Speed control is very valuable

            // Life Orb: Great power, but recoil adds up
            LIFE_ORB -> {
                val recoilPenalty = turnsElapsed * 0.1f  // 10% per turn
                if (hpPercent - recoilPenalty <= 0.2f) -1.0f  // Too risky if low HP
                else 1.5f - recoilPenalty  // Value decreases with use
            }

            // Expert Belt: Good for coverage moves
            EXPERT_BELT -> 1.2f

            // Leftovers: Value increases over time
            LEFTOVERS -> {
                val healingValue = turnsElapsed * 0.0625f  // 1/16 per turn
                minOf(2.0f, 0.5f + healingValue)
            }

            // Focus Sash: Very valuable when at full HP, worthless after
            FOCUS_SASH -> if (hpPercent >= 1.0f) 2.5f else 0.0f

            // Black Sludge: Like Leftovers for Poison types
            BLACK_SLUDGE -> {
                val healingValue = turnsElapsed * 0.0625f
                minOf(2.0f, 0.5f + healingValue)
            }

            else -> 0.5f  // Default minor value for other items
        }
    }

    /**
     * Check if an item should trigger a warning for the player.
     * For example, warn about Life Orb recoil or Choice item lock.
     *
     * @param itemId Item ID
     * @param state Current battle state
     * @param isPlayer True to check player's item
     * @return Warning message, or null if no warning
     */
    fun getItemWarning(
        itemId: Int,
        state: BattleState,
        isPlayer: Boolean
    ): String? {
        val battler = if (isPlayer) state.player else state.enemy

        return when (itemId) {
            LIFE_ORB -> {
                val recoilDamage = battler.mon.maxHp / 10
                val hpAfterRecoil = battler.currentHp - recoilDamage
                if (hpAfterRecoil <= 0) {
                    "Life Orb recoil would KO you!"
                } else if (hpAfterRecoil < battler.mon.maxHp * 0.15f) {
                    "Life Orb recoil will leave you in KO range"
                } else null
            }

            CHOICE_BAND, CHOICE_SPECS, CHOICE_SCARF -> {
                if (battler.tempBoosts.lockedMove != 0) {
                    val moveName = PokemonData.getMoveData(battler.tempBoosts.lockedMove)?.name
                    "Locked into $moveName by Choice item"
                } else null
            }

            FOCUS_SASH -> {
                if (battler.currentHp < battler.mon.maxHp) {
                    "Focus Sash inactive (not at full HP)"
                } else null
            }

            BLACK_SLUDGE -> {
                val types = PokemonData.getSpeciesTypes(battler.mon.species)
                if (!types.contains(3)) {  // Not Poison type
                    "Black Sludge damages non-Poison types!"
                } else null
            }

            else -> null
        }
    }
}
