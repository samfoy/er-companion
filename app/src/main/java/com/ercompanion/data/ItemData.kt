package com.ercompanion.data

/**
 * Held item effects for Pokemon Emerald Rogue
 * Item IDs from Gen 3 format (13-459 range covers most items)
 */
data class ItemEffect(
    val id: Int,
    val name: String,
    val attackMod: Float = 1.0f,
    val defenseMod: Float = 1.0f,
    val spAttackMod: Float = 1.0f,
    val spDefenseMod: Float = 1.0f,
    val speedMod: Float = 1.0f,
    val damageMod: Float = 1.0f,
    val typeBoostedMove: Int = -1,  // -1 = none, else type ID
    val typeBoostMultiplier: Float = 1.0f,
    val locksMove: Boolean = false,
    val description: String = ""
)

object ItemData {
    // Choice items
    private const val CHOICE_BAND = 222
    private const val CHOICE_SPECS = 223
    private const val CHOICE_SCARF = 224

    // Power items
    private const val LIFE_ORB = 225
    private const val EXPERT_BELT = 226
    private const val MUSCLE_BAND = 227
    private const val WISE_GLASSES = 228

    // Defensive items
    private const val ASSAULT_VEST = 229
    private const val ROCKY_HELMET = 230
    private const val EVIOLITE = 231

    // Type plates (Gen 4+)
    private const val FLAME_PLATE = 298
    private const val SPLASH_PLATE = 299
    private const val ZAP_PLATE = 300
    private const val MEADOW_PLATE = 301
    private const val ICICLE_PLATE = 302
    private const val FIST_PLATE = 303
    private const val TOXIC_PLATE = 304
    private const val EARTH_PLATE = 305
    private const val SKY_PLATE = 306
    private const val MIND_PLATE = 307
    private const val INSECT_PLATE = 308
    private const val STONE_PLATE = 309
    private const val SPOOKY_PLATE = 310
    private const val DRACO_PLATE = 311
    private const val DREAD_PLATE = 312
    private const val IRON_PLATE = 313
    private const val PIXIE_PLATE = 314

    // Stat berries
    private const val LIECHI_BERRY = 201
    private const val GANLON_BERRY = 202
    private const val SALAC_BERRY = 203
    private const val PETAYA_BERRY = 204
    private const val APICOT_BERRY = 205

    // Common items
    private const val LEFTOVERS = 234
    private const val FOCUS_SASH = 235
    private const val AIR_BALLOON = 236

