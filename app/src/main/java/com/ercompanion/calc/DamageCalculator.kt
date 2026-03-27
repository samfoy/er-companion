package com.ercompanion.calc

import com.ercompanion.data.MoveData
import com.ercompanion.parser.PartyMon
import kotlin.math.max
import com.ercompanion.calc.CurseEffects
import com.ercompanion.calc.CurseState

data class DamageResult(
    val moveName: String,
    val minDamage: Int,       // at 85% roll
    val maxDamage: Int,       // at 100% roll
    val effectiveness: Float, // 0.0, 0.25, 0.5, 1.0, 2.0, 4.0
    val effectLabel: String,  // "", "Not very effective", "Super effective!", "No effect"
    val percentMin: Int,      // min as % of target maxHP
    val percentMax: Int,      // max as % of target maxHP
    val isStab: Boolean,
    val wouldKO: Boolean,     // percentMax >= 100
    val isValid: Boolean = true  // false when stats are unavailable (pre-battle)
)

/**
 * Damage calculator implementing Gen 3 formula for Pokémon Emerald Rogue.
 *
 * Formula source: https://github.com/pret/pokeemerald/blob/master/src/battle_script_commands.c
 * Type chart: emerogue uses Gen 6+ mechanics (B_STEEL_RESISTANCES = GEN_LATEST)
 *
 * Gen 3 Formula: (((2×Level÷5+2)×Power×Atk÷Def÷50)×Burn+2)×STAB×Type×Random
 */
object DamageCalculator {
    // Type IDs: 0=Normal, 1=Fighting, 2=Flying, 3=Poison, 4=Ground, 5=Rock, 6=Bug, 7=Ghost,
    // 8=Steel, 9=Fire, 10=Water, 11=Grass, 12=Electric, 13=Psychic, 14=Ice, 15=Dragon, 16=Dark, 17=Fairy

    // Issue 2.4: Maximum damage cap (Pokemon games cap at 65535)
    private const val MAX_DAMAGE = 65535

    // Cache for type effectiveness calculations (Issue 3.3)
    private val typeEffectivenessCache = mutableMapOf<Pair<Int, List<Int>>, Float>()

    // Type effectiveness table [attacking type][defending type]
    // Gen 6+ chart with Fairy type (correct for emerogue)
    // Source: https://bulbapedia.bulbagarden.net/wiki/Type/Type_chart
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

    // Issue 2.17: Compile-time validation for type chart dimensions
    init {
        require(TYPE_CHART.size == 18) { "TYPE_CHART must have 18 rows (one per type)" }
        TYPE_CHART.forEachIndexed { index, row ->
            require(row.size == 18) { "TYPE_CHART row $index must have 18 columns" }
        }
    }

