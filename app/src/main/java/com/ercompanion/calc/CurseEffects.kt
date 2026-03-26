package com.ercompanion.calc

/**
 * Curse system for Emerogue ROM hack.
 *
 * Curses are permanent debuffs that make enemy Pokemon stronger. There are 11 battle-affecting
 * curses that can be accumulated throughout the game. Each curse has different mechanics and
 * maximum stack limits.
 *
 * Based on emerogue source code analysis from:
 * - src/battle_script_commands.c (curse effect implementations)
 * - src/data/text/curses.h (curse descriptions and mechanics)
 */

/**
 * All battle-affecting curses in Emerogue.
 *
 * Note: ENCOUNTER_CURSE and SHINY_CURSE are not included as they don't affect battle calculations.
 */
enum class Curse {
    /**
     * Crit Curse: Increases enemy critical hit chance.
     * Max stacks: 9 (90% crit chance)
     * Formula: 10% per curse
     */
    CRIT_CURSE,

    /**
     * Adaptability Curse: Boosts enemy STAB (Same Type Attack Bonus) damage.
     * Max stacks: 3 (1.5x base STAB becomes 2.0x)
     * Formula: Base 1.5x + (5% per curse) = 1.5x to 2.0x (15% total boost = 50% relative boost)
     */
    ADAPTABILITY_CURSE,

    /**
     * Endure Curse: Gives enemies a chance to survive fatal hits at 1 HP.
     * Max stacks: 4 (80% chance)
     * Formula: 20% per curse
     */
    ENDURE_CURSE,

    /**
     * OHKO Curse: Enemies have a chance to land one-hit KO moves.
     * Max stacks: 1 (binary on/off)
     * Effect: OHKO moves (Fissure, Horn Drill, etc.) can hit
     */
    OHKO_CURSE,

    /**
     * Unaware Curse: Enemies ignore your stat stage changes.
     * Max stacks: 1 (binary on/off)
     * Effect: All stat boosts/drops ignored when calculating damage to/from enemies
     */
    UNAWARE_CURSE,

    /**
     * Priority Curse: Enemies have a chance to move first regardless of speed/priority.
     * Max stacks: 9 (90% chance)
     * Formula: 10% per curse
     */
    PRIORITY_CURSE,

    /**
     * Serene Grace Curse: Boosts enemy secondary effect chances.
     * Max stacks: 3 (150% boost = 2.5x total)
     * Formula: 50% per curse (e.g., 30% burn chance becomes 45%/60%/75%)
     */
    SERENE_GRACE_CURSE,

    /**
     * Flinch Curse: Increases enemy flinch chance.
     * Max stacks: 9 (90% flinch chance)
     * Formula: 10% per curse
     */
    FLINCH_CURSE,

    /**
     * Shed Skin Curse: Gives enemies a chance to cure status conditions each turn.
     * Max stacks: 6 (90% cure chance)
     * Formula: 15% per curse
     */
    SHED_SKIN_CURSE,

    /**
     * Torment Curse: Prevents using the same move twice in a row.
     * Max stacks: 1 (binary on/off)
     * Effect: Player cannot use the same move consecutively
     */
    TORMENT_CURSE,

    /**
     * Pressure Curse: Doubles PP consumption for all moves.
     * Max stacks: 1 (binary on/off)
     * Effect: Each move costs 2 PP instead of 1
     */
    PRESSURE_CURSE
}

/**
 * Represents the current curse state in a playthrough.
 *
 * Each field corresponds to a curse type and stores the number of active curses of that type.
 * Binary curses (OHKO, Unaware, Torment, Pressure) use Boolean values.
 *
 * Default values represent a curse-free state.
 */
