package com.ercompanion.calc

/**
 * Contains data for moves that inflict status conditions.
 * Maps move IDs to their status effects and infliction chances.
 */
object StatusMoves {

    /**
     * Represents a status effect that a move can inflict.
     */
    data class StatusEffect(
        val condition: Int,
        val chance: Float  // 0.0-1.0
    )

    /**
     * Database of moves that can inflict status conditions.
     * Move IDs are from Pokemon Emerald's move list.
     */
    private val STATUS_MOVE_MAP = mapOf(
        // 100% status moves
        107 to StatusEffect(StatusConditions.BURN, 1.0f),          // Will-o-Wisp
        86 to StatusEffect(StatusConditions.PARALYSIS, 1.0f),      // Thunder Wave
        92 to StatusEffect(StatusConditions.TOXIC, 1.0f),          // Toxic
        79 to StatusEffect(StatusConditions.SLEEP_2, 0.75f),       // Sleep Powder
        95 to StatusEffect(StatusConditions.SLEEP_2, 1.0f),        // Hypnosis
        142 to StatusEffect(StatusConditions.SLEEP_2, 0.75f),      // Lovely Kiss
        147 to StatusEffect(StatusConditions.SLEEP_2, 1.0f),       // Spore
        214 to StatusEffect(StatusConditions.SLEEP_2, 0.6f),       // Sleep Talk
        281 to StatusEffect(StatusConditions.SLEEP_2, 0.75f),      // Yawn (delayed, simplified)

        // Damaging moves with status effects (secondary effects)
        // 10% burn chance
        52 to StatusEffect(StatusConditions.BURN, 0.1f),           // Ember
        53 to StatusEffect(StatusConditions.BURN, 0.1f),           // Flamethrower
        126 to StatusEffect(StatusConditions.BURN, 0.3f),          // Fire Blast
        257 to StatusEffect(StatusConditions.BURN, 0.1f),          // Heat Wave
        315 to StatusEffect(StatusConditions.BURN, 0.1f),          // Overheat

        // 30% burn chance
        503 to StatusEffect(StatusConditions.BURN, 0.3f),          // Scald (Gen 5+)

        // 10% paralysis chance
        84 to StatusEffect(StatusConditions.PARALYSIS, 0.1f),      // Thunder Shock
        85 to StatusEffect(StatusConditions.PARALYSIS, 0.1f),      // Thunderbolt
        87 to StatusEffect(StatusConditions.PARALYSIS, 0.3f),      // Thunder
        351 to StatusEffect(StatusConditions.PARALYSIS, 0.1f),     // Shock Wave

        // 30% paralysis chance
        9 to StatusEffect(StatusConditions.PARALYSIS, 0.3f),       // Thunder Punch
        129 to StatusEffect(StatusConditions.PARALYSIS, 0.3f),     // Body Slam
        189 to StatusEffect(StatusConditions.PARALYSIS, 0.3f),     // Zap Cannon

        // 10% freeze chance
        58 to StatusEffect(StatusConditions.FREEZE, 0.1f),         // Ice Beam
        59 to StatusEffect(StatusConditions.FREEZE, 0.1f),         // Blizzard

        // 10% poison chance
        40 to StatusEffect(StatusConditions.POISON, 0.3f),         // Poison Sting
        92 to StatusEffect(StatusConditions.POISON, 0.9f),         // Toxic (effectively 100% in practice)
        139 to StatusEffect(StatusConditions.POISON, 0.2f),        // Poison Gas
        203 to StatusEffect(StatusConditions.POISON, 0.3f),        // Sludge
        202 to StatusEffect(StatusConditions.POISON, 0.3f),        // Sludge Bomb

        // 30% poison chance
        188 to StatusEffect(StatusConditions.POISON, 0.3f),        // Sludge Wave

        // Toxic (badly poisoned)
        92 to StatusEffect(StatusConditions.TOXIC, 1.0f)           // Toxic
    )

    /**
     * Get the status effect that a move can inflict, if any.
     */
    fun getStatusEffect(moveId: Int): StatusEffect? = STATUS_MOVE_MAP[moveId]

    /**
     * Check if a move can inflict a status condition.
     */
    fun canInflictStatus(moveId: Int): Boolean = moveId in STATUS_MOVE_MAP

    /**
     * Get all moves that can inflict a specific status condition.
     */
    fun getMovesForStatus(statusCondition: Int): List<Int> {
        return STATUS_MOVE_MAP.filter { (_, effect) ->
            effect.condition == statusCondition ||
            (StatusConditions.isSleep(statusCondition) && StatusConditions.isSleep(effect.condition))
        }.keys.toList()
    }

    /**
     * Common status moves that players should consider.
     * Curated list for UI suggestions.
     */
    val COMMON_STATUS_MOVES = setOf(
        107,  // Will-o-Wisp
        86,   // Thunder Wave
        92,   // Toxic
        79,   // Sleep Powder
        147,  // Spore
        503   // Scald
    )
}
