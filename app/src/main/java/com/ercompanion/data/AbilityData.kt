package com.ercompanion.data

/**
 * Pokemon ability effects for damage calculation
 *
 * IMPORTANT: Stats read from memory already include base stats, IVs, EVs, and Nature.
 * We should NOT reapply Nature modifiers. Only apply ability effects that:
 * 1. Modify damage calculation (Sheer Force, Technician, etc.)
 * 2. Grant type immunities (Levitate, Flash Fire, etc.)
 * 3. Apply temporary battle modifiers not baked into stats
 */
data class AbilityEffect(
    val id: Int,
    val name: String,
    val damageMultiplier: Float = 1.0f,
    val stabMultiplier: Float = 1.5f,  // Default STAB, some abilities change this
    val typeImmunities: List<Int> = emptyList(),
    val lowPowerBoost: Boolean = false,  // Technician: <=60 power
    val sheerForce: Boolean = false,
    val description: String = ""
)

object AbilityData {
    // Common ability IDs from Gen 3+
    private const val OVERGROW = 65
    private const val BLAZE = 66
    private const val TORRENT = 67
    private const val SWARM = 68
    private const val STURDY = 5
    private const val LEVITATE = 26
    private const val INTIMIDATE = 22
    private const val HUGE_POWER = 37
    private const val PURE_POWER = 74
    private const val ADAPTABILITY = 91
    private const val TECHNICIAN = 101
    private const val SHEER_FORCE = 125
    private const val FLASH_FIRE = 18
    private const val VOLT_ABSORB = 10
    private const val WATER_ABSORB = 11
    private const val MOTOR_DRIVE = 78
    private const val SAP_SIPPER = 157
    private const val THICK_FAT = 47
    private const val MARVEL_SCALE = 63
    private const val GUTS = 62
    private const val SHED_SKIN = 61
    private const val COMPOUND_EYES = 14
    private const val MAGIC_GUARD = 98
    private const val NO_GUARD = 99
    private const val CLEAR_BODY = 29

    private val ABILITY_EFFECTS = mapOf(
        // Starter abilities (1.5x at low HP)
        OVERGROW to AbilityEffect(
            id = OVERGROW,
            name = "Overgrow",
            description = "+50% Grass moves at ≤33% HP"
        ),
        BLAZE to AbilityEffect(
            id = BLAZE,
            name = "Blaze",
            description = "+50% Fire moves at ≤33% HP"
        ),
        TORRENT to AbilityEffect(
            id = TORRENT,
            name = "Torrent",
            description = "+50% Water moves at ≤33% HP"
        ),
        SWARM to AbilityEffect(
            id = SWARM,
            name = "Swarm",
            description = "+50% Bug moves at ≤33% HP"
        ),

        // Type immunities
        LEVITATE to AbilityEffect(
            id = LEVITATE,
            name = "Levitate",
            typeImmunities = listOf(4),  // Ground
            description = "Immune to Ground"
        ),
        FLASH_FIRE to AbilityEffect(
            id = FLASH_FIRE,
            name = "Flash Fire",
            typeImmunities = listOf(9),  // Fire
            description = "Immune to Fire, +50% Fire dmg when activated"
        ),
        VOLT_ABSORB to AbilityEffect(
            id = VOLT_ABSORB,
            name = "Volt Absorb",
            typeImmunities = listOf(12),  // Electric
            description = "Immune to Electric, heals 25%"
        ),
        WATER_ABSORB to AbilityEffect(
            id = WATER_ABSORB,
            name = "Water Absorb",
            typeImmunities = listOf(10),  // Water
            description = "Immune to Water, heals 25%"
        ),
        MOTOR_DRIVE to AbilityEffect(
            id = MOTOR_DRIVE,
            name = "Motor Drive",
            typeImmunities = listOf(12),  // Electric
            description = "Immune to Electric, +1 Speed"
        ),
        SAP_SIPPER to AbilityEffect(
            id = SAP_SIPPER,
            name = "Sap Sipper",
            typeImmunities = listOf(11),  // Grass
            description = "Immune to Grass, +1 Attack"
        ),

        // Damage modifiers
        HUGE_POWER to AbilityEffect(
            id = HUGE_POWER,
            name = "Huge Power",
            description = "⚠️ 2x Attack (already in stats)"
        ),
        PURE_POWER to AbilityEffect(
            id = PURE_POWER,
            name = "Pure Power",
            description = "⚠️ 2x Attack (already in stats)"
        ),
        ADAPTABILITY to AbilityEffect(
            id = ADAPTABILITY,
            name = "Adaptability",
            stabMultiplier = 2.0f,
            description = "STAB is 2x instead of 1.5x"
        ),
        TECHNICIAN to AbilityEffect(
            id = TECHNICIAN,
            name = "Technician",
            lowPowerBoost = true,
            description = "+50% damage for moves ≤60 power"
        ),
        SHEER_FORCE to AbilityEffect(
            id = SHEER_FORCE,
            name = "Sheer Force",
            sheerForce = true,
            damageMultiplier = 1.3f,
            description = "+30% damage, removes secondary effects"
        ),

        // Defensive abilities
        THICK_FAT to AbilityEffect(
            id = THICK_FAT,
            name = "Thick Fat",
            description = "Halves Fire/Ice damage taken"
        ),
        MARVEL_SCALE to AbilityEffect(
            id = MARVEL_SCALE,
            name = "Marvel Scale",
            description = "+50% Defense when statused"
        ),
        GUTS to AbilityEffect(
            id = GUTS,
            name = "Guts",
            description = "+50% Attack when statused"
        ),
        SHED_SKIN to AbilityEffect(
            id = SHED_SKIN,
            name = "Shed Skin",
            description = "33% chance to cure status each turn"
        ),
        MAGIC_GUARD to AbilityEffect(
            id = MAGIC_GUARD,
            name = "Magic Guard",
            description = "Only damaged by direct attacks"
        ),
        STURDY to AbilityEffect(
            id = STURDY,
            name = "Sturdy",
            description = "Survives 1 HP from full health"
        ),

        // Utility
        INTIMIDATE to AbilityEffect(
            id = INTIMIDATE,
            name = "Intimidate",
            description = "Lowers opponent Attack on switch-in"
        ),
        COMPOUND_EYES to AbilityEffect(
            id = COMPOUND_EYES,
            name = "Compound Eyes",
            description = "+30% move accuracy"
        ),
        NO_GUARD to AbilityEffect(
            id = NO_GUARD,
            name = "No Guard",
            description = "All moves always hit"
        ),
        CLEAR_BODY to AbilityEffect(
            id = CLEAR_BODY,
            name = "Clear Body",
            description = "Prevents stat reduction"
        )
    )

