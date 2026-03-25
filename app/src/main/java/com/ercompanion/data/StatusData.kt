package com.ercompanion.data

/**
 * Pokemon status condition decoder
 *
 * Status format (32-bit value):
 * - 0x00 = None
 * - 0x01-0x07 = Sleep (turns remaining, bits 0-2)
 * - 0x08 = Poisoned
 * - 0x10 = Burned
 * - 0x20 = Frozen
 * - 0x40 = Paralyzed
 * - 0x80 = Toxic poisoned
 */
data class StatusCondition(
    val name: String,
    val emoji: String,
    val color: Long,
    val affectsStats: Boolean = false
)

object StatusData {
    private const val STATUS_SLEEP_MASK = 0x07
    private const val STATUS_POISON = 0x08
    private const val STATUS_BURN = 0x10
    private const val STATUS_FREEZE = 0x20
    private const val STATUS_PARALYSIS = 0x40
    private const val STATUS_TOXIC = 0x80

    fun getStatusCondition(status: Int): StatusCondition? {
        return when {
            status == 0 -> null
            (status and STATUS_SLEEP_MASK) > 0 -> {
                val turns = status and STATUS_SLEEP_MASK
                StatusCondition("Asleep ($turns)", "💤", 0xFF9C27B0)
            }
            (status and STATUS_BURN) != 0 -> {
                StatusCondition("Burned", "🔥", 0xFFFF5722, affectsStats = true)
            }
            (status and STATUS_PARALYSIS) != 0 -> {
                StatusCondition("Paralyzed", "⚡", 0xFFFFEB3B, affectsStats = true)
            }
            (status and STATUS_FREEZE) != 0 -> {
                StatusCondition("Frozen", "❄️", 0xFF03A9F4)
            }
            (status and STATUS_TOXIC) != 0 -> {
                StatusCondition("Badly Poisoned", "☠️", 0xFF9C27B0, affectsStats = true)
            }
            (status and STATUS_POISON) != 0 -> {
                StatusCondition("Poisoned", "☠️", 0xFF9C27B0, affectsStats = true)
            }
            else -> null
        }
    }

    /**
     * Get status multiplier for catch rate calculations
     */
    fun getCatchRateMultiplier(status: Int): Float {
        return when {
            (status and STATUS_SLEEP_MASK) > 0 -> 2.0f  // Sleep
            (status and STATUS_FREEZE) != 0 -> 2.0f     // Freeze
            status > 0 -> 1.5f                           // Para/Burn/Poison
            else -> 1.0f                                 // None
        }
    }
}
