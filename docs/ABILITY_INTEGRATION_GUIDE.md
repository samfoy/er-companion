# Ability Effects Integration Guide

## For Developers: How to Add New Abilities

This guide explains how to add new ability effects to the ER Companion battle calculator.

## Quick Start: Adding a New Ability

### 1. Determine the Ability Category

First, identify when your ability triggers:

- **Switch-in**: Ability activates when Pokemon enters battle
  - Examples: Intimidate, Download, Trace

- **On-KO**: Ability activates when the Pokemon scores a knockout
  - Examples: Moxie, Beast Boost

- **End-of-Turn**: Ability activates at the end of each turn
  - Examples: Speed Boost, Shed Skin, Regenerator

- **Stat Multiplier**: Ability modifies attack/defense stats
  - Examples: Guts, Marvel Scale, Huge Power

- **Move Power Modifier**: Ability modifies move power
  - Examples: Technician, Iron Fist, Sheer Force

- **Continuous Effect**: Ability has a passive effect during battle
  - Examples: Levitate (immunity), Thick Fat (resistance)

### 2. Add the Ability Constant

In `/app/src/main/java/com/ercompanion/calc/AbilityEffects.kt`:

```kotlin
object AbilityEffects {
    // Existing constants...
    private const val YOUR_ABILITY = <ability_id>  // Find ID from AbilityData.kt
}
```

### 3. Implement the Effect

Add the logic to the appropriate function:

#### Switch-In Ability Example

```kotlin
fun applySwitchInAbility(state: BattleState, isPlayer: Boolean): BattleState {
    val battler = if (isPlayer) state.player else state.enemy
    val opponent = if (isPlayer) state.enemy else state.player

    return when (battler.mon.ability) {
        // Existing abilities...

        YOUR_ABILITY -> {
            // Your logic here
            val newOpponent = opponent.copy(
                statStages = opponent.statStages.copy(
                    attack = maxOf(-6, opponent.statStages.attack - 1)
                )
            )
            if (isPlayer) {
                state.copy(enemy = newOpponent)
            } else {
                state.copy(player = newOpponent)
            }
        }

        else -> state
    }
}
```

#### On-KO Ability Example

```kotlin
fun applyKOAbility(state: BattleState, isPlayerKO: Boolean): BattleState {
    val battler = if (isPlayerKO) state.player else state.enemy

    return when (battler.mon.ability) {
        // Existing abilities...

        YOUR_ABILITY -> {
            // Your logic here
            val newBattler = battler.copy(
                statStages = battler.statStages.copy(
                    attack = minOf(6, battler.statStages.attack + 1)
                )
            )
            if (isPlayerKO) {
                state.copy(player = newBattler)
            } else {
                state.copy(enemy = newBattler)
            }
        }

        else -> state
    }
}
```

#### End-of-Turn Ability Example

```kotlin
fun applyEndOfTurnAbilities(state: BattleState): BattleState {
    var newState = state

    // Player abilities
    when (newState.player.mon.ability) {
        // Existing abilities...

        YOUR_ABILITY -> {
            // Your logic here
            newState = newState.copy(
                player = newState.player.copy(
                    statStages = newState.player.statStages.copy(
                        speed = minOf(6, newState.player.statStages.speed + 1)
                    )
                )
            )
        }
    }

    // Enemy abilities (same logic)
    when (newState.enemy.mon.ability) {
        YOUR_ABILITY -> {
            // Same logic for enemy
        }
    }

    return newState
}
```

#### Stat Multiplier Example

```kotlin
fun getAttackerStatMultiplier(battler: BattlerState, moveCategory: Int): Float {
    return when (battler.mon.ability) {
        // Existing abilities...

        YOUR_ABILITY -> {
            // Condition check
            if (someCondition && moveCategory == 0) {
                1.5f  // 1.5x multiplier
            } else {
                1.0f  // No change
            }
        }

        else -> 1.0f
    }
}
```

#### Move Power Modifier Example

```kotlin
fun getMovePowerMultiplier(battler: BattlerState, moveId: Int): Float {
    val moveData = PokemonData.getMoveData(moveId) ?: return 1.0f

    return when (battler.mon.ability) {
        // Existing abilities...

        YOUR_ABILITY -> {
            if (isSpecialMoveType(moveId)) {
                1.3f  // 1.3x boost
            } else {
                1.0f
            }
        }

        else -> 1.0f
    }
}
```

### 4. Add Helper Functions (if needed)

If your ability requires move classification or special checks:

