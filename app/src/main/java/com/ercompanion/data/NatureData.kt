package com.ercompanion.data

/**
 * Pokemon Nature system
 *
 * Nature is determined by: personality % 25
 * Most natures boost one stat (+10%) and reduce another (-10%)
 *
 * IMPORTANT: Stats read from memory already include nature modifiers.
 * This data is for DISPLAY ONLY - do NOT reapply these modifiers.
 */
data class Nature(
    val id: Int,
    val name: String,
    val increasedStat: String = "",  // "Attack", "Defense", etc. Empty for neutral
    val decreasedStat: String = ""   // Empty for neutral
) {
    val isNeutral: Boolean get() = increasedStat.isEmpty()
}

object NatureData {
    private val NATURES = listOf(
        Nature(0, "Hardy"),      // Neutral
        Nature(1, "Lonely", "Attack", "Defense"),
        Nature(2, "Brave", "Attack", "Speed"),
        Nature(3, "Adamant", "Attack", "Sp.Atk"),
        Nature(4, "Naughty", "Attack", "Sp.Def"),
        Nature(5, "Bold", "Defense", "Attack"),
        Nature(6, "Docile"),     // Neutral
        Nature(7, "Relaxed", "Defense", "Speed"),
        Nature(8, "Impish", "Defense", "Sp.Atk"),
        Nature(9, "Lax", "Defense", "Sp.Def"),
        Nature(10, "Timid", "Speed", "Attack"),
        Nature(11, "Hasty", "Speed", "Defense"),
        Nature(12, "Serious"),   // Neutral
        Nature(13, "Jolly", "Speed", "Sp.Atk"),
        Nature(14, "Naive", "Speed", "Sp.Def"),
        Nature(15, "Modest", "Sp.Atk", "Attack"),
        Nature(16, "Mild", "Sp.Atk", "Defense"),
        Nature(17, "Quiet", "Sp.Atk", "Speed"),
        Nature(18, "Bashful"),   // Neutral
        Nature(19, "Rash", "Sp.Atk", "Sp.Def"),
        Nature(20, "Calm", "Sp.Def", "Attack"),
        Nature(21, "Gentle", "Sp.Def", "Defense"),
        Nature(22, "Sassy", "Sp.Def", "Speed"),
        Nature(23, "Careful", "Sp.Def", "Sp.Atk"),
        Nature(24, "Quirky")     // Neutral
    )

    /**
     * Get nature from personality value
     * Formula: personality % 25
     */
    fun getNatureFromPersonality(personality: UInt): Nature {
        val natureId = (personality % 25u).toInt()
        return NATURES.getOrNull(natureId) ?: NATURES[0]
    }

    fun getNatureName(natureId: Int): String {
        return NATURES.getOrNull(natureId)?.name ?: "Unknown"
    }

    /**
     * Get display string for nature effect
     * Example: "+Atk -Def" or "Neutral"
     */
    fun getNatureEffectText(nature: Nature): String {
        return if (nature.isNeutral) {
            "Neutral"
        } else {
            "+${nature.increasedStat} -${nature.decreasedStat}"
        }
    }
}
