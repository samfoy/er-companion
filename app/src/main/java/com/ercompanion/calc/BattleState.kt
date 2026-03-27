package com.ercompanion.calc

import com.ercompanion.parser.PartyMon

/**
 * Represents the current state of a singles battle.
 */
data class BattleState(
    val player: BattlerState,
    val enemy: BattlerState,
    val turn: Int = 1,
    val weather: Weather = Weather.NONE,
    val terrain: Terrain = Terrain.NONE,
    val curses: CurseState = CurseState.NONE
)

/**
 * Represents a single battler's state (player or enemy).
 */
data class BattlerState(
    val mon: PartyMon,
    val currentHp: Int,
    val statStages: StatStages,
    val status: Int = 0,
    val tempBoosts: TempBoosts = TempBoosts(),
    val confusionTurns: Int = 0  // Remaining turns of confusion (0 = not confused, 1-4 = confused)
)

/**
 * Stat stages range from -6 to +6, with 0 being the baseline.
 * These are multiplied by the base stats to get effective stats.
 */
data class StatStages(
    val attack: Int = 0,
    val defense: Int = 0,
    val spAttack: Int = 0,
    val spDefense: Int = 0,
    val speed: Int = 0,
    val accuracy: Int = 0,
    val evasion: Int = 0
) {
    /**
     * Ensure all stages are clamped to valid range [-6, 6].
     */
    fun clamp(): StatStages {
        return copy(
            attack = attack.coerceIn(-6, 6),
            defense = defense.coerceIn(-6, 6),
            spAttack = spAttack.coerceIn(-6, 6),
            spDefense = spDefense.coerceIn(-6, 6),
            speed = speed.coerceIn(-6, 6),
            accuracy = accuracy.coerceIn(-6, 6),
            evasion = evasion.coerceIn(-6, 6)
        )
    }

    /**
     * Apply stat stage changes from a move.
     */
    fun applyChanges(changes: StatStageChanges): StatStages {
        return copy(
            attack = attack + changes.attack,
            defense = defense + changes.defense,
            spAttack = spAttack + changes.spAttack,
            spDefense = spDefense + changes.spDefense,
            speed = speed + changes.speed,
            accuracy = accuracy + changes.accuracy,
            evasion = evasion + changes.evasion
        ).clamp()
    }
}

/**
 * Stat stage changes from a move (e.g., Swords Dance = +2 attack).
 */
data class StatStageChanges(
    val attack: Int = 0,
    val defense: Int = 0,
    val spAttack: Int = 0,
    val spDefense: Int = 0,
    val speed: Int = 0,
    val accuracy: Int = 0,
    val evasion: Int = 0
)

/**
 * Temporary battle effects that reset between turns or after certain actions.
 */
data class TempBoosts(
    val isProtected: Boolean = false,
    val isCharging: Boolean = false,  // For Sky Attack, Solar Beam, etc.
    val isFlinched: Boolean = false,
    val toxicCounter: Int = 0,  // Tracks number of turns for toxic damage (1/16, 2/16, 3/16, etc.)
    val lockedMove: Int = 0,    // For Choice items - the move ID we're locked into
    val lockedTurns: Int = 0,   // How many turns we've been locked (resets on switch)
    val isUngrounded: Boolean = false,  // For Magnet Rise, Telekinesis, or Air Balloon
    val ungroundedTurns: Int = 0,  // Turns remaining for ungrounded status
    val lastUsedMove: Int = 0  // For Torment curse tracking
)

/**
 * Status condition constants and helper functions.
 * Status field encoding:
 * - 0x00 = No status
 * - 0x01-0x07 = Sleep (lower 3 bits = turns remaining)
 * - 0x08 = Poison
 * - 0x10 = Burn
 * - 0x20 = Freeze
 * - 0x40 = Paralysis
 * - 0x80 = Toxic (badly poisoned)
 */
object StatusConditions {
    const val NONE = 0x00
    const val SLEEP_1 = 0x01  // Sleep for 1 more turn
    const val SLEEP_2 = 0x02  // Sleep for 2 more turns
    const val SLEEP_3 = 0x03  // Sleep for 3 more turns
    const val POISON = 0x08
    const val BURN = 0x10
    const val FREEZE = 0x20
    const val PARALYSIS = 0x40
    const val TOXIC = 0x80     // Badly poisoned

    /**
     * Check if status represents sleep.
     */
    fun isSleep(status: Int) = status in 0x01..0x07

    /**
     * Get number of turns remaining for sleep.
     */
    fun getSleepTurns(status: Int) = status and 0x07

    /**
     * Alias for isSleep for clarity.
     */
    fun isAsleep(status: Int) = isSleep(status)

    /**
     * Check if status represents poison (regular or toxic).
     */
    fun isPoisoned(status: Int) = (status and POISON) != 0 || (status and TOXIC) != 0

    /**
     * Check if status represents burn.
     */
    fun isBurned(status: Int) = (status and BURN) != 0

    /**
     * Check if status represents freeze.
     */
    fun isFrozen(status: Int) = (status and FREEZE) != 0

    /**
     * Check if status represents paralysis.
     */
    fun isParalyzed(status: Int) = (status and PARALYSIS) != 0

    /**
     * Check if status is toxic (badly poisoned).
     */
    fun isToxic(status: Int) = (status and TOXIC) != 0

    /**
     * Check if battler has any status condition.
     */
    fun hasStatus(status: Int) = status != NONE

    /**
     * Get a human-readable name for the status condition.
     */
    fun getStatusName(status: Int): String = when {
        isAsleep(status) -> "Sleep (${getSleepTurns(status)} turns)"
        isToxic(status) -> "Badly Poisoned"
        isPoisoned(status) -> "Poisoned"
        isBurned(status) -> "Burned"
        isFrozen(status) -> "Frozen"
        isParalyzed(status) -> "Paralyzed"
        else -> "None"
    }
}

/**
 * Weather conditions in battle.
 */
enum class Weather {
    NONE,
    SUN,
    RAIN,
    SANDSTORM,
    HAIL,
    HARSH_SUN,     // Primal weather (can't be changed)
    HEAVY_RAIN,    // Primal weather
    STRONG_WINDS   // Primal weather
}

/**
 * Terrain types in battle.
 */
enum class Terrain {
    NONE,
    ELECTRIC,
    GRASSY,
    MISTY,
    PSYCHIC
}
