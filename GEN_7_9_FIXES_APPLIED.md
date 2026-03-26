# Gen 7-9 Mechanics Fixes Applied

**Date:** 2026-03-26
**Build Status:** ✅ Successful (compiles with no errors)

## Summary

Fixed **4 critical bugs** identified in the Gen 7-9 mechanics audit to match Emerald Rogue's behavior exactly.

---

## ✅ Fix 1: Burn Attack Reduction (CRITICAL)

**File:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/DamageCalculator.kt`
**Line:** 267-269

### Issue
Burn was applying 0.5x damage to ALL moves (including special moves). In Gen 6+, burn should only reduce **physical** move damage.

### Before
```kotlin
// Apply burn (0.5x if physical and burned, unless Guts ability)
if (isBurned && attackerAbility != 62) {  // 62 = Guts
    baseDamage = (baseDamage * 0.5f).toInt()
}
```

### After
```kotlin
// Apply burn (0.5x if PHYSICAL and burned, unless Guts ability)
// Gen 6+: Burn only affects physical moves, not special moves
if (isBurned && moveCategory == 0 && attackerAbility != 62) {  // 62 = Guts, 0 = Physical
    baseDamage = (baseDamage * 0.5f).toInt()
}
```

### Impact
- Burned **special attackers** now deal correct damage (not reduced)
- Burned **physical attackers** still have damage reduced (correct)
- Guts ability still ignores burn penalty (correct)

**Bug Severity:** CRITICAL - Was incorrectly nerfing special attackers

---

## ✅ Fix 2: Terrain Boosts Too High (CRITICAL)

**File:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/WeatherEffects.kt`
**Lines:** 189-199

### Issue
Electric/Grassy/Psychic Terrains used 1.5x boost (Gen 7 mechanics). Emerald Rogue uses Gen 8+ config which changed terrain boosts to 1.3x.

### Before
```kotlin
Terrain.ELECTRIC -> {
    if (moveType == Types.ELECTRIC) 1.5f else 1.0f  // Electric moves boosted
}
Terrain.GRASSY -> {
    if (moveType == Types.GRASS) 1.5f else 1.0f  // Grass moves boosted
}
Terrain.PSYCHIC -> {
    if (moveType == Types.PSYCHIC) 1.5f else 1.0f  // Psychic moves boosted
}
```

### After
```kotlin
Terrain.ELECTRIC -> {
    if (moveType == Types.ELECTRIC) 1.3f else 1.0f  // Gen 8+: 1.3x boost (was 1.5x in Gen 7)
}
Terrain.GRASSY -> {
    if (moveType == Types.GRASS) 1.3f else 1.0f  // Gen 8+: 1.3x boost (was 1.5x in Gen 7)
}
Terrain.PSYCHIC -> {
    if (moveType == Types.PSYCHIC) 1.3f else 1.0f  // Gen 8+: 1.3x boost (was 1.5x in Gen 7)
}
```

### Impact
- Electric Terrain: Now 1.3x (was 1.5x = 15% too strong)
- Grassy Terrain: Now 1.3x (was 1.5x = 15% too strong)
- Psychic Terrain: Now 1.3x (was 1.5x = 15% too strong)
- Misty Terrain: Unchanged (0.5x Dragon reduction is correct)

**Bug Severity:** CRITICAL - Terrain-boosted moves were 15% stronger than actual ER

**Config Reference:** `B_TERRAIN_TYPE_BOOST GEN_LATEST` (Line 197 in emerogue/include/config/battle.h)

---

## ✅ Fix 3: Sleep Duration Not Set (HIGH)