```kotlin
private fun isYourMoveType(moveId: Int): Boolean {
    val yourMoveList = setOf(
        moveId1, moveId2, moveId3
    )
    return moveId in yourMoveList
}
```

### 5. Write Tests

In `/app/src/test/java/com/ercompanion/calc/AbilityEffectsTest.kt`:

```kotlin
@Test
fun testYourAbility() {
    // Setup
    val pokemon = createTestMon(ability = YOUR_ABILITY)
    val state = BattleState(
        player = createBattlerState(pokemon),
        enemy = createBattlerState(createTestMon())
    )

    // Act
    val newState = AbilityEffects.applySwitchInAbility(state, isPlayer = true)

    // Assert
    assertEquals(expectedValue, newState.player.statStages.attack)
}
```

### 6. Update Documentation

Add your ability to `/docs/ABILITY_REFERENCE.md`:

```markdown
| ID  | Name       | Effect                    | Implementation |
|-----|------------|---------------------------|----------------|
| 123 | Your Ability | Does something cool      | ✅ Complete    |
```

## Complete Examples

### Example 1: Justified (On-Hit Ability)

**Ability:** +1 Attack when hit by a Dark-type move

**Step 1:** Add constant
```kotlin
private const val JUSTIFIED = 154
```

**Step 2:** Implement in `applyOnHitAbility()`
```kotlin
fun applyOnHitAbility(
    state: BattleState,
    defender: BattlerState,
    moveType: Int,
    moveCategory: Int
): BattleState {
    return when (defender.mon.ability) {
        JUSTIFIED -> {
            // Dark type = 16
            if (moveType == 16) {
                val newDefender = defender.copy(
                    statStages = defender.statStages.copy(
                        attack = minOf(6, defender.statStages.attack + 1)
                    )
                )
                if (state.player == defender) {
                    state.copy(player = newDefender)
                } else {
                    state.copy(enemy = newDefender)
                }
            } else {
                state
            }
        }
        else -> state
    }
}
```

**Step 3:** Call from `executePlayerMove()` / `executeEnemyMove()`
```kotlin
// After damage is dealt
if (moveData != null && moveData.power > 0) {
    // ... damage calculation ...

    // Apply on-hit abilities
    newState = AbilityEffects.applyOnHitAbility(
        newState,
        newState.enemy,  // defender
        moveData.type,
        moveData.category
    )
}
```

**Step 4:** Add test
```kotlin
@Test
fun testJustifiedBoostsAttackOnDarkHit() {
    val lucario = createTestMon(ability = JUSTIFIED)
    val battler = createBattlerState(lucario)

    val state = BattleState(
        player = createBattlerState(createTestMon()),
        enemy = battler
    )

    // Hit with Dark-type move (type = 16)
    val newState = AbilityEffects.applyOnHitAbility(
        state,
        battler,
        moveType = 16,  // Dark
        moveCategory = 0  // Physical
    )

    assertEquals(1, newState.enemy.statStages.attack)
}
```

### Example 2: Regenerator (Switch-Out Ability)

**Ability:** Heals 1/3 max HP when switching out

**Note:** This requires tracking switch events, which is not yet implemented. Here's the planned approach:

```kotlin
fun applySwitchOutAbility(
    state: BattleState,
    isPlayer: Boolean
): BattleState {
    val battler = if (isPlayer) state.player else state.enemy

    return when (battler.mon.ability) {
        REGENERATOR -> {
            val healAmount = battler.mon.maxHp / 3
            val newHp = minOf(battler.mon.maxHp, battler.currentHp + healAmount)
            val newBattler = battler.copy(currentHp = newHp)

            if (isPlayer) {
                state.copy(player = newBattler)
            } else {
                state.copy(enemy = newBattler)
            }
        }
        else -> state
    }
}
```

This would be called from a `handleSwitch()` function (to be implemented).

### Example 3: Drought (Weather Setter)

**Ability:** Sets sun on switch-in

**Requirements:**
- Add `weather: Weather` field to `BattleState`
- Create `Weather` enum: `NONE, SUN, RAIN, SANDSTORM, HAIL`
- Track weather turn counter

```kotlin
// In BattleState.kt
data class BattleState(
    val player: BattlerState,
    val enemy: BattlerState,
    val turn: Int = 1,
    val weather: Weather = Weather.NONE,
    val weatherTurnsLeft: Int = 0
)

enum class Weather {
    NONE, SUN, RAIN, SANDSTORM, HAIL
}

// In AbilityEffects.kt
fun applySwitchInAbility(state: BattleState, isPlayer: Boolean): BattleState {
    val battler = if (isPlayer) state.player else state.enemy

    return when (battler.mon.ability) {
        DROUGHT -> {
            // Set sun for 5 turns (Gen 6+)
            state.copy(
                weather = Weather.SUN,
                weatherTurnsLeft = 5
            )
        }
        // ... other abilities ...
    }
}
```

