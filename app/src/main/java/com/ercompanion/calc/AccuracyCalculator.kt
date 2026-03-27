package com.ercompanion.calc

import com.ercompanion.data.AbilityData
import com.ercompanion.data.MoveData
import kotlin.random.Random

/**
 * Handles accuracy and evasion calculations for Pokemon battles.
 *
 * Gen 3+ Mechanics:
 * - Accuracy stages: -6 to +6
 * - Evasion stages: -6 to +6
 * - Stage modifiers: 3/3, 3/4, 3/5, 3/6, 3/7, 3/8, 3/9
 * - Hit chance = (MoveAccuracy * AccuracyMod) / (EvasionMod * 100)
 * - Some moves never miss (Swift, Aerial Ace, etc.)
 * - Some abilities affect accuracy (Keen Eye, Compound Eyes, No Guard)
 */
object AccuracyCalculator {

    // Ability constants
    private const val COMPOUND_EYES = 14  // +30% accuracy
    private const val KEEN_EYE = 51       // Ignores evasion boosts
    private const val NO_GUARD = 99       // All moves always hit (both sides)
    private const val HUSTLE = 55         // Physical moves: +50% attack, -20% accuracy
    private const val TANGLED_FEET = 77   // +1 evasion when confused
    private const val SAND_VEIL = 8       // +1 evasion in sandstorm
    private const val SNOW_CLOAK = 81     // +1 evasion in hail

    /**
     * Get stage multiplier for accuracy/evasion.
     * Stage 0 = 3/3 = 1.0x
     * Positive stages increase multiplier: +1 = 4/3, +2 = 5/3, ..., +6 = 9/3 = 3.0x
     * Negative stages decrease multiplier: -1 = 3/4, -2 = 3/5, ..., -6 = 3/9 = 0.33x
     */
    private fun getStageMultiplier(stage: Int): Float {
        val clampedStage = stage.coerceIn(-6, 6)
        return if (clampedStage >= 0) {
            (3 + clampedStage) / 3.0f
        } else {
            3.0f / (3 - clampedStage)
        }
    }

    /**
     * Calculate hit chance for a move.
     *
     * @param moveData The move being used
     * @param attackerAccuracy Attacker's accuracy stage (-6 to +6)
     * @param defenderEvasion Defender's evasion stage (-6 to +6)
     * @param attackerAbility Attacker's ability ID
     * @param defenderAbility Defender's ability ID
     * @param attackerConfused Whether attacker is confused (for Tangled Feet)
     * @param weather Current weather (for Sand Veil, Snow Cloak)
     * @return Hit chance as float (0.0 to 1.0), or 1.0 if move always hits
     */
    fun calculateHitChance(
        moveData: MoveData,
        attackerAccuracy: Int = 0,
        defenderEvasion: Int = 0,
        attackerAbility: Int = 0,
        defenderAbility: Int = 0,
        attackerConfused: Boolean = false,
        weather: Weather = Weather.NONE
    ): Float {
        // No Guard: All moves always hit (attacker or defender)
        if (attackerAbility == NO_GUARD || defenderAbility == NO_GUARD) {
            return 1.0f
        }

        // Move always hits (Swift, Aerial Ace, etc.)
        if (moveData.alwaysHits || moveData.accuracy < 0) {
            return 1.0f
        }

        // Start with move's base accuracy
        var accuracy = moveData.accuracy.toFloat() / 100f

        // Apply Compound Eyes (+30% accuracy)
        if (attackerAbility == COMPOUND_EYES) {
            accuracy *= 1.3f
        }

        // Apply Hustle (-20% accuracy for physical moves)
        if (attackerAbility == HUSTLE && moveData.category == 0) { // Physical
            accuracy *= 0.8f
        }

        // Calculate accuracy stage multiplier
        val accuracyMod = getStageMultiplier(attackerAccuracy)

        // Calculate evasion stage multiplier
        var effectiveEvasion = defenderEvasion

        // Keen Eye: Ignores evasion boosts (but not drops)
        if (attackerAbility == KEEN_EYE && effectiveEvasion > 0) {
            effectiveEvasion = 0
        }

        // Tangled Feet: +1 evasion when confused
        if (attackerConfused && defenderAbility == TANGLED_FEET) {
            effectiveEvasion += 1
        }

        // Sand Veil: +1 evasion in sandstorm
        if (weather == Weather.SANDSTORM && defenderAbility == SAND_VEIL) {
            effectiveEvasion += 1
        }

        // Snow Cloak: +1 evasion in hail
        if (weather == Weather.HAIL && defenderAbility == SNOW_CLOAK) {
            effectiveEvasion += 1
        }

        val evasionMod = getStageMultiplier(effectiveEvasion)

        // Final hit chance = (Accuracy × AccuracyMod) / EvasionMod
        val hitChance = (accuracy * accuracyMod) / evasionMod

        // Clamp to valid range [0.0, 1.0]
        return hitChance.coerceIn(0.0f, 1.0f)
    }

    /**
     * Roll for whether a move hits.
     * @return true if move hits, false if it misses
     */
    fun rollForHit(
        moveData: MoveData,
        attackerAccuracy: Int = 0,
        defenderEvasion: Int = 0,
        attackerAbility: Int = 0,
        defenderAbility: Int = 0,
        attackerConfused: Boolean = false,
        weather: Weather = Weather.NONE
    ): Boolean {
        val hitChance = calculateHitChance(
            moveData,
            attackerAccuracy,
            defenderEvasion,
            attackerAbility,
            defenderAbility,
            attackerConfused,
            weather
        )

        // If hit chance is 100%, always hit
        if (hitChance >= 1.0f) return true

        // Roll random number [0.0, 1.0) and check if it's less than hit chance
        return Random.nextFloat() < hitChance
    }

    /**
     * Get accuracy percentage for display purposes.
     * @return Accuracy as percentage string (e.g., "95%", "100%")
     */
    fun getAccuracyDisplay(
        moveData: MoveData,
        attackerAccuracy: Int = 0,
        defenderEvasion: Int = 0,
        attackerAbility: Int = 0,
        defenderAbility: Int = 0,
        attackerConfused: Boolean = false,
        weather: Weather = Weather.NONE
    ): String {
        val hitChance = calculateHitChance(
            moveData,
            attackerAccuracy,
            defenderEvasion,
            attackerAbility,
            defenderAbility,
            attackerConfused,
            weather
        )

        return if (hitChance >= 1.0f) {
            "100%"
        } else {
            "${(hitChance * 100).toInt()}%"
        }
    }

    /**
     * Check if a move will never miss given the current conditions.
     */
    fun isGuaranteedHit(
        moveData: MoveData,
        attackerAbility: Int = 0,
        defenderAbility: Int = 0
    ): Boolean {
        return moveData.alwaysHits
            || moveData.accuracy < 0
            || attackerAbility == NO_GUARD
            || defenderAbility == NO_GUARD
    }
}
