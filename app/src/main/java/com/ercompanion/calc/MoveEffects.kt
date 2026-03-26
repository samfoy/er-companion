package com.ercompanion.calc

/**
 * Contains move effect data for stat-changing moves.
 * This complements PokemonData.MOVE_DATA which only has power/type/category.
 */
object MoveEffects {

    /**
     * Get stat stage changes for a move that modifies stats.
     * Returns null if the move doesn't change stats.
     */
    fun getStatChanges(moveId: Int): MoveEffect? {
        return MOVE_EFFECTS[moveId]
    }

    /**
     * Check if a move is a setup move (boosts user's stats).
     */
    fun isSetupMove(moveId: Int): Boolean {
        val effect = MOVE_EFFECTS[moveId]
        if (effect == null || effect.target != EffectTarget.USER) return false

        // A setup move is one that boosts offensive or speed stats
        val changes = effect.changes
        return changes.attack > 0 || changes.spAttack > 0 || changes.speed > 0
    }

    data class MoveEffect(
        val target: EffectTarget,
        val changes: StatStageChanges,
        val chance: Int = 100  // Percentage chance of effect occurring
    )

    enum class EffectTarget {
        USER,      // Affects the user
        TARGET     // Affects the target
    }

    /**
     * Move effect database for common setup and stat-changing moves.
     * IDs are from Pokemon Emerald's move list.
     */
    private val MOVE_EFFECTS = mapOf(
        // Setup moves (boost user)
        14 to MoveEffect(EffectTarget.USER, StatStageChanges(attack = 2)),  // Swords Dance
        97 to MoveEffect(EffectTarget.USER, StatStageChanges(speed = 2)),   // Agility
        339 to MoveEffect(EffectTarget.USER, StatStageChanges(attack = 1, defense = 1)),  // Bulk Up (Gen 3)
        347 to MoveEffect(EffectTarget.USER, StatStageChanges(spAttack = 1, spDefense = 1)),  // Calm Mind (Gen 3)
        349 to MoveEffect(EffectTarget.USER, StatStageChanges(attack = 1, speed = 1)),  // Dragon Dance (Gen 3)
        417 to MoveEffect(EffectTarget.USER, StatStageChanges(spAttack = 2)),  // Nasty Plot

        // Defense boosters
        104 to MoveEffect(EffectTarget.USER, StatStageChanges(defense = 1)),  // Harden
        110 to MoveEffect(EffectTarget.USER, StatStageChanges(defense = 1)),  // Withdraw
        334 to MoveEffect(EffectTarget.USER, StatStageChanges(defense = 2)),  // Iron Defense
        445 to MoveEffect(EffectTarget.USER, StatStageChanges(spDefense = 2)), // Cosmic Power (also +2 def in actual game)

        // Speed boosters
        173 to MoveEffect(EffectTarget.USER, StatStageChanges(speed = 1)),  // Rock Polish (if in game)

        // Special Attack boosters
        366 to MoveEffect(EffectTarget.USER, StatStageChanges(spAttack = 1)), // Tail Glow (Gen 3)

        // Stat-lowering moves (affect target)
        28 to MoveEffect(EffectTarget.TARGET, StatStageChanges(accuracy = -1)),  // Sand Attack
        39 to MoveEffect(EffectTarget.TARGET, StatStageChanges(defense = -1)),   // Tail Whip
        43 to MoveEffect(EffectTarget.TARGET, StatStageChanges(defense = -1)),   // Leer
        45 to MoveEffect(EffectTarget.TARGET, StatStageChanges(attack = -1)),    // Growl
        103 to MoveEffect(EffectTarget.TARGET, StatStageChanges(defense = -2)),  // Screech
        109 to MoveEffect(EffectTarget.TARGET, StatStageChanges(speed = -1)),    // String Shot

        // Gen 5+ moves (if ER includes them)
        483 to MoveEffect(EffectTarget.USER, StatStageChanges(spAttack = 1, spDefense = 1, speed = 1)),  // Quiver Dance
        489 to MoveEffect(EffectTarget.USER, StatStageChanges(attack = 1, defense = 1, accuracy = 1)),  // Coil
        508 to MoveEffect(EffectTarget.USER, StatStageChanges(attack = 1, spAttack = 1, speed = 1))  // Shift Gear
    )

    /**
     * Common setup moves that ER players should know about.
     * This is a curated list for the UI.
     */
    val COMMON_SETUP_MOVES = setOf(
        14,  // Swords Dance
        97,  // Agility
        339, // Bulk Up
        347, // Calm Mind
        349, // Dragon Dance
        417, // Nasty Plot
        334, // Iron Defense
        483, // Quiver Dance (if in ER)
        489  // Coil (if in ER)
    )

    /**
     * Weather-setting moves.
     */
    val WEATHER_MOVES = mapOf(
        241 to Weather.SUN,         // Sunny Day
        240 to Weather.RAIN,        // Rain Dance
        201 to Weather.SANDSTORM,   // Sandstorm
        258 to Weather.HAIL         // Hail
    )

    /**
     * Terrain-setting moves.
     */
    val TERRAIN_MOVES = mapOf(
        678 to Terrain.ELECTRIC,    // Electric Terrain
        580 to Terrain.GRASSY,      // Grassy Terrain
        581 to Terrain.MISTY,       // Misty Terrain
        579 to Terrain.PSYCHIC      // Psychic Terrain
    )

    /**
     * Check if a move sets weather.
     */
    fun setsWeather(moveId: Int): Weather? = WEATHER_MOVES[moveId]

    /**
     * Check if a move sets terrain.
     */
    fun setsTerrain(moveId: Int): Terrain? = TERRAIN_MOVES[moveId]
}