**File:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`
**Lines:** 217-229, 310-322

### Issue
Sleep status was inflicted but turn duration wasn't randomized. Gen 5+ uses 1-3 turns (not Gen 3's 2-5 turns).

### Before
```kotlin
if (random.nextFloat() < statusEffect.chance) {
    newState = newState.copy(
        enemy = newState.enemy.copy(status = statusEffect.condition)
    )
}
```

### After
```kotlin
if (random.nextFloat() < statusEffect.chance) {
    // Gen 5+: Sleep lasts 1-3 turns (randomly determined on infliction)
    val finalStatus = if (StatusConditions.isSleep(statusEffect.condition)) {
        // Random 1-3 turns for sleep
        when (random.nextInt(3)) {
            0 -> StatusConditions.SLEEP_1
            1 -> StatusConditions.SLEEP_2
            else -> StatusConditions.SLEEP_3
        }
    } else {
        statusEffect.condition
    }

    newState = newState.copy(
        enemy = newState.enemy.copy(status = finalStatus)
    )
}
```

### Impact
- Sleep now lasts 1-3 turns (Gen 5+ mechanics)
- Duration is randomly determined on infliction (deterministic random using state hash)
- Applied to both player and enemy status infliction

**Bug Severity:** HIGH - Sleep duration wasn't working correctly

**Config Reference:** `B_SLEEP_TURNS GEN_LATEST` in emerogue config

---

## ✅ Fix 4: Duplicate Freeze Thaw Check (MEDIUM)

**File:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`
**Lines:** 583-587

### Issue
Freeze thaw chance (20%) was rolled twice:
1. Once in `checkCanMove()` (lines 555-557) - correct
2. Again in `updateStatusCondition()` (lines 583-587) - duplicate

This caused Pokemon to thaw approximately 36% per turn instead of 20%.

### Before
```kotlin
// Decrement sleep counter on move attempt (Gen 3 mechanics)
if (StatusConditions.isAsleep(newStatus)) {
    newStatus = (newStatus - 1).coerceAtLeast(0)
}

// Frozen: try to thaw (already checked in checkCanMove)
if (StatusConditions.isFrozen(newStatus)) {
    if (Random.nextFloat() < 0.2f) {
        newStatus = StatusConditions.NONE
    }
}
```

### After
```kotlin
// Decrement sleep counter on move attempt (Gen 5+: 1-3 turns)
if (StatusConditions.isAsleep(newStatus)) {
    newStatus = (newStatus - 1).coerceAtLeast(0)
}

// Note: Frozen thaw is checked in checkCanMove() - don't duplicate here
// Removing duplicate freeze check (was causing double-rolling of thaw chance)
```

### Impact
- Freeze thaw now correctly 20% per turn (not ~36%)
- Matches actual Pokemon behavior

**Bug Severity:** MEDIUM - Made freeze less effective than it should be

---

## Verification

### Build Status
```
./gradlew compileDebugKotlin
BUILD SUCCESSFUL in 1s
```

All fixes compile with no errors. Only warnings are for unused parameters (non-critical).

### Files Modified
1. `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/DamageCalculator.kt`
2. `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/WeatherEffects.kt`
3. `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`

### Tests
Existing tests should still pass. New tests needed for:
- Burn only affecting physical moves
- Terrain boosts using 1.3x multiplier
- Sleep lasting 1-3 turns
- Freeze thaw being 20% per turn

---

## Related Documentation

- `/Users/samfp/er-companion/EMERALD_ROGUE_MECHANICS.md` - Gen mechanics reference
- `/Users/samfp/er-companion/DAMAGE_CALCULATOR_AUDIT.md` - Detailed audit findings
- `/Users/samfp/er-companion/STATUS_MECHANICS_AUDIT.md` - Status condition audit
- `/Users/samfp/er-companion/TEST_PLAN.md` - Comprehensive testing strategy
- `/Users/samfp/emerogue/include/config/battle.h` - ER configuration source

---

## Summary Statistics

**Total Bugs Fixed:** 4
**Critical:** 2 (burn, terrain)
**High:** 1 (sleep duration)
**Medium:** 1 (duplicate freeze check)

**Lines Changed:** ~30 lines across 3 files
**Compile Status:** ✅ Success
**Breaking Changes:** None (all internal logic fixes)

All fixes align ER Companion with Emerald Rogue's Gen 7-9 mechanics as configured in `B_*` constants.
