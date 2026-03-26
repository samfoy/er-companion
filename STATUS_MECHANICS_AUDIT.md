# Status Condition Mechanics Audit Report

**Date:** 2026-03-26
**File Audited:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`
**Reference:** Emerald Rogue (Gen 7-9 mechanics) + `/Users/samfp/er-companion/EMERALD_ROGUE_MECHANICS.md`

---

## Executive Summary

**Overall Status:** 🟡 PARTIALLY CORRECT - 4 of 6 mechanics need fixes

Emerald Rogue uses `GEN_LATEST` (Gen 7-9) mechanics for status conditions, NOT Gen 3 mechanics. Our implementation has several critical errors that need correction.

---

## 1. Burn Damage ✅ CORRECT

**Expected (Gen 7+):** 1/16 max HP per turn
**Config:** `B_BURN_DAMAGE GEN_LATEST` (battle.h:28)
**Source:** `/Users/samfp/emerogue/src/battle_util.c:2842`
```c
gBattleMoveDamage = GetNonDynamaxMaxHP(battler) / (B_BURN_DAMAGE >= GEN_7 ? 16 : 8);
```

**Our Implementation:** MoveSimulator.kt:583-584
```kotlin
StatusConditions.isBurned(battler.status) -> {
    // Burn: 1/16 max HP per turn
    damage = maxHp / 16
}
```

**Result:** ✅ **CORRECT** - We correctly implement 1/16 HP per turn

---

## 2. Sleep Duration ❌ INCORRECT

**Expected (Gen 5+):** 1-3 turns
**Config:** `B_SLEEP_TURNS GEN_LATEST` (battle.h:56)
**Source:** `/Users/samfp/emerogue/src/battle_script_commands.c:3424-3427`
```c
if (B_SLEEP_TURNS >= GEN_5)
    gBattleMons[gEffectBattler].status1 |= STATUS1_SLEEP_TURN(1 + RandomUniform(RNG_SLEEP_TURNS, 1, 3));
else
    gBattleMons[gEffectBattler].status1 |= STATUS1_SLEEP_TURN(1 + RandomUniform(RNG_SLEEP_TURNS, 2, 5));
```

**Our Implementation:** StatusConditions.kt (NOT in MoveSimulator.kt)
- **Problem:** MoveSimulator.kt does NOT handle sleep infliction or duration
- Sleep status is applied in lines 224-227 but only checks if status is NONE
- Sleep counter decrement happens in `updateStatusCondition()` (line 554-556) but no initial duration is set

**Result:** ❌ **MISSING** - We don't set sleep turn duration when inflicting sleep status

**Fix Required:**
```kotlin
// In StatusConditions object, add:
const val SLEEP_MIN_TURNS = 1  // Gen 5+
const val SLEEP_MAX_TURNS = 3  // Gen 5+ (was 2-5 in Gen 3-4)

