# Damage Calculator Audit - 2026-03-26

## Reference: Gen 3 pokeemerald Formula
```c
// From pokeemerald src/battle_script_commands.c
damage = ((2 * level / 5 + 2) * power * attack / defense / 50)
if burned (physical move, no Guts): damage = damage / 2
damage = damage + 2
if STAB: damage = damage * 15 / 10   // INTEGER division!
for each defender type: damage = damage * typeEffectiveness
damage = damage * random (85-100) / 100
```

**Key rules:**
- ALL divisions are INTEGER (floor) division — no floats
- Burn halved BEFORE the +2 constant
- STAB is `x15/10` (integer), not `x1.5f` (float) — important for rounding
- Type effectiveness applied as sequential multiplications, each floored
- Random roll: 85-100 (one random number, applied last)
- Critical hits: x2 in Gen 3 (NOT x1.5)

---

## Issues Found

### 1. ❌ CRITICAL: STAB uses float math instead of integer division
**Location**: DamageCalculator.kt:362

**Current code:**
```kotlin
if (isStab) {
    baseDamage = (baseDamage * finalStabMultiplier).toInt()
}
```

**Problem**: When `finalStabMultiplier = 1.5f`, this does `damage = (damage * 1.5f).toInt()`, which gives different rounding than Gen 3's `damage = damage * 15 / 10` (integer division).

**Example**:
- Damage = 47
- Gen 3: `47 * 15 / 10 = 705 / 10 = 70` (integer division)
- Current: `(47 * 1.5f).toInt() = 70.5.toInt() = 70` (happens to match)
- Damage = 43
- Gen 3: `43 * 15 / 10 = 645 / 10 = 64` (integer division)
- Current: `(43 * 1.5f).toInt() = 64.5.toInt() = 64` (happens to match)

**Impact**: Minor rounding differences. While Kotlin's `.toInt()` truncates (floors) like integer division, float multiplication can introduce precision errors.

**Fix**: Use integer math: `baseDamage = baseDamage * 15 / 10` for 1.5x STAB, `baseDamage * 2` for 2x Adaptability.

---

### 2. ❌ CRITICAL: Burn calculation uses float math
**Location**: DamageCalculator.kt:335

**Current code:**
```kotlin
if (isBurned && moveCategory == 0 && attackerAbility != 62) {
    baseDamage = (baseDamage * 0.5f).toInt()
}
```

**Problem**: Uses `* 0.5f` (float) instead of `/ 2` (integer division).

**Impact**: Same result in most cases due to `.toInt()` truncation, but float math is unnecessary and doesn't match GBA code style.

**Fix**: Use integer division: `baseDamage = baseDamage / 2`

---

### 3. ❌ CRITICAL: Missing moveCategory parameter in multiple callers
**Locations:**
- `MoveSimulator.kt:415` - Missing moveCategory
- `MainScreen.kt:415` - Missing moveCategory
- `MainScreen.kt:434` - Missing moveCategory
- `MainScreen.kt:1337` - Missing moveCategory
- `CompactBattleScreen.kt:831` - Missing moveCategory
- `CompactBattleScreen.kt:1079` - Missing moveCategory
- `CompactBattleScreen.kt:1359` - Missing moveCategory
- `CompactBattleScreen.kt:1377` - Missing moveCategory

**Problem**: When moveCategory is not passed, it defaults to 0 (Physical), causing:
- Wrong stat selection in damage calc (uses Attack/Defense for special moves)
- Wrong burn logic (applies burn penalty to special moves)

**Impact**: HIGH - Special moves will have incorrect damage calculations.

**Fix**: Add `moveCategory = moveData.category` to all call sites.

---

### 4. ✅ Type Effectiveness Calculation
**Location**: DamageCalculator.kt:372-380

**Current code:**
```kotlin
var eff = 1f
for (defType in defenderTypes) {
    if (moveType in 0..17 && defType in 0..17) {
        eff *= TYPE_CHART[moveType][defType]
    }
}
```

**Status**: ACCEPTABLE. Type effectiveness uses float values (0.5, 2.0, etc.) from the type chart. The sequential multiplication happens in float space, then is converted to int when applied to damage (line 383). This matches Gen 3 behavior where type multipliers are applied sequentially.

---

### 5. ✅ Stat Selection in Callers
**Status**: CORRECT where moveCategory is present.

Examples:
- `MainScreen.kt:417-418`: `if (md.category == 0) mon.attack else mon.spAttack` ✅
- `MainScreen.kt:436-437`: `if (md.category == 0) enemyTarget.attack else enemyTarget.spAttack` ✅
- `CompactBattleScreen.kt:1361-1362`: `if (md.category == 0) attacker.attack else attacker.spAttack` ✅

However, without moveCategory passed to calc(), the internal burn logic and ability stat multipliers will still fail.

---

### 6. ✅ gBattleMons Double-Application Check
**Location**: AbilityEffects.kt:327-328, DamageCalculator.kt:289-295

**Current code:**
```kotlin
// AbilityEffects.kt:327-328
// Note: Huge Power/Pure Power are NOT applied here because they're
// already in the stats read from memory
```

**Status**: CORRECT. The code correctly notes that Intimidate and Huge Power are already baked into gBattleMons stats. Only conditional multipliers like Guts are applied.

---

### 7. ✅ Weather Multipliers
**Location**: WeatherEffects.kt:60-73

**Status**: CORRECT.
- Sun: Fire x1.5, Water x0.5 ✅
- Rain: Water x1.5, Fire x0.5 ✅

---

### 8. ⚠️ Documentation: Incorrect formula comment
**Location**: DamageCalculator.kt:91

**Current**: "Calculate damage for a Pokemon move using Gen 6+ damage formula."

**Problem**: This is actually the Gen 3 formula (as correctly noted on line 25).

**Fix**: Change to "Gen 3 damage formula".

---

## Summary

**Critical Issues (must fix):**
1. STAB calculation uses float math instead of integer division
2. Burn calculation uses float math instead of integer division
3. Missing moveCategory parameter in 8 call sites → breaks special move calculations

**Minor Issues:**
4. Documentation says "Gen 6+" instead of "Gen 3"

**Verified Correct:**
- Type effectiveness sequential multiplication
- Stat selection in callers (where moveCategory is present)
- No double-application of Intimidate/Huge Power
- Weather multipliers for fire/water moves
- Formula order (base calc → burn → +2 → STAB → type → weather)