    /**
     * Calculate damage for a Pokemon move using Gen 6+ damage formula.
     *
     * @param isEnemyAttacking True if this is an enemy attack (for curse effects)
     * @param curses Active curse state (modifies enemy battle performance)
     *
     * Curse effects implemented:
     * - OHKO Curse: Enemy attacks always OHKO
     * - Adaptability Curse: Enemy STAB moves deal extra damage
     * - Crit Curse: Not implemented (base damage calc doesn't handle crits)
     * - Endure Curse: Should be checked by caller when applying damage
     */
    fun calc(
        attackerLevel: Int,
        attackStat: Int,    // atk or spAtk (MUST be post-stat-stage for active battlers)
        defenseStat: Int,   // def or spDef (MUST be post-stat-stage for active battlers)
        movePower: Int,
        moveType: Int,      // type ID
        attackerTypes: List<Int>,
        defenderTypes: List<Int>,
        targetMaxHP: Int,
        isBurned: Boolean = false,
        weather: Int = 0,   // 0 = none, Weather enum ordinal
        moveName: String = "Unknown",
        attackerItem: Int = 0,
        defenderItem: Int = 0,
        attackerHp: Int = -1,
        attackerMaxHp: Int = -1,
        isSuperEffective: Boolean = false,
        attackerAbility: Int = 0,
        defenderAbility: Int = 0,

        // New parameters for advanced effects
        attackerStatus: Int = 0,          // Full status for Guts, Marvel Scale
        defenderStatus: Int = 0,
        terrain: Int = 0,                 // Terrain enum ordinal
        moveCategory: Int = 0,            // 0=Physical, 1=Special, 2=Status
        moveData: MoveData? = null,       // Full move data for advanced checks
        effectiveness: Float = -1f,       // Pre-calculated effectiveness (or -1 to calculate)
        isGrounded: Boolean = true,       // For terrain effects (true if not Flying/Levitate)
        attackerSpecies: Int = 0,         // For type checking in weather stat multipliers
        defenderSpecies: Int = 0,
        isEnemyAttacking: Boolean = false,  // Needed to know when to apply curses
        curses: CurseState = CurseState.NONE
    ): DamageResult {
        // NOTE: For active battlers, stats should come from gBattleMons which are already
        // modified by stat stages (baseline=6). Do NOT manually apply stat stage modifiers.

        // Check if stats are invalid (pre-battle state)
        if (defenseStat <= 0 || attackStat <= 0 || attackerLevel <= 0) {
            return DamageResult(
                moveName = moveName,
                minDamage = 0,
                maxDamage = 0,
                effectiveness = 0f,
                effectLabel = "Stats unavailable",
                percentMin = 0,
                percentMax = 0,
                isStab = false,
                wouldKO = false,
                isValid = false
            )
        }

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
                wouldKO = false,
                isValid = true
            )
        }

        // OHKO Curse: Enemy attacks always OHKO if they deal any damage
        if (CurseEffects.isOhkoCurseActive(curses) && isEnemyAttacking && movePower > 0) {
            val isStab = attackerTypes.contains(moveType)
            // Calculate effectiveness if not provided
            val actualEffectiveness = if (effectiveness >= 0f) {
                effectiveness
            } else {
                var eff = 1f
                for (defType in defenderTypes) {
                    if (moveType in 0..17 && defType in 0..17) {
                        eff *= TYPE_CHART[moveType][defType]
                    }
                }
                eff
            }
            return DamageResult(
                moveName = moveName,
                minDamage = targetMaxHP,
                maxDamage = targetMaxHP,
                effectiveness = actualEffectiveness,
                effectLabel = "⚠️ OHKO CURSE - Instant KO",
                percentMin = 100,
                percentMax = 100,
                isStab = isStab,
                wouldKO = true,
                isValid = true
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

        // Create temporary BattlerState objects for ability/weather/terrain checks
        val attackerBattler = createTempBattler(
            attackStat = attackStat,
            defenseStat = defenseStat,
            attackerTypes = attackerTypes,
            attackerHp = attackerHp,
            attackerMaxHp = attackerMaxHp,
            attackerAbility = attackerAbility,
            attackerItem = attackerItem,
            attackerStatus = attackerStatus,
            attackerSpecies = attackerSpecies,
            attackerLevel = attackerLevel
        )

        val defenderBattler = createTempBattler(
            attackStat = defenseStat,
            defenseStat = attackStat,
            attackerTypes = defenderTypes,
            attackerHp = targetMaxHP,
            attackerMaxHp = targetMaxHP,
            attackerAbility = defenderAbility,
            attackerItem = defenderItem,
            attackerStatus = defenderStatus,
            attackerSpecies = defenderSpecies,
            attackerLevel = attackerLevel
        )

        // Get or create MoveData
        val actualMoveData = moveData ?: MoveData(
            name = moveName,
            power = movePower,
            type = moveType,
            category = moveCategory
        )

        // ----- STAT MODIFICATIONS -----

        // Apply attacker's held item stat modifiers (Choice Band/Specs)
        // NOTE: moveCategory 0=Physical (uses Attack), 1=Special (uses SpAtk)
        val attackerItemEffect = com.ercompanion.data.ItemData.getItemEffect(attackerItem)
        var modifiedAttackStat = attackStat
        if (attackerItemEffect != null) {
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
            // Use correct stat based on move category: Physical=Attack, Special=SpAtk
            modifiedAttackStat = if (moveCategory == 0) statMods.attack else statMods.spAttack
        }

        // Apply defender's held item stat modifiers
        // NOTE: Physical moves use Defense, Special moves use SpDefense
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
            // Use correct stat based on move category: Physical=Defense, Special=SpDefense
            modifiedDefenseStat = if (moveCategory == 0) statMods.defense else statMods.spDefense
        }

        // Apply ability stat multipliers (Guts for attack, Marvel Scale for defense)
        // NOTE: gBattleMons stats already include Intimidate and stat stage changes.
        // Only apply ability multipliers for things NOT already baked in (e.g. Guts, Huge Power).
        // Intimidate is already reflected in gBattleMons[slot].attack — do NOT double-apply.
        val abilityAtkMult = AbilityEffects.getAttackerStatMultiplier(attackerBattler, moveCategory)
        modifiedAttackStat = (modifiedAttackStat * abilityAtkMult).toInt()

        val abilityDefMult = AbilityEffects.getDefenderStatMultiplier(defenderBattler, moveCategory)
        modifiedDefenseStat = (modifiedDefenseStat * abilityDefMult).toInt()

        // Apply weather stat multipliers (Sandstorm boosts Rock SpDef)
        if (weather != 0 && weather < Weather.values().size) {
            val weatherEnum = Weather.values()[weather]
            val weatherStatMult = WeatherEffects.getWeatherStatMultiplier(
                weatherEnum,
                defenderBattler,
                if (moveCategory == 0) "defense" else "spDefense"
            )
            modifiedDefenseStat = (modifiedDefenseStat * weatherStatMult).toInt()
        }

        // ----- MOVE POWER MODIFICATIONS -----

        var modifiedMovePower = movePower

        // Apply ability move power multipliers (Technician, Iron Fist, etc.)
        // Note: We can't use AbilityEffects.getMovePowerMultiplier here without a move ID.
        // Instead, we'll check Technician directly since it only depends on power.
        if (attackerAbility == 101 && movePower > 0 && movePower <= 60) {
            // Technician: 1.5x for moves with 60 or less base power
            modifiedMovePower = (modifiedMovePower * 1.5f).toInt()
        }

        // ----- BASE DAMAGE CALCULATION -----

        val levelMod = (2 * attackerLevel / 5 + 2)
        // Prevent division by zero - ensure defense is at least 1
        val safeDefenseStat = modifiedDefenseStat.coerceAtLeast(1)

        // Gen 3 formula from pokeemerald src/battle_script_commands.c:
        // damage = (((2*level/5+2) * power * Atk / Def) / 50)
        // then: if burned, damage /= 2   (BEFORE the +2)
        // then: damage += 2
        var baseDamage = (modifiedMovePower.toLong() * modifiedAttackStat * levelMod / safeDefenseStat / 50).toInt()

        // Apply burn BEFORE +2 (Gen 3 order: burn halves, then +2 is added)
        // Only affects physical moves; Guts ability (62) negates burn penalty
        if (isBurned && moveCategory == 0 && attackerAbility != 62) {
            baseDamage = (baseDamage * 0.5f).toInt()
        }

        // Add the +2 constant (Gen 3 formula)
        baseDamage += 2

        // ----- STAB -----

        val isStab = attackerTypes.contains(moveType)

        // STAB multiplier (1.5x base, 2x with Adaptability ability)
        val baseStabMultiplier = com.ercompanion.data.AbilityData.getStabMultiplier(attackerAbility)

        // Apply Adaptability curse to ENEMY moves - adds bonus STAB damage
        val finalStabMultiplier = if (isStab) {
            if (isEnemyAttacking) {
                // Enemy gets curse-enhanced STAB (replaces base STAB calculation)
                // getEnemyStabMultiplier returns 1.5 + (0.05 * adaptabilityCurse), max 2.0
                CurseEffects.getEnemyStabMultiplier(curses)
            } else {
                baseStabMultiplier
            }
        } else {
            1.0f
        }

        if (isStab) {
            baseDamage = (baseDamage * finalStabMultiplier).toInt()
        }

        // ----- TYPE EFFECTIVENESS -----

        val typeEffectiveness = if (effectiveness >= 0f) {
            effectiveness  // Use pre-calculated
        } else {
            // Use cached type effectiveness calculation (Issue 3.3)
            val cacheKey = moveType to defenderTypes
            typeEffectivenessCache.getOrPut(cacheKey) {
                var eff = 1f
                for (defType in defenderTypes) {
                    if (moveType in 0..17 && defType in 0..17) {
                        eff *= TYPE_CHART[moveType][defType]
                    }
                }
                eff
            }
        }

        var effectiveBaseDamage = (baseDamage * typeEffectiveness).toInt()

        // ----- WEATHER MULTIPLIERS -----

        if (weather != 0 && weather < Weather.values().size) {
            val weatherEnum = Weather.values()[weather]
            val weatherMult = WeatherEffects.getWeatherMultiplier(
                weatherEnum,
                moveType,
                attackerBattler
            )
            effectiveBaseDamage = (effectiveBaseDamage * weatherMult).toInt()
        }

        // ----- TERRAIN MULTIPLIERS -----

        if (terrain != 0 && terrain < Terrain.values().size && isGrounded) {
            val terrainEnum = Terrain.values()[terrain]
            val terrainMult = TerrainEffects.getTerrainMultiplier(
                terrainEnum,
                moveType,
                attackerBattler,
                isGrounded
            )
            effectiveBaseDamage = (effectiveBaseDamage * terrainMult).toInt()
        }

        // ----- ABILITY DAMAGE MULTIPLIERS -----

        // Legacy ability damage multipliers (Sheer Force, pinch abilities)
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

        // Defender ability defensive multipliers (Thick Fat)
        val defensiveAbilityMod = com.ercompanion.data.AbilityData.getDefensiveMultiplier(defenderAbility, moveType)
        if (defensiveAbilityMod != 1.0f) {
            effectiveBaseDamage = (effectiveBaseDamage * defensiveAbilityMod).toInt()
        }

        // ----- ITEM DAMAGE MULTIPLIERS -----

        // Use advanced item effects from ItemEffects module
        val itemDamageMult = ItemEffects.getDamageMultiplier(
            attackerItem,
            actualMoveData,
            typeEffectiveness
        )
        effectiveBaseDamage = (effectiveBaseDamage * itemDamageMult).toInt()

        // Legacy: Fall back to old item effect system if new one returns 1.0
        if (itemDamageMult == 1.0f && attackerItemEffect != null) {
            // Life Orb: 1.3x all damage
            if (attackerItemEffect.damageMod > 1.0f) {
                effectiveBaseDamage = (effectiveBaseDamage * attackerItemEffect.damageMod).toInt()
            }

            // Expert Belt: 1.2x on super effective (only if effectiveness > 1)
            if (attackerItemEffect.name == "Expert Belt" && typeEffectiveness > 1.0f) {
                effectiveBaseDamage = (effectiveBaseDamage * 1.2f).toInt()
            }

            // Type plates: 1.2x for specific move type
            if (attackerItemEffect.typeBoostedMove == moveType && attackerItemEffect.typeBoostMultiplier > 1.0f) {
                effectiveBaseDamage = (effectiveBaseDamage * attackerItemEffect.typeBoostMultiplier).toInt()
            }
        }

        // Random factor: 85-100%
        // Issue 2.4: Cap damage at MAX_DAMAGE (65535) to prevent overflow
        val minDamage = (effectiveBaseDamage * 0.85f).toInt().coerceIn(1, MAX_DAMAGE)
        val maxDamage = effectiveBaseDamage.coerceIn(1, MAX_DAMAGE)

        // Calculate percentages
        val percentMin = if (targetMaxHP > 0) (minDamage * 100 / targetMaxHP) else 0
        val percentMax = if (targetMaxHP > 0) (maxDamage * 100 / targetMaxHP) else 0

        // Effect label
        val effectLabel = when {
            typeEffectiveness == 0f -> "No effect"
            typeEffectiveness < 0.5f -> "Not very effective"
            typeEffectiveness < 1f -> "Not very effective"
            typeEffectiveness > 2f -> "Super effective!"
            typeEffectiveness > 1f -> "Super effective!"
            else -> ""
        }

        return DamageResult(
            moveName = moveName,
            minDamage = minDamage,
            maxDamage = maxDamage,
            effectiveness = typeEffectiveness,
            effectLabel = effectLabel,
            percentMin = percentMin,
            percentMax = percentMax,
            isStab = isStab,
            wouldKO = percentMax >= 100
        )
    }

    /**
     * Helper function to create a temporary BattlerState for ability/weather/terrain checks.
     * This is a simplified version that only includes the minimal data needed.
     */
    private fun createTempBattler(
        attackStat: Int,
        defenseStat: Int,
        attackerTypes: List<Int>,
        attackerHp: Int,
        attackerMaxHp: Int,
        attackerAbility: Int,
        attackerItem: Int,
        attackerStatus: Int,
        attackerSpecies: Int,
        attackerLevel: Int
    ): BattlerState {
        val tempMon = PartyMon(
            species = attackerSpecies,
            level = attackerLevel,
            hp = if (attackerHp > 0) attackerHp else if (attackerMaxHp > 0) attackerMaxHp else 100,
            maxHp = if (attackerMaxHp > 0) attackerMaxHp else 100,
            nickname = "",
            moves = listOf(0, 0, 0, 0),
            attack = attackStat,
            defense = defenseStat,
            speed = 100,
            spAttack = attackStat,
            spDefense = defenseStat,
            experience = 0,
            friendship = 0,
            heldItem = attackerItem,
            ability = attackerAbility,
            personality = 0u,
            ivHp = 0,
            ivAttack = 0,
            ivDefense = 0,
            ivSpeed = 0,
            ivSpAttack = 0,
            ivSpDefense = 0,
            status = attackerStatus,
            movePP = listOf(0, 0, 0, 0),
            otId = 0L,
            nature = 0u,
            abilitySlot = 0
        )

        return BattlerState(
            mon = tempMon,
            currentHp = if (attackerHp > 0) attackerHp else if (attackerMaxHp > 0) attackerMaxHp else 100,
            statStages = StatStages(),
            status = attackerStatus,
            tempBoosts = TempBoosts()
        )
    }

    fun getTypeEffectiveness(attackType: Int, defenderTypes: List<Int>): Float {
        // Use cached type effectiveness calculation (Issue 3.3)
        val cacheKey = attackType to defenderTypes
        return typeEffectivenessCache.getOrPut(cacheKey) {
            var effectiveness = 1f
            for (defType in defenderTypes) {
                if (attackType in 0..17 && defType in 0..17) {
                    effectiveness *= TYPE_CHART[attackType][defType]
                }
            }
            effectiveness
        }
    }
}
