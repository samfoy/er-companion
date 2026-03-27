package com.ercompanion.calc

import com.ercompanion.data.MoveData
import com.ercompanion.data.PokemonData
import com.ercompanion.parser.PartyMon

/**
 * Simulates ER's battle AI scoring from battle_ai_main.c.
 *
 * The AI scores each move 0-10 (starting at 0), applies flag-based adjustments,
 * then picks randomly among tied highest-score moves.
 *
 * Wild AI flags by enemy level:
 *   Lv1+:  CHECK_BAD_MOVE
 *   Lv20+: CHECK_VIABILITY
 *   Lv60+: PREFER_STRONGEST_MOVE
 *   Lv80+: HP_AWARE
 *
 * Trainer AI typically: CHECK_BAD_MOVE | TRY_TO_FAINT | CHECK_VIABILITY
 */
data class ScoredMove(
    val moveId: Int,
    val moveName: String,
    val moveData: MoveData?,
    val score: Int,
    val damage: Int,        // min damage vs target
    val damagePercent: Int, // damage as % of target max HP
    val label: String       // human-readable prediction
)

object BattleAISimulator {

    // AI flags
    private const val AI_FLAG_CHECK_BAD_MOVE        = 1 shl 0
    private const val AI_FLAG_TRY_TO_FAINT          = 1 shl 1
    private const val AI_FLAG_CHECK_VIABILITY       = 1 shl 2
    private const val AI_FLAG_PREFER_STRONGEST_MOVE = 1 shl 5
    private const val AI_FLAG_HP_AWARE              = 1 shl 8

    // Wild AI flags by level (from GetWildAiFlags)
    private fun getWildAiFlags(level: Int): Int {
        var flags = AI_FLAG_CHECK_BAD_MOVE
        if (level >= 20) flags = flags or AI_FLAG_CHECK_VIABILITY
        if (level >= 60) flags = flags or AI_FLAG_PREFER_STRONGEST_MOVE
        if (level >= 80) flags = flags or AI_FLAG_HP_AWARE
        return flags
    }

    // Trainer AI: check bad + try to faint + check viability (standard trainer)
    private const val TRAINER_AI_FLAGS =
        AI_FLAG_CHECK_BAD_MOVE or AI_FLAG_TRY_TO_FAINT or AI_FLAG_CHECK_VIABILITY

    /**
     * Score each of the enemy's moves against the player's active mon.
     * isTrainer = true for trainers, false for wild encounters.
     */
    fun scoreMovesVsTarget(
        enemy: PartyMon,
        target: PartyMon,
        isTrainer: Boolean = true
    ): List<ScoredMove> {
        val aiFlags = if (isTrainer) TRAINER_AI_FLAGS else getWildAiFlags(enemy.level)
        val enemyTypes = PokemonData.getSpeciesTypes(enemy.species)
        val targetTypes = PokemonData.getSpeciesTypes(target.species)

        return enemy.moves.map { moveId ->
            if (moveId == 0) {
                ScoredMove(0, "—", null, -99, 0, 0, "none")
            } else {
                val moveData = PokemonData.getMoveData(moveId)
                val moveName = PokemonData.getMoveName(moveId)

                // Calc damage
                val damage: Int
                val damagePercent: Int
                if (moveData != null && moveData.power > 0) {
                    val atkStat = if (moveData.category == 0) enemy.attack else enemy.spAttack
                    val defStat = if (moveData.category == 0) target.defense else target.spDefense
                    val result = DamageCalculator.calc(
                        attackerLevel = enemy.level,
                        attackStat = atkStat,
                        defenseStat = defStat,
                        movePower = moveData.power,
                        moveType = moveData.type,
                        moveCategory = moveData.category,
                        attackerTypes = enemyTypes,
                        defenderTypes = targetTypes,
                        targetMaxHP = target.maxHp
                    )
                    // Handle invalid results (pre-battle stats)
                    if (!result.isValid) {
                        damage = 0
                        damagePercent = 0
                    } else {
                        damage = result.minDamage
                        damagePercent = if (target.maxHp > 0) (damage * 100 / target.maxHp) else 0
                    }
                } else {
                    damage = 0
                    damagePercent = 0
                }

                val effectiveness = if (moveData != null && moveData.power > 0) {
                    DamageCalculator.getTypeEffectiveness(moveData.type, targetTypes)
                } else 1f

                var score = 0

                // CHECK_BAD_MOVE: penalise immune/resisted moves
                if (aiFlags and AI_FLAG_CHECK_BAD_MOVE != 0) {
                    if (effectiveness == 0f) score -= 10  // immune
                    else if (effectiveness < 1f) score -= 2 // resisted
                    if (moveData != null && moveData.power == 0) score -= 1 // status
                }

                // TRY_TO_FAINT
                if (aiFlags and AI_FLAG_TRY_TO_FAINT != 0 && moveData != null && moveData.power > 0) {
                    val canOHKO = damage >= target.hp
                    val fasterThanTarget = enemy.speed > target.speed
                    when {
                        canOHKO && fasterThanTarget -> score += 5
                        canOHKO                     -> score += 4
                        damagePercent >= 50         -> score += 2
                    }
                }

                // CHECK_VIABILITY: favour super-effective + STAB
                if (aiFlags and AI_FLAG_CHECK_VIABILITY != 0 && moveData != null && moveData.power > 0) {
                    if (effectiveness > 1f) score += 2
                    if (enemyTypes.contains(moveData.type)) score += 1 // STAB
                }

                // PREFER_STRONGEST_MOVE
                if (aiFlags and AI_FLAG_PREFER_STRONGEST_MOVE != 0 && moveData != null && moveData.power > 0) {
                    when {
                        damage >= target.hp    -> score += 3  // 1HKO
                        damagePercent >= 50    -> score += 2  // 2HKO range
                    }
                }

                // HP_AWARE: at low enemy HP, prefer high damage
                if (aiFlags and AI_FLAG_HP_AWARE != 0 && moveData != null) {
                    val enemyHpPct = if (enemy.maxHp > 0) enemy.hp * 100 / enemy.maxHp else 100
                    if (enemyHpPct < 33 && moveData.power > 0) score += 1
                }

                val label = buildLabel(score, damagePercent, damage, target.hp, effectiveness)
                ScoredMove(moveId, moveName, moveData, score, damage, damagePercent, label)
            }
        }.filter { it.moveId != 0 }
    }

    private fun buildLabel(score: Int, pct: Int, dmg: Int, targetHp: Int, effectiveness: Float): String {
        return when {
            dmg >= targetHp -> "⚠ Will KO"
            pct >= 50       -> "~${pct}% HP"
            effectiveness == 0f -> "Immune"
            effectiveness < 1f  -> "Resisted"
            effectiveness > 1f  -> "Super eff. ~${pct}%"
            else                -> "~${pct}% HP"
        }
    }

    /**
     * Returns the predicted move(s) the AI will use — the highest scored.
     * Multiple = tied, genuinely random between them.
     */
    fun predictAiMove(scored: List<ScoredMove>): List<ScoredMove> {
        if (scored.isEmpty()) return emptyList()
        val best = scored.maxOf { it.score }
        return scored.filter { it.score == best }
    }
}