    private val ITEM_EFFECTS = mapOf(
        // Choice items - 1.5x boost but lock move
        CHOICE_BAND to ItemEffect(
            id = CHOICE_BAND,
            name = "Choice Band",
            attackMod = 1.5f,
            locksMove = true,
            description = "+50% Attack, locks move"
        ),
        CHOICE_SPECS to ItemEffect(
            id = CHOICE_SPECS,
            name = "Choice Specs",
            spAttackMod = 1.5f,
            locksMove = true,
            description = "+50% Sp.Atk, locks move"
        ),
        CHOICE_SCARF to ItemEffect(
            id = CHOICE_SCARF,
            name = "Choice Scarf",
            speedMod = 1.5f,
            locksMove = true,
            description = "+50% Speed, locks move"
        ),

        // Power items
        LIFE_ORB to ItemEffect(
            id = LIFE_ORB,
            name = "Life Orb",
            damageMod = 1.3f,
            description = "+30% damage, 10% recoil"
        ),
        EXPERT_BELT to ItemEffect(
            id = EXPERT_BELT,
            name = "Expert Belt",
            damageMod = 1.2f,
            description = "+20% on super effective"
        ),
        MUSCLE_BAND to ItemEffect(
            id = MUSCLE_BAND,
            name = "Muscle Band",
            attackMod = 1.1f,
            description = "+10% physical damage"
        ),
        WISE_GLASSES to ItemEffect(
            id = WISE_GLASSES,
            name = "Wise Glasses",
            spAttackMod = 1.1f,
            description = "+10% special damage"
        ),

        // Defensive items
        ASSAULT_VEST to ItemEffect(
            id = ASSAULT_VEST,
            name = "Assault Vest",
            spDefenseMod = 1.5f,
            description = "+50% Sp.Def, no status moves"
        ),
        ROCKY_HELMET to ItemEffect(
            id = ROCKY_HELMET,
            name = "Rocky Helmet",
            description = "1/6 damage to attacker on contact"
        ),
        EVIOLITE to ItemEffect(
            id = EVIOLITE,
            name = "Eviolite",
            defenseMod = 1.5f,
            spDefenseMod = 1.5f,
            description = "+50% Def/Sp.Def (unevolved)"
        ),

        // Type plates (+20% damage for specific type)
        FLAME_PLATE to ItemEffect(
            id = FLAME_PLATE,
            name = "Flame Plate",
            typeBoostedMove = 9,  // Fire
            typeBoostMultiplier = 1.2f,
            description = "+20% Fire damage"
        ),
        SPLASH_PLATE to ItemEffect(
            id = SPLASH_PLATE,
            name = "Splash Plate",
            typeBoostedMove = 10,  // Water
            typeBoostMultiplier = 1.2f,
            description = "+20% Water damage"
        ),
        ZAP_PLATE to ItemEffect(
            id = ZAP_PLATE,
            name = "Zap Plate",
            typeBoostedMove = 12,  // Electric
            typeBoostMultiplier = 1.2f,
            description = "+20% Electric damage"
        ),
        MEADOW_PLATE to ItemEffect(
            id = MEADOW_PLATE,
            name = "Meadow Plate",
            typeBoostedMove = 11,  // Grass
            typeBoostMultiplier = 1.2f,
            description = "+20% Grass damage"
        ),
        ICICLE_PLATE to ItemEffect(
            id = ICICLE_PLATE,
            name = "Icicle Plate",
            typeBoostedMove = 14,  // Ice
            typeBoostMultiplier = 1.2f,
            description = "+20% Ice damage"
        ),
        FIST_PLATE to ItemEffect(
            id = FIST_PLATE,
            name = "Fist Plate",
            typeBoostedMove = 1,  // Fighting
            typeBoostMultiplier = 1.2f,
            description = "+20% Fighting damage"
        ),
        TOXIC_PLATE to ItemEffect(
            id = TOXIC_PLATE,
            name = "Toxic Plate",
            typeBoostedMove = 3,  // Poison
            typeBoostMultiplier = 1.2f,
            description = "+20% Poison damage"
        ),
        EARTH_PLATE to ItemEffect(
            id = EARTH_PLATE,
            name = "Earth Plate",
            typeBoostedMove = 4,  // Ground
            typeBoostMultiplier = 1.2f,
            description = "+20% Ground damage"
        ),
        SKY_PLATE to ItemEffect(
            id = SKY_PLATE,
            name = "Sky Plate",
            typeBoostedMove = 2,  // Flying
            typeBoostMultiplier = 1.2f,
            description = "+20% Flying damage"
        ),
        MIND_PLATE to ItemEffect(
            id = MIND_PLATE,
            name = "Mind Plate",
            typeBoostedMove = 13,  // Psychic
            typeBoostMultiplier = 1.2f,
            description = "+20% Psychic damage"
        ),
        INSECT_PLATE to ItemEffect(
            id = INSECT_PLATE,
            name = "Insect Plate",
            typeBoostedMove = 6,  // Bug
            typeBoostMultiplier = 1.2f,
            description = "+20% Bug damage"
        ),
        STONE_PLATE to ItemEffect(
            id = STONE_PLATE,
            name = "Stone Plate",
            typeBoostedMove = 5,  // Rock
            typeBoostMultiplier = 1.2f,
            description = "+20% Rock damage"
        ),
        SPOOKY_PLATE to ItemEffect(
            id = SPOOKY_PLATE,
            name = "Spooky Plate",
            typeBoostedMove = 7,  // Ghost
            typeBoostMultiplier = 1.2f,
            description = "+20% Ghost damage"
        ),
        DRACO_PLATE to ItemEffect(
            id = DRACO_PLATE,
            name = "Draco Plate",
            typeBoostedMove = 15,  // Dragon
            typeBoostMultiplier = 1.2f,
            description = "+20% Dragon damage"
        ),
        DREAD_PLATE to ItemEffect(
            id = DREAD_PLATE,
            name = "Dread Plate",
            typeBoostedMove = 16,  // Dark
            typeBoostMultiplier = 1.2f,
            description = "+20% Dark damage"
        ),
        IRON_PLATE to ItemEffect(
            id = IRON_PLATE,
            name = "Iron Plate",
            typeBoostedMove = 8,  // Steel
            typeBoostMultiplier = 1.2f,
            description = "+20% Steel damage"
        ),
        PIXIE_PLATE to ItemEffect(
            id = PIXIE_PLATE,
            name = "Pixie Plate",
            typeBoostedMove = 17,  // Fairy
            typeBoostMultiplier = 1.2f,
            description = "+20% Fairy damage"
        ),

        // Stat boost berries (activate at 25% HP)
        LIECHI_BERRY to ItemEffect(
            id = LIECHI_BERRY,
            name = "Liechi Berry",
            attackMod = 1.5f,
            description = "+50% Atk at <25% HP"
        ),
        GANLON_BERRY to ItemEffect(
            id = GANLON_BERRY,
            name = "Ganlon Berry",
            defenseMod = 1.5f,
            description = "+50% Def at <25% HP"
        ),
        SALAC_BERRY to ItemEffect(
            id = SALAC_BERRY,
            name = "Salac Berry",
            speedMod = 1.5f,
            description = "+50% Speed at <25% HP"
        ),
        PETAYA_BERRY to ItemEffect(
            id = PETAYA_BERRY,
            name = "Petaya Berry",
            spAttackMod = 1.5f,
            description = "+50% Sp.Atk at <25% HP"
        ),
        APICOT_BERRY to ItemEffect(
            id = APICOT_BERRY,
            name = "Apicot Berry",
            spDefenseMod = 1.5f,
            description = "+50% Sp.Def at <25% HP"
        ),

        // Utility items
        LEFTOVERS to ItemEffect(
            id = LEFTOVERS,
            name = "Leftovers",
            description = "Restores 1/16 HP per turn"
        ),
        FOCUS_SASH to ItemEffect(
            id = FOCUS_SASH,
            name = "Focus Sash",
            description = "Survives 1 HP from full health"
        ),
        AIR_BALLOON to ItemEffect(
            id = AIR_BALLOON,
            name = "Air Balloon",
            description = "Immune to Ground until hit"
        )
    )