    fun getAbilityEffect(abilityId: Int): AbilityEffect? {
        return ABILITY_EFFECTS[abilityId]
    }

    fun getAbilityName(abilityId: Int): String {
        // First try to get from effects (includes description)
        val effect = ABILITY_EFFECTS[abilityId]
        if (effect != null) return effect.name

        // Fall back to name-only lookup
        return AbilityNames.getName(abilityId)
    }

    /**
     * Check if ability grants immunity to a specific move type
     */
    fun grantsImmunity(abilityId: Int, moveType: Int): Boolean {
        val ability = ABILITY_EFFECTS[abilityId] ?: return false
        return ability.typeImmunities.contains(moveType)
    }

    /**
     * Get damage multiplier from ability
     * Does NOT modify stats - only affects damage calculation
     *
     * @param abilityId The ability ID
     * @param movePower The move's base power
     * @param moveType The move's type
     * @param attackerTypes The attacker's types (for STAB check)
     * @param currentHp Current HP (for pinch abilities like Overgrow)
     * @param maxHp Max HP
     */
    fun getDamageMultiplier(
        abilityId: Int,
        movePower: Int,
        moveType: Int,
        attackerTypes: List<Int>,
        currentHp: Int = -1,
        maxHp: Int = -1
    ): Float {
        val ability = ABILITY_EFFECTS[abilityId] ?: return 1.0f

        var multiplier = 1.0f

        // Sheer Force
        if (ability.sheerForce) {
            multiplier *= ability.damageMultiplier
        }

        // Technician (<=60 power moves)
        if (ability.lowPowerBoost && movePower > 0 && movePower <= 60) {
            multiplier *= 1.5f
        }

        // Pinch abilities (Overgrow, Blaze, Torrent, Swarm)
        if (currentHp > 0 && maxHp > 0 && (currentHp.toFloat() / maxHp) <= 0.33f) {
            when (abilityId) {
                OVERGROW -> if (moveType == 11) multiplier *= 1.5f  // Grass
                BLAZE -> if (moveType == 9) multiplier *= 1.5f  // Fire
                TORRENT -> if (moveType == 10) multiplier *= 1.5f  // Water
                SWARM -> if (moveType == 6) multiplier *= 1.5f  // Bug
            }
        }

        return multiplier
    }

    /**
     * Get STAB multiplier (normally 1.5x, but Adaptability makes it 2x)
     */
    fun getStabMultiplier(abilityId: Int): Float {
        val ability = ABILITY_EFFECTS[abilityId] ?: return 1.5f
        return ability.stabMultiplier
    }

    /**
     * Check if ability reduces damage from a specific type
     * (Thick Fat halves Fire/Ice damage)
     */
    fun getDefensiveMultiplier(abilityId: Int, moveType: Int): Float {
        if (abilityId == THICK_FAT && (moveType == 9 || moveType == 14)) {
            return 0.5f  // Fire or Ice
        }
        return 1.0f
    }
}
