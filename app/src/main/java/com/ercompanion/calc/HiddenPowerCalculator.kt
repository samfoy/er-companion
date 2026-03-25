package com.ercompanion.calc

/**
 * Hidden Power type and power calculator
 *
 * Type calculation:
 * type = ((HP_bit0 * 1) + (Atk_bit0 * 2) + (Def_bit0 * 4) + (Spe_bit0 * 8) + (SpA_bit0 * 16) + (SpD_bit0 * 32)) * 15 / 63
 * where bit0 = IV % 2
 *
 * Power calculation (Gen 3-5):
 * power = 30 + ((HP_bit1 * 1) + (Atk_bit1 * 2) + (Def_bit1 * 4) + (Spe_bit1 * 8) + (SpA_bit1 * 16) + (SpD_bit1 * 32)) * 40 / 63
 * where bit1 = (IV >> 1) % 2
 */
data class HiddenPowerResult(
    val type: Int,        // Type ID (0-17)
    val typeName: String,
    val power: Int        // 30-70 for Gen 3-5
)

object HiddenPowerCalculator {
    private val TYPE_NAMES = listOf(
        "Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost",
        "Steel", "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon",
        "Dark", "Fairy"
    )

    /**
     * Calculate Hidden Power type and power from IVs
     * Emerald Rogue uses Gen 9 mechanics, supporting all 18 types
     */
    fun calculate(
        ivHp: Int,
        ivAttack: Int,
        ivDefense: Int,
        ivSpeed: Int,
        ivSpAttack: Int,
        ivSpDefense: Int
    ): HiddenPowerResult {
        // Type calculation - use bit 0 of each IV
        val a = (ivHp and 1)
        val b = (ivAttack and 1)
        val c = (ivDefense and 1)
        val d = (ivSpeed and 1)
        val e = (ivSpAttack and 1)
        val f = (ivSpDefense and 1)

        // Modified for Gen 9: map to all 18 types instead of 16
        val typeIndex = ((a + (b * 2) + (c * 4) + (d * 8) + (e * 16) + (f * 32)) * 17) / 63

        // Power calculation - use bit 1 of each IV
        val u = ((ivHp shr 1) and 1)
        val v = ((ivAttack shr 1) and 1)
        val w = ((ivDefense shr 1) and 1)
        val x = ((ivSpeed shr 1) and 1)
        val y = ((ivSpAttack shr 1) and 1)
        val z = ((ivSpDefense shr 1) and 1)

        val power = 30 + ((u + (v * 2) + (w * 4) + (x * 8) + (y * 16) + (z * 32)) * 40) / 63

        return HiddenPowerResult(
            type = typeIndex,
            typeName = TYPE_NAMES.getOrNull(typeIndex) ?: "Unknown",
            power = power
        )
    }
}