    fun getItemEffect(itemId: Int): ItemEffect? {
        return ITEM_EFFECTS[itemId]
    }

    fun getItemName(itemId: Int): String {
        return ITEM_EFFECTS[itemId]?.name ?: "Unknown Item #$itemId"
    }

    /**
     * Apply item stat modifiers to base stats
     * Note: Some items have conditional effects (berries at low HP, etc)
     */
    fun applyItemToStats(
        attack: Int,
        defense: Int,
        spAttack: Int,
        spDefense: Int,
        speed: Int,
        itemId: Int,
        currentHp: Int = -1,
        maxHp: Int = -1
    ): StatModifiers {
        val effect = getItemEffect(itemId) ?: return StatModifiers(attack, defense, spAttack, spDefense, speed)

        // Check if stat boost berries should activate (< 25% HP)
        val isBerryActive = currentHp > 0 && maxHp > 0 &&
            (currentHp.toFloat() / maxHp) < 0.25f &&
            itemId in listOf(LIECHI_BERRY, GANLON_BERRY, SALAC_BERRY, PETAYA_BERRY, APICOT_BERRY)

        val atkMod = if (isBerryActive && itemId == LIECHI_BERRY) effect.attackMod else
                     if (itemId == CHOICE_BAND || itemId == MUSCLE_BAND) effect.attackMod else 1.0f
        val defMod = if (isBerryActive && itemId == GANLON_BERRY) effect.defenseMod else
                     if (itemId == EVIOLITE) effect.defenseMod else 1.0f
        val spaMod = if (isBerryActive && itemId == PETAYA_BERRY) effect.spAttackMod else
                     if (itemId == CHOICE_SPECS || itemId == WISE_GLASSES) effect.spAttackMod else 1.0f
        val spdMod = if (isBerryActive && itemId == APICOT_BERRY) effect.spDefenseMod else
                     if (itemId == ASSAULT_VEST || itemId == EVIOLITE) effect.spDefenseMod else 1.0f
        val speMod = if (isBerryActive && itemId == SALAC_BERRY) effect.speedMod else
                     if (itemId == CHOICE_SCARF) effect.speedMod else 1.0f

        return StatModifiers(
            attack = (attack * atkMod).toInt(),
            defense = (defense * defMod).toInt(),
            spAttack = (spAttack * spaMod).toInt(),
            spDefense = (spDefense * spdMod).toInt(),
            speed = (speed * speMod).toInt()
        )
    }

    data class StatModifiers(
        val attack: Int,
        val defense: Int,
        val spAttack: Int,
        val spDefense: Int,
        val speed: Int
    )
}
