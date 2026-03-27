#!/usr/bin/env kotlin

// Simple script to validate DamageCalculator handles all effects

import com.ercompanion.calc.DamageCalculator
import com.ercompanion.calc.Weather
import com.ercompanion.calc.Terrain
import com.ercompanion.calc.StatusConditions

fun main() {
    println("=== Testing DamageCalculator Advanced Effects ===\n")

    // Base damage test
    val baseDamage = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 9,  // Fire
        attackerTypes = listOf(10),  // Water (no STAB)
        defenderTypes = listOf(0),  // Normal
        targetMaxHP = 150
    )
    println("Base damage (no effects): ${baseDamage.maxDamage}")

    // Test 1: Weather - Sun boosts Fire
    val sunDamage = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 9,
        attackerTypes = listOf(10),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        weather = Weather.SUN
    )
    val sunBoost = sunDamage.maxDamage.toFloat() / baseDamage.maxDamage
    println("Sun boost for Fire: ${sunBoost}x (expected 1.5x)")
    assert(sunBoost > 1.4f && sunBoost < 1.6f) { "Sun should boost Fire by ~1.5x" }

    // Test 2: Weather - Rain weakens Fire
    val rainDamage = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 9,
        attackerTypes = listOf(10),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        weather = Weather.RAIN
    )
    val rainWeaken = rainDamage.maxDamage.toFloat() / baseDamage.maxDamage
    println("Rain nerf for Fire: ${rainWeaken}x (expected 0.5x)")
    assert(rainWeaken > 0.4f && rainWeaken < 0.6f) { "Rain should weaken Fire to ~0.5x" }

    // Test 3: Terrain - Electric Terrain boosts Electric
    val baseElectric = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 12,  // Electric
        attackerTypes = listOf(10),  // Water
        defenderTypes = listOf(0),
        targetMaxHP = 150
    )

    val terrainElectric = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 12,
        attackerTypes = listOf(10),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        terrain = Terrain.ELECTRIC.ordinal,
        isGrounded = true
    )
    val terrainBoost = terrainElectric.maxDamage.toFloat() / baseElectric.maxDamage
    println("Electric Terrain boost: ${terrainBoost}x (expected 1.5x)")
    assert(terrainBoost > 1.4f && terrainBoost < 1.6f) { "Electric Terrain should boost Electric by ~1.5x" }

    // Test 4: Ability - Guts boosts Attack when statused
    val normalPhysical = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 0,
        attackerTypes = listOf(0),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        moveCategory = 0,  // Physical
        attackerAbility = 0,
        attackerStatus = 0
    )

    val gutsPhysical = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 80,
        moveType = 0,
        attackerTypes = listOf(0),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        moveCategory = 0,
        attackerAbility = 62,  // Guts
        attackerStatus = StatusConditions.BURN
    )
    val gutsBoost = gutsPhysical.maxDamage.toFloat() / normalPhysical.maxDamage
    println("Guts boost (with status): ${gutsBoost}x (expected 1.5x)")
    assert(gutsBoost > 1.4f && gutsBoost < 1.6f) { "Guts should boost Attack by ~1.5x when statused" }

    // Test 5: Ability - Technician boosts low power moves
    val normalLowPower = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 60,
        moveType = 0,
        attackerTypes = listOf(0),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        attackerAbility = 0
    )

    val technicianLowPower = DamageCalculator.calc(
        attackerLevel = 50,
        attackStat = 100,
        defenseStat = 100,
        movePower = 60,
        moveType = 0,
        attackerTypes = listOf(0),
        defenderTypes = listOf(0),
        targetMaxHP = 150,
        attackerAbility = 101  // Technician
    )
    val techBoost = technicianLowPower.maxDamage.toFloat() / normalLowPower.maxDamage
    println("Technician boost (60 BP): ${techBoost}x (expected 1.5x)")
    assert(techBoost > 1.4f && techBoost < 1.6f) { "Technician should boost low power moves by ~1.5x" }

    println("\n=== All tests passed! ===")
    println("DamageCalculator successfully handles:")
    println("  - Weather effects (Sun, Rain)")
    println("  - Terrain effects (Electric Terrain)")
    println("  - Ability stat multipliers (Guts)")
    println("  - Ability move power multipliers (Technician)")
}

main()
