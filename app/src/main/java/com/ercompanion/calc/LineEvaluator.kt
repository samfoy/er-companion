package com.ercompanion.calc

import kotlin.math.max

/**
 * Represents a sequence of moves (a "battle line") and its evaluation.
 */
data class BattleLine(
    val moves: List<Int>,
    val finalState: BattleState,
    val turnsToKO: Int,
    val damageDealt: Int,
    val damageTaken: Int,
    val survivalProbability: Float,
    val score: Float,
    val description: String
)

/**
 * Evaluates and scores battle lines (move sequences).
 */
object LineEvaluator {

    /**
     * Score a battle line based on multiple factors:
     * - Turns to KO (lower is better)
     * - Damage dealt (higher is better)
     * - Survival (higher is better)
     * - Efficiency (damage per turn)
     * - Status conditions (player having status is bad, enemy having status is good)
     *
     * Score range: 0.0 to 10.0
     */
    fun evaluateLine(line: BattleLine): Float {
        var score = 0f

        // Factor 1: Turns to KO (heavily weighted)
        // 1HKO = 5 points, 2HKO = 4 points, 3HKO = 3 points, no KO = 0 points
        score += when (line.turnsToKO) {
            1 -> 5.0f
            2 -> 4.0f
            3 -> 3.0f
            else -> {
                // Partial credit for damage dealt
                val damagePercent = line.damageDealt.toFloat() / line.finalState.enemy.mon.maxHp
                damagePercent * 2f  // Up to 2 points for high damage
            }
        }

        // Factor 2: Survival probability (critical)
        // Full credit for surviving (up to 3 points)
        score += line.survivalProbability * 3f

        // Factor 3: Efficiency (damage per turn)
        if (line.moves.isNotEmpty()) {
            val damagePerTurn = line.damageDealt.toFloat() / line.moves.size
            val efficiencyPercent = damagePerTurn / line.finalState.enemy.mon.maxHp
            score += efficiencyPercent * 1.5f  // Up to 1.5 points
        }

        // Factor 4: Risk assessment
        // Penalize if we take significant damage without securing KO
        if (line.turnsToKO < 0) {
            val damageTakenPercent = line.damageTaken.toFloat() / line.finalState.player.mon.maxHp
            if (damageTakenPercent > 0.7f) {
                score -= 1.0f  // Risky play without payoff
            }
        }

        // Factor 5: Status condition evaluation
        // Player having status is bad
        if (StatusConditions.isBurned(line.finalState.player.status)) {
            score -= 2.0f  // Burn is very bad for physical attackers
        }
        if (StatusConditions.isParalyzed(line.finalState.player.status)) {
            score -= 1.5f  // Paralysis reduces speed and has full para chance
        }
        if (StatusConditions.isSleep(line.finalState.player.status)) {
            score -= 3.0f  // Sleep is extremely bad (can't move)
        }
        if (StatusConditions.isFrozen(line.finalState.player.status)) {
            score -= 3.5f  // Freeze is even worse (harder to thaw)
        }
        if (StatusConditions.isPoisoned(line.finalState.player.status)) {
            score -= 1.0f  // Poison chips away HP
        }
        if (StatusConditions.isToxic(line.finalState.player.status)) {
            score -= 2.0f  // Toxic is worse (increasing damage)
        }

        // Enemy having status is good!
        if (StatusConditions.isBurned(line.finalState.enemy.status)) {
            score += 2.0f  // Burn cripples physical attackers
        }
        if (StatusConditions.isParalyzed(line.finalState.enemy.status)) {
            score += 1.5f  // Paralysis reduces their speed
        }
        if (StatusConditions.isSleep(line.finalState.enemy.status)) {
            score += 3.0f  // Sleep gives us free turns
        }
        if (StatusConditions.isFrozen(line.finalState.enemy.status)) {
            score += 3.5f  // Freeze is amazing
        }
        if (StatusConditions.isPoisoned(line.finalState.enemy.status)) {
            score += 1.0f  // Poison helps whittle them down
        }
        if (StatusConditions.isToxic(line.finalState.enemy.status)) {
            score += 2.0f  // Toxic is great for stalling
        }

        // Bonus: Quick KO with minimal damage taken
        if (line.turnsToKO <= 2 && line.survivalProbability > 0.8f) {
            score += 0.5f  // Clean sweep bonus
        }

        // Item-specific considerations
        val hpPercent = line.finalState.player.currentHp.toFloat() / line.finalState.player.mon.maxHp

        // Choice item considerations
        if (ItemEffects.isChoiceItem(line.finalState.player.mon.heldItem)) {
            if (line.finalState.player.tempBoosts.lockedMove != 0) {
                val lockedMove = line.finalState.player.tempBoosts.lockedMove
                val moveData = com.ercompanion.data.PokemonData.getMoveData(lockedMove)
                if (moveData != null && moveData.power < 80) {
                    score -= 1.5f  // Locked into weak move is bad
                }
            }

            // Bonus for high-power move with Choice item
            val firstMove = line.moves.firstOrNull()
            if (firstMove != null) {
                val moveData = com.ercompanion.data.PokemonData.getMoveData(firstMove)
                if (moveData != null && moveData.power >= 100) {
                    score += 1.0f  // Good choice lock
                }
            }
        }

        // Life Orb considerations
        if (line.finalState.player.mon.heldItem == 225) {  // Life Orb
            val recoilPerMove = line.finalState.player.mon.maxHp / 10
            val totalRecoil = line.moves.size * recoilPerMove
            if (line.finalState.player.currentHp - totalRecoil <= 0) {
                score -= 3.0f  // Would faint from recoil
            } else if (hpPercent < 0.3f) {
                score -= 1.0f  // Risky with low HP
            } else {
                score += 0.5f  // Power boost is worth it
            }
        }

        // Leftovers healing value
        if (line.finalState.player.mon.heldItem == 234) {  // Leftovers
            val healingPerTurn = line.finalState.player.mon.maxHp / 16
            val totalHealing = line.moves.size * healingPerTurn
            score += (totalHealing.toFloat() / line.finalState.player.mon.maxHp) * 2.0f
        }

        // Focus Sash value
        if (line.finalState.player.mon.heldItem == 235) {  // Focus Sash
            if (hpPercent >= 1.0f) {
                score += 1.5f  // Active and valuable
            } else {
                score -= 0.5f  // Wasted item slot
            }
        }

        // Black Sludge (similar to Leftovers for Poison types)
        if (line.finalState.player.mon.heldItem == 282) {  // Black Sludge
            val types = com.ercompanion.data.PokemonData.getSpeciesTypes(line.finalState.player.mon.species)
            if (types.contains(3)) {  // Poison type
                val healingPerTurn = line.finalState.player.mon.maxHp / 16
                val totalHealing = line.moves.size * healingPerTurn
                score += (totalHealing.toFloat() / line.finalState.player.mon.maxHp) * 2.0f
            } else {
                score -= 3.0f  // Very bad - damages non-Poison types!
            }
        }

        // Weather/terrain bonuses
        val playerTypes = com.ercompanion.data.PokemonData.getSpeciesTypes(line.finalState.player.mon.species)

        // Weather-setting ability bonuses
        if (line.finalState.weather != Weather.NONE) {
            // Big bonus if weather matches our type
            when {
                line.finalState.weather == Weather.SUN && playerTypes.contains(9) -> {
                    score += 2.0f  // Fire type in sun (Drought Charizard, etc.)
                }
                line.finalState.weather == Weather.RAIN && playerTypes.contains(10) -> {
                    score += 2.0f  // Water type in rain (Drizzle Politoed, etc.)
                }
            }

            // Bonus for speed-boosting abilities in weather
            when (line.finalState.player.mon.ability) {
                34 -> if (line.finalState.weather == Weather.SUN) score += 1.5f  // Chlorophyll
                33 -> if (line.finalState.weather == Weather.RAIN) score += 1.5f  // Swift Swim
                146 -> if (line.finalState.weather == Weather.SANDSTORM) score += 1.5f  // Sand Rush
                202 -> if (line.finalState.weather == Weather.HAIL) score += 1.5f  // Slush Rush
            }
        }

        // Beneficial weather (traditional bonuses)
        if (line.finalState.weather == Weather.SUN && playerTypes.contains(9)) {
            score += 0.5f  // Fire type in sun (additional damage bonus)
        }
        if (line.finalState.weather == Weather.RAIN && playerTypes.contains(10)) {
            score += 0.5f  // Water type in rain (additional damage bonus)
        }
        if (line.finalState.weather == Weather.SANDSTORM && playerTypes.contains(5)) {
            score += 1.0f  // Rock type in sandstorm (SpDef boost)
        }

        // Harmful weather
        if (line.finalState.weather == Weather.SANDSTORM &&
            !playerTypes.contains(5) && !playerTypes.contains(4) && !playerTypes.contains(8)) {
            score -= 0.5f  // Taking sandstorm damage
        }
        if (line.finalState.weather == Weather.HAIL && !playerTypes.contains(14)) {
            score -= 0.5f  // Taking hail damage
        }

        // Beneficial terrain
        if (line.finalState.terrain == Terrain.GRASSY && !playerTypes.contains(2)) {
            score += 0.5f  // Healing from Grassy Terrain
        }
        if (line.finalState.terrain == Terrain.ELECTRIC && playerTypes.contains(12)) {
            score += 1.0f  // Electric type with Electric Terrain
        }
        if (line.finalState.terrain == Terrain.PSYCHIC && playerTypes.contains(13)) {
            score += 1.0f  // Psychic type with Psychic Terrain
        }
        if (line.finalState.terrain == Terrain.GRASSY && playerTypes.contains(11)) {
            score += 1.0f  // Grass type with Grassy Terrain
        }

        return score.coerceIn(0f, 10f)
    }