data class CurseState(
    val critCurse: Int = 0,           // 0-9 (10% per curse, max 90%)
    val adaptabilityCurse: Int = 0,   // 0-3 (5% per curse, max 15% additive = 50% relative boost to STAB)
    val endureCurse: Int = 0,         // 0-4 (20% per curse, max 80%)
    val ohkoCurse: Boolean = false,   // Binary on/off
    val unawareCurse: Boolean = false, // Binary on/off
    val priorityCurse: Int = 0,       // 0-9 (10% per curse, max 90%)
    val sereneGraceCurse: Int = 0,    // 0-3 (50% per curse, max 150%)
    val flinchCurse: Int = 0,         // 0-9 (10% per curse, max 90%)
    val shedSkinCurse: Int = 0,       // 0-6 (15% per curse, max 90%)
    val tormentCurse: Boolean = false, // Binary on/off
    val pressureCurse: Boolean = false // Binary on/off
) {
    companion object {
        /**
         * Represents a state with no active curses.
         */
        val NONE = CurseState()

        // Maximum stack limits for each curse type
        const val MAX_CRIT_CURSE = 9
        const val MAX_ADAPTABILITY_CURSE = 3
        const val MAX_ENDURE_CURSE = 4
        const val MAX_PRIORITY_CURSE = 9
        const val MAX_SERENE_GRACE_CURSE = 3
        const val MAX_FLINCH_CURSE = 9
        const val MAX_SHED_SKIN_CURSE = 6
    }

    /**
     * Validates that all curse values are within their legal ranges.
     * @return true if all curse values are valid
     */
    fun isValid(): Boolean {
        return critCurse in 0..MAX_CRIT_CURSE &&
               adaptabilityCurse in 0..MAX_ADAPTABILITY_CURSE &&
               endureCurse in 0..MAX_ENDURE_CURSE &&
               priorityCurse in 0..MAX_PRIORITY_CURSE &&
               sereneGraceCurse in 0..MAX_SERENE_GRACE_CURSE &&
               flinchCurse in 0..MAX_FLINCH_CURSE &&
               shedSkinCurse in 0..MAX_SHED_SKIN_CURSE
    }

    /**
     * Returns the total number of active curses.
     */
    fun totalCurses(): Int {
        return critCurse + adaptabilityCurse + endureCurse + priorityCurse +
               sereneGraceCurse + flinchCurse + shedSkinCurse +
               (if (ohkoCurse) 1 else 0) +
               (if (unawareCurse) 1 else 0) +
               (if (tormentCurse) 1 else 0) +
               (if (pressureCurse) 1 else 0)
    }
}

/**
 * Helper functions for calculating curse effects on battle mechanics.
 *
 * All percentage-based effects are returned as Float values (0.0 to 1.0 for percentages,
 * or multipliers like 1.5 for STAB).
 */
object CurseEffects {

    /**
     * Get enemy critical hit chance boost.
     *
     * Formula: 10% per crit curse, max 90%
     *
     * @param curses The current curse state
     * @return Critical hit chance boost (0.0 to 0.9)
     *
     * Example:
     * - 0 curses: 0.0 (0%)
     * - 3 curses: 0.3 (30%)
     * - 9 curses: 0.9 (90%)
     */
    fun getEnemyCritBoost(curses: CurseState): Float {
        return (curses.critCurse * 0.1f).coerceAtMost(0.9f)
    }

    /**
     * Get enemy STAB (Same Type Attack Bonus) multiplier.
     *
     * Base STAB: 1.5x
     * Formula: 1.5 + (0.05 * adaptabilityCurse) = 1.5 to 2.0
     *
     * This represents a 15% additive boost (max), which is equivalent to a 50% relative
     * boost to the STAB bonus (from 1.5x to 2.0x).
     *
     * @param curses The current curse state
     * @return STAB multiplier (1.5 to 2.0)
     *
     * Example:
     * - 0 curses: 1.5x (normal STAB)
     * - 1 curse: 1.55x
     * - 2 curses: 1.6x
     * - 3 curses: 2.0x (Adaptability ability equivalent)
     */
    fun getEnemyStabMultiplier(curses: CurseState): Float {
        return (1.5f + (curses.adaptabilityCurse * 0.05f)).coerceAtMost(2.0f)
    }

