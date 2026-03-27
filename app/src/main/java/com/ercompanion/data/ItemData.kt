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
    // Emerald Rogue item IDs from emerogue/include/constants/items.h
    // The 10-bit heldItem field in Pokemon data stores these IDs directly (0-1023).

    // Choice items
    private const val CHOICE_BAND = 453    // ITEM_CHOICE_BAND
    private const val CHOICE_SPECS = 454   // ITEM_CHOICE_SPECS
    private const val CHOICE_SCARF = 455   // ITEM_CHOICE_SCARF

    // Power items
    private const val LIFE_ORB = 490       // ITEM_LIFE_ORB
    private const val EXPERT_BELT = 488    // ITEM_EXPERT_BELT
    private const val MUSCLE_BAND = 486    // ITEM_MUSCLE_BAND
    private const val WISE_GLASSES = 487   // ITEM_WISE_GLASSES

    // Defensive items
    private const val ASSAULT_VEST = 514   // ITEM_ASSAULT_VEST
    private const val ROCKY_HELMET = 507   // ITEM_ROCKY_HELMET
    private const val EVIOLITE = 505       // ITEM_EVIOLITE
    private const val AIR_BALLOON = 508    // ITEM_AIR_BALLOON
    private const val IRON_BALL = 495      // ITEM_IRON_BALL

    // Status orbs
    private const val FLAME_ORB = 456      // ITEM_FLAME_ORB
    private const val TOXIC_ORB = 457      // ITEM_TOXIC_ORB

    // Healing items
    private const val BLACK_SLUDGE = 498   // ITEM_BLACK_SLUDGE

    // Type plates (Gen 4 - 1.2x boost)
    private const val FLAME_PLATE = 261    // Fire
    private const val SPLASH_PLATE = 262   // Water
    private const val ZAP_PLATE = 263      // Electric
    private const val MEADOW_PLATE = 264   // Grass
    private const val ICICLE_PLATE = 265   // Ice
    private const val FIST_PLATE = 266     // Fighting
    private const val TOXIC_PLATE = 267    // Poison
    private const val EARTH_PLATE = 268    // Ground
    private const val SKY_PLATE = 269      // Flying
    private const val MIND_PLATE = 270     // Psychic
    private const val INSECT_PLATE = 271   // Bug
    private const val STONE_PLATE = 272    // Rock
    private const val SPOOKY_PLATE = 273   // Ghost
    private const val DRACO_PLATE = 274    // Dragon
    private const val DREAD_PLATE = 275    // Dark
    private const val IRON_PLATE = 276     // Steel
    private const val PIXIE_PLATE = 277    // Fairy

    // Type gems (Gen 5 - 1.3x boost, one-time use)
    private const val NORMAL_GEM = 350
    private const val FIRE_GEM = 351
    private const val WATER_GEM = 352
    private const val ELECTRIC_GEM = 353
    private const val GRASS_GEM = 354
    private const val ICE_GEM = 355
    private const val FIGHTING_GEM = 356
    private const val POISON_GEM = 357
    private const val GROUND_GEM = 358
    private const val FLYING_GEM = 359
    private const val PSYCHIC_GEM = 360
    private const val BUG_GEM = 361
    private const val ROCK_GEM = 362
    private const val GHOST_GEM = 363
    private const val DRAGON_GEM = 364
    private const val DARK_GEM = 365
    private const val STEEL_GEM = 366
    private const val FAIRY_GEM = 367

    // Type-boosting items (Gen 3 - 1.1x boost)
    private const val SILK_SCARF = 436     // Normal
    private const val CHARCOAL = 437       // Fire
    private const val MYSTIC_WATER = 438   // Water
    private const val MAGNET = 439         // Electric
    private const val MIRACLE_SEED = 440   // Grass
    private const val NEVER_MELT_ICE = 441 // Ice
    private const val BLACK_BELT = 442     // Fighting
    private const val POISON_BARB = 443    // Poison
    private const val SOFT_SAND = 444      // Ground
    private const val SHARP_BEAK = 445     // Flying
    private const val TWISTED_SPOON = 446  // Psychic
    private const val SILVER_POWDER = 447  // Bug
    private const val HARD_STONE = 448     // Rock
    private const val SPELL_TAG = 449      // Ghost
    private const val DRAGON_FANG = 450    // Dragon
    private const val BLACK_GLASSES = 451  // Dark
    private const val METAL_COAT = 452     // Steel

    // Accuracy/Priority items
    private const val BRIGHT_POWDER = 470
    private const val QUICK_CLAW = 473
    private const val SCOPE_LENS = 482
    private const val WIDE_LENS = 485
    private const val ZOOM_LENS = 493

    // Utility items
    private const val WHITE_HERB = 471
    private const val MENTAL_HERB = 475
    private const val KINGS_ROCK = 476
    private const val LIGHT_CLAY = 489
    private const val POWER_HERB = 491
    private const val METRONOME = 494
    private const val LAGGING_TAIL = 496
    private const val DESTINY_KNOT = 497
    private const val GRIP_CLAW = 499
    private const val STICKY_BARB = 500
    private const val SHED_SHELL = 501
    private const val BIG_ROOT = 502
    private const val RAZOR_CLAW = 503
    private const val RAZOR_FANG = 504
    private const val FLOAT_STONE = 506
    private const val RED_CARD = 509
    private const val RING_TARGET = 510
    private const val BINDING_BAND = 511
    private const val EJECT_BUTTON = 512
    private const val WEAKNESS_POLICY = 513
    private const val SAFETY_GOGGLES = 515
    private const val ADRENALINE_ORB = 516
    private const val TERRAIN_EXTENDER = 517
    private const val PROTECTIVE_PADS = 518
    private const val THROAT_SPRAY = 519
    private const val EJECT_PACK = 520
    private const val HEAVY_DUTY_BOOTS = 521
    private const val BLUNDER_POLICY = 522
    private const val ROOM_SERVICE = 523
    private const val UTILITY_UMBRELLA = 524

    // Status cure berries
    private const val CHERI_BERRY = 525    // Cures paralysis
    private const val CHESTO_BERRY = 526   // Cures sleep
    private const val PECHA_BERRY = 527    // Cures poison
    private const val RAWST_BERRY = 528    // Cures burn
    private const val ASPEAR_BERRY = 529   // Cures freeze
    private const val PERSIM_BERRY = 532   // Cures confusion
    private const val LUM_BERRY = 533      // Cures all status
    private const val SITRUS_BERRY = 534   // Heals 30 HP

    // Type-resistance berries (reduce super-effective damage by 50%)
    private const val CHILAN_BERRY = 560   // Normal
    private const val OCCA_BERRY = 561     // Fire
    private const val PASSHO_BERRY = 562   // Water
    private const val WACAN_BERRY = 563    // Electric
    private const val RINDO_BERRY = 564    // Grass
    private const val YACHE_BERRY = 565    // Ice
    private const val CHOPLE_BERRY = 566   // Fighting
    private const val KEBIA_BERRY = 567    // Poison
    private const val SHUCA_BERRY = 568    // Ground
    private const val COBA_BERRY = 569     // Flying
    private const val PAYAPA_BERRY = 570   // Psychic
    private const val TANGA_BERRY = 571    // Bug
    private const val CHARTI_BERRY = 572   // Rock
    private const val KASIB_BERRY = 573    // Ghost
    private const val HABAN_BERRY = 574    // Dragon
    private const val COLBUR_BERRY = 575   // Dark
    private const val BABIRI_BERRY = 576   // Steel
    private const val ROSELI_BERRY = 577   // Fairy

    // Stat berries (activate at low HP)
    private const val LIECHI_BERRY = 578   // Attack
    private const val GANLON_BERRY = 579   // Defense
    private const val SALAC_BERRY = 580    // Speed
    private const val PETAYA_BERRY = 581   // Sp.Atk
    private const val APICOT_BERRY = 582   // Sp.Def

    // Power berries (special effects at low HP)
    private const val LANSAT_BERRY = 583   // Crit boost
    private const val STARF_BERRY = 584    // Random stat boost
    private const val ENIGMA_BERRY = 585   // Heal on super-effective
    private const val MICLE_BERRY = 586    // Accuracy boost
    private const val CUSTAP_BERRY = 587   // Priority
    private const val JABOCA_BERRY = 588   // Physical recoil
    private const val ROWAP_BERRY = 589    // Special recoil
    private const val KEE_BERRY = 590      // Def boost on physical hit
    private const val MARANGA_BERRY = 591  // SpD boost on special hit

    // Gen 8+ competitive items
    private const val ABILITY_SHIELD = 769
    private const val CLEAR_AMULET = 770
    private const val PUNCHING_GLOVE = 771
    private const val COVERT_CLOAK = 772
    private const val LOADED_DICE = 773
    private const val BOOSTER_ENERGY = 775
    private const val MIRROR_HERB = 780

    // Common held items
    private const val LEFTOVERS = 483      // Heals 1/16 HP per turn
    private const val FOCUS_SASH = 492     // Survive at 1 HP from full health
    private const val FOCUS_BAND = 480     // 10% chance to survive at 1 HP

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
        AIR_BALLOON to ItemEffect(
            id = AIR_BALLOON,
            name = "Air Balloon",
            description = "Immune to Ground until hit"
        ),

        // Type gems (+30% damage for specific type, one-time use)
        NORMAL_GEM to ItemEffect(
            id = NORMAL_GEM,
            name = "Normal Gem",
            typeBoostedMove = 0,  // Normal
            typeBoostMultiplier = 1.3f,
            description = "+30% Normal damage once"
        ),
        FIRE_GEM to ItemEffect(
            id = FIRE_GEM,
            name = "Fire Gem",
            typeBoostedMove = 9,  // Fire
            typeBoostMultiplier = 1.3f,
            description = "+30% Fire damage once"
        ),
        WATER_GEM to ItemEffect(
            id = WATER_GEM,
            name = "Water Gem",
            typeBoostedMove = 10,  // Water
            typeBoostMultiplier = 1.3f,
            description = "+30% Water damage once"
        ),
        ELECTRIC_GEM to ItemEffect(
            id = ELECTRIC_GEM,
            name = "Electric Gem",
            typeBoostedMove = 12,  // Electric
            typeBoostMultiplier = 1.3f,
            description = "+30% Electric damage once"
        ),
        GRASS_GEM to ItemEffect(
            id = GRASS_GEM,
            name = "Grass Gem",
            typeBoostedMove = 11,  // Grass
            typeBoostMultiplier = 1.3f,
            description = "+30% Grass damage once"
        ),
        ICE_GEM to ItemEffect(
            id = ICE_GEM,
            name = "Ice Gem",
            typeBoostedMove = 14,  // Ice
            typeBoostMultiplier = 1.3f,
            description = "+30% Ice damage once"
        ),
        FIGHTING_GEM to ItemEffect(
            id = FIGHTING_GEM,
            name = "Fighting Gem",
            typeBoostedMove = 1,  // Fighting
            typeBoostMultiplier = 1.3f,
            description = "+30% Fighting damage once"
        ),
        POISON_GEM to ItemEffect(
            id = POISON_GEM,
            name = "Poison Gem",
            typeBoostedMove = 3,  // Poison
            typeBoostMultiplier = 1.3f,
            description = "+30% Poison damage once"
        ),
        GROUND_GEM to ItemEffect(
            id = GROUND_GEM,
            name = "Ground Gem",
            typeBoostedMove = 4,  // Ground
            typeBoostMultiplier = 1.3f,
            description = "+30% Ground damage once"
        ),
        FLYING_GEM to ItemEffect(
            id = FLYING_GEM,
            name = "Flying Gem",
            typeBoostedMove = 2,  // Flying
            typeBoostMultiplier = 1.3f,
            description = "+30% Flying damage once"
        ),
        PSYCHIC_GEM to ItemEffect(
            id = PSYCHIC_GEM,
            name = "Psychic Gem",
            typeBoostedMove = 13,  // Psychic
            typeBoostMultiplier = 1.3f,
            description = "+30% Psychic damage once"
        ),
        BUG_GEM to ItemEffect(
            id = BUG_GEM,
            name = "Bug Gem",
            typeBoostedMove = 6,  // Bug
            typeBoostMultiplier = 1.3f,
            description = "+30% Bug damage once"
        ),
        ROCK_GEM to ItemEffect(
            id = ROCK_GEM,
            name = "Rock Gem",
            typeBoostedMove = 5,  // Rock
            typeBoostMultiplier = 1.3f,
            description = "+30% Rock damage once"
        ),
        GHOST_GEM to ItemEffect(
            id = GHOST_GEM,
            name = "Ghost Gem",
            typeBoostedMove = 7,  // Ghost
            typeBoostMultiplier = 1.3f,
            description = "+30% Ghost damage once"
        ),
        DRAGON_GEM to ItemEffect(
            id = DRAGON_GEM,
            name = "Dragon Gem",
            typeBoostedMove = 15,  // Dragon
            typeBoostMultiplier = 1.3f,
            description = "+30% Dragon damage once"
        ),
        DARK_GEM to ItemEffect(
            id = DARK_GEM,
            name = "Dark Gem",
            typeBoostedMove = 16,  // Dark
            typeBoostMultiplier = 1.3f,
            description = "+30% Dark damage once"
        ),
        STEEL_GEM to ItemEffect(
            id = STEEL_GEM,
            name = "Steel Gem",
            typeBoostedMove = 8,  // Steel
            typeBoostMultiplier = 1.3f,
            description = "+30% Steel damage once"
        ),
        FAIRY_GEM to ItemEffect(
            id = FAIRY_GEM,
            name = "Fairy Gem",
            typeBoostedMove = 17,  // Fairy
            typeBoostMultiplier = 1.3f,
            description = "+30% Fairy damage once"
        ),

        // Type-boosting items Gen 3 (+10% damage for specific type)
        SILK_SCARF to ItemEffect(
            id = SILK_SCARF,
            name = "Silk Scarf",
            typeBoostedMove = 0,  // Normal
            typeBoostMultiplier = 1.1f,
            description = "+10% Normal damage"
        ),
        CHARCOAL to ItemEffect(
            id = CHARCOAL,
            name = "Charcoal",
            typeBoostedMove = 9,  // Fire
            typeBoostMultiplier = 1.1f,
            description = "+10% Fire damage"
        ),
        MYSTIC_WATER to ItemEffect(
            id = MYSTIC_WATER,
            name = "Mystic Water",
            typeBoostedMove = 10,  // Water
            typeBoostMultiplier = 1.1f,
            description = "+10% Water damage"
        ),
        MAGNET to ItemEffect(
            id = MAGNET,
            name = "Magnet",
            typeBoostedMove = 12,  // Electric
            typeBoostMultiplier = 1.1f,
            description = "+10% Electric damage"
        ),
        MIRACLE_SEED to ItemEffect(
            id = MIRACLE_SEED,
            name = "Miracle Seed",
            typeBoostedMove = 11,  // Grass
            typeBoostMultiplier = 1.1f,
            description = "+10% Grass damage"
        ),
        NEVER_MELT_ICE to ItemEffect(
            id = NEVER_MELT_ICE,
            name = "NeverMeltIce",
            typeBoostedMove = 14,  // Ice
            typeBoostMultiplier = 1.1f,
            description = "+10% Ice damage"
        ),
        BLACK_BELT to ItemEffect(
            id = BLACK_BELT,
            name = "Black Belt",
            typeBoostedMove = 1,  // Fighting
            typeBoostMultiplier = 1.1f,
            description = "+10% Fighting damage"
        ),
        POISON_BARB to ItemEffect(
            id = POISON_BARB,
            name = "Poison Barb",
            typeBoostedMove = 3,  // Poison
            typeBoostMultiplier = 1.1f,
            description = "+10% Poison damage"
        ),
        SOFT_SAND to ItemEffect(
            id = SOFT_SAND,
            name = "Soft Sand",
            typeBoostedMove = 4,  // Ground
            typeBoostMultiplier = 1.1f,
            description = "+10% Ground damage"
        ),
        SHARP_BEAK to ItemEffect(
            id = SHARP_BEAK,
            name = "Sharp Beak",
            typeBoostedMove = 2,  // Flying
            typeBoostMultiplier = 1.1f,
            description = "+10% Flying damage"
        ),
        TWISTED_SPOON to ItemEffect(
            id = TWISTED_SPOON,
            name = "TwistedSpoon",
            typeBoostedMove = 13,  // Psychic
            typeBoostMultiplier = 1.1f,
            description = "+10% Psychic damage"
        ),
        SILVER_POWDER to ItemEffect(
            id = SILVER_POWDER,
            name = "SilverPowder",
            typeBoostedMove = 6,  // Bug
            typeBoostMultiplier = 1.1f,
            description = "+10% Bug damage"
        ),
        HARD_STONE to ItemEffect(
            id = HARD_STONE,
            name = "Hard Stone",
            typeBoostedMove = 5,  // Rock
            typeBoostMultiplier = 1.1f,
            description = "+10% Rock damage"
        ),
        SPELL_TAG to ItemEffect(
            id = SPELL_TAG,
            name = "Spell Tag",
            typeBoostedMove = 7,  // Ghost
            typeBoostMultiplier = 1.1f,
            description = "+10% Ghost damage"
        ),
        DRAGON_FANG to ItemEffect(
            id = DRAGON_FANG,
            name = "Dragon Fang",
            typeBoostedMove = 15,  // Dragon
            typeBoostMultiplier = 1.1f,
            description = "+10% Dragon damage"
        ),
        BLACK_GLASSES to ItemEffect(
            id = BLACK_GLASSES,
            name = "BlackGlasses",
            typeBoostedMove = 16,  // Dark
            typeBoostMultiplier = 1.1f,
            description = "+10% Dark damage"
        ),
        METAL_COAT to ItemEffect(
            id = METAL_COAT,
            name = "Metal Coat",
            typeBoostedMove = 8,  // Steel
            typeBoostMultiplier = 1.1f,
            description = "+10% Steel damage"
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

        // Stat boost berries (activate at low HP)
        LIECHI_BERRY to ItemEffect(
            id = LIECHI_BERRY,
            name = "Liechi Berry",
            attackMod = 1.5f,
            description = "+1 stage Atk at low HP"
        ),
        GANLON_BERRY to ItemEffect(
            id = GANLON_BERRY,
            name = "Ganlon Berry",
            defenseMod = 1.5f,
            description = "+1 stage Def at low HP"
        ),
        SALAC_BERRY to ItemEffect(
            id = SALAC_BERRY,
            name = "Salac Berry",
            speedMod = 1.5f,
            description = "+1 stage Speed at low HP"
        ),
        PETAYA_BERRY to ItemEffect(
            id = PETAYA_BERRY,
            name = "Petaya Berry",
            spAttackMod = 1.5f,
            description = "+1 stage Sp.Atk at low HP"
        ),
        APICOT_BERRY to ItemEffect(
            id = APICOT_BERRY,
            name = "Apicot Berry",
            spDefenseMod = 1.5f,
            description = "+1 stage Sp.Def at low HP"
        ),

        // Utility items
        LEFTOVERS to ItemEffect(
            id = LEFTOVERS,
            name = "Leftovers",
            description = "Restores 1/16 HP per turn"
        ),
        BLACK_SLUDGE to ItemEffect(
            id = BLACK_SLUDGE,
            name = "Black Sludge",
            description = "Heals Poison types, damages others"
        ),
        FOCUS_SASH to ItemEffect(
            id = FOCUS_SASH,
            name = "Focus Sash",
            description = "Survives 1 HP from full health"
        ),
        FOCUS_BAND to ItemEffect(
            id = FOCUS_BAND,
            name = "Focus Band",
            description = "10% chance to survive at 1 HP"
        ),
        FLAME_ORB to ItemEffect(
            id = FLAME_ORB,
            name = "Flame Orb",
            description = "Burns holder after 1 turn"
        ),
        TOXIC_ORB to ItemEffect(
            id = TOXIC_ORB,
            name = "Toxic Orb",
            description = "Badly poisons holder after 1 turn"
        ),
        IRON_BALL to ItemEffect(
            id = IRON_BALL,
            name = "Iron Ball",
            speedMod = 0.5f,
            description = "Halves speed, grounds Flying types"
        ),

        // Accuracy/Priority items
        BRIGHT_POWDER to ItemEffect(
            id = BRIGHT_POWDER,
            name = "BrightPowder",
            description = "Lowers foe accuracy"
        ),
        QUICK_CLAW to ItemEffect(
            id = QUICK_CLAW,
            name = "Quick Claw",
            description = "20% chance to move first"
        ),
        SCOPE_LENS to ItemEffect(
            id = SCOPE_LENS,
            name = "Scope Lens",
            description = "Raises critical hit ratio"
        ),
        WIDE_LENS to ItemEffect(
            id = WIDE_LENS,
            name = "Wide Lens",
            description = "Boosts move accuracy"
        ),
        ZOOM_LENS to ItemEffect(
            id = ZOOM_LENS,
            name = "Zoom Lens",
            description = "Boosts accuracy if slower"
        ),

        // Utility items
        WHITE_HERB to ItemEffect(
            id = WHITE_HERB,
            name = "White Herb",
            description = "Restores lowered stats once"
        ),
        MENTAL_HERB to ItemEffect(
            id = MENTAL_HERB,
            name = "Mental Herb",
            description = "Cures infatuation/taunt"
        ),
        KINGS_ROCK to ItemEffect(
            id = KINGS_ROCK,
            name = "King's Rock",
            description = "10% flinch chance"
        ),
        LIGHT_CLAY to ItemEffect(
            id = LIGHT_CLAY,
            name = "Light Clay",
            description = "Extends screen duration"
        ),
        POWER_HERB to ItemEffect(
            id = POWER_HERB,
            name = "Power Herb",
            description = "1-turn charge moves"
        ),
        METRONOME to ItemEffect(
            id = METRONOME,
            name = "Metronome",
            description = "Boosts repeated moves"
        ),
        LAGGING_TAIL to ItemEffect(
            id = LAGGING_TAIL,
            name = "Lagging Tail",
            speedMod = 0.5f,
            description = "Holder moves last"
        ),
        DESTINY_KNOT to ItemEffect(
            id = DESTINY_KNOT,
            name = "Destiny Knot",
            description = "Passes infatuation"
        ),
        GRIP_CLAW to ItemEffect(
            id = GRIP_CLAW,
            name = "Grip Claw",
            description = "Extends binding moves"
        ),
        STICKY_BARB to ItemEffect(
            id = STICKY_BARB,
            name = "Sticky Barb",
            description = "Damages holder each turn"
        ),
        SHED_SHELL to ItemEffect(
            id = SHED_SHELL,
            name = "Shed Shell",
            description = "Escape trapping moves"
        ),
        BIG_ROOT to ItemEffect(
            id = BIG_ROOT,
            name = "Big Root",
            description = "+30% HP drain moves"
        ),
        RAZOR_CLAW to ItemEffect(
            id = RAZOR_CLAW,
            name = "Razor Claw",
            description = "Raises critical hit ratio"
        ),
        RAZOR_FANG to ItemEffect(
            id = RAZOR_FANG,
            name = "Razor Fang",
            description = "10% flinch chance"
        ),
        FLOAT_STONE to ItemEffect(
            id = FLOAT_STONE,
            name = "Float Stone",
            description = "Halves weight"
        ),
        RED_CARD to ItemEffect(
            id = RED_CARD,
            name = "Red Card",
            description = "Forces switch on contact"
        ),
        RING_TARGET to ItemEffect(
            id = RING_TARGET,
            name = "Ring Target",
            description = "Negates type immunity"
        ),
        BINDING_BAND to ItemEffect(
            id = BINDING_BAND,
            name = "Binding Band",
            description = "Boosts binding moves"
        ),
        EJECT_BUTTON to ItemEffect(
            id = EJECT_BUTTON,
            name = "Eject Button",
            description = "Switches out when hit"
        ),
        WEAKNESS_POLICY to ItemEffect(
            id = WEAKNESS_POLICY,
            name = "Weakness Policy",
            attackMod = 2.0f,
            spAttackMod = 2.0f,
            description = "+2 Atk/SpA on SE hit"
        ),
        SAFETY_GOGGLES to ItemEffect(
            id = SAFETY_GOGGLES,
            name = "Safety Goggles",
            description = "Weather immunity"
        ),
        ADRENALINE_ORB to ItemEffect(
            id = ADRENALINE_ORB,
            name = "Adrenaline Orb",
            description = "+Speed on intimidate"
        ),
        TERRAIN_EXTENDER to ItemEffect(
            id = TERRAIN_EXTENDER,
            name = "Terrain Extender",
            description = "Extends terrain duration"
        ),
        PROTECTIVE_PADS to ItemEffect(
            id = PROTECTIVE_PADS,
            name = "Protective Pads",
            description = "No contact effects"
        ),
        THROAT_SPRAY to ItemEffect(
            id = THROAT_SPRAY,
            name = "Throat Spray",
            description = "+SpA on sound move"
        ),
        EJECT_PACK to ItemEffect(
            id = EJECT_PACK,
            name = "Eject Pack",
            description = "Switches on stat drop"
        ),
        HEAVY_DUTY_BOOTS to ItemEffect(
            id = HEAVY_DUTY_BOOTS,
            name = "Heavy-Duty Boots",
            description = "Hazard immunity"
        ),
        BLUNDER_POLICY to ItemEffect(
            id = BLUNDER_POLICY,
            name = "Blunder Policy",
            description = "+Speed on miss"
        ),
        ROOM_SERVICE to ItemEffect(
            id = ROOM_SERVICE,
            name = "Room Service",
            description = "-Speed in Trick Room"
        ),
        UTILITY_UMBRELLA to ItemEffect(
            id = UTILITY_UMBRELLA,
            name = "Utility Umbrella",
            description = "Blocks weather effects"
        ),

        // Status cure berries
        CHERI_BERRY to ItemEffect(
            id = CHERI_BERRY,
            name = "Cheri Berry",
            description = "Cures paralysis"
        ),
        CHESTO_BERRY to ItemEffect(
            id = CHESTO_BERRY,
            name = "Chesto Berry",
            description = "Cures sleep"
        ),
        PECHA_BERRY to ItemEffect(
            id = PECHA_BERRY,
            name = "Pecha Berry",
            description = "Cures poison"
        ),
        RAWST_BERRY to ItemEffect(
            id = RAWST_BERRY,
            name = "Rawst Berry",
            description = "Cures burn"
        ),
        ASPEAR_BERRY to ItemEffect(
            id = ASPEAR_BERRY,
            name = "Aspear Berry",
            description = "Cures freeze"
        ),
        PERSIM_BERRY to ItemEffect(
            id = PERSIM_BERRY,
            name = "Persim Berry",
            description = "Cures confusion"
        ),
        LUM_BERRY to ItemEffect(
            id = LUM_BERRY,
            name = "Lum Berry",
            description = "Cures any status"
        ),
        SITRUS_BERRY to ItemEffect(
            id = SITRUS_BERRY,
            name = "Sitrus Berry",
            description = "Heals 25% HP"
        ),

        // Type-resistance berries
        CHILAN_BERRY to ItemEffect(
            id = CHILAN_BERRY,
            name = "Chilan Berry",
            description = "Halves Normal damage"
        ),
        OCCA_BERRY to ItemEffect(
            id = OCCA_BERRY,
            name = "Occa Berry",
            description = "Halves Fire damage"
        ),
        PASSHO_BERRY to ItemEffect(
            id = PASSHO_BERRY,
            name = "Passho Berry",
            description = "Halves Water damage"
        ),
        WACAN_BERRY to ItemEffect(
            id = WACAN_BERRY,
            name = "Wacan Berry",
            description = "Halves Electric damage"
        ),
        RINDO_BERRY to ItemEffect(
            id = RINDO_BERRY,
            name = "Rindo Berry",
            description = "Halves Grass damage"
        ),
        YACHE_BERRY to ItemEffect(
            id = YACHE_BERRY,
            name = "Yache Berry",
            description = "Halves Ice damage"
        ),
        CHOPLE_BERRY to ItemEffect(
            id = CHOPLE_BERRY,
            name = "Chople Berry",
            description = "Halves Fighting damage"
        ),
        KEBIA_BERRY to ItemEffect(
            id = KEBIA_BERRY,
            name = "Kebia Berry",
            description = "Halves Poison damage"
        ),
        SHUCA_BERRY to ItemEffect(
            id = SHUCA_BERRY,
            name = "Shuca Berry",
            description = "Halves Ground damage"
        ),
        COBA_BERRY to ItemEffect(
            id = COBA_BERRY,
            name = "Coba Berry",
            description = "Halves Flying damage"
        ),
        PAYAPA_BERRY to ItemEffect(
            id = PAYAPA_BERRY,
            name = "Payapa Berry",
            description = "Halves Psychic damage"
        ),
        TANGA_BERRY to ItemEffect(
            id = TANGA_BERRY,
            name = "Tanga Berry",
            description = "Halves Bug damage"
        ),
        CHARTI_BERRY to ItemEffect(
            id = CHARTI_BERRY,
            name = "Charti Berry",
            description = "Halves Rock damage"
        ),
        KASIB_BERRY to ItemEffect(
            id = KASIB_BERRY,
            name = "Kasib Berry",
            description = "Halves Ghost damage"
        ),
        HABAN_BERRY to ItemEffect(
            id = HABAN_BERRY,
            name = "Haban Berry",
            description = "Halves Dragon damage"
        ),
        COLBUR_BERRY to ItemEffect(
            id = COLBUR_BERRY,
            name = "Colbur Berry",
            description = "Halves Dark damage"
        ),
        BABIRI_BERRY to ItemEffect(
            id = BABIRI_BERRY,
            name = "Babiri Berry",
            description = "Halves Steel damage"
        ),
        ROSELI_BERRY to ItemEffect(
            id = ROSELI_BERRY,
            name = "Roseli Berry",
            description = "Halves Fairy damage"
        ),

        // Power berries
        LANSAT_BERRY to ItemEffect(
            id = LANSAT_BERRY,
            name = "Lansat Berry",
            description = "+Crit ratio at low HP"
        ),
        STARF_BERRY to ItemEffect(
            id = STARF_BERRY,
            name = "Starf Berry",
            description = "+2 random stat at low HP"
        ),
        ENIGMA_BERRY to ItemEffect(
            id = ENIGMA_BERRY,
            name = "Enigma Berry",
            description = "Heals on SE hit"
        ),
        MICLE_BERRY to ItemEffect(
            id = MICLE_BERRY,
            name = "Micle Berry",
            description = "+Accuracy at low HP"
        ),
        CUSTAP_BERRY to ItemEffect(
            id = CUSTAP_BERRY,
            name = "Custap Berry",
            description = "Priority at low HP"
        ),
        JABOCA_BERRY to ItemEffect(
            id = JABOCA_BERRY,
            name = "Jaboca Berry",
            description = "Physical recoil berry"
        ),
        ROWAP_BERRY to ItemEffect(
            id = ROWAP_BERRY,
            name = "Rowap Berry",
            description = "Special recoil berry"
        ),
        KEE_BERRY to ItemEffect(
            id = KEE_BERRY,
            name = "Kee Berry",
            defenseMod = 1.5f,
            description = "+Def on physical hit"
        ),
        MARANGA_BERRY to ItemEffect(
            id = MARANGA_BERRY,
            name = "Maranga Berry",
            spDefenseMod = 1.5f,
            description = "+SpD on special hit"
        ),

        // Gen 8+ items
        ABILITY_SHIELD to ItemEffect(
            id = ABILITY_SHIELD,
            name = "Ability Shield",
            description = "Blocks ability changes"
        ),
        CLEAR_AMULET to ItemEffect(
            id = CLEAR_AMULET,
            name = "Clear Amulet",
            description = "Blocks stat drops"
        ),
        PUNCHING_GLOVE to ItemEffect(
            id = PUNCHING_GLOVE,
            name = "Punching Glove",
            description = "+10% punch moves"
        ),
        COVERT_CLOAK to ItemEffect(
            id = COVERT_CLOAK,
            name = "Covert Cloak",
            description = "Blocks move effects"
        ),
        LOADED_DICE to ItemEffect(
            id = LOADED_DICE,
            name = "Loaded Dice",
            description = "Multi-hit always 4-5"
        ),
        BOOSTER_ENERGY to ItemEffect(
            id = BOOSTER_ENERGY,
            name = "Booster Energy",
            description = "Activates Paradox ability"
        ),
        MIRROR_HERB to ItemEffect(
            id = MIRROR_HERB,
            name = "Mirror Herb",
            description = "Copies stat boosts"
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

        // Check if stat boost berries should activate (< 25% HP in Gen 3)
        val isBerryActive = currentHp > 0 && maxHp > 0 &&
            (currentHp.toFloat() / maxHp) < 0.25f &&
            itemId in listOf(LIECHI_BERRY, GANLON_BERRY, SALAC_BERRY, PETAYA_BERRY, APICOT_BERRY)

        // Apply permanent stat modifiers (Choice items, power items, etc.)
        // Berries only apply when HP is low
        val atkMod = if (isBerryActive && itemId == LIECHI_BERRY) effect.attackMod
                     else if (itemId in listOf(CHOICE_BAND, MUSCLE_BAND)) effect.attackMod
                     else 1.0f

        val defMod = if (isBerryActive && itemId == GANLON_BERRY) effect.defenseMod
                     else if (itemId == EVIOLITE) effect.defenseMod
                     else 1.0f

        val spaMod = if (isBerryActive && itemId == PETAYA_BERRY) effect.spAttackMod
                     else if (itemId in listOf(CHOICE_SPECS, WISE_GLASSES)) effect.spAttackMod
                     else 1.0f

        val spdMod = if (isBerryActive && itemId == APICOT_BERRY) effect.spDefenseMod
                     else if (itemId in listOf(ASSAULT_VEST, EVIOLITE)) effect.spDefenseMod
                     else 1.0f

        val speMod = if (isBerryActive && itemId == SALAC_BERRY) effect.speedMod
                     else if (itemId == CHOICE_SCARF) effect.speedMod
                     else 1.0f

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