// When inflicting sleep (in executePlayerMove/executeEnemyMove around line 224):
if (random.nextFloat() < statusEffect.chance) {
    val sleepTurns = Random.nextInt(SLEEP_MIN_TURNS, SLEEP_MAX_TURNS + 1)
    newState = newState.copy(
        enemy = newState.enemy.copy(status = StatusConditions.ASLEEP + sleepTurns)
    )
}
```

---

## 3. Confusion Self-Damage Chance ❌ NOT IMPLEMENTED

**Expected (Gen 7+):** 33.3% chance (2 in 3 odds)
**Config:** `B_CONFUSION_SELF_DMG_CHANCE GEN_LATEST` (battle.h:8)
**Source:** `/Users/samfp/emerogue/src/battle_util.c:3696`
```c
if (RandomWeighted(RNG_CONFUSION, (B_CONFUSION_SELF_DMG_CHANCE >= GEN_7 ? 2 : 1), 1))
```

This translates to:
- Gen 7+: RandomWeighted(2, 1) = 2/(2+1) = **66.7% to move**, 33.3% self-damage
- Gen 3-6: RandomWeighted(1, 1) = 1/(1+1) = **50% to move**, 50% self-damage

**Our Implementation:** MoveSimulator.kt
- **Problem:** Confusion is NOT implemented at all in MoveSimulator.kt
- No confusion status checks in `checkCanMove()` (lines 524-541)
- No confusion counter in BattlerState or TempBoosts

**Result:** ❌ **MISSING** - Confusion mechanics not implemented

**Fix Required:**
```kotlin
// In checkCanMove() after paralysis check:
// Confused: 33.3% chance of self-damage (Gen 7+)
if (StatusConditions.isConfused(battler.status)) {
    // Gen 7+: 66.7% chance to successfully use move
    return Random.nextFloat() >= 0.333f  // 66.7% chance to move
}
```

---

## 4. Paralysis Full Paralysis Chance ✅ CORRECT

**Expected:** 25% chance of not moving (75% chance to move)
**Source:** `/Users/samfp/emerogue/src/battle_util.c:3721`
```c
if (!RandomPercentage(RNG_PARALYSIS, 75))  // 75% to succeed = 25% to fail
```

**Our Implementation:** MoveSimulator.kt:536-538
```kotlin
// Paralyzed: 25% chance of full paralysis
if (StatusConditions.isParalyzed(battler.status)) {
    return Random.nextFloat() >= 0.25f  // 75% chance to move
}
```

**Result:** ✅ **CORRECT** - We correctly implement 25% full paralysis chance

---

## 5. Poison Damage ✅ CORRECT

**Expected:**
- Regular Poison: 1/8 max HP per turn
- Toxic (Bad Poison): N/16 max HP (N = toxic counter, increases each turn)

**Source:** `/Users/samfp/emerogue/src/battle_util.c`
- Regular Poison (line 2794): `GetNonDynamaxMaxHP(battler) / 8`
- Toxic (line 2824): `GetNonDynamaxMaxHP(battler) / 16` multiplied by counter (line 2829)

**Our Implementation:** MoveSimulator.kt:609-612 and 586-607
```kotlin
StatusConditions.isPoisoned(battler.status) && !StatusConditions.isToxic(battler.status) -> {
    // Regular poison: 1/8 max HP per turn
    damage = maxHp / 8
}
StatusConditions.isToxic(battler.status) -> {
    // Toxic: N/16 max HP (increases each turn)
    val toxicCounter = battler.tempBoosts.toxicCounter + 1
    damage = (maxHp * toxicCounter) / 16
    // ... updates counter ...
}
```

**Result:** ✅ **CORRECT** - Both regular poison and toxic damage are correctly implemented

---

## 6. Freeze Mechanics ✅ CORRECT (No Frostbite)

**Expected:**
- Freeze: 20% chance to thaw each turn when attempting to move
- Frostbite: NOT used (Config: `B_USE_FROSTBITE FALSE`)

**Source:** `/Users/samfp/emerogue/src/battle_util.c:3578`
```c
if (!RandomPercentage(RNG_FROZEN, 20))  // 20% to thaw
```

**Our Implementation:** MoveSimulator.kt:531-533
```kotlin
// Frozen: 20% chance to thaw
if (StatusConditions.isFrozen(battler.status)) {
    return Random.nextFloat() < 0.2f  // 20% thaw chance
}
```

**Result:** ✅ **CORRECT** - We correctly implement 20% thaw chance, and don't implement Frostbite

**Note:** There's a duplicate thaw check in `updateStatusCondition()` (lines 559-563) that should be removed to avoid double-rolling.

---

## Summary Table

| Mechanic | Expected (Gen 7-9) | Current Implementation | Status |
|----------|-------------------|------------------------|--------|
| **Burn Damage** | 1/16 HP/turn | 1/16 HP/turn | ✅ CORRECT |
| **Sleep Duration** | 1-3 turns | Not set on infliction | ❌ MISSING |
| **Confusion** | 33.3% self-damage | Not implemented | ❌ MISSING |
| **Paralysis** | 25% full paralysis | 25% full paralysis | ✅ CORRECT |
| **Poison** | 1/8 HP/turn | 1/8 HP/turn | ✅ CORRECT |
| **Toxic** | N/16 HP/turn | N/16 HP/turn | ✅ CORRECT |
| **Freeze** | 20% thaw chance | 20% thaw chance | ✅ CORRECT |

**Score: 4/6 mechanics correct** (Burn, Paralysis, Poison/Toxic, Freeze)

---

## Critical Fixes Needed

### Priority 1: Sleep Duration
**Location:** MoveSimulator.kt lines 217-229 (executePlayerMove) and 298-311 (executeEnemyMove)

**Current Code:**
```kotlin
if (random.nextFloat() < statusEffect.chance) {
    newState = newState.copy(
        enemy = newState.enemy.copy(status = statusEffect.condition)
    )
}
```

**Fixed Code:**
```kotlin
if (random.nextFloat() < statusEffect.chance) {
    var statusValue = statusEffect.condition

    // Gen 5+: Sleep lasts 1-3 turns
    if (StatusConditions.isAsleep(statusEffect.condition)) {
        val sleepTurns = random.nextInt(1, 4)  // 1-3 turns inclusive
        statusValue = statusEffect.condition + sleepTurns
    }

    newState = newState.copy(
        enemy = newState.enemy.copy(status = statusValue)
    )
}
```

### Priority 2: Remove Duplicate Freeze Check
**Location:** MoveSimulator.kt lines 559-563

**Current Code (REMOVE THIS):**
```kotlin
// Frozen: try to thaw (already checked in checkCanMove)
if (StatusConditions.isFrozen(newStatus)) {
    if (Random.nextFloat() < 0.2f) {
        newStatus = StatusConditions.NONE
    }
}
```

**Reason:** This causes double-rolling for freeze thaw - once in `checkCanMove()` and once in `updateStatusCondition()`. The check in `checkCanMove()` is sufficient.

### Priority 3: Implement Confusion (Optional - Not Currently Used)
If confusion status is ever added to the game:

1. Add confusion counter to BattlerState/TempBoosts
2. Add confusion check in `checkCanMove()` with 33.3% self-damage chance
3. Implement confusion self-damage calculation (40 power typeless move)
4. Decrement confusion counter in `updateStatusCondition()`

---

## Code References

### Emerald Rogue Source Files
- **Status Damage:** `/Users/samfp/emerogue/src/battle_util.c:2774-2870`
- **Paralysis Check:** `/Users/samfp/emerogue/src/battle_util.c:3721`
- **Freeze Check:** `/Users/samfp/emerogue/src/battle_util.c:3576-3591`
- **Confusion Check:** `/Users/samfp/emerogue/src/battle_util.c:3696`
- **Sleep Duration:** `/Users/samfp/emerogue/src/battle_script_commands.c:3424-3427`
- **Config Constants:** `/Users/samfp/emerogue/include/config/battle.h`

### Our Implementation Files
- **Main File:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/MoveSimulator.kt`
- **Status Checks:** Lines 524-541 (checkCanMove)
- **Status Damage:** Lines 576-622 (applyStatusDamage)
- **Status Updates:** Lines 548-571 (updateStatusCondition)
- **Status Infliction:** Lines 217-229, 298-311 (execute move functions)

---

## Testing Recommendations

After applying fixes:

1. **Sleep Duration Test:** Verify sleep status values are 1-3 when inflicted
2. **Freeze Double-Check Test:** Ensure frozen Pokemon only roll thaw chance once per turn
3. **Status Damage Test:** Verify burn (1/16), poison (1/8), toxic (N/16) damage amounts
4. **Paralysis Test:** Verify 25% full paralysis rate over multiple simulations

---

**End of Audit Report**
