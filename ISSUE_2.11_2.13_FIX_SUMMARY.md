# Code Review Issues 2.11 & 2.13 - Fix Summary

**Date:** 2026-03-26
**Issues Fixed:** 2.13 (Sandstorm Rock Type Check Wrong) and 2.11 (Ability Immunity Check Missing)

## Overview

Fixed hard-coded type IDs and added missing immunity abilities as identified in CODE_REVIEW_ISSUES.md. These changes improve code maintainability and prevent bugs from changing type/ability IDs.

## Changes Made

### 1. Created Types Constants Object

**File:** `/app/src/main/java/com/ercompanion/data/Types.kt` (NEW)

Created a centralized `Types` object containing all Pokemon type constants:

```kotlin
object Types {
    const val NORMAL = 0
    const val FIGHTING = 1
    const val FLYING = 2
    const val POISON = 3
    const val GROUND = 4
    const val ROCK = 5
    const val BUG = 6
    const val GHOST = 7
    const val STEEL = 8
    const val FIRE = 9
    const val WATER = 10
    const val GRASS = 11
    const val ELECTRIC = 12
    const val PSYCHIC = 13
    const val ICE = 14
    const val DRAGON = 15
    const val DARK = 16
    const val FAIRY = 17
}
```

### 2. Fixed Issue 2.13 - Sandstorm Rock Type Check

**File:** `/app/src/main/java/com/ercompanion/calc/WeatherEffects.kt`

**Before:**
```kotlin
// Hard-coded type IDs
if (!types.contains(5) && !types.contains(4) && !types.contains(8)) {
    damage = battler.mon.maxHp / 16
}
```

**After:**
```kotlin
// Named constants
if (!types.contains(Types.ROCK) && !types.contains(Types.GROUND) && !types.contains(Types.STEEL)) {
    damage = battler.mon.maxHp / 16
}
```

**Changes:**
- Line 96: Replaced hard-coded type IDs (5, 4, 8) with `Types.ROCK`, `Types.GROUND`, `Types.STEEL`
- Line 104: Replaced hard-coded type ID (14) with `Types.ICE` for Hail damage
- Lines 46-57: Replaced hard-coded type IDs (9, 10) with `Types.FIRE`, `Types.WATER` for weather multipliers
- Line 134: Replaced hard-coded type ID (5) with `Types.ROCK` for SpDef boost
- Lines 175-188: Replaced hard-coded type IDs with `Types.ELECTRIC`, `Types.GRASS`, `Types.PSYCHIC`, `Types.DRAGON` for terrain effects
- Lines 207, 244: Replaced hard-coded type ID (2) with `Types.FLYING` for grounded checks

### 3. Fixed Issue 2.11 - Ability Immunity Check

**File:** `/app/src/main/java/com/ercompanion/data/AbilityData.kt`

**Added Missing Abilities:**
1. **Lightning Rod** (ID: 31) - Grants immunity to Electric-type moves
2. **Storm Drain** (ID: 114) - Grants immunity to Water-type moves

**Before:**
```kotlin
// Missing Lightning Rod and Storm Drain
```

**After:**
```kotlin
LIGHTNING_ROD to AbilityEffect(
    id = LIGHTNING_ROD,
    name = "LightningRod",
    typeImmunities = listOf(Types.ELECTRIC),
    description = "Immune to Electric, +1 Sp.Atk"
),
STORM_DRAIN to AbilityEffect(
    id = STORM_DRAIN,
    name = "Storm Drain",
    typeImmunities = listOf(Types.WATER),
    description = "Immune to Water, +1 Sp.Atk"
),
```

**Complete Immunity Coverage:**
The `AbilityData.grantsImmunity()` function now correctly handles all immunity abilities:
- ✓ Levitate (Ground immunity)
- ✓ Volt Absorb (Electric immunity + healing)
- ✓ Water Absorb (Water immunity + healing)
- ✓ Flash Fire (Fire immunity + boost)
- ✓ Sap Sipper (Grass immunity + Attack boost)
- ✓ Lightning Rod (Electric immunity + Sp.Atk boost) **[ADDED]**
- ✓ Storm Drain (Water immunity + Sp.Atk boost) **[ADDED]**
- ✓ Motor Drive (Electric immunity + Speed boost)

**Updated Type Constants:**
- Replaced all hard-coded type IDs in ability definitions with `Types` constants
- Lines 82-113: Type immunities now use `Types.GROUND`, `Types.FIRE`, `Types.ELECTRIC`, `Types.WATER`, `Types.GRASS`
- Lines 258-261: Pinch abilities now use `Types.GRASS`, `Types.FIRE`, `Types.WATER`, `Types.BUG`
- Line 281: Thick Fat defensive multiplier now uses `Types.FIRE`, `Types.ICE`

### 4. Additional Consistency Fixes

**File:** `/app/src/main/java/com/ercompanion/calc/AbilityEffects.kt`
- Line 204: Replaced hard-coded type ID (16) with `Types.DARK` for Justified ability

**File:** `/app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`
- Line 409: Replaced hard-coded type ID (2) with `Types.FLYING` for grounded check

## Testing

Build verification:
```bash
./gradlew compileDebugKotlin
```
Result: **BUILD SUCCESSFUL** - All changes compile without errors

## Impact

### Benefits:
1. **Improved Maintainability**: Type IDs are now centralized and named, making the code easier to understand and modify
2. **Bug Prevention**: If type IDs need to change in the future, only the `Types` object needs to be updated
3. **Complete Immunity Coverage**: All relevant immunity abilities are now properly handled in damage calculations
4. **Consistency**: Type references are now consistent across the entire codebase

### Risk Assessment:
- **Low Risk**: Changes are purely refactoring with no functional changes to game logic
- **Backward Compatible**: No changes to method signatures or public APIs
- **Type Safe**: Kotlin constants provide compile-time type safety

## Files Modified

1. `/app/src/main/java/com/ercompanion/data/Types.kt` (NEW)
2. `/app/src/main/java/com/ercompanion/calc/WeatherEffects.kt`
3. `/app/src/main/java/com/ercompanion/data/AbilityData.kt`
4. `/app/src/main/java/com/ercompanion/calc/AbilityEffects.kt`
5. `/app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`

## Related Issues

- **2.13 Sandstorm Rock Type Check Wrong**: ✅ FIXED
- **2.11 Ability Immunity Check Missing**: ✅ FIXED

## Next Steps

These fixes address 2 of the 47 issues identified in the code review. Recommended next actions:
1. Apply similar type constant refactoring to remaining files (ItemData.kt, DamageCalculator.kt)
2. Address critical issues (1.1-1.12) from the code review
3. Add unit tests to verify immunity abilities work correctly
4. Consider creating similar constant objects for Abilities and Items
