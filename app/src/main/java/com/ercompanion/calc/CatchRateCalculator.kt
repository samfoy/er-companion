package com.ercompanion.calc

import kotlin.math.pow

data class CatchChance(
    val ballName: String,
    val catchRate: Float,  // 0.0 to 1.0
    val percentChance: Int,  // 0-100
    val shakeCount: Int     // Expected number of shakes (0-4)
)

object CatchRateCalculator {
    // Poke Ball multipliers (Gen 3)
    const val POKE_BALL = 1.0f
    const val GREAT_BALL = 1.5f
    const val ULTRA_BALL = 2.0f
    const val MASTER_BALL = 255.0f
    const val NET_BALL = 3.0f      // 3x for Bug/Water
    const val NEST_BALL = 1.0f     // (40 - level) / 10, max 3.9x
    const val REPEAT_BALL = 3.0f   // 3x if already caught
    const val TIMER_BALL = 1.0f    // 1 + (turns / 10), max 4x
    const val DIVE_BALL = 3.5f     // 3.5x when underwater/fishing
    const val DUSK_BALL = 3.5f     // 3.5x at night or in caves
    const val QUICK_BALL = 4.0f    // 4x on turn 1
    const val LUXURY_BALL = 1.0f
    const val PREMIER_BALL = 1.0f
    const val SAFARI_BALL = 1.5f

    // Status multipliers
    const val STATUS_NONE = 1.0f
    const val STATUS_SLEEP_FREEZE = 2.0f
    const val STATUS_PARA_BURN_POISON = 1.5f

    /**
     * Calculate catch chance using Gen 3 formula:
     * a = ((3*maxHP - 2*currentHP) * catchRate * ballMod * statusMod) / (3*maxHP)
     * Then: b = 65536 / sqrt(sqrt(255/a))
     * Catch if: 4 random numbers (0-65535) are all < b
     */
    fun calculateCatchChance(
        currentHP: Int,
        maxHP: Int,
        speciesCatchRate: Int,  // Species base catch rate (3-255)
        ballModifier: Float,
        statusModifier: Float = STATUS_NONE
    ): CatchChance {
        if (currentHP <= 0 || maxHP <= 0 || speciesCatchRate <= 0) {
            return CatchChance("", 0f, 0, 0)
        }

        // Master Ball always catches
        if (ballModifier >= MASTER_BALL) {
            return CatchChance("Master Ball", 1.0f, 100, 4)
        }

        // Modified catch rate
        val a = ((3 * maxHP - 2 * currentHP) * speciesCatchRate * ballModifier * statusModifier) / (3 * maxHP)

        // Clamp to valid range
        val aClamped = a.coerceIn(0f, 255f)

        // Shake check value
        val b = (65536 / Math.sqrt(Math.sqrt(255.0 / aClamped.toDouble()))).toInt()

        // Each shake is a 0-65535 check against b
        // Probability of passing one check: b / 65536
        val singleShakeChance = b.toFloat() / 65536f

        // Probability of passing all 4 shakes (catch)
        val catchChance = singleShakeChance.pow(4)

        // Expected number of shakes before failure
        val expectedShakes = when {
            catchChance >= 0.99f -> 4
            singleShakeChance >= 0.75f -> 3
            singleShakeChance >= 0.5f -> 2
            singleShakeChance >= 0.25f -> 1
            else -> 0
        }

        return CatchChance(
            ballName = "",
            catchRate = catchChance,
            percentChance = (catchChance * 100).toInt(),
            shakeCount = expectedShakes
        )
    }

    fun getCommonBalls(): List<Pair<String, Float>> {
        return listOf(
            "Poké Ball" to POKE_BALL,
            "Great Ball" to GREAT_BALL,
            "Ultra Ball" to ULTRA_BALL,
            "Quick Ball" to QUICK_BALL,
            "Timer Ball" to TIMER_BALL,
            "Dusk Ball" to DUSK_BALL,
            "Repeat Ball" to REPEAT_BALL,
            "Net Ball" to NET_BALL
        )
    }

    /**
     * Get species base catch rate (simplified - only common ones)
     * Full list would be 400+ entries
     */
    fun getSpeciesCatchRate(speciesId: Int): Int {
        // Legendaries: 3
        // Starters: 45
        // Early game: 255
        // Mid game: 120-190
        // Late game: 45-75

        // Default to 100 for unknown (mid-range)
        return when (speciesId) {
            // Starters
            1, 2, 3 -> 45    // Bulbasaur line
            4, 5, 6 -> 45    // Charmander line
            7, 8, 9 -> 45    // Squirtle line

            // Early game commons
            10, 11, 12 -> 255  // Caterpie line
            13, 14, 15 -> 255  // Weedle line
            16, 17, 18 -> 255  // Pidgey line
            19, 20 -> 255      // Rattata line

            // Pikachu
            25 -> 190
            26 -> 75

            // Pseudo-legendaries
            147, 148, 149 -> 45  // Dratini line
            246, 247, 248 -> 45  // Larvitar line
            371, 372, 373 -> 45  // Bagon line

            // Legendaries
            144, 145, 146 -> 3   // Legendary birds
            150, 151 -> 3        // Mewtwo, Mew
            243, 244, 245 -> 3   // Legendary beasts
            249, 250 -> 3        // Lugia, Ho-Oh
            377, 378, 379 -> 3   // Regis
            380, 381 -> 2        // Lati@s (lower for roaming)
            382, 383, 384 -> 3   // Weather trio

            else -> 100  // Default mid-range
        }
    }
}
