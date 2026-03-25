package com.ercompanion.calc

import kotlin.math.max

data class DamageResult(
    val moveName: String,
    val minDamage: Int,       // at 85% roll
    val maxDamage: Int,       // at 100% roll
    val effectiveness: Float, // 0.0, 0.25, 0.5, 1.0, 2.0, 4.0
    val effectLabel: String,  // "", "Not very effective", "Super effective!", "No effect"
    val percentMin: Int,      // min as % of target maxHP
    val percentMax: Int,      // max as % of target maxHP
    val isStab: Boolean,
    val wouldKO: Boolean      // percentMax >= 100
)

object DamageCalculator {
    // Type IDs: 0=Normal, 1=Fighting, 2=Flying, 3=Poison, 4=Ground, 5=Rock, 6=Bug, 7=Ghost,
    // 8=Steel, 9=Fire, 10=Water, 11=Grass, 12=Electric, 13=Psychic, 14=Ice, 15=Dragon, 16=Dark, 17=Fairy

    // Type effectiveness table [attacking type][defending type]
    // Gen6+ chart with Fairy type
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
        attackStat: Int,    // atk or spAtk depending on move category
        defenseStat: Int,   // def or spDef depending on move category
        movePower: Int,
        moveType: Int,      // type ID
        attackerTypes: List<Int>,
        defenderTypes: List<Int>,
        targetMaxHP: Int,
        isBurned: Boolean = false,
        weather: Int = 0,   // 0 = none, could extend for weather
        moveName: String = "Unknown"
    ): DamageResult {
        // Status moves or moves with 0 power
        if (movePower == 0) {
            return DamageResult(
                moveName = moveName,
                minDamage = 0,
                maxDamage = 0,
                effectiveness = 0f,
                effectLabel = "",
                percentMin = 0,
                percentMax = 0,
                isStab = false,
                wouldKO = false
            )
        }

        // Base damage calculation: power * attack * (2 * level / 5 + 2) / defense / 50 + 2
        val levelMod = (2 * attackerLevel / 5 + 2)
        var baseDamage = movePower * attackStat * levelMod / defenseStat / 50 + 2

        // Apply burn (0.5x if physical and burned, unless Guts - we don't track abilities yet)
        if (isBurned) {
            baseDamage = (baseDamage * 0.5f).toInt()
        }

        // STAB (1.5x if move type matches any attacker type)
        val isStab = attackerTypes.contains(moveType)
        if (isStab) {
            baseDamage = (baseDamage * 1.5f).toInt()
        }

        // Type effectiveness (multiply by effectiveness against each defender type)
        var effectiveness = 1f
        for (defType in defenderTypes) {
            if (moveType in 0..17 && defType in 0..17) {
                effectiveness *= TYPE_CHART[moveType][defType]
            }
        }

        val effectiveBaseDamage = (baseDamage * effectiveness).toInt()

        // Random factor: 85-100%
        val minDamage = max(1, (effectiveBaseDamage * 0.85f).toInt())
        val maxDamage = max(1, effectiveBaseDamage)

        // Calculate percentages
        val percentMin = if (targetMaxHP > 0) (minDamage * 100 / targetMaxHP) else 0
        val percentMax = if (targetMaxHP > 0) (maxDamage * 100 / targetMaxHP) else 0

        // Effect label
        val effectLabel = when {
            effectiveness == 0f -> "No effect"
            effectiveness < 0.5f -> "Not very effective"
            effectiveness < 1f -> "Not very effective"
            effectiveness > 2f -> "Super effective!"
            effectiveness > 1f -> "Super effective!"
            else -> ""
        }

        return DamageResult(
            moveName = moveName,
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

    fun getTypeEffectiveness(attackType: Int, defenderTypes: List<Int>): Float {
        var effectiveness = 1f
        for (defType in defenderTypes) {
            if (attackType in 0..17 && defType in 0..17) {
                effectiveness *= TYPE_CHART[attackType][defType]
            }
        }
        return effectiveness
    }
}