    /**
     * Get enemy Endure chance (survive fatal hit at 1 HP).
     *
     * Formula: 20% per endure curse, max 80%
     *
     * @param curses The current curse state
     * @return Endure chance (0.0 to 0.8)
     *
     * Example:
     * - 0 curses: 0.0 (0%)
     * - 1 curse: 0.2 (20%)
     * - 2 curses: 0.4 (40%)
     * - 4 curses: 0.8 (80%)
     */
    fun getEnemyEndureChance(curses: CurseState): Float {
        return (curses.endureCurse * 0.2f).coerceAtMost(0.8f)
    }

    /**
     * Check if OHKO (One-Hit Knockout) curse is active.
     *
     * When active, enemy OHKO moves (Fissure, Horn Drill, Guillotine, Sheer Cold)
     * can hit the player's Pokemon.
     *
     * @param curses The current curse state
     * @return true if OHKO curse is active
     */
    fun isOhkoCurseActive(curses: CurseState): Boolean {
        return curses.ohkoCurse
    }

    /**
     * Check if Unaware curse is active.
     *
     * When active, enemies ignore all stat stage changes when calculating damage.
     * This affects both:
     * - Damage dealt by enemies (ignores your Defense/Sp. Def boosts)
     * - Damage received by enemies (ignores your Attack/Sp. Atk boosts)
     *
     * @param curses The current curse state
     * @return true if Unaware curse is active
     */
    fun isUnawareCurseActive(curses: CurseState): Boolean {
        return curses.unawareCurse
    }

    /**
     * Get enemy priority boost chance.
     *
     * Formula: 10% per priority curse, max 90%
     *
     * When triggered, enemy moves first regardless of speed or move priority.
     * This overrides even +1 priority moves like Quick Attack.
     *
     * @param curses The current curse state
     * @return Priority boost chance (0.0 to 0.9)
     *
     * Example:
     * - 0 curses: 0.0 (0%)
     * - 5 curses: 0.5 (50%)
     * - 9 curses: 0.9 (90%)
     */
    fun getEnemyPriorityChance(curses: CurseState): Float {
        return (curses.priorityCurse * 0.1f).coerceAtMost(0.9f)
    }

    /**
     * Get enemy secondary effect multiplier (Serene Grace equivalent).
     *
     * Formula: 1.0 + (0.5 * sereneGraceCurse) = 1.0x to 2.5x
     *
     * This boosts the chance of secondary effects like:
     * - Status conditions (burn, paralyze, poison, etc.)
     * - Stat drops (Intimidate, Screech, etc.)
     * - Flinch (note: separate from Flinch Curse, which adds flat flinch chance)
     *
     * @param curses The current curse state
     * @return Secondary effect multiplier (1.0 to 2.5)
     *
     * Example:
     * - 0 curses: 1.0x (normal rates)
     * - 1 curse: 1.5x (30% burn becomes 45%)
     * - 2 curses: 2.0x (30% burn becomes 60%)
     * - 3 curses: 2.5x (30% burn becomes 75%)
     */
    fun getEnemySecondaryEffectMultiplier(curses: CurseState): Float {
        return (1.0f + (curses.sereneGraceCurse * 0.5f)).coerceAtMost(2.5f)
    }

    /**
     * Get enemy flinch boost.
     *
     * Formula: 10% per flinch curse, max 90%
     *
     * This is a flat flinch chance added to all enemy damaging moves, regardless of
     * whether they normally have a flinch chance. Stacks with secondary effect boosts
     * from Serene Grace Curse.
     *
     * @param curses The current curse state
     * @return Flinch chance boost (0.0 to 0.9)
     *
     * Example:
     * - 0 curses: 0.0 (0%)
     * - 3 curses: 0.3 (30%)
     * - 9 curses: 0.9 (90%)
     */
    fun getEnemyFlinchBoost(curses: CurseState): Float {
        return (curses.flinchCurse * 0.1f).coerceAtMost(0.9f)
    }