    /**
     * Compare two lines and return the better one.
     * Prioritizes: KO > Survival > Efficiency
     */
    fun compareLines(line1: BattleLine, line2: BattleLine): BattleLine {
        // First priority: Turns to KO (lower is better, but only if both KO)
        if (line1.turnsToKO > 0 && line2.turnsToKO > 0) {
            if (line1.turnsToKO != line2.turnsToKO) {
                return if (line1.turnsToKO < line2.turnsToKO) line1 else line2
            }
        } else if (line1.turnsToKO > 0) {
            return line1  // line1 KOs, line2 doesn't
        } else if (line2.turnsToKO > 0) {
            return line2  // line2 KOs, line1 doesn't
        }

        // Second priority: Survival
        if (line1.survivalProbability != line2.survivalProbability) {
            return if (line1.survivalProbability > line2.survivalProbability) line1 else line2
        }

        // Third priority: Overall score
        return if (line1.score >= line2.score) line1 else line2
    }

    /**
     * Calculate survival probability based on HP remaining.
     * Returns 1.0 if we survive, 0.0 if we faint, or a probability in between.
     *
     * For now, uses a simple heuristic:
     * - HP > 50% = 1.0 (very safe)
     * - HP 25-50% = 0.7 (safe)
     * - HP 10-25% = 0.5 (risky)
     * - HP < 10% = 0.2 (very risky)
     * - HP = 0 = 0.0 (fainted)
     */
    fun calculateSurvivalProbability(currentHp: Int, maxHp: Int): Float {
        if (currentHp <= 0) return 0f
        val hpPercent = currentHp.toFloat() / maxHp

        return when {
            hpPercent > 0.5f -> 1.0f
            hpPercent > 0.25f -> 0.7f
            hpPercent > 0.1f -> 0.5f
            else -> 0.2f
        }
    }

    /**
     * Build a human-readable description of a battle line.
     */
    fun buildDescription(
        moves: List<Int>,
        turnsToKO: Int,
        survivalProb: Float
    ): String {
        if (moves.isEmpty()) return "No moves"

        val moveNames = moves.mapNotNull { moveId ->
            com.ercompanion.data.PokemonData.getMoveData(moveId)?.name
        }

        val moveSequence = when {
            moveNames.size == 1 -> moveNames[0]
            moveNames.size == 2 -> "${moveNames[0]} → ${moveNames[1]}"
            moveNames.size == 3 -> "${moveNames[0]} → ${moveNames[1]} → ${moveNames[2]}"
            else -> moveNames.take(3).joinToString(" → ") + "..."
        }

        val koText = when (turnsToKO) {
            1 -> " (1HKO)"
            2 -> " (2HKO)"
            3 -> " (3HKO)"
            else -> ""
        }

        val riskText = when {
            survivalProb < 0.3f -> " ⚠ Very Risky"
            survivalProb < 0.6f -> " ⚠ Risky"
            else -> ""
        }

        return "$moveSequence$koText$riskText"
    }
}
