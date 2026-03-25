package com.ercompanion.data

import kotlin.math.floor

data class DamageResult(
    val moveName: String,
    val minDamage: Int,
    val maxDamage: Int,
    val effectiveness: Float,
    val effectLabel: String,
    val percentMin: Int,
    val percentMax: Int,
    val isStab: Boolean,
    val wouldKO: Boolean
)

object DamageCalculator {
    // Type IDs (Gen3+ ordering)
    const val TYPE_NORMAL = 0
    const val TYPE_FIGHTING = 1
    const val TYPE_FLYING = 2
    const val TYPE_POISON = 3
    const val TYPE_GROUND = 4
    const val TYPE_ROCK = 5
    const val TYPE_BUG = 6
    const val TYPE_GHOST = 7
    const val TYPE_STEEL = 8
    const val TYPE_FIRE = 9
    const val TYPE_WATER = 10
    const val TYPE_GRASS = 11
    const val TYPE_ELECTRIC = 12
    const val TYPE_PSYCHIC = 13
    const val TYPE_ICE = 14
    const val TYPE_DRAGON = 15
    const val TYPE_DARK = 16
    const val TYPE_FAIRY = 17

    // Type effectiveness table [attacker][defender] = multiplier
    // 0.0 = immune, 0.5 = not very effective, 1.0 = neutral, 2.0 = super effective
    private val TYPE_CHART = arrayOf(
        // Normal
        floatArrayOf(1f, 1f, 1f, 1f, 1f, 0.5f, 1f, 0f, 0.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f),
        // Fighting
        floatArrayOf(2f, 1f, 0.5f, 0.5f, 1f, 2f, 0.5f, 0f, 2f, 1f, 1f, 1f, 1f, 0.5f, 2f, 1f, 2f, 0.5f),
        // Flying
        floatArrayOf(1f, 2f, 1f, 1f, 1f, 0.5f, 2f, 1f, 0.5f, 1f, 1f, 2f, 0.5f, 1f, 1f, 1f, 1f, 1f),
        // Poison
        floatArrayOf(1f, 1f, 1f, 0.5f, 0.5f, 0.5f, 1f, 0.5f, 0f, 1f, 1f, 2f, 1f, 1f, 1f, 1f, 1f, 2f),
        // Ground
        floatArrayOf(1f, 1f, 0f, 2f, 1f, 2f, 0.5f, 1f, 2f, 2f, 1f, 0.5f, 2f, 1f, 1f, 1f, 1f, 1f),
        // Rock
        floatArrayOf(1f, 0.5f, 2f, 1f, 0.5f, 1f, 2f, 1f, 0.5f, 2f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 1f),
        // Bug
        floatArrayOf(1f, 0.5f, 0.5f, 0.5f, 1f, 1f, 1f, 0.5f, 0.5f, 0.5f, 1f, 2f, 1f, 2f, 1f, 1f, 2f, 0.5f),
        // Ghost
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 0.5f, 1f),
        // Steel
        floatArrayOf(1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 0.5f, 0.5f, 0.5f, 1f, 0.5f, 1f, 2f, 1f, 1f, 2f),
        // Fire
        floatArrayOf(1f, 1f, 1f, 1f, 1f, 0.5f, 2f, 1f, 2f, 0.5f, 0.5f, 2f, 1f, 1f, 2f, 0.5f, 1f, 1f),
        // Water
        floatArrayOf(1f, 1f, 1f, 1f, 2f, 2f, 1f, 1f, 1f, 2f, 0.5f, 0.5f, 1f, 1f, 1f, 0.5f, 1f, 1f),
        // Grass
        floatArrayOf(1f, 1f, 0.5f, 0.5f, 2f, 2f, 0.5f, 1f, 0.5f, 0.5f, 2f, 0.5f, 1f, 1f, 1f, 0.5f, 1f, 1f),
        // Electric
        floatArrayOf(1f, 1f, 2f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 2f, 0.5f, 0.5f, 1f, 1f, 0.5f, 1f, 1f),
        // Psychic
        floatArrayOf(1f, 2f, 1f, 2f, 1f, 1f, 1f, 1f, 0.5f, 1f, 1f, 1f, 1f, 0.5f, 1f, 1f, 0f, 1f),
        // Ice
        floatArrayOf(1f, 1f, 2f, 1f, 2f, 1f, 1f, 1f, 0.5f, 0.5f, 0.5f, 2f, 1f, 1f, 0.5f, 2f, 1f, 1f),
        // Dragon
        floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.5f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 0f),
        // Dark
        floatArrayOf(1f, 0.5f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 0.5f, 0.5f),
        // Fairy
        floatArrayOf(1f, 2f, 1f, 0.5f, 1f, 1f, 1f, 1f, 0.5f, 0.5f, 1f, 1f, 1f, 1f, 1f, 2f, 2f, 1f)
    )

    fun calc(
        attackerLevel: Int,
        attackStat: Int,
        defenseStat: Int,
        movePower: Int,
        moveType: Int,
        attackerTypes: List<Int>,
        defenderTypes: List<Int>,
        targetMaxHP: Int,
        targetCurrentHP: Int,
        isBurned: Boolean = false,
        weather: Int = 0,
        isCrit: Boolean = false,
        moveCategory: Int = 0 // 0=physical, 1=special
    ): DamageResult {
        // Status moves deal no damage
        if (movePower == 0) {
            return DamageResult(
                moveName = "",
                minDamage = 0,
                maxDamage = 0,
                effectiveness = 1f,
                effectLabel = "",
                percentMin = 0,
                percentMax = 0,
                isStab = false,
                wouldKO = false
            )
        }

        // Base damage: power * attack * (2 * level / 5 + 2) / defense / 50 + 2
        val levelFactor = (2 * attackerLevel / 5 + 2).toFloat()
        var baseDamage = movePower * attackStat * levelFactor / defenseStat / 50f + 2f

        // Critical hit (1.5x)
        if (isCrit) {
            baseDamage *= 1.5f
        }

        // STAB (Same Type Attack Bonus)
        val isStab = attackerTypes.contains(moveType)
        if (isStab) {
            baseDamage *= 1.5f
        }

        // Type effectiveness (can stack for dual types)
        var effectiveness = 1f
        for (defType in defenderTypes) {
            if (moveType in 0..17 && defType in 0..17) {
                effectiveness *= TYPE_CHART[moveType][defType]
            }
        }
        baseDamage *= effectiveness

        // Burn (0.5x for physical moves)
        if (isBurned && moveCategory == 0) {
            baseDamage *= 0.5f
        }

        // Random factor: 85% - 100%
        val minDamage = floor(baseDamage * 0.85f).toInt().coerceAtLeast(1)
        val maxDamage = floor(baseDamage * 1.0f).toInt().coerceAtLeast(1)

        // Calculate percentages
        val percentMin = if (targetMaxHP > 0) (minDamage * 100 / targetMaxHP) else 0
        val percentMax = if (targetMaxHP > 0) (maxDamage * 100 / targetMaxHP) else 0

        // Determine effect label
        val effectLabel = when {
            effectiveness == 0f -> "No effect"
            effectiveness < 1f -> "Not very effective"
            effectiveness > 1f -> "Super effective!"
            else -> ""
        }

        return DamageResult(
            moveName = "",
            minDamage = minDamage,
            maxDamage = maxDamage,
            effectiveness = effectiveness,
            effectLabel = effectLabel,
            percentMin = percentMin,
            percentMax = percentMax,
            isStab = isStab,
            wouldKO = percentMax >= 100
        )
    }

    fun getTypeEffectiveness(moveType: Int, defenderTypes: List<Int>): Float {
        var effectiveness = 1f
        for (defType in defenderTypes) {
            if (moveType in 0..17 && defType in 0..17) {
                effectiveness *= TYPE_CHART[moveType][defType]
            }
        }
        return effectiveness
    }
}