    /**
     * Get enemy Shed Skin boost (status cure chance).
     *
     * Formula: 15% per shed skin curse, max 90%
     *
     * At the end of each turn, enemies have this chance to cure any status condition
     * (burn, poison, paralyze, sleep, freeze).
     *
     * @param curses The current curse state
     * @return Shed Skin chance (0.0 to 0.9)
     *
     * Example:
     * - 0 curses: 0.0 (0%)
     * - 2 curses: 0.3 (30%)
     * - 4 curses: 0.6 (60%)
     * - 6 curses: 0.9 (90%)
     */
    fun getEnemyShedSkinBoost(curses: CurseState): Float {
        return (curses.shedSkinCurse * 0.15f).coerceAtMost(0.9f)
    }

    /**
     * Check if Torment curse is active.
     *
     * When active, the player cannot use the same move twice in a row.
     * This forces move variety and prevents PP-efficient strategies.
     *
     * @param curses The current curse state
     * @return true if Torment curse is active
     */
    fun isTormentCurseActive(curses: CurseState): Boolean {
        return curses.tormentCurse
    }

    /**
     * Check if Pressure curse is active.
     *
     * When active, all player moves consume 2 PP instead of 1.
     * This effectively halves the PP of all moves, making PP management critical.
     *
     * @param curses The current curse state
     * @return true if Pressure curse is active
     */
    fun isPressureCurseActive(curses: CurseState): Boolean {
        return curses.pressureCurse
    }

    /**
     * Helper function to calculate effective flinch chance considering both
     * Flinch Curse and Serene Grace Curse.
     *
     * Formula:
     * 1. Base flinch chance from move (e.g., 0.1 for Bite)
     * 2. Apply Serene Grace multiplier: base * sereneGraceMultiplier
     * 3. Add flat Flinch Curse boost: result + flinchBoost
     * 4. Cap at 100%
     *
     * @param baseFlinchChance The move's base flinch chance (0.0 to 1.0)
     * @param curses The current curse state
     * @return Effective flinch chance (0.0 to 1.0)
     *
     * Example (Bite with 10% base flinch):
     * - No curses: 0.1 (10%)
     * - 3 Serene Grace: 0.25 (25%)
     * - 5 Flinch: 0.6 (60%)
     * - 3 Serene Grace + 5 Flinch: 0.75 (75%)
     */
    fun calculateEffectiveFlinchChance(baseFlinchChance: Float, curses: CurseState): Float {
        val sereneGraceMultiplier = getEnemySecondaryEffectMultiplier(curses)
        val flinchBoost = getEnemyFlinchBoost(curses)
        return ((baseFlinchChance * sereneGraceMultiplier) + flinchBoost).coerceAtMost(1.0f)
    }

    /**
     * Helper function to calculate effective secondary effect chance considering
     * Serene Grace Curse.
     *
     * Formula: baseChance * sereneGraceMultiplier, capped at 100%
     *
     * @param baseChance The move's base secondary effect chance (0.0 to 1.0)
     * @param curses The current curse state
     * @return Effective secondary effect chance (0.0 to 1.0)
     *
     * Example (Flamethrower with 10% burn):
     * - 0 curses: 0.1 (10%)
     * - 1 curse: 0.15 (15%)
     * - 2 curses: 0.2 (20%)
     * - 3 curses: 0.25 (25%)
     */
    fun calculateEffectiveSecondaryChance(baseChance: Float, curses: CurseState): Float {
        val multiplier = getEnemySecondaryEffectMultiplier(curses)
        return (baseChance * multiplier).coerceAtMost(1.0f)
    }
}