## Common Patterns

### Pattern 1: Stat Stage Changes

**Always use `minOf` / `maxOf` to clamp:**
```kotlin
// Boost Attack (max +6)
statStages.copy(attack = minOf(6, statStages.attack + amount))

// Lower Defense (min -6)
statStages.copy(defense = maxOf(-6, statStages.defense - amount))
```

### Pattern 2: Conditional Multipliers

**Return 1.0f as default:**
```kotlin
fun getMultiplier(battler: BattlerState): Float {
    return when (battler.mon.ability) {
        ABILITY_ID -> {
            if (condition) multiplier else 1.0f
        }
        else -> 1.0f
    }
}
```

### Pattern 3: Random Chance

**Use `Random.nextFloat()`:**
```kotlin
// 33% chance
if (Random.nextFloat() < 0.33f) {
    // Effect triggers
}

// 20% chance
if (Random.nextFloat() < 0.20f) {
    // Effect triggers
}
```

### Pattern 4: Updating Both Battlers

**Process player first, then enemy:**
```kotlin
var newState = state

// Player ability
when (newState.player.mon.ability) {
    ABILITY -> {
        newState = newState.copy(
            player = newState.player.copy(/* changes */)
        )
    }
}

// Enemy ability
when (newState.enemy.mon.ability) {
    ABILITY -> {
        newState = newState.copy(
            enemy = newState.enemy.copy(/* changes */)
        )
    }
}

return newState
```

## Debugging Tips

### 1. Check Ability ID

Verify the ability ID matches `AbilityData.kt`:
```kotlin
// In AbilityData.kt
private const val YOUR_ABILITY = 123
```

### 2. Add Logging

Temporarily add logging to verify trigger:
```kotlin
when (battler.mon.ability) {
    YOUR_ABILITY -> {
        println("YOUR_ABILITY triggered for ${battler.mon.nickname}")
        // ... rest of logic
    }
}
```

### 3. Unit Test First

Write the test before implementation:
```kotlin
@Test
fun testYourAbility() {
    // This will fail initially
    val state = /* setup */
    val result = AbilityEffects.yourFunction(state)
    assertEquals(expected, result.player.statStages.attack)
}
```

### 4. Check Integration Points

Verify the ability function is called from:
- `MoveSimulator.simulateMove()` for end-of-turn
- `MoveSimulator.executePlayerMove()` for on-damage
- `MoveSimulator.executeEnemyMove()` for on-damage

## Performance Considerations

### Do:
✅ Use simple when/switch statements
✅ Use Set lookups for move classification: `moveId in punchMoves`
✅ Clamp values inline: `minOf(6, value + 1)`
✅ Return early when ability doesn't apply

### Don't:
❌ Use recursive calls
❌ Use complex data structures (maps of maps)
❌ Call `PokemonData` functions in loops
❌ Create new collections on every call

## Code Review Checklist

Before submitting your ability implementation:

- [ ] Ability constant added with correct ID
- [ ] Logic added to appropriate function(s)
- [ ] Stat stages clamped to [-6, +6]
- [ ] Multipliers return 1.0f as default
- [ ] Helper functions are private
- [ ] Tests added and passing
- [ ] Documentation updated in ABILITY_REFERENCE.md
- [ ] Edge cases handled (e.g., immunity, Clear Body)
- [ ] Code compiles: `./gradlew :app:compileDebugKotlin`

## Questions?

If you're unsure about:
- **Ability ID**: Check `/app/src/main/java/com/ercompanion/data/AbilityData.kt`
- **Move data**: Check `/app/src/main/java/com/ercompanion/data/PokemonData.kt`
- **Type IDs**: See type chart in `/app/src/main/java/com/ercompanion/calc/DamageCalculator.kt`
- **Status IDs**: Check `/app/src/main/java/com/ercompanion/calc/BattleState.kt` StatusConditions object

## Future Extensions

As the codebase evolves, new integration points may be added:

- **Weather system**: `handleWeatherEffects(state)`
- **Entry hazards**: `handleHazards(state, isPlayer)`
- **Terrain effects**: `handleTerrain(state)`
- **Multi-turn moves**: `handleCharging(state, moveId)`

When these are added, this guide will be updated with examples.
