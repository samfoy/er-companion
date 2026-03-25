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
        moveName: String = "Unknown",
        attackerItem: Int = 0,
        defenderItem: Int = 0,
        attackerHp: Int = -1,
        attackerMaxHp: Int = -1,
        isSuperEffective: Boolean = false,
        attackerAbility: Int = 0,
        defenderAbility: Int = 0
    ): DamageResult {
        // Status moves or moves with 0 power
        if (movePower == 0 || defenseStat <= 0 || attackStat <= 0 || attackerLevel <= 0) {
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

        // Check defender ability for type immunity (Levitate, Flash Fire, etc.)
        if (com.ercompanion.data.AbilityData.grantsImmunity(defenderAbility, moveType)) {
            return DamageResult(
                moveName = moveName,
                minDamage = 0,
                maxDamage = 0,
                effectiveness = 0f,
                effectLabel = "No effect (${com.ercompanion.data.AbilityData.getAbilityName(defenderAbility)})",
                percentMin = 0,
                percentMax = 0,
                isStab = false,
                wouldKO = false
            )
        }

        // Apply attacker's held item stat modifiers
        val attackerItemEffect = com.ercompanion.data.ItemData.getItemEffect(attackerItem)
        var modifiedAttackStat = attackStat
        if (attackerItemEffect != null) {
            // Apply stat modifiers (Choice Band/Specs, Muscle Band, Wise Glasses, etc.)
            val statMods = com.ercompanion.data.ItemData.applyItemToStats(
                attack = attackStat,
                defense = 0,
                spAttack = attackStat,
                spDefense = 0,
                speed = 0,
                itemId = attackerItem,
                currentHp = attackerHp,
                maxHp = attackerMaxHp
            )
            // Use the appropriate stat based on move category (simplified - assumes attack stat passed is correct)
            modifiedAttackStat = maxOf(statMods.attack, statMods.spAttack)
        }

        // Apply defender's held item stat modifiers
        val defenderItemEffect = com.ercompanion.data.ItemData.getItemEffect(defenderItem)
        var modifiedDefenseStat = defenseStat
        if (defenderItemEffect != null) {
            val statMods = com.ercompanion.data.ItemData.applyItemToStats(
                attack = 0,
                defense = defenseStat,
                spAttack = 0,
                spDefense = defenseStat,
                speed = 0,
                itemId = defenderItem
            )
            modifiedDefenseStat = maxOf(statMods.defense, statMods.spDefense)
        }

        // Base damage calculation: power * attack * (2 * level / 5 + 2) / defense / 50 + 2
        val levelMod = (2 * attackerLevel / 5 + 2)
        var baseDamage = (movePower.toLong() * modifiedAttackStat * levelMod / modifiedDefenseStat / 50 + 2).toInt()

        // Apply burn (0.5x if physical and burned, unless Guts ability)
        if (isBurned && attackerAbility != 62) {  // 62 = Guts
            baseDamage = (baseDamage * 0.5f).toInt()
        }

        // STAB (normally 1.5x, but Adaptability makes it 2x)
        val isStab = attackerTypes.contains(moveType)
        if (isStab) {
            val stabMultiplier = com.ercompanion.data.AbilityData.getStabMultiplier(attackerAbility)
            baseDamage = (baseDamage * stabMultiplier).toInt()
        }

        // Type effectiveness (multiply by effectiveness against each defender type)
        var effectiveness = 1f
        for (defType in defenderTypes) {
            if (moveType in 0..17 && defType in 0..17) {
                effectiveness *= TYPE_CHART[moveType][defType]
            }
        }

        var effectiveBaseDamage = (baseDamage * effectiveness).toInt()

        // Apply attacker ability damage multipliers (Sheer Force, Technician, pinch abilities)
        val abilityDamageMod = com.ercompanion.data.AbilityData.getDamageMultiplier(
            abilityId = attackerAbility,
            movePower = movePower,
            moveType = moveType,
            attackerTypes = attackerTypes,
            currentHp = attackerHp,
            maxHp = attackerMaxHp
        )
        if (abilityDamageMod > 1.0f) {
            effectiveBaseDamage = (effectiveBaseDamage * abilityDamageMod).toInt()
        }

        // Apply defender ability defensive multipliers (Thick Fat)
        val defensiveAbilityMod = com.ercompanion.data.AbilityData.getDefensiveMultiplier(defenderAbility, moveType)
        if (defensiveAbilityMod != 1.0f) {
            effectiveBaseDamage = (effectiveBaseDamage * defensiveAbilityMod).toInt()
        }

        // Apply attacker's held item damage multipliers
        if (attackerItemEffect != null) {
            // Life Orb: 1.3x all damage
            if (attackerItemEffect.damageMod > 1.0f) {
                effectiveBaseDamage = (effectiveBaseDamage * attackerItemEffect.damageMod).toInt()
            }

            // Expert Belt: 1.2x on super effective (only if effectiveness > 1)
            if (attackerItemEffect.name == "Expert Belt" && effectiveness > 1.0f) {
                effectiveBaseDamage = (effectiveBaseDamage * 1.2f).toInt()
            }

            // Type plates: 1.2x for specific move type
            if (attackerItemEffect.typeBoostedMove == moveType && attackerItemEffect.typeBoostMultiplier > 1.0f) {
                effectiveBaseDamage = (effectiveBaseDamage * attackerItemEffect.typeBoostMultiplier).toInt()
            }
        }

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
