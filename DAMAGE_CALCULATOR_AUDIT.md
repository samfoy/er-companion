# DamageCalculator.kt Gen 7-9 Mechanics Audit Report
**Date:** 2026-03-26
**File:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/DamageCalculator.kt`
**Reference:** `/Users/samfp/emerogue/src/battle_util.c` (Emerald Rogue source)

---

## Executive Summary

✅ **4 CORRECT** | ❌ **1 INCORRECT** | ⚠️ **1 MISSING FEATURE**

The DamageCalculator correctly implements most Gen 7-9 mechanics, but has one critical bug with burn damage reduction and is missing critical hit support.

---

## Detailed Findings

### 1. ✅ CORRECT: Type Effectiveness Chart (18x18 with Fairy)

**Current Implementation (Lines 32-69):**
```kotlin
private val TYPE_CHART = arrayOf(
    // 18x18 type chart including Fairy type (index 17)
    floatArrayOf(1f, 1f, 1f, 1f, 1f, 0.5f, 1f, 0f, 0.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f),
    // ... [full 18x18 chart]
)
```

**Emerald Rogue Reference (battle_util.c:1187-1218):**
```c
static const uq4_12_t sTypeEffectivenessTable[NUMBER_OF_MON_TYPES][NUMBER_OF_MON_TYPES]
// 18 types including TYPE_FAIRY at index 17
```

**Verification:**
- Kotlin chart has 18 rows (one per attacking type) ✅
- Each row has 18 columns (one per defending type) ✅
- Init block validates dimensions at compile time (lines 72-77) ✅
- Fairy type effectiveness matches Gen 6+ mechanics ✅

**Status:** ✅ **CORRECT** - Full Gen 6+ type chart implementation

---

### 2. ✅ CORRECT: STAB Multiplier (1.5x, 2x with Adaptability)

**Current Implementation (Lines 273-277):**
```kotlin
val isStab = attackerTypes.contains(moveType)
if (isStab) {
    val stabMultiplier = com.ercompanion.data.AbilityData.getStabMultiplier(attackerAbility)
    baseDamage = (baseDamage * stabMultiplier).toInt()
}
```

**AbilityData.kt (Lines 284-287):**
```kotlin
fun getStabMultiplier(abilityId: Int): Float {
    val ability = ABILITY_EFFECTS[abilityId] ?: return 1.5f
    return ability.stabMultiplier  // 2.0f for Adaptability, 1.5f otherwise
}
```

**Emerald Rogue Reference (battle_util.c:9985-9993):**
```c
static inline uq4_12_t GetSameTypeAttackBonusModifier(u32 battlerAtk, u32 moveType, u32 move, u32 abilityAtk)
{
    if (!IS_BATTLER_OF_TYPE(battlerAtk, moveType) || move == MOVE_STRUGGLE || move == MOVE_NONE)
        return UQ_4_12(1.0);
    else
        return (abilityAtk == ABILITY_ADAPTABILITY) ? UQ_4_12(2.0) : UQ_4_12(1.5);
}
```

**Status:** ✅ **CORRECT** - Normal STAB is 1.5x, Adaptability is 2.0x

---

### 3. ❌ INCORRECT: Burn Attack Reduction

**Current Implementation (Lines 266-269):**
```kotlin
// Apply burn (0.5x if physical and burned, unless Guts ability)
if (isBurned && attackerAbility != 62) {  // 62 = Guts
    baseDamage = (baseDamage * 0.5f).toInt()
}
```

**Problem:** The code applies burn penalty to **ALL moves** when burned, but it should only apply to **PHYSICAL moves**.

**Emerald Rogue Reference (battle_util.c:10020-10033):**
```c
static inline uq4_12_t GetBurnOrFrostBiteModifier(u32 battlerAtk, u32 move, u32 abilityAtk)
{
    if (gBattleMons[battlerAtk].status1 & STATUS1_BURN
        && IS_MOVE_PHYSICAL(move)  // <-- Critical check!
        && (B_BURN_FACADE_DMG < GEN_6 || gBattleMoves[move].effect != EFFECT_FACADE)
        && abilityAtk != ABILITY_GUTS)
        return UQ_4_12(0.5);
    // ... (similar for frostbite on special moves)
    return UQ_4_12(1.0);
}
```

**Correct Behavior:**
- Burn reduces **physical** attack damage by 50% (Gen 1+)
- Does NOT affect special moves
- Exception: Guts ability ignores burn penalty (correctly implemented)
- Exception: Facade is NOT reduced by burn in Gen 6+ (NOT implemented)

**Fix Required:**
```kotlin
// Apply burn (0.5x if physical and burned, unless Guts ability)
if (isBurned && moveCategory == 0 && attackerAbility != 62) {  // 62 = Guts, 0 = Physical
    baseDamage = (baseDamage * 0.5f).toInt()
}
```

**Impact:**
- **HIGH** - Special attackers with burn are incorrectly penalized
- Example: Burned Charizard using Flamethrower would do 50% damage instead of 100%
- Physical moves are correctly penalized

**Status:** ❌ **INCORRECT** - Missing physical move category check

---

### 4. ✅ CORRECT: Burn Damage Per Turn (1/16 HP)

**Current Implementation (MoveSimulator.kt:582-585):**
```kotlin
StatusConditions.isBurned(battler.status) -> {
    // Burn: 1/16 max HP per turn
    damage = maxHp / 16
}
```

**Emerald Rogue Reference (battle_util.c:2842):**
```c
gBattleMoveDamage = GetNonDynamaxMaxHP(battler) / (B_BURN_DAMAGE >= GEN_7 ? 16 : 8);
```

**Config (battle.h:28):**
```c
#define B_BURN_DAMAGE GEN_LATEST // In Gen7+, burn damage is 1/16th of max HP instead of 1/8th
```

**Status:** ✅ **CORRECT** - Gen 7+ uses 1/16 HP per turn

**Note:** Regular poison is correctly 1/8 HP per turn (unchanged across generations)

---

### 5. ⚠️ MISSING FEATURE: Critical Hit Multiplier

**Current Implementation:** Not implemented in DamageCalculator.kt

**Emerald Rogue Reference (battle_util.c:10035-10040):**
```c
static inline uq4_12_t GetCriticalModifier(bool32 isCrit)
{
    if (isCrit)
        return B_CRIT_MULTIPLIER >= GEN_6 ? UQ_4_12(1.5) : UQ_4_12(2.0);
    return UQ_4_12(1.0);
}
```

**Config (battle.h:6):**
```c
#define B_CRIT_MULTIPLIER GEN_LATEST // In Gen6+, critical hits multiply damage by 1.5 instead of 2.0
```

**Expected Behavior:**
- Gen 6+ (Emerald Rogue uses): **1.5x damage**
- Gen 3-5: 2.0x damage

**Status:** ⚠️ **MISSING FEATURE** - Critical hits not calculated

**Note:** The README (calc/README_BATTLE_LINES.md:257) acknowledges this limitation:
> "Does not account for critical hits"

---

### 6. ✅ CORRECT: Regular Poison Damage (1/8 HP per turn)

**Current Implementation (MoveSimulator.kt:609-612):**
```kotlin
StatusConditions.isPoisoned(battler.status) && !StatusConditions.isToxic(battler.status) -> {
    // Regular poison: 1/8 max HP per turn
    damage = maxHp / 8
}
```

**Emerald Rogue Test (test/battle/status1/poison.c:4):**
```c
SINGLE_BATTLE_TEST("Poison deals 1/8th damage per turn")
```

**Status:** ✅ **CORRECT** - Regular poison unchanged across generations

---

## Summary Table

| Mechanic | Gen 7-9 Value | Current Implementation | Status |
|----------|---------------|------------------------|--------|
| Type Chart (Fairy) | 18x18 chart | 18x18 chart (lines 32-69) | ✅ CORRECT |
| STAB multiplier | 1.5x (2x with Adaptability) | 1.5x (2x with Adaptability) | ✅ CORRECT |
| Critical hit | 1.5x | Not implemented | ⚠️ MISSING |
| Burn damage/turn | 1/16 HP | 1/16 HP (MoveSimulator:584) | ✅ CORRECT |
| Burn attack reduction | Physical moves only | ALL moves | ❌ INCORRECT |
| Poison damage/turn | 1/8 HP | 1/8 HP (MoveSimulator:611) | ✅ CORRECT |

---

## Required Fixes

### Priority 1: Fix Burn Attack Reduction (CRITICAL BUG)

**File:** `/Users/samfp/er-companion/app/src/main/java/com/ercompanion/calc/DamageCalculator.kt`

**Line 266-269:**

**Current:**
```kotlin
// Apply burn (0.5x if physical and burned, unless Guts ability)
if (isBurned && attackerAbility != 62) {  // 62 = Guts
    baseDamage = (baseDamage * 0.5f).toInt()
}
```

**Fixed:**
```kotlin
// Apply burn (0.5x if physical and burned, unless Guts ability)
if (isBurned && moveCategory == 0 && attackerAbility != 62) {  // 62 = Guts, 0 = Physical
    baseDamage = (baseDamage * 0.5f).toInt()
}
```

**Additional Enhancement (Optional - Facade exception):**
```kotlin
// Apply burn (0.5x if physical and burned, unless Guts ability or Facade)
if (isBurned && moveCategory == 0 && attackerAbility != 62) {  // 62 = Guts, 0 = Physical
    // In Gen 6+, Facade is NOT penalized by burn
    val isFacade = moveData?.name?.equals("Facade", ignoreCase = true) == true
    if (!isFacade) {
        baseDamage = (baseDamage * 0.5f).toInt()
    }
}
```

---

## Testing Recommendations

### Test Case 1: Burned Physical Attacker
```kotlin
// Should reduce damage by 50%
val physical = calc(
    attackerLevel = 50,
    attackStat = 100,
    defenseStat = 100,
    movePower = 80,
    moveType = Types.NORMAL,
    moveCategory = 0, // Physical
    isBurned = true,
    attackerAbility = 0 // Not Guts
)
// Expected: damage reduced by ~50%
```

### Test Case 2: Burned Special Attacker
```kotlin
// Should NOT reduce damage
val special = calc(
    attackerLevel = 50,
    attackStat = 100,
    defenseStat = 100,
    movePower = 80,
    moveType = Types.FIRE,
    moveCategory = 1, // Special
    isBurned = true,
    attackerAbility = 0
)
// Expected: damage NOT reduced (BUG CURRENTLY - will be reduced incorrectly)
```

### Test Case 3: Burned with Guts
```kotlin
// Should NOT reduce damage even for physical moves
val guts = calc(
    attackerLevel = 50,
    attackStat = 100,
    defenseStat = 100,
    movePower = 80,
    moveType = Types.NORMAL,
    moveCategory = 0, // Physical
    isBurned = true,
    attackerAbility = 62 // Guts
)
// Expected: damage NOT reduced (CORRECT)
```

---

## References

- **Emerald Rogue Config:** `/Users/samfp/emerogue/include/config/battle.h`
- **Damage Calculation:** `/Users/samfp/emerogue/src/battle_util.c` (lines 9985-10040)
- **Type Chart:** `/Users/samfp/emerogue/src/battle_util.c` (lines 1187-1218)
- **EMERALD_ROGUE_MECHANICS.md:** `/Users/samfp/er-companion/EMERALD_ROGUE_MECHANICS.md`

---

## Conclusion

The DamageCalculator is **mostly correct** for Gen 7-9 mechanics, with excellent implementations of:
- Type effectiveness (18x18 chart with Fairy)
- STAB mechanics (1.5x, 2x with Adaptability)
- Burn damage per turn (1/16 HP)

However, there is **one critical bug** that needs immediate fixing:
- **Burn attack reduction is applied to ALL moves instead of just physical moves**

This bug causes burned special attackers to incorrectly deal 50% damage with special moves.
